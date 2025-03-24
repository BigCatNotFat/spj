package com.example.spj.encoder;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import com.example.spj.render.filters.ScreenFilter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoEncoder {
    private static final String TAG = "VideoEncoder";
    private static final String MIME_TYPE_VIDEO = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String MIME_TYPE_AUDIO = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final int FRAME_RATE = 30;
    private static final int I_FRAME_INTERVAL = 1;
    private static final int BIT_RATE_FACTOR = 2;
    private static final int SAMPLE_RATE = 44100;  // 44.1kHz is a common sample rate
    private static final int CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_BIT_RATE = 128000;  // 128kbps is standard for AAC audio

    private static final int MAX_PENDING_FRAMES = 30;
    private static class EncoderFrame {
        int textureId;
        long timestamp;

        EncoderFrame(int textureId, long timestamp) {
            this.textureId = textureId;
            this.timestamp = timestamp;
        }
    }
    private ArrayBlockingQueue<EncoderFrame> mPendingFrames = new ArrayBlockingQueue<>(MAX_PENDING_FRAMES);
    private Context mContext;
    private int mWidth, mHeight;
    private String mOutputPath;
    private MediaCodec mVideoCodec;
    private MediaCodec mAudioCodec;
    private MediaMuxer mMediaMuxer;
    private Surface mInputSurface;
    private EGLDisplay mEglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEglSurface = EGL14.EGL_NO_SURFACE;
    private EGLConfig mEglConfig;
    private ScreenFilter mScreenFilter;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;
    private boolean mMuxerStarted;
    private AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private EGLContext mSharedContext;
    private CountDownLatch mEosLatch;

    // 修复: 添加基准时间戳
    private long mBaseTimestampNs = 0;
    private long mLastPresentationTimeUs = 0;
    private final Object mTimestampLock = new Object();

    // Audio recording components
    private AudioRecord mAudioRecord;
    private int mAudioBufferSize;
    private Thread mAudioThread;
    private AtomicBoolean mIsAudioRecording = new AtomicBoolean(false);

    // 添加编码完成回调
    public interface OnEncodingFinishedListener {
        void onEncodingFinished(String outputPath, boolean success);
    }

    private OnEncodingFinishedListener mListener;

    // 视频录制统计
    private int mFramesProcessed = 0;
    private long mStartTime = 0;

    public VideoEncoder(Context context, int width, int height, EGLContext sharedContext) {
        mContext = context;
        mWidth = height;  // 注意这里交换了宽高
        mHeight = width;
        mSharedContext = sharedContext;
        Log.d(TAG, "创建编码器 宽: " + mWidth + ", 高: " + mHeight + " (原始: " + width + "x" + height + ")");
    }

    public void setOnEncodingFinishedListener(OnEncodingFinishedListener listener) {
        mListener = listener;
    }

    public void start(String outputPath) {
        mOutputPath = outputPath;
        mFramesProcessed = 0;
        mStartTime = System.currentTimeMillis();
        mPendingFrames.clear();

        // 修复: 重置时间戳
        synchronized (mTimestampLock) {
            mBaseTimestampNs = 0;
            mLastPresentationTimeUs = 0;
        }

        // 确保目录存在
        File outputFile = new File(outputPath);
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean dirCreated = parentDir.mkdirs();
            Log.d(TAG, "创建目录 " + parentDir + ": " + dirCreated);
        }

        mHandlerThread = new HandlerThread("EncoderThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    prepareEncoder();
                    mIsRecording.set(true);
                    Log.d(TAG, "编码器已准备好，开始录制");

                    // 启动音频录制线程
                    startAudioRecording();

                    // 启动编码循环
                    processFrames();
                } catch (IOException e) {
                    Log.e(TAG, "准备编码器失败", e);
                    releaseEncoder();
                    if (mListener != null) {
                        mListener.onEncodingFinished(mOutputPath, false);
                    }
                }
            }
        });
    }

    public void stop() {
        Log.d(TAG, "停止录制");
        if (!mIsRecording.getAndSet(false)) {
            Log.d(TAG, "已经停止或未开始录制");
            return;
        }

        // 停止音频录制
        mIsAudioRecording.set(false);
        if (mAudioThread != null) {
            try {
                mAudioThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for audio thread to stop", e);
            }
            mAudioThread = null;
        }

        mEosLatch = new CountDownLatch(1);

        try {
            // 清空帧队列但等待当前队列中的所有帧处理完成
            final CountDownLatch queueDrainLatch = new CountDownLatch(1);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // 处理队列中剩余的帧
                    int remainingFrames = mPendingFrames.size();
                    Log.d(TAG, "处理剩余的 " + remainingFrames + " 帧");

                    // 帧已经停止入队，但我们要处理完已有的帧
                    try {
                        EncoderFrame frame;
                        while ((frame = mPendingFrames.poll()) != null) {
                            renderFrame(frame.textureId, frame.timestamp);
                            mFramesProcessed++;
                        }
                    } finally {
                        queueDrainLatch.countDown();
                    }
                }
            });

            // 等待队列排空，最多等待2秒
            boolean drained = queueDrainLatch.await(2000, TimeUnit.MILLISECONDS);
            if (!drained) {
                Log.w(TAG, "队列排空超时");
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 记录统计信息
                        long duration = System.currentTimeMillis() - mStartTime;
                        float fps = (duration > 0) ? (mFramesProcessed * 1000.0f / duration) : 0;
                        Log.d(TAG, String.format("录制结束: 处理 %d 帧, 时长 %.1f 秒, 平均 %.1f fps",
                                mFramesProcessed, duration/1000.0f, fps));

                        // 确保正确终止编码流程
                        drainEncoder(true);
                        releaseEncoder();
                        Log.d(TAG, "已释放编码器");

                        // 通知监听器编码完成
                        if (mListener != null) {
                            mListener.onEncodingFinished(mOutputPath, true);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "停止录制失败", e);
                        if (mListener != null) {
                            mListener.onEncodingFinished(mOutputPath, false);
                        }
                    } finally {
                        mEosLatch.countDown();
                    }
                }
            });

            // 等待编码器处理完成，最多等待5秒
            boolean completed = mEosLatch.await(5000, TimeUnit.MILLISECONDS);
            if (!completed) {
                Log.w(TAG, "等待编码器释放超时");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "等待编码器释放被中断", e);
        }

        try {
            if (mHandlerThread != null) {
                mHandlerThread.quitSafely();
                mHandlerThread.join(1000);
                mHandlerThread = null;
                mHandler = null;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "停止编码线程失败", e);
        }
    }

    private void processFrames() {
        while (mIsRecording.get()) {
            EncoderFrame frame = null;
            try {
                // 最多等待100毫秒获取帧
                frame = mPendingFrames.poll(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (frame != null) {
                renderFrame(frame.textureId, frame.timestamp);
                mFramesProcessed++;
                // 及时清理队列中积累的帧
                if (mPendingFrames.size() > MAX_PENDING_FRAMES / 2) {
                    Log.w(TAG, "帧队列积累过多: " + mPendingFrames.size());
                }
            }
        }
    }

    private void renderFrame(int textureId, long timestampNs) {
        try {
            if (mEglDisplay == EGL14.EGL_NO_DISPLAY) {
                Log.w(TAG, "EGL未初始化，跳过帧");
                return;
            }

            // 修复: 使用相对时间戳而不是系统绝对时间戳
            long presentationTimeUs;
            synchronized (mTimestampLock) {
                // 第一帧，设置基准时间戳并使用0作为起始时间
                if (mBaseTimestampNs == 0) {
                    mBaseTimestampNs = timestampNs;
                    presentationTimeUs = 0;
                } else {
                    // 计算相对于基准的微秒时间戳 (纳秒 -> 微秒)
                    long relativeTimeUs = (timestampNs - mBaseTimestampNs) / 1000;

                    // 确保时间戳始终递增
                    presentationTimeUs = Math.max(mLastPresentationTimeUs + 1000, relativeTimeUs);
                }

                // 更新最后的时间戳
                mLastPresentationTimeUs = presentationTimeUs;
            }

            // 确保在OpenGL线程上操作
            boolean eglResult = EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext);
            if (!eglResult) {
                Log.e(TAG, "eglMakeCurrent失败: " + EGL14.eglGetError());
                return;
            }

            mScreenFilter.onDrawFrame(textureId);

            // 修复: 使用正确处理的presentationTimeUs设置帧时间
            EGLExt.eglPresentationTimeANDROID(mEglDisplay, mEglSurface, presentationTimeUs * 1000);

            eglResult = EGL14.eglSwapBuffers(mEglDisplay, mEglSurface);
            if (!eglResult) {
                Log.e(TAG, "eglSwapBuffers失败: " + EGL14.eglGetError());
                return;
            }

            drainEncoder(false);
        } catch (Exception e) {
            Log.e(TAG, "处理帧异常", e);
        }
    }

    public void frameAvailable(final int textureId, final long timestamp) {
        if (!mIsRecording.get()) return;

        try {
            // 修复: 确保有足够的帧缓冲区空间
            if (mPendingFrames.remainingCapacity() == 0) {
                // 如果队列已满，丢弃最旧的几帧，确保新帧可以被处理
                int framesToDrop = MAX_PENDING_FRAMES / 4;  // 丢弃25%的帧
                for (int i = 0; i < framesToDrop && !mPendingFrames.isEmpty(); i++) {
                    mPendingFrames.poll();
                    Log.w(TAG, "帧队列已满，丢弃一帧");
                }
            }

            // 添加新帧到队列
            boolean added = mPendingFrames.offer(new EncoderFrame(textureId, timestamp), 5, TimeUnit.MILLISECONDS);
            if (!added) {
                Log.w(TAG, "添加帧到队列失败，队列可能已满");
            }
        } catch (Exception e) {
            Log.e(TAG, "添加帧到队列失败", e);
        }
    }

    private void prepareEncoder() throws IOException {
        // 配置视频编码器，使用较低的比特率
        int videoBitRate = mWidth * mHeight * FRAME_RATE / BIT_RATE_FACTOR;
        Log.d(TAG, "设置视频比特率: " + videoBitRate + " bps");

        MediaFormat videoFormat = MediaFormat.createVideoFormat(MIME_TYPE_VIDEO, mWidth, mHeight);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoBitRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        // 在配置中添加时间刻度信息以避免时长计算问题
        try {
            videoFormat.setLong("time-scale", 1000000); // 微秒时间单位
        } catch (Exception e) {
            // 忽略不支持的参数
            Log.w(TAG, "设置time-scale不受支持: " + e.getMessage());
        }

        // 可选：添加关键参数
        try {
            videoFormat.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);
            videoFormat.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31);
            videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            videoFormat.setInteger(MediaFormat.KEY_PRIORITY, 0); // 实时优先级
        } catch (Exception e) {
            // 忽略不支持的参数
            Log.w(TAG, "不支持的视频MediaFormat参数: " + e.getMessage());
        }

        // 配置音频编码器
        MediaFormat audioFormat = MediaFormat.createAudioFormat(MIME_TYPE_AUDIO, SAMPLE_RATE, 1);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);

        try {
            // 创建视频编码器
            mVideoCodec = MediaCodec.createEncoderByType(MIME_TYPE_VIDEO);
            mVideoCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mVideoCodec.createInputSurface();
            mVideoCodec.start();

            // 创建音频编码器
            mAudioCodec = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO);
            mAudioCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mAudioCodec.start();

            // 创建媒体混合器
            mMediaMuxer = new MediaMuxer(mOutputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mVideoTrackIndex = -1;
            mAudioTrackIndex = -1;
            mMuxerStarted = false;

            // 配置EGL
            prepareEGL();

            // 创建ScreenFilter
            mScreenFilter = new ScreenFilter(mContext);
            mScreenFilter.prepare(mWidth, mHeight);

            // 准备AudioRecord
            prepareAudioRecord();

            Log.d(TAG, "编码器准备完成: 尺寸=" + mWidth + "x" + mHeight);
        } catch (Exception e) {
            Log.e(TAG, "准备编码器失败", e);
            releaseEncoder();
            throw new IOException("准备编码器失败", e);
        }
    }

    private void prepareAudioRecord() {
        // 计算音频缓冲区大小
        mAudioBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNELS, AUDIO_FORMAT);

        // Double the minimum buffer size for better performance
        mAudioBufferSize = mAudioBufferSize * 2;

        Log.d(TAG, "音频缓冲区大小: " + mAudioBufferSize + " 字节");

        // 创建AudioRecord实例
        mAudioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNELS,
                AUDIO_FORMAT,
                mAudioBufferSize);

        if (mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord初始化失败");
            return;
        }

        Log.d(TAG, "AudioRecord准备完成: 采样率=" + SAMPLE_RATE + ", 缓冲区大小=" + mAudioBufferSize);
    }

    private void startAudioRecording() {
        if (mAudioRecord == null || mAudioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord未初始化，无法开始录音");
            return;
        }

        // 启动音频录制
        try {
            mAudioRecord.startRecording();

            if (mAudioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord启动失败");
                return;
            }

            mIsAudioRecording.set(true);

            // 创建并启动音频处理线程
            mAudioThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    processAudio();
                }
            }, "AudioRecordThread");

            mAudioThread.start();
            Log.d(TAG, "音频录制线程已启动");
        } catch (Exception e) {
            Log.e(TAG, "启动音频录制失败", e);
            if (mAudioRecord != null) {
                mAudioRecord.release();
                mAudioRecord = null;
            }
        }
    }

    private void amplifyAudio(byte[] audioData, int bytesRead, float amplification) {
        // 对于16位PCM音频 (AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT)
        // 将byte[]转换为short[](16位样本)
        short[] shorts = new short[bytesRead / 2];
        ByteBuffer.wrap(audioData, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);

        // 放大每个样本
        for (int i = 0; i < shorts.length; i++) {
            // 转换为float进行乘法运算
            float sample = shorts[i] * amplification;

            // 应用限幅以避免溢出
            if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
            if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;

            // 转换回short
            shorts[i] = (short) sample;
        }

        // 将short[]转换回byte[]
        ByteBuffer.wrap(audioData, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts);
    }

    private void processAudio() {
        // 创建一个缓冲区来存放从AudioRecord读取的音频数据
        byte[] audioData = new byte[mAudioBufferSize];

        // 使用0作为起始时间戳，而不是系统时间
        long startTimeUs = 0;
        long totalBytesRead = 0;

        // 计算每个音频样本的时长（微秒）
        // 对于16位单声道PCM，每个样本2字节
        final double BYTES_PER_SAMPLE = 2.0; // 16-bit PCM = 2 bytes per sample
        final double US_PER_BYTE = 1000000.0 / (BYTES_PER_SAMPLE * SAMPLE_RATE);

        // 使用小块处理音频，避免缓冲区溢出问题
        final int CHUNK_SIZE = 1024; // 1KB块，适合大多数编码器缓冲区

        try {
            while (mIsAudioRecording.get()) {
                // 读取音频数据到字节数组
                int bytesRead = mAudioRecord.read(audioData, 0, audioData.length);

                if (bytesRead <= 0) {
                    Log.w(TAG, "读取音频数据失败: " + bytesRead);
                    continue;
                }

                // 放大音频音量 (2.0倍)
                amplifyAudio(audioData, bytesRead, 2.0f);

                // 更新总读取字节数
                totalBytesRead += bytesRead;

                // 计算这块数据的时间戳（相对于起始时间的微秒偏移）
                long presentationTimeUs = startTimeUs + (long)(totalBytesRead * US_PER_BYTE);

                // 按块处理数据，确保不会超出编码器缓冲区
                int bytesProcessed = 0;
                while (bytesProcessed < bytesRead) {
                    // 获取MediaCodec输入缓冲区
                    int inputBufferIndex = mAudioCodec.dequeueInputBuffer(10000);
                    if (inputBufferIndex < 0) {
                        // 如果没有可用缓冲区，尝试从编码器获取已编码的数据
                        drainAudioEncoder(false);
                        continue;
                    }

                    ByteBuffer inputBuffer = mAudioCodec.getInputBuffer(inputBufferIndex);
                    if (inputBuffer == null) {
                        Log.e(TAG, "获取到空的编码器输入缓冲区");
                        break;
                    }

                    inputBuffer.clear();

                    // 确定本次写入的字节数，不超过剩余数据量和缓冲区大小
                    int maxBytes = Math.min(bytesRead - bytesProcessed,
                            Math.min(CHUNK_SIZE, inputBuffer.remaining()));

                    // 将数据写入输入缓冲区
                    inputBuffer.put(audioData, bytesProcessed, maxBytes);

                    // 更新已处理字节数
                    bytesProcessed += maxBytes;

                    // 计算这块数据对应的时间戳
                    long chunkOffsetUs = (long)((double)bytesProcessed * US_PER_BYTE);
                    long chunkPresentationTimeUs = startTimeUs + chunkOffsetUs;

                    // 将数据提交给编码器
                    mAudioCodec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            maxBytes,
                            chunkPresentationTimeUs,
                            0);
                }

                // 尝试从编码器获取已编码的数据
                drainAudioEncoder(false);
            }

            // 处理最后的数据
            int inputBufferIndex = mAudioCodec.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                mAudioCodec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        0,
                        startTimeUs + (long)(totalBytesRead * US_PER_BYTE),
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }

            // 耗尽编码器
            drainAudioEncoder(true);

        } catch (Exception e) {
            Log.e(TAG, "音频处理过程中发生异常", e);
        } finally {
            // 停止并释放AudioRecord
            try {
                if (mAudioRecord != null) {
                    if (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        mAudioRecord.stop();
                    }
                    mAudioRecord.release();
                    mAudioRecord = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "释放AudioRecord失败", e);
            }
        }
    }

    private void drainAudioEncoder(boolean endOfStream) {
        if (mAudioCodec == null) return;

        // 设置耗尽编码器超时时间
        final int TIMEOUT_USEC = endOfStream ? 10000 : 0;

        while (true) {
            // 获取已编码的输出
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int encoderStatus = mAudioCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);

            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // 没有更多输出
                if (!endOfStream) break;
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // 编码器格式已更改
                if (mMuxerStarted) {
                    Log.w(TAG, "音频编码器格式在编码过程中更改，这是不正常的");
                    continue;
                }

                // 获取新的格式
                MediaFormat audioFormat = mAudioCodec.getOutputFormat();
                Log.d(TAG, "音频编码器输出格式已更改: " + audioFormat);

                // 添加音频轨道
                try {
                    mAudioTrackIndex = mMediaMuxer.addTrack(audioFormat);
                    Log.d(TAG, "添加音频轨道: " + mAudioTrackIndex);

                    // 如果视频轨道已添加，则启动混合器
                    checkStartMuxer();
                } catch (Exception e) {
                    Log.e(TAG, "添加音频轨道失败", e);
                }

            } else if (encoderStatus >= 0) {
                // 有效的已编码数据
                ByteBuffer encodedData = mAudioCodec.getOutputBuffer(encoderStatus);

                if (encodedData == null) {
                    Log.e(TAG, "获取到空的编码器输出缓冲区");
                    mAudioCodec.releaseOutputBuffer(encoderStatus, false);
                    continue;
                }

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // 忽略codec配置数据
                    bufferInfo.size = 0;
                }

                if (bufferInfo.size > 0 && mMuxerStarted) {
                    // 调整缓冲区边界
                    encodedData.position(bufferInfo.offset);
                    encodedData.limit(bufferInfo.offset + bufferInfo.size);

                    // 写入混合器
                    try {
                        mMediaMuxer.writeSampleData(mAudioTrackIndex, encodedData, bufferInfo);
                    } catch (Exception e) {
                        Log.e(TAG, "写入音频样本数据失败", e);
                    }
                }

                // 释放输出缓冲区
                mAudioCodec.releaseOutputBuffer(encoderStatus, false);

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    // 到达流结束
                    if (!endOfStream) {
                        Log.w(TAG, "音频编码器意外地结束了流");
                    }
                    break;
                }
            }
        }
    }

    private void prepareEGL() {
        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("无法获取EGL显示");
        }

        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            throw new RuntimeException("无法初始化EGL");
        }

        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(mEglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)) {
            throw new RuntimeException("无法选择EGL配置");
        }
        mEglConfig = configs[0];

        int[] contextAttributes = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        mEglContext = EGL14.eglCreateContext(mEglDisplay, mEglConfig, mSharedContext, contextAttributes, 0);
        if (mEglContext == EGL14.EGL_NO_CONTEXT) {
            throw new RuntimeException("无法创建EGL上下文");
        }

        int[] surfaceAttributes = {
                EGL14.EGL_NONE
        };
        mEglSurface = EGL14.eglCreateWindowSurface(mEglDisplay, mEglConfig, mInputSurface, surfaceAttributes, 0);
        if (mEglSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException("无法创建EGL表面");
        }

        if (!EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            throw new RuntimeException("无法设置当前EGL上下文");
        }
    }

    private void drainEncoder(boolean endOfStream) {
        if (endOfStream && mIsRecording.get()) {
            Log.d(TAG, "发送结束标志到编码器");
            try {
                mVideoCodec.signalEndOfInputStream();
            } catch (Exception e) {
                Log.e(TAG, "signalEndOfInputStream失败", e);
                return;
            }
        }

        // 修复: 增加超时时间使更多帧得到处理
        final int TIMEOUT_USEC = endOfStream ? 30000 : 5000;

        try {
            ByteBuffer[] encoderOutputBuffers = mVideoCodec.getOutputBuffers();
            int retriesCount = 0;
            final int MAX_RETRIES = endOfStream ? 30 : 5; // 结束时多次尝试确保所有帧处理完成

            while ((mIsRecording.get() || endOfStream) && retriesCount < MAX_RETRIES) {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                int encoderStatus = mVideoCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);

                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) break;  // 非结束状态，直接返回
                    retriesCount++;
                    Log.d(TAG, "等待编码器输出 (重试 " + retriesCount + "/" + MAX_RETRIES + ")");
                    try {
                        Thread.sleep(10); // 短暂休眠，避免CPU占用过高
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 编码器格式改变，设置轨道
                    if (mMuxerStarted) {
                        Log.w(TAG, "视频格式已经改变，忽略");
                        continue;
                    }

                    MediaFormat format = mVideoCodec.getOutputFormat();
                    Log.d(TAG, "视频编码器输出格式改变: " + format);

                    try {
                        mVideoTrackIndex = mMediaMuxer.addTrack(format);
                        Log.d(TAG, "添加视频轨道: " + mVideoTrackIndex);

                        // 检查是否可以启动混合器
                        checkStartMuxer();
                    } catch (Exception e) {
                        Log.e(TAG, "添加视频轨道失败", e);
                        return;
                    }
                } else if (encoderStatus >= 0) {
                    // 有效的编码数据
                    if (encoderOutputBuffers == null) {
                        encoderOutputBuffers = mVideoCodec.getOutputBuffers();
                    }

                    if (encoderStatus >= encoderOutputBuffers.length) {
                        Log.w(TAG, "无效的encoderStatus: " + encoderStatus);
                        continue;
                    }

                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        Log.e(TAG, "encoderOutputBuffer为空");
                        continue;
                    }

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // 编码器配置数据
                        bufferInfo.size = 0;
                    }

                    if (bufferInfo.size > 0 && mMuxerStarted) {
                        // 调整ByteBuffer位置
                        encodedData.position(bufferInfo.offset);
                        encodedData.limit(bufferInfo.offset + bufferInfo.size);

                        try {
                            // 修复: 调试用，记录写入的帧时间戳
                            Log.d(TAG, "写入视频帧, pts=" + bufferInfo.presentationTimeUs);
                            mMediaMuxer.writeSampleData(mVideoTrackIndex, encodedData, bufferInfo);
                        } catch (Exception e) {
                            Log.e(TAG, "写入视频采样数据失败", e);
                        }
                    }

                    mVideoCodec.releaseOutputBuffer(encoderStatus, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "收到视频编码器结束标志");
                        endOfStream = false;
                        break;  // 结束
                    }

                    // 重置尝试计数，因为我们成功处理了一帧
                    retriesCount = 0;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "drainEncoder异常", e);
        }
    }

    private void checkStartMuxer() {
        // 确保视频和音频轨道都已添加后再启动muxer
        if (!mMuxerStarted && mVideoTrackIndex >= 0 && mAudioTrackIndex >= 0) {
            try {
                mMediaMuxer.start();
                mMuxerStarted = true;
                Log.d(TAG, "MediaMuxer已启动");
            } catch (Exception e) {
                Log.e(TAG, "启动MediaMuxer失败", e);
                throw new RuntimeException("启动MediaMuxer失败", e);
            }
        }
    }

    private void releaseEncoder() {
        Log.d(TAG, "释放编码器资源");

        // 停止编码
        if (mVideoCodec != null) {
            try {
                mVideoCodec.stop();
                Log.d(TAG, "VideoCodec已停止");
            } catch (Exception e) {
                Log.e(TAG, "停止VideoCodec失败", e);
            }
        }

        if (mAudioCodec != null) {
            try {
                mAudioCodec.stop();
                Log.d(TAG, "AudioCodec已停止");
            } catch (Exception e) {
                Log.e(TAG, "停止AudioCodec失败", e);
            }
        }

        // 停止muxer
        if (mMediaMuxer != null && mMuxerStarted) {
            try {
                mMediaMuxer.stop();
                Log.d(TAG, "MediaMuxer已停止");
            } catch (Exception e) {
                Log.e(TAG, "停止MediaMuxer失败", e);
            }
            mMuxerStarted = false;
        }

        // 先确保EGL环境释放
        if (mEglDisplay != EGL14.EGL_NO_DISPLAY) {
            try {
                // 确保我们在释放资源前处于当前上下文
                EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);

                if (mEglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(mEglDisplay, mEglSurface);
                    mEglSurface = EGL14.EGL_NO_SURFACE;
                }

                if (mEglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(mEglDisplay, mEglContext);
                    mEglContext = EGL14.EGL_NO_CONTEXT;
                }

                EGL14.eglTerminate(mEglDisplay);
                Log.d(TAG, "EGL资源已释放");
            } catch (Exception e) {
                Log.e(TAG, "释放EGL资源失败", e);
            } finally {
                mEglDisplay = EGL14.EGL_NO_DISPLAY;
            }
        }

        // 释放ScreenFilter
        if (mScreenFilter != null) {
            try {
                mScreenFilter.release();
            } catch (Exception e) {
                Log.e(TAG, "释放ScreenFilter失败", e);
            } finally {
                mScreenFilter = null;
            }
        }

        // 释放Surface
        if (mInputSurface != null) {
            try {
                mInputSurface.release();
            } catch (Exception e) {
                Log.e(TAG, "释放InputSurface失败", e);
            } finally {
                mInputSurface = null;
            }
        }

        // 释放MediaCodec
        if (mVideoCodec != null) {
            try {
                mVideoCodec.release();
                Log.d(TAG, "VideoCodec已释放");
            } catch (Exception e) {
                Log.e(TAG, "释放VideoCodec失败", e);
            } finally {
                mVideoCodec = null;
            }
        }

        if (mAudioCodec != null) {
            try {
                mAudioCodec.release();
                Log.d(TAG, "AudioCodec已释放");
            } catch (Exception e) {
                Log.e(TAG, "释放AudioCodec失败", e);
            } finally {
                mAudioCodec = null;
            }
        }

        // 释放MediaMuxer
        if (mMediaMuxer != null) {
            try {
                mMediaMuxer.release();
                Log.d(TAG, "MediaMuxer已释放");
            } catch (Exception e) {
                Log.e(TAG, "释放muxer失败", e);
            } finally {
                mMediaMuxer = null;
            }
        }

        // 清理队列
        mPendingFrames.clear();
    }
}