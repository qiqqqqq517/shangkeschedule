package com.shangkeschedule.ui.glass

/*
 * AGSL / SkSL 着色器源码。
 *
 * 出处与授权：折射（SDF + circleMap + 法线位移）与色散（七通道采样）算法移植自
 * Kyant/backdrop（仓库 Kyant0/AndroidLiquidGlass）的 internal/Shaders.kt，
 * 该仓库以 **Apache License 2.0** 发布（Copyright 2025 Kyant）。本文件保留同一授权，
 * 并针对本项目做了三处偏离，均在下方注释标注：
 *   ① 圆角半径查表改用**居中坐标**（上游用未偏移坐标，padding > 0 时贴边象限可能取错角）；
 *   ② 径向梯度做了零长度保护（上游 `normalize(centeredCoord)` 在极扁/极小形状的中心
 *      会产生 NaN → 该像素变黑）；
 *   ③ 去掉了上游未在本库使用的 uniform，只保留实际写入的量。
 */

/**
 * 圆角矩形 SDF 公共函数体。
 *
 * 四角半径以 float4 = (左上, 右上, 右下, 左下) 传入，按象限选择；
 * 梯度在「圆角外的角部」沿角点法线、在「直边区域」退化为轴向，
 * 这正是折射在直边处表现为平移、在圆角处表现为弧形汇聚的原因。
 */
private const val ROUNDED_RECT_SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

// 径向梯度：偏离 ②（零长度保护，避免中心处 NaN）
float2 safeRadialGrad(float2 coord) {
    float rl = length(coord);
    if (rl < 0.0001) return float2(0.0, 0.0);
    return coord / rl;
}"""

/**
 * 边缘折射（透镜）：把形状边缘一圈的采样坐标沿 SDF 法线**向内**位移，
 * 越靠边位移越大 —— 观感即"玻璃边缘把背后画面放大并挤压"。
 *
 * `refractionAmount` 传入负值（见 [glassLens]），配合向外法线即得到向内的采样偏移。
 */
internal const val REFRACTION_AGSL = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

$ROUNDED_RECT_SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    // 偏离 ①：用居中坐标查圆角表
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    // 远离边缘的区域完全不折射：直接返回原图，省掉无谓的采样
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * safeRadialGrad(centeredCoord));

    return content.eval(coord + d * grad);
}"""

/**
 * 边缘折射 + 彩虹色散（chromatic aberration）。
 *
 * 色散强度沿"角部"最大、直边中点为零（用 `x*y/(halfW*halfH)` 调制），
 * 于是彩边只出现在四个圆角处 —— 与真实玻璃边缘的棱镜色散位置一致。
 * 七个采样点分别贡献 R/G/B 通道，模拟白光被分解后的偏折差。
 */
internal const val REFRACTION_DISPERSION_AGSL = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$ROUNDED_RECT_SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(centeredCoord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * safeRadialGrad(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}"""
