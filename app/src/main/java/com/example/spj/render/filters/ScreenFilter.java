package com.example.spj.render.filters;

import android.content.Context;
import android.opengl.GLES20;

public class ScreenFilter extends BaseFilter {
    private static final String VERTEX_SHADER =
            "attribute vec4 vPosition;\n" +
                    "attribute vec4 vCoord;\n" +
                    "varying vec2 aCoord;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    gl_Position = vPosition;\n" +
                    "    aCoord = vCoord.xy;\n" +
                    "}";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;\n" +
                    "varying vec2 aCoord;\n" +
                    "uniform sampler2D vTexture;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(vTexture, aCoord);\n" +
                    "}";

    public ScreenFilter(Context context) {
        super(context);
    }

    @Override
    protected void initProgram() {
        mProgramId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        vPosition = GLES20.glGetAttribLocation(mProgramId, "vPosition");
        vCoord = GLES20.glGetAttribLocation(mProgramId, "vCoord");
        vTexture = GLES20.glGetUniformLocation(mProgramId, "vTexture");
    }

    @Override
    public int onDrawFrame(int textureId) {
        GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(mProgramId);

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

        GLES20.glDisableVertexAttribArray(vPosition);
        GLES20.glDisableVertexAttribArray(vCoord);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        return textureId;
    }
}