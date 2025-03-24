precision mediump float;
varying vec2 aCoord;
uniform sampler2D vTexture;

void main() {
    // 获取原始像素颜色
    vec4 color = texture2D(vTexture, aCoord);

    // 富士胶片特有的颜色处理
    // 增强绿色和蓝色，轻微增强红色
    vec3 fuji;

    // 红色通道处理 - 轻微增强红色，减少绿色影响
    fuji.r = color.r * 1.12 - color.g * 0.07 + color.b * 0.05;

    // 绿色通道处理 - 增强绿色，微调绿色对比度
    fuji.g = color.r * 0.08 + color.g * 1.22 - color.b * 0.05;

    // 蓝色通道处理 - 增强蓝色，减少红色影响
    fuji.b = -color.r * 0.16 + color.g * 0.1 + color.b * 1.25;

    // 整体颜色调整 - 略微提高饱和度
    fuji = mix(vec3(dot(fuji, vec3(0.299, 0.587, 0.114))), fuji, 1.15);

    // 提高阴影部分
    fuji = pow(fuji, vec3(0.95));

    // 确保颜色值在有效范围内
    fuji = clamp(fuji, 0.0, 1.0);

    // 输出处理后的颜色
    gl_FragColor = vec4(fuji, color.a);
}