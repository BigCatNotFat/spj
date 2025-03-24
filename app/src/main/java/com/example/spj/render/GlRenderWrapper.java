package com.example.spj.render;
import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;

import com.example.spj.VideoCompletionCallback;
import com.example.spj.camera.Camera2Helper;
import com.example.spj.encoder.VideoEncoder;
import com.example.spj.render.filters.CameraFilter;
import com.example.spj.render.filters.CustomFilter;
import com.example.spj.render.filters.FilterManager;
import com.example.spj.render.filters.InvertFilter;
import com.example.spj.render.filters.ScreenFilter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GlRenderWrapper implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "GlRenderWrapper";
    private GlRenderView mGlRenderView;
    private SurfaceTexture mSurfaceTexture;
    private int mTextureId = -1;
    private boolean isMirrored = false;
    private int currentFilterId = 0; // 0 = no filter

    private Camera2Helper mCamera2Helper;
    private CameraFilter mCameraFilter;
    private InvertFilter mInvertFilter;
    private ScreenFilter mScreenFilter;
    private VideoEncoder mVideoEncoder;

    private boolean mInvertFilterEnabled = false;
    private boolean mIsRecording = false;
    private float[] mMatrix = new float[16];

    private int mSurfaceWidth, mSurfaceHeight;
    private boolean mCameraInitialized = false;

    // 添加UI线程Handler
    private Handler mMainHandler = new Handler(Looper.getMainLooper());

    // 强制资源初始化标志
    private boolean mInitializedResources = false;

    // 跟踪帧可用状态
    private boolean mFrameAvailable = false;
    private final Object mFrameSyncObject = new Object();

    // 添加错误恢复计数
    private int mErrorCount = 0;
    private long mLastErrorTime = 0;
    private static final int MAX_ERRORS_BEFORE_RECOVERY = 5;
    private static final long ERROR_RESET_TIME_MS = 5000; // 5秒内的错误会被累计计数

    // 添加标志，跟踪相机是否提供了第一帧
    private boolean mFirstFrameReceived = false;
    private long mCameraOpenTime = 0;
    private CustomFilter mCustomFilter;
    private boolean mCustomFilterEnabled = false;
    private FilterManager mFilterManager;

    public GlRenderWrapper(GlRenderView glRenderView) {
        mGlRenderView = glRenderView;
        mFilterManager = new FilterManager(mGlRenderView.getContext());
        mCustomFilter = new CustomFilter(mGlRenderView.getContext(), null, null);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated");

        try {
            // 我们将等待所有资源的初始化完成
            releaseGlResources();

            // 在初始化OpenGL资源之前先检查状态
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            // 清除任何挂起的GL错误
            int errorBefore = GLES20.glGetError();
            if (errorBefore != GLES20.GL_NO_ERROR) {
                Log.d(TAG, "清除之前的OpenGL错误: " + errorBefore);
            }

            // 创建OpenGL纹理 - 重要：使用OES外部纹理，而不是普通的2D纹理
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            mTextureId = textures[0];
            Log.d(TAG, "创建纹理ID: " + mTextureId);

            // 设置纹理参数 - 使用适当的纹理目标
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

            // 检查OpenGL错误
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "纹理创建错误: " + error);
            }

            // 如果已存在SurfaceTexture，先释放它
            if (mSurfaceTexture != null) {
                mSurfaceTexture.setOnFrameAvailableListener(null);
                mSurfaceTexture.release();
                mSurfaceTexture = null;
            }

            // 创建新的SurfaceTexture
            mSurfaceTexture = new SurfaceTexture(mTextureId);
            mSurfaceTexture.setOnFrameAvailableListener(this);
            Log.d(TAG, "创建新的SurfaceTexture成功: " + mSurfaceTexture);

            // 初始化滤镜
            mCameraFilter = new CameraFilter(mGlRenderView.getContext());
            mInvertFilter = new InvertFilter(mGlRenderView.getContext());
            mScreenFilter = new ScreenFilter(mGlRenderView.getContext());

            // 重置错误计数和帧状态
            mErrorCount = 0;
            mLastErrorTime = 0;
            mFirstFrameReceived = false;

            // 标记资源已初始化
            mInitializedResources = true;

            // 通知View Surface已创建
            mGlRenderView.notifySurfaceCreated();
        } catch (Exception e) {
            Log.e(TAG, "onSurfaceCreated 异常", e);
            mInitializedResources = false;
        }
    }

    public void enableFilter(int filterId) {
        currentFilterId = filterId;

        // Disable all filters first
        mInvertFilterEnabled = false;
        mCustomFilterEnabled = false;

        // Enable the selected filter
        FilterManager.ShaderEffect effect = mFilterManager.getEffect(filterId);
        if (effect != null) {
            switch (filterId) {
                case 0: // No filter
                    break;
                case 1: // Invert filter - use the built-in one
                    mInvertFilterEnabled = true;
                    break;
                default: // Custom shaders
                    mCustomFilterEnabled = true;
                    mCustomFilter.setShaders(effect.getVertexShader(), effect.getFragmentShader());
                    // 确保为自定义滤镜准备帧缓冲区
                    if (mSurfaceWidth > 0 && mSurfaceHeight > 0) {
                        mCustomFilter.prepare(mSurfaceWidth, mSurfaceHeight);
                    }
                    break;
            }
        }

        Log.d(TAG, "设置滤镜: " + filterId);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(TAG, "onSurfaceChanged: " + width + "x" + height);

        if (width <= 0 || height <= 0) {
            Log.e(TAG, "无效的Surface尺寸");
            return;
        }

        // 保存Surface尺寸
        mSurfaceWidth = width;
        mSurfaceHeight = height;

        // 设置视口
        GLES20.glViewport(0, 0, width, height);

        try {
            // 准备滤镜
            if (mCameraFilter != null) {
                try {
                    mCameraFilter.prepare(width, height);
                } catch (Exception e) {
                    Log.e(TAG, "CameraFilter准备失败", e);
                }
            }
            if (mCustomFilter != null) {
                try {
                    mCustomFilter.prepare(width, height);
                } catch (Exception e) {
                    Log.e(TAG, "CustomFilter准备失败", e);
                }
            }
            if (mInvertFilter != null) {
                try {
                    mInvertFilter.prepare(width, height);
                } catch (Exception e) {
                    Log.e(TAG, "InvertFilter准备失败", e);
                }
            }
            if (mScreenFilter != null) {
                try {
                    mScreenFilter.prepare(width, height);
                } catch (Exception e) {
                    Log.e(TAG, "ScreenFilter准备失败", e);
                }
            }

            // 延迟初始化相机，确保OpenGL资源完全准备好
            mMainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    initCamera();
                }
            }, 500); // 延迟500ms初始化相机

        } catch (Exception e) {
            Log.e(TAG, "onSurfaceChanged 异常", e);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // 检查资源是否初始化
        if (!mInitializedResources) {
            Log.d(TAG, "资源未初始化，跳过渲染");
            return;
        }

        // 清除屏幕
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        // 检查相机是否刚刚打开但尚未收到第一帧
        if (mCameraInitialized && !mFirstFrameReceived) {
            long currentTime = System.currentTimeMillis();
            if (mCameraOpenTime > 0 && (currentTime - mCameraOpenTime > 3000)) {
                // 如果超过3秒没有收到帧，尝试重新初始化相机
                Log.w(TAG, "相机打开3秒后仍未收到第一帧，尝试重新初始化");
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        releaseCamera();
                        mMainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                initCamera();
                            }
                        }, 1000);
                    }
                });
                mCameraOpenTime = 0; // 重置计时器
                return;
            }
        }

        // 检查是否有新帧可用
        boolean frameProcessed = false;
        synchronized (mFrameSyncObject) {
            if (!mFrameAvailable) {
                return; // 没有新帧，无需渲染
            }

            if (mSurfaceTexture == null) {
                Log.d(TAG, "SurfaceTexture为空，跳过渲染");
                mFrameAvailable = false;
                return;
            }

            try {
                // 更新纹理前确保绑定了正确的纹理目标
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);

                // 更新纹理
                mSurfaceTexture.updateTexImage();
                mSurfaceTexture.getTransformMatrix(mMatrix);

                // 解绑纹理
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

                frameProcessed = true;

                // 标记已收到第一帧
                if (!mFirstFrameReceived) {
                    mFirstFrameReceived = true;
                    Log.d(TAG, "成功收到并处理第一帧");
                }

                // 成功处理帧，重置错误计数
                mErrorCount = 0;
            } catch (Exception e) {
                // 检查是否需要执行错误恢复
                long currentTime = System.currentTimeMillis();
                if (currentTime - mLastErrorTime > ERROR_RESET_TIME_MS) {
                    // 如果超过了重置时间，重置计数
                    mErrorCount = 1;
                } else {
                    // 否则增加计数
                    mErrorCount++;
                }
                mLastErrorTime = currentTime;

                Log.e(TAG, "更新纹理失败 (错误计数: " + mErrorCount + ")", e);

                // 如果错误次数超过阈值，尝试恢复相机
                if (mErrorCount >= MAX_ERRORS_BEFORE_RECOVERY) {
                    Log.w(TAG, "错误计数达到阈值，尝试恢复相机");
                    mErrorCount = 0;
                    mFirstFrameReceived = false;

                    // 通过主线程尝试恢复相机
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                // 释放相机并等待一段时间
                                releaseCamera();

                                // 延迟重新初始化相机
                                mMainHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.d(TAG, "尝试重新初始化相机");
                                        initCamera();
                                    }
                                }, 1000); // 等待1秒后重试
                            } catch (Exception ex) {
                                Log.e(TAG, "恢复相机过程中发生错误", ex);
                            }
                        }
                    });
                }

                // 添加小延迟避免连续错误
                try {
                    Thread.sleep(30);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                // 总是重置帧可用标志，避免卡住
                mFrameAvailable = false;
            }
        }

        // 如果没有成功处理帧，提前返回
        if (!frameProcessed) {
            return;
        }

        try {
            // 传递变换矩阵
            if (mCameraFilter != null) {
                mCameraFilter.setMatrix(mMatrix);
            } else {
                return; // 缺少关键滤镜，无法渲染
            }
            if (mCameraFilter != null) {
                mCameraFilter.setMatrix(mMatrix);
                // 设置镜像状态
                mCameraFilter.setMirrored(isMirrored);
            } else {
                return;
            }
            // 渲染流程
            int textureId = mCameraFilter.onDrawFrame(mTextureId);

            // 应用滤镜 - 这里是新增的代码
            if (mInvertFilterEnabled && mInvertFilter != null) {
                textureId = mInvertFilter.onDrawFrame(textureId);
            } else if (mCustomFilterEnabled && mCustomFilter != null) {
                textureId = mCustomFilter.onDrawFrame(textureId);
            }

            if (mScreenFilter != null) {
                mScreenFilter.onDrawFrame(textureId);
            }

            // 如果正在录制，发送到编码器
            if (mIsRecording && mVideoEncoder != null) {
                mVideoEncoder.frameAvailable(textureId, mSurfaceTexture.getTimestamp());
            }
        } catch (Exception e) {
            Log.e(TAG, "渲染帧失败", e);
        }
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (mFrameSyncObject) {
            mFrameAvailable = true;
        }
        mGlRenderView.requestRender();
    }

    public void initCamera() {
        if (mCameraInitialized) {
            Log.d(TAG, "相机已初始化");
            return;
        }

        // 检查必要条件
        if (mSurfaceTexture == null) {
            Log.e(TAG, "SurfaceTexture为空，无法初始化相机");
            return;
        }

        if (mSurfaceWidth <= 0 || mSurfaceHeight <= 0) {
            Log.e(TAG, "Surface尺寸无效，无法初始化相机");
            return;
        }

        // 记录相机开始初始化的时间
        mCameraOpenTime = System.currentTimeMillis();
        mFirstFrameReceived = false;

        // 在主线程创建和初始化相机
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mCamera2Helper == null) {
                        mCamera2Helper = new Camera2Helper((Activity) mGlRenderView.getContext());
                    }

                    // 打开相机 - 确保SurfaceTexture有效
                    final CountDownLatch latch = new CountDownLatch(1);

                    // 在GLThread上确认SurfaceTexture状态
                    mGlRenderView.queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            if (mSurfaceTexture != null && mInitializedResources) {
                                Log.d(TAG, "SurfaceTexture有效，正在打开相机");
                                try {
                                    // 配置SurfaceTexture
                                    mSurfaceTexture.setOnFrameAvailableListener(null); // 先重置监听器
                                    mSurfaceTexture.setOnFrameAvailableListener(GlRenderWrapper.this);

                                    // 设置默认缓冲区大小
                                    mSurfaceTexture.setDefaultBufferSize(mSurfaceWidth, mSurfaceHeight);

                                    // 打开相机
                                    mCamera2Helper.openCamera(mSurfaceWidth, mSurfaceHeight, mSurfaceTexture);
                                    mCameraInitialized = true;

                                    // 重置错误计数
                                    mErrorCount = 0;
                                    mLastErrorTime = 0;
                                } catch (Exception e) {
                                    Log.e(TAG, "配置SurfaceTexture错误", e);
                                }
                            } else {
                                Log.e(TAG, "SurfaceTexture无效，取消相机初始化");
                            }
                            latch.countDown();
                        }
                    });

                    // 等待GLThread确认，最多等待1秒
                    latch.await(1000, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    Log.e(TAG, "初始化相机失败", e);
                }
            }
        });
    }

    public void releaseCamera() {
        if (mCamera2Helper != null) {
            mCamera2Helper.closeCamera();
            mCameraInitialized = false;
            mFirstFrameReceived = false;
            Log.d(TAG, "相机已释放");
        }
    }

    private void releaseGlResources() {
        try {
            if (mCameraFilter != null) {
                mCameraFilter.release();
                mCameraFilter = null;
            }

            if (mInvertFilter != null) {
                mInvertFilter.release();
                mInvertFilter = null;
            }

            if (mScreenFilter != null) {
                mScreenFilter.release();
                mScreenFilter = null;
            }

            Log.d(TAG, "OpenGL资源已释放");
        } catch (Exception e) {
            Log.e(TAG, "释放OpenGL资源失败", e);
        }
    }

    public void enableInvertFilter(boolean enable) {
        mInvertFilterEnabled = enable;
    }
    public void setMirror(boolean mirrored) {
        this.isMirrored = mirrored;
        Log.d(TAG, "设置镜像模式: " + mirrored);
    }

    public void switchCamera() {
        if (mCamera2Helper != null) {
            final CountDownLatch latch = new CountDownLatch(1);

            // 在主线程切换相机
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        mCamera2Helper.switchCamera();
                        Log.d(TAG, "切换相机完成");
                    } catch (Exception e) {
                        Log.e(TAG, "切换相机失败", e);
                    } finally {
                        latch.countDown();
                    }
                }
            });

            try {
                // 等待切换完成
                latch.await(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "等待相机切换被中断", e);
            }
        }
    }

    public void startRecording(String outputPath) {
        if (mIsRecording) return;

        if (!mCameraInitialized || mCamera2Helper == null || mCamera2Helper.getPreviewSize() == null) {
            Log.e(TAG, "无法开始录制：相机未初始化");
            return;
        }

        try {
            // Use the camera preview size
            mVideoEncoder = new VideoEncoder(
                    mGlRenderView.getContext(),
                    mCamera2Helper.getPreviewSize().getWidth(),
                    mCamera2Helper.getPreviewSize().getHeight(),
                    EGL14.eglGetCurrentContext());

            mVideoEncoder.start(outputPath);
            mIsRecording = true;
            Log.d(TAG, "开始录制: " + outputPath);
        } catch (Exception e) {
            Log.e(TAG, "开始录制失败", e);
            if (mVideoEncoder != null) {
                mVideoEncoder.stop();
                mVideoEncoder = null;
            }
        }
    }

    /**
     * Stop recording with callback to be notified when encoding is complete.
     *
     * @param callback The callback to be notified when video encoding is finished
     */
    public void stopRecording(final VideoCompletionCallback callback) {
        if (!mIsRecording) return;

        mIsRecording = false;
        if (mVideoEncoder != null) {
            try {
                // Set completion listener
                mVideoEncoder.setOnEncodingFinishedListener(
                        new VideoEncoder.OnEncodingFinishedListener() {
                            @Override
                            public void onEncodingFinished(final String outputPath, boolean success) {
                                if (success) {
                                    Log.d(TAG, "视频编码完成: " + outputPath);
                                    // Notify on main thread
                                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (callback != null) {
                                                callback.onVideoSaved(outputPath);
                                            }
                                        }
                                    });
                                } else {
                                    Log.e(TAG, "视频编码失败");
                                }
                            }
                        }
                );

                mVideoEncoder.stop();
                mVideoEncoder = null;
                Log.d(TAG, "停止录制");
            } catch (Exception e) {
                Log.e(TAG, "停止录制失败", e);
            }
        }
    }

    /**
     * Stop recording without callback (for use in lifecycle methods).
     */
    public void stopRecording() {
        if (!mIsRecording) return;

        mIsRecording = false;
        if (mVideoEncoder != null) {
            try {
                mVideoEncoder.stop();
                mVideoEncoder = null;
                Log.d(TAG, "停止录制");
            } catch (Exception e) {
                Log.e(TAG, "停止录制失败", e);
            }
        }
    }

    public int getCameraId() {
        if (mCamera2Helper != null) {
            return mCamera2Helper.getCameraId();
        }
        return CameraCharacteristics.LENS_FACING_BACK; // 默认返回后置摄像头
    }

    public void release() {
        stopRecording();
        releaseCamera();
        releaseGlResources();

        if (mSurfaceTexture != null) {
            mSurfaceTexture.setOnFrameAvailableListener(null);
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }

        Log.d(TAG, "所有资源已释放");
    }
}