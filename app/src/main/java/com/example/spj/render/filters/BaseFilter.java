package com.example.spj.render.filters;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public abstract class BaseFilter {
    private static final String TAG = "BaseFilter";

    protected Context mContext;
    protected FloatBuffer mGlVertexBuffer;
    protected FloatBuffer mGlTextureBuffer;
    protected int mProgramId;
    protected int vPosition;
    protected int vCoord;
    protected int vTexture;
    protected int vMatrix;

    // FBO相关
    protected int[] mFrameBuffers;
    protected int[] mFBOTextures;
    protected int mOutputWidth, mOutputHeight;

    public BaseFilter(Context context) {
        this.mContext = context;

        // 顶点坐标
        float[] VERTEX = {
                -1.0f, 1.0f,   // 左上
                1.0f, 1.0f,    // 右上
                -1.0f, -1.0f,  // 左下
                1.0f, -1.0f    // 右下
        };
//左下，右下，左上，右上
        // 纹理坐标
        float[] TEXTURE = {
                0.0f, 1f,
                1.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f
        };
//        float[] TEXTURE = {
//                1,0,
//                1,1,
//                0,0,
//                0,1
//        };

        // 初始化缓冲区
        mGlVertexBuffer = ByteBuffer.allocateDirect(VERTEX.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGlVertexBuffer.put(VERTEX).position(0);

        mGlTextureBuffer = ByteBuffer.allocateDirect(TEXTURE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGlTextureBuffer.put(TEXTURE).position(0);

        // 初始化shader程序
        initProgram();
    }

    protected abstract void initProgram();

    public void prepare(int width, int height) {
        mOutputWidth = width;
        mOutputHeight = height;
        Log.d(TAG, "准备滤镜: " + this.getClass().getSimpleName() + ", 尺寸: " + width + "x" + height);

        if (mFrameBuffers != null) {
            destroyFrameBuffers();
        }

        // 创建FBO
        mFrameBuffers = new int[1];
        mFBOTextures = new int[1];

        GLES20.glGenFramebuffers(1, mFrameBuffers, 0);
        checkGlError("glGenFramebuffers");

        GLES20.glGenTextures(1, mFBOTextures, 0);
        checkGlError("glGenTextures");

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFBOTextures[0]);
        checkGlError("glBindTexture");

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        checkGlError("glTexImage2D");

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        checkGlError("glBindFramebuffer");

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mFBOTextures[0], 0);
        checkGlError("glFramebufferTexture2D");

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "FBO创建失败, status: " + status);
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    public int onDrawFrame(int textureId) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(mProgramId);
        checkGlError("glUseProgram " + mProgramId);

        mGlVertexBuffer.position(0);
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, mGlVertexBuffer);
        GLES20.glEnableVertexAttribArray(vPosition);

        mGlTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(vCoord, 2, GLES20.GL_FLOAT, false, 0, mGlTextureBuffer);
        GLES20.glEnableVertexAttribArray(vCoord);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(vTexture, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays");

        GLES20.glDisableVertexAttribArray(vPosition);
        GLES20.glDisableVertexAttribArray(vCoord);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        return mFBOTextures[0];
    }

    public void destroyFrameBuffers() {
        if (mFrameBuffers != null) {
            GLES20.glDeleteFramebuffers(1, mFrameBuffers, 0);
            GLES20.glDeleteTextures(1, mFBOTextures, 0);
            mFrameBuffers = null;
            mFBOTextures = null;
        }
    }

    public void release() {
        destroyFrameBuffers();
        if (mProgramId != 0) {
            GLES20.glDeleteProgram(mProgramId);
            mProgramId = 0;
        }
    }

    // OpenGL辅助方法
    protected int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "着色器编译失败: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        return shader;
    }

    protected int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }

        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader);
            return 0;
        }

        int program = GLES20.glCreateProgram();
        if (program == 0) {
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            return 0;
        }

        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "程序链接失败: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }

        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);

        return program;
    }

    // 添加OpenGL错误检查
// 在BaseFilter.java中修改此方法
    protected void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            // 不抛出异常，只记录错误
            // 原来的代码: throw new RuntimeException(op + ": glError " + error);
        }
    }
}