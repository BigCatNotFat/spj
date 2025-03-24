precision mediump float;
varying vec2 aCoord;
uniform sampler2D vTexture;

void main() {
    vec4 color = texture2D(vTexture, aCoord);
    // Warm tint (more red, slightly less blue)
    color.r = min(color.r * 1.2, 1.0);
    color.b = color.b * 0.8;
    gl_FragColor = color;
}