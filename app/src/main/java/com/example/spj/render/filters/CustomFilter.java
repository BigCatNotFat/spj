package com.example.spj.render.filters;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

public class CustomFilter extends BaseFilter {
    private static final String TAG = "CustomFilter";
    private String vertexShader;
    private String fragmentShader;

    public CustomFilter(Context context, String vertexShader, String fragmentShader) {
        super(context);
        this.vertexShader = vertexShader;
        this.fragmentShader = fragmentShader;
        recompileShaders();
    }

    public void setShaders(String vertexShader, String fragmentShader) {
        this.vertexShader = vertexShader;
        this.fragmentShader = fragmentShader;
        recompileShaders();
    }

    private void recompileShaders() {
        // Release existing program if any
        if (mProgramId != 0) {
            GLES20.glDeleteProgram(mProgramId);
            mProgramId = 0;
        }

        // Create new program with the provided shaders
        initProgram();
    }

    @Override
    protected void initProgram() {
        // Use provided shaders if available, otherwise use defaults
        String vShader = vertexShader != null ? vertexShader :
                "attribute vec4 vPosition;\n" +
                        "attribute vec4 vCoord;\n" +
                        "varying vec2 aCoord;\n" +
                        "\n" +
                        "void main() {\n" +
                        "    gl_Position = vPosition;\n" +
                        "    aCoord = vCoord.xy;\n" +
                        "}";

        String fShader = fragmentShader != null ? fragmentShader :
                "precision mediump float;\n" +
                        "varying vec2 aCoord;\n" +
                        "uniform sampler2D vTexture;\n" +
                        "\n" +
                        "void main() {\n" +
                        "    gl_FragColor = texture2D(vTexture, aCoord);\n" +
                        "}";

        mProgramId = createProgram(vShader, fShader);

        if (mProgramId == 0) {
            Log.e(TAG, "Failed to create shader program!");
            return;
        }

        vPosition = GLES20.glGetAttribLocation(mProgramId, "vPosition");
        vCoord = GLES20.glGetAttribLocation(mProgramId, "vCoord");
        vTexture = GLES20.glGetUniformLocation(mProgramId, "vTexture");
    }
}