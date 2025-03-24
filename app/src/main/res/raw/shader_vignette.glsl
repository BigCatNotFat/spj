precision mediump float;
varying vec2 aCoord;
uniform sampler2D vTexture;

void main() {
    vec4 color = texture2D(vTexture, aCoord);

    // Create vignette effect
    vec2 center = vec2(0.5, 0.5);
    float dist = distance(aCoord, center);
    float vignette = smoothstep(0.5, 0.2, dist);

    color.rgb = color.rgb * vignette;
    gl_FragColor = color;
}