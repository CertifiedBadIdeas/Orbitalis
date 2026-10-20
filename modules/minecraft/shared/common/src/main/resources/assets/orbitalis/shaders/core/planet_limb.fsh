/*
 * The Orbitalis Developers
 *
 * Copyright 2026 Vsevolod Petrov (lazyhat)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#version 150

uniform sampler2D PlanetDepth;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 NativeProjection;
uniform mat4 InverseProjMat;
uniform float SceneScale;
uniform vec2 Viewport;
uniform vec4 ColorModulator;
uniform vec3 Observer;
uniform vec3 SunDirection;
uniform vec3 SunRadiance;
#moj_import <orbitalis:atmosphere_optics.glsl>
uniform vec3 BoundsMin;
uniform vec3 BoundsMax;
uniform float Feather;
uniform int UsePlanetDepth;
in vec3 earthPosition;
in vec3 parallelRay;
out vec4 fragColor;

vec3 linearToSrgb(vec3 color) {
    vec3 low = color * 12.92;
    vec3 high = 1.055 * pow(max(color, vec3(0.0)), vec3(1.0 / 2.4)) - 0.055;
    return mix(high, low, lessThanEqual(color, vec3(0.0031308)));
}

void main() {
    bool perspective = abs(ProjMat[3][3]) < 0.5;
    vec3 ray = perspective ? normalize(earthPosition - Observer) : normalize(parallelRay);
    vec3 origin = perspective ? Observer : earthPosition - ray * length(earthPosition - Observer);
    float entry;
    float exit;
    if (!atmosphereBoxInterval(origin, ray, BoundsMin, BoundsMax, entry, exit)) discard;
    entry = max(0.0, entry);
    if (entry >= exit) discard;
    // Rasterize each ray once, including when its observer is inside the atmosphere.
    if (abs(dot(earthPosition - origin, ray) - exit) > max(0.02, exit * 0.00001)) discard;
    if (UsePlanetDepth != 0) {
        vec2 uv = gl_FragCoord.xy / Viewport;
        float depth = texture(PlanetDepth, uv).r;
        if (depth < 1.0) {
            vec4 surface = InverseProjMat * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
            vec3 viewRay = normalize((ModelViewMat * vec4(ray, 0.0)).xyz);
            exit = min(exit, dot(surface.xyz / surface.w, viewRay) / SceneScale);
        }
    }
    if (exit <= entry) discard;
    float earthEntry;
    float earthExit;
    if (atmosphereBoxInterval(origin, ray, atmosphereEarthMin(), atmosphereEarthMax(), earthEntry, earthExit) && earthExit > 0.0) {
        exit = min(exit, max(0.0, earthEntry));
    }
    if (exit <= entry) discard;
    float stepLength = (exit - entry) / 32.0;
    vec3 transmittance = vec3(1.0);
    vec3 radiance = vec3(0.0);
    vec2 halfSize = BoundsMax.xz - vec2(Feather);
    float cosine = clamp(dot(ray, SunDirection), -1.0, 1.0);
    float rayleighPhase = 3.0 / (16.0 * 3.14159265) * (1.0 + cosine * cosine);
    const float g = 0.76;
    float miePhase = (1.0 - g * g) / (4.0 * 3.14159265 * pow(max(0.001, 1.0 + g * g - 2.0 * g * cosine), 1.5));
    for (int sampleIndex = 0; sampleIndex < 32; ++sampleIndex) {
        vec3 p = origin + ray * (entry + (float(sampleIndex) + 0.5) * stepLength);
        float altitude = max(0.0, p.y - BoundsMin.y);
        float outside = length(max(abs(p.xz) - halfSize, vec2(0.0)));
        float edge = 1.0 - smoothstep(0.0, Feather, outside);
        vec2 density = exp(-altitude / vec2(RayleighHeight, MieHeight)) * edge;
        vec3 rayleigh = RayleighCoefficient * density.x;
        float mie = MieCoefficient * density.y;
        vec3 extinction = rayleigh + vec3(mie);
        vec3 segmentTransmission = exp(-extinction * stepLength);
        vec3 segmentIntegral = (vec3(1.0) - segmentTransmission) / max(extinction, vec3(1e-8));
        vec3 scattering = rayleigh * rayleighPhase + vec3(mie * miePhase);
        radiance += transmittance * atmosphereSunTransmission(p, SunDirection) * SunRadiance * scattering * segmentIntegral;
        transmittance *= segmentTransmission;
    }
    if (max(radiance.r, max(radiance.g, radiance.b)) < 0.000001) discard;
    vec4 front = ModelViewMat * vec4(origin + ray * entry, 1.0);
    // Native depth belongs to physical metres; the distant/map target uses compressed metres.
    if (UsePlanetDepth != 0) front.xyz /= SceneScale;
    vec4 physical = UsePlanetDepth != 0 ? NativeProjection * front : ProjMat * front;
    gl_FragDepth = clamp(physical.z / physical.w * 0.5 + 0.5, 0.0, 1.0);
    // The existing sky target is display encoded; exposure precedes the transfer function.
    vec3 display = linearToSrgb(vec3(1.0) - exp(-radiance));
    fragColor = vec4(display, 1.0) * ColorModulator;
}
