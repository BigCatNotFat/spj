precision mediump float;
varying vec2 aCoord;
uniform sampler2D vTexture;

void main() {
    vec4 color = texture2D(vTexture, aCoord);
    // Cool tint (more blue, slightly less red)
    color.r = color.r * 0.8;
    color.b = min(color.b * 1.2, 1.0);
    gl_FragColor = color;
}