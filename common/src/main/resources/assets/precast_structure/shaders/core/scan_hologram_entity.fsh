#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float GameTime;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 worldPos;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 safeSample(vec2 uv, vec4 fallback) {
    vec4 s = texture(Sampler0, uv);
    return s.a < 0.1 ? fallback : s;
}

void main() {
    float time = GameTime * 1200.0;

    vec4 center = texture(Sampler0, texCoord0);
    if (center.a < 0.1) {
        discard;
    }

    float tearRow = floor(worldPos.y * 16.0 + time * 7.0);
    float tear = step(0.965, hash(vec2(tearRow, floor(time * 4.0))));
    float glitchX = (hash(vec2(tearRow, 3.1)) - 0.5) * tear * 0.012;
    float wave = sin(worldPos.y * 18.0 + time * 6.5) * 0.0018;
    vec2 uv = texCoord0 + vec2(glitchX + wave, sin(time * 3.2 + worldPos.x * 4.0) * 0.0012);

    vec4 base = safeSample(uv, center);

    float aberr = 0.0025 + 0.0015 * sin(time * 5.5);
    float r = safeSample(uv + vec2(aberr, 0.0), base).r;
    float g = base.g;
    float b = safeSample(uv - vec2(aberr, 0.0), base).b;

    vec3 tex = vec3(r, g, b);
    float lum = dot(tex, vec3(0.299, 0.587, 0.114));
    vec3 hologramBlue = vec3(0.35, 0.82, 1.0);
    vec3 hologramCyan = vec3(0.55, 0.98, 1.0);

    vec3 tinted = tex * vec3(0.75, 1.05, 1.25);
    tinted = mix(tinted, hologramBlue * (0.55 + lum * 0.9), 0.38);
    tinted = mix(tinted, hologramCyan * max(lum, 0.25), 0.18);
    tinted *= 1.35 + lum * 0.35;
    tinted = min(tinted, vec3(1.6));

    float scan = sin((worldPos.y - time * 0.85) * 90.0);
    tinted *= 0.90 + 0.14 * scan;

    float sweep = fract(worldPos.y * 0.45 - time * 0.85);
    float beam = smoothstep(0.0, 0.04, sweep) * smoothstep(0.18, 0.04, sweep);
    tinted += hologramCyan * beam * 0.45;
    tinted *= 1.0 + beam * 0.25;

    float sweep2 = fract(worldPos.y * 0.2 + time * 0.35);
    float beam2 = smoothstep(0.0, 0.03, sweep2) * smoothstep(0.12, 0.03, sweep2);
    tinted += hologramBlue * beam2 * 0.18;

    tinted = mix(tinted, tinted * vec3(1.15, 1.35, 1.55) + hologramCyan * 0.25, tear);

    float flicker = 0.92 + 0.08 * sin(time * 22.0 + worldPos.y * 5.0);
    flicker *= 0.96 + 0.04 * sin(time * 61.0);
    float dropout = 1.0 - 0.12 * step(0.985, hash(vec2(floor(time * 8.0), 9.7)));
    flicker *= dropout;

    float noise = hash(floor(worldPos.xz * 24.0) + floor(time * 12.0));
    tinted += hologramCyan * (noise * 0.06);

    float alpha = clamp(center.a * vertexColor.a * (0.74 + beam * 0.16 + beam2 * 0.06) * flicker, 0.0, 0.94);

    vec4 color = vec4(tinted * vertexColor.rgb * flicker, alpha) * ColorModulator;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
