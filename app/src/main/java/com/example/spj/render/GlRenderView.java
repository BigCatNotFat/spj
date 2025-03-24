package com.example.spj.render;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;

import com.example.spj.VideoCompletionCallback;

public class GlRenderView extends GLSurfaceView {
    private static final String TAG = "GlRenderView";
    private GlRenderWrapper mRender;
    private boolean mIsSurfaceCreated = false;
    private boolean isMirrored = false;

    public GlRenderView(Context context) {
        this(context, null);
    }

    public GlRenderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    private void initView() {
        // 设置OpenGL版本
        setEGLContextClientVersion(2);

        // 使用更安全的EGL配置
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        // 创建和设置渲染器
        mRender = new GlRenderWrapper(this);
        setRenderer(mRender);

        // 使用RENDERMODE_WHEN_DIRTY模式减少不必要的渲染
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        // 第一次渲染请求
        requestRender();

        Log.d(TAG, "GlRenderView初始化完成");
    }

    /**
     * 获取渲染器实例，用于在CameraActivity中访问
     * @return GlRenderWrapper实例
     */
    public GlRenderWrapper getEGLHandler() {
        return mRender;
    }

    public void enableInvertFilter(boolean enable) {
        queueEvent(() -> {
            mRender.enableInvertFilter(enable);
        });
        requestRender(); // 请求立即重新渲染
    }

    public void enableFilter(int filterId) {
        queueEvent(() -> {
            mRender.enableFilter(filterId);
        });
        requestRender();
    }

    public void switchCamera() {
        queueEvent(() -> {
            mRender.switchCamera();
        });
    }

    public void toggleMirror() {
        isMirrored = !isMirrored;
        queueEvent(() -> {
            mRender.setMirror(isMirrored);
        });
        requestRender();
    }

    public boolean isMirrored() {
        return isMirrored;
    }

    public void startRecording(final String outputPath) {
        queueEvent(() -> {
            mRender.startRecording(outputPath);
        });
    }

    public void stopRecording(final VideoCompletionCallback callback) {
        queueEvent(() -> {
            mRender.stopRecording(callback);
        });
    }

    public void stopRecording() {
        queueEvent(() -> {
            mRender.stopRecording();
        });
    }

    @Override
    public void onPause() {
        Log.d(TAG, "GlRenderView onPause");

        // 先确保在OpenGL线程上停止相机
        queueEvent(() -> {
            mRender.releaseCamera();
        });

        // 然后暂停GLSurfaceView
        super.onPause();
        mIsSurfaceCreated = false;
    }

    @Override
    public void onResume() {
        Log.d(TAG, "GlRenderView onResume");

        // 先恢复GLSurfaceView
        super.onResume();

        // 然后尝试初始化相机
        if (mIsSurfaceCreated) {
            queueEvent(() -> {
                mRender.initCamera();
            });
        }
    }

    public void notifySurfaceCreated() {
        mIsSurfaceCreated = true;
    }
}