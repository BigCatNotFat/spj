package com.example.spj.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2Helper {
    private static final String TAG = "Camera2Helper";
    private Activity mActivity;
    // 尝试从后置相机开始，可能更稳定
    private int mCameraId = CameraCharacteristics.LENS_FACING_BACK;
    private String mCameraIdStr;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private Size mPreviewSize;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private SurfaceTexture mSurfaceTexture;

    // 添加信号量来确保线程安全
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);

    // 用于控制流程的状态标志
    private volatile boolean mConfigured = false;
    private volatile boolean mCameraOpened = false;

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            try {
                Log.d(TAG, "相机已打开");
                // 相机已打开，释放信号量
                mCameraOpenCloseLock.release();
                mCameraDevice = cameraDevice;
                mCameraOpened = true;

                // 保证SurfaceTexture存在并有效
                if (mSurfaceTexture != null) {
                    createCameraPreviewSession();
                } else {
                    Log.e(TAG, "SurfaceTexture为空，无法创建预览会话");
                }
            } catch (Exception e) {
                Log.e(TAG, "相机打开回调异常", e);
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "相机已断开连接");
            mCameraOpenCloseLock.release();
            mCameraOpened = false;
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            Log.e(TAG, "相机出错: " + error);
            mCameraOpenCloseLock.release();
            mCameraOpened = false;
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    public Camera2Helper(Activity activity) {
        this.mActivity = activity;
        startBackgroundThread();
    }

    public void openCamera(int width, int height, SurfaceTexture surfaceTexture) {
        if (surfaceTexture == null) {
            Log.e(TAG, "SurfaceTexture为空，无法打开相机");
            return;
        }

        // 保存SurfaceTexture引用
        this.mSurfaceTexture = surfaceTexture;

        Log.d(TAG, "尝试打开相机: 宽=" + width + ", 高=" + height);

        if (ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "无相机权限");
            return;
        }

        try {
            // 获取相机前先锁定
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("获取相机访问锁超时");
            }

            CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
            // 列出所有可用相机ID
            String[] cameraIds = manager.getCameraIdList();
            Log.d(TAG, "可用相机数量: " + cameraIds.length);
            for (String id : cameraIds) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                Log.d(TAG, "相机ID: " + id + ", 方向: " + (facing == CameraCharacteristics.LENS_FACING_FRONT ? "前置" : "后置"));
            }

            // 获取相机ID
            mCameraIdStr = findCameraId(manager, mCameraId);
            if (mCameraIdStr == null) {
                Log.e(TAG, "找不到相机ID: " + mCameraId + "，尝试其他相机");
                // 如果找不到指定的相机，尝试其他的
                mCameraId = (mCameraId == CameraCharacteristics.LENS_FACING_FRONT) ?
                        CameraCharacteristics.LENS_FACING_BACK : CameraCharacteristics.LENS_FACING_FRONT;
                mCameraIdStr = findCameraId(manager, mCameraId);

                if (mCameraIdStr == null) {
                    mCameraOpenCloseLock.release();
                    Log.e(TAG, "没有找到任何可用相机");
                    return;
                }
            }

            Log.d(TAG, "使用相机ID: " + mCameraIdStr);

            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraIdStr);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                mCameraOpenCloseLock.release();
                Log.e(TAG, "无法获取相机配置");
                return;
            }

            // 列出可用的预览尺寸
            Size[] availableSizes = map.getOutputSizes(SurfaceTexture.class);
            Log.d(TAG, "可用预览尺寸:");
            for (Size size : availableSizes) {
                Log.d(TAG, size.getWidth() + "x" + size.getHeight());
            }

            // 选择合适的预览尺寸
//            mPreviewSize = chooseOptimalSize(availableSizes, width, height);
            mPreviewSize = new Size(1920, 1080);
            Log.d(TAG, "选择的预览尺寸: " + mPreviewSize.getWidth() + "x" + mPreviewSize.getHeight());

            // 打开相机
            manager.openCamera(mCameraIdStr, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            mCameraOpenCloseLock.release();
            Log.e(TAG, "相机访问异常", e);
        } catch (InterruptedException e) {
            mCameraOpenCloseLock.release();
            Log.e(TAG, "打开相机被中断", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            mCameraOpenCloseLock.release();
            Log.e(TAG, "打开相机失败", e);
        }
    }

    private String findCameraId(CameraManager manager, int facing) {
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer cameraFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cameraFacing != null && cameraFacing == facing) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "查找相机ID失败", e);
        }
        return null;
    }

    public void updatePreviewTexture(SurfaceTexture surfaceTexture) {
        if (mCameraDevice == null || !mCameraOpened) {
            Log.d(TAG, "相机未打开，保存SurfaceTexture供后续使用");
            this.mSurfaceTexture = surfaceTexture;
            return;
        }

        Log.d(TAG, "更新预览SurfaceTexture");

        // 如果已经有会话，先关闭它
        if (mCaptureSession != null) {
            try {
                mCaptureSession.stopRepeating();
                mCaptureSession.close();
                mCaptureSession = null;
            } catch (Exception e) {
                Log.e(TAG, "关闭现有会话失败", e);
            }
        }

        this.mSurfaceTexture = surfaceTexture;

        // 创建新的预览会话
        createCameraPreviewSession();
    }

    private void createCameraPreviewSession() {
        try {
            if (mCameraDevice == null) {
                Log.e(TAG, "相机设备为空，无法创建预览会话");
                return;
            }

            if (mSurfaceTexture == null) {
                Log.e(TAG, "SurfaceTexture为空，无法创建预览会话");
                return;
            }

            if (mPreviewSize == null) {
                Log.e(TAG, "预览尺寸为空，无法创建预览会话");
                return;
            }

            Log.d(TAG, "创建相机预览会话");

            // 配置SurfaceTexture
            mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface surface = new Surface(mSurfaceTexture);

            // 创建预览请求
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // 创建CaptureSession
            mCameraDevice.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (mCameraDevice == null) {
                                Log.e(TAG, "相机会话创建时相机已关闭");
                                return;
                            }

                            mCaptureSession = session;
                            mConfigured = true;
                            try {
                                // 设置自动对焦
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // 设置自动曝光
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // 开始预览
                                CaptureRequest previewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(previewRequest,
                                        new CameraCaptureSession.CaptureCallback() {
                                            @Override
                                            public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                                                         @NonNull CaptureRequest request,
                                                                         long timestamp,
                                                                         long frameNumber) {
                                                if (frameNumber % 30 == 0) { // 每30帧打印一次，避免日志过多
                                                    Log.d(TAG, "捕获帧: " + frameNumber);
                                                }
                                            }
                                        }, mBackgroundHandler);
                                Log.d(TAG, "相机预览已启动");
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "设置相机预览失败", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "相机会话配置失败");
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "创建相机预览会话失败", e);
        } catch (Exception e) {
            Log.e(TAG, "创建预览会话时发生异常", e);
        }
    }

    public void switchCamera() {
        closeCamera();
        mCameraId = (mCameraId == CameraCharacteristics.LENS_FACING_FRONT) ?
                CameraCharacteristics.LENS_FACING_BACK : CameraCharacteristics.LENS_FACING_FRONT;
        Log.d(TAG, "切换到相机: " + (mCameraId == CameraCharacteristics.LENS_FACING_FRONT ? "前置" : "后置"));

        // 如果SurfaceTexture存在，重新打开相机
        if (mSurfaceTexture != null) {
            openCamera(mPreviewSize != null ? mPreviewSize.getWidth() : 1280,
                    mPreviewSize != null ? mPreviewSize.getHeight() : 720,
                    mSurfaceTexture);
        }
    }

    public int getCameraId() {
        return mCameraId;
    }

    public Size getPreviewSize() {
        return mPreviewSize;
    }

    private Size chooseOptimalSize(Size[] choices, int width, int height) {
        if (choices == null || choices.length == 0) return new Size(width, height);

        // 计算屏幕比例
        float targetRatio = (float) width / height;
        Log.d(TAG, "目标屏幕比例: " + targetRatio + " (" + width + "x" + height + ")");

        // 记录所有选项
        for (Size option : choices) {
            float ratio = (float) option.getWidth() / option.getHeight();
            Log.d(TAG, "相机支持分辨率: " + option.getWidth() + "x" + option.getHeight() + " 比例: " + ratio);
        }

        // 首先，尝试找到与屏幕比例最接近的选项
        Size bestMatch = null;
        float minRatioDiff = Float.MAX_VALUE;

        for (Size option : choices) {
            float ratio = (float) option.getWidth() / option.getHeight();
            float ratioDiff = Math.abs(ratio - targetRatio);

            if (ratioDiff < minRatioDiff) {
                minRatioDiff = ratioDiff;
                bestMatch = option;
            }
        }

        // 如果找到了与屏幕比例十分接近的选项（差异小于5%），直接使用它
        if (minRatioDiff < 0.05 && bestMatch != null) {
            Log.d(TAG, "选择与屏幕比例接近的最佳尺寸: " + bestMatch.getWidth() + "x" + bestMatch.getHeight());
            return bestMatch;
        }

        // 如果没有接近的比例，则获取所有比例接近的选项，按面积从大到小排序
        List<Size> sameRatioOptions = new ArrayList<>();

        for (Size option : choices) {
            float ratio = (float) option.getWidth() / option.getHeight();
            if (Math.abs(ratio - targetRatio) < 0.2) { // 20%的容差
                sameRatioOptions.add(option);
            }
        }

        if (!sameRatioOptions.isEmpty()) {
            // 按面积从大到小排序
            Collections.sort(sameRatioOptions, new Comparator<Size>() {
                @Override
                public int compare(Size o1, Size o2) {
                    return Integer.compare(o2.getWidth() * o2.getHeight(),
                            o1.getWidth() * o1.getHeight());
                }
            });

            // 选择面积不超过1080p的最大选项
            for (Size option : sameRatioOptions) {
                if (option.getWidth() <= 1920 && option.getHeight() <= 1080) {
                    Log.d(TAG, "选择相似比例的最佳尺寸: " + option.getWidth() + "x" + option.getHeight());
                    return option;
                }
            }

            // 如果所有选项都大于1080p，选择最小的一个
            Size result = sameRatioOptions.get(sameRatioOptions.size() - 1);
            Log.d(TAG, "选择相似比例的可用尺寸: " + result.getWidth() + "x" + result.getHeight());
            return result;
        }

        // 如果没有比例相似的选项，返回最接近的那个
        if (bestMatch != null) {
            Log.d(TAG, "选择次优尺寸: " + bestMatch.getWidth() + "x" + bestMatch.getHeight());
            return bestMatch;
        }

        // 如果什么都没找到，返回第一个选项
        Log.d(TAG, "选择默认尺寸: " + choices[0].getWidth() + "x" + choices[0].getHeight());
        return choices[0];
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (mBackgroundThread != null) {
            mBackgroundThread.quitSafely();
            try {
                mBackgroundThread.join(500);  // 最多等待500毫秒
                mBackgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "停止后台线程失败", e);
            }
        }
    }

    public void closeCamera() {
        Log.d(TAG, "关闭相机");
        try {
            mCameraOpenCloseLock.acquire();
            mConfigured = false;
            mCameraOpened = false;

            if (mCaptureSession != null) {
                try {
                    mCaptureSession.stopRepeating();
                } catch (Exception e) {
                    // 忽略这个异常，可能会在关闭时发生
                }
                mCaptureSession.close();
                mCaptureSession = null;
            }

            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "关闭相机被中断", e);
            Thread.currentThread().interrupt();
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    public void release() {
        closeCamera();
        stopBackgroundThread();
        mSurfaceTexture = null;
    }

    public boolean isConfigured() {
        return mConfigured;
    }

    public boolean isCameraOpened() {
        return mCameraOpened;
    }
}