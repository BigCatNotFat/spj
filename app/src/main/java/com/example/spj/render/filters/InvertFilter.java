package com.example.spj.render.filters;

import android.content.Context;
import android.opengl.GLES20;

public class InvertFilter extends BaseFilter {
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
                    "    vec4 color = texture2D(vTexture, aCoord);\n" +
                    "    gl_FragColor = vec4(1.0 - color.r, 1.0 - color.g, 1.0 - color.b, color.a);\n" +
                    "}";

    public InvertFilter(Context context) {
        super(context);
    }

    @Override
    protected void initProgram() {
        mProgramId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        vPosition = GLES20.glGetAttribLocation(mProgramId, "vPosition");
        vCoord = GLES20.glGetAttribLocation(mProgramId, "vCoord");
        vTexture = GLES20.glGetUniformLocation(mProgramId, "vTexture");
    }
}