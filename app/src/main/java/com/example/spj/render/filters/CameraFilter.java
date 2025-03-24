package com.example.spj.render.filters;
import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

public class CameraFilter extends BaseFilter {
    private static final String TAG = "CameraFilter";
    private float[] matrix = new float[16];
    private boolean mIsMirrored = false;
    public void setMirrored(boolean mirrored) {
        if (mIsMirrored != mirrored) {
            mIsMirrored = mirrored;
            updateTextureBuffer();
        }
    }
    private void updateTextureBuffer() {
        // 根据是否镜像选择正确的纹理坐标
        float[] textureCoords;
        if (mIsMirrored) {
            // 镜像的纹理坐标（水平翻转）
            textureCoords = new float[]{
                    1.0f, 1.0f,  // 右上
                    0.0f, 1.0f,  // 左上
                    1.0f, 0.0f,  // 右下
                    0.0f, 0.0f   // 左下
            };
        } else {
            // 正常的纹理坐标
            textureCoords = new float[]{
                    0.0f, 1.0f,  // 左上
                    1.0f, 1.0f,  // 右上
                    0.0f, 0.0f,  // 左下
                    1.0f, 0.0f   // 右下
            };
        }

        // 更新纹理缓冲区
        mGlTextureBuffer.clear();
        mGlTextureBuffer.put(textureCoords).position(0);
    }
    private static final String VERTEX_SHADER =
            "attribute vec4 vPosition;\n" +
                    "attribute vec4 vCoord;\n" +
                    "uniform mat4 vMatrix;\n" +
                    "varying vec2 aCoord;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    gl_Position = vPosition;\n" +
                    "    aCoord = (vMatrix * vCoord).xy;\n" +
                    "}";

    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 aCoord;\n" +
                    "uniform samplerExternalOES vTexture;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(vTexture, aCoord);\n" +
                    "}";

    public CameraFilter(Context context) {
        super(context);
        Matrix.setIdentityM(matrix, 0);
    }

    @Override
    protected void initProgram() {
        mProgramId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (mProgramId == 0) {
            Log.e(TAG, "创建CameraFilter着色器程序失败");
            return;
        }

        vPosition = GLES20.glGetAttribLocation(mProgramId, "vPosition");
        vCoord = GLES20.glGetAttribLocation(mProgramId, "vCoord");
        vTexture = GLES20.glGetUniformLocation(mProgramId, "vTexture");
        vMatrix = GLES20.glGetUniformLocation(mProgramId, "vMatrix");

        Log.d(TAG, "CameraFilter着色器程序初始化完成: mProgramId=" + mProgramId +
                ", vPosition=" + vPosition +
                ", vCoord=" + vCoord +
                ", vTexture=" + vTexture +
                ", vMatrix=" + vMatrix);
    }

    public void setMatrix(float[] matrix) {
        System.arraycopy(matrix, 0, this.matrix, 0, 16);
    }

    @Override
    public int onDrawFrame(int textureId) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffers[0]);
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(mProgramId);
        checkGlError("glUseProgram");

        mGlVertexBuffer.position(0);
        GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, mGlVertexBuffer);
        GLES20.glEnableVertexAttribArray(vPosition);

        mGlTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(vCoord, 2, GLES20.GL_FLOAT, false, 0, mGlTextureBuffer);
        GLES20.glEnableVertexAttribArray(vCoord);

        GLES20.glUniformMatrix4fv(vMatrix, 1, false, matrix, 0);
        checkGlError("glUniformMatrix4fv");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUniform1i(vTexture, 0);
        checkGlError("glBindTexture");

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays");

        GLES20.glDisableVertexAttribArray(vPosition);
        GLES20.glDisableVertexAttribArray(vCoord);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        return mFBOTextures[0];
    }
}