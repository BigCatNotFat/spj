package com.example.spj.render.filters;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import com.example.spj.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FilterManager {
    private static final String TAG = "FilterManager";

    private final Context context;
    private final Map<Integer, ShaderEffect> effectsMap = new HashMap<>();
    private final List<ShaderEffect> effectsList = new ArrayList<>();

    public FilterManager(Context context) {
        this.context = context;
        loadBuiltinEffects();
        loadRawShaders();
    }

    private void loadBuiltinEffects() {
        // Add built-in effects
        ShaderEffect noEffect = new ShaderEffect(0, "无滤镜", null, null);
        effectsMap.put(0, noEffect);
        effectsList.add(noEffect);

        ShaderEffect invertEffect = new ShaderEffect(
                1,
                "反转滤镜",
                null,  // Use built-in shader
                "precision mediump float;\n" +
                        "varying vec2 aCoord;\n" +
                        "uniform sampler2D vTexture;\n" +
                        "\n" +
                        "void main() {\n" +
                        "    vec4 color = texture2D(vTexture, aCoord);\n" +
                        "    gl_FragColor = vec4(1.0 - color.r, 1.0 - color.g, 1.0 - color.b, color.a);\n" +
                        "}"
        );
        effectsMap.put(1, invertEffect);
        effectsList.add(invertEffect);

        // Add grayscale filter as built-in
        ShaderEffect grayscaleEffect = new ShaderEffect(
                2,
                "黑白滤镜",
                null,  // Use built-in shader
                "precision mediump float;\n" +
                        "varying vec2 aCoord;\n" +
                        "uniform sampler2D vTexture;\n" +
                        "\n" +
                        "void main() {\n" +
                        "    vec4 color = texture2D(vTexture, aCoord);\n" +
                        "    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));\n" +
                        "    gl_FragColor = vec4(vec3(gray), color.a);\n" +
                        "}"
        );
        effectsMap.put(2, grayscaleEffect);
        effectsList.add(grayscaleEffect);

        // Add sepia filter as built-in
        ShaderEffect sepiaEffect = new ShaderEffect(
                3,
                "复古滤镜",
                null,  // Use built-in shader
                "precision mediump float;\n" +
                        "varying vec2 aCoord;\n" +
                        "uniform sampler2D vTexture;\n" +
                        "\n" +
                        "void main() {\n" +
                        "    vec4 color = texture2D(vTexture, aCoord);\n" +
                        "    float r = color.r * 0.393 + color.g * 0.769 + color.b * 0.189;\n" +
                        "    float g = color.r * 0.349 + color.g * 0.686 + color.b * 0.168;\n" +
                        "    float b = color.r * 0.272 + color.g * 0.534 + color.b * 0.131;\n" +
                        "    gl_FragColor = vec4(r, g, b, color.a);\n" +
                        "}"
        );
        effectsMap.put(3, sepiaEffect);
        effectsList.add(sepiaEffect);
    }

    private void loadRawShaders() {
        // Get all raw resources with name starting with "shader_"
        Field[] fields = R.raw.class.getFields();
        int baseId = 10; // Start custom shaders from ID 10

        for (Field field : fields) {
            try {
                String name = field.getName();
                if (name.startsWith("shader_")) {
                    int resourceId = field.getInt(null);
                    String shaderName = prettifyShaderName(name);
                    String fragmentShader = readRawTextFile(resourceId);

                    if (fragmentShader != null && !fragmentShader.isEmpty()) {
                        ShaderEffect effect = new ShaderEffect(baseId, shaderName, null, fragmentShader);
                        effectsMap.put(baseId, effect);
                        effectsList.add(effect);
                        baseId++;
                        Log.d(TAG, "Loaded shader: " + shaderName);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading raw shader: " + e.getMessage());
            }
        }
    }

    private String prettifyShaderName(String rawName) {
        // Convert "shader_sepia_effect" to "Sepia Effect"
        String nameWithoutPrefix = rawName.replace("shader_", "");
        String[] parts = nameWithoutPrefix.split("_");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (part.length() > 0) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
                result.append(" ");
            }
        }

        return result.toString().trim();
    }

    private String readRawTextFile(int resourceId) {
        try {
            Resources resources = context.getResources();
            InputStream inputStream = resources.openRawResource(resourceId);

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }

            reader.close();
            return builder.toString();
        } catch (IOException e) {
            Log.e(TAG, "Error reading raw file: " + e.getMessage());
            return null;
        }
    }

    public List<ShaderEffect> getEffectsList() {
        return effectsList;
    }

    public ShaderEffect getEffect(int id) {
        return effectsMap.get(id);
    }

    public String[] getEffectNames() {
        String[] names = new String[effectsList.size()];
        for (int i = 0; i < effectsList.size(); i++) {
            names[i] = effectsList.get(i).getName();
        }
        return names;
    }

    // Inner class for shader effect
    public static class ShaderEffect {
        private final int id;
        private final String name;
        private final String vertexShader;
        private final String fragmentShader;

        public ShaderEffect(int id, String name, String vertexShader, String fragmentShader) {
            this.id = id;
            this.name = name;
            this.vertexShader = vertexShader;
            this.fragmentShader = fragmentShader;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getVertexShader() {
            // Use default vertex shader if none provided
            if (vertexShader == null) {
                return "attribute vec4 vPosition;\n" +
                        "attribute vec4 vCoord;\n" +
                        "varying vec2 aCoord;\n" +
                        "\n" +
                        "void main() {\n" +
                        "    gl_Position = vPosition;\n" +
                        "    aCoord = vCoord.xy;\n" +
                        "}";
            }
            return vertexShader;
        }

        public String getFragmentShader() {
            return fragmentShader;
        }
    }
}