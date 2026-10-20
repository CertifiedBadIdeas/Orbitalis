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
uniform mat4 InverseProjMat;
uniform float SceneScale;
uniform vec2 Viewport;
uniform vec4 ColorModulator;
uniform vec3 Observer;
uniform vec3 SunDirection;
uniform vec3 BoundsMin;
uniform vec3 BoundsMax;
uniform float Feather;
uniform int UsePlanetDepth;
in vec3 earthPosition;
in vec3 parallelRay;
out vec4 fragColor;

void main() {
    bool perspective = abs(ProjMat[3][3]) < 0.5;
    vec3 ray = perspective ? normalize(earthPosition - Observer) : normalize(parallelRay);
    vec3 origin = perspective ? Observer : earthPosition - ray * length(earthPosition - Observer);
    float entry = 0.0;
    float exit = 1e20;
    for (int axis = 0; axis < 3; ++axis) {
        if (abs(ray[axis]) < 0.000001) {
            if (origin[axis] < BoundsMin[axis] || origin[axis] > BoundsMax[axis]) discard;
        } else {
            float a = (BoundsMin[axis] - origin[axis]) / ray[axis];
            float b = (BoundsMax[axis] - origin[axis]) / ray[axis];
            entry = max(entry, min(a, b));
            exit = min(exit, max(a, b));
        }
    }
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
    float stepLength = (exit - entry) / 32.0;
    float opticalDepth = 0.0;
    vec2 halfSize = BoundsMax.xz - vec2(Feather);
    float thickness = BoundsMax.y - BoundsMin.y;
    for (int sampleIndex = 0; sampleIndex < 32; ++sampleIndex) {
        vec3 p = origin + ray * (entry + (float(sampleIndex) + 0.5) * stepLength);
        float altitude = (p.y - BoundsMin.y) / thickness;
        float outside = length(max(abs(p.xz) - halfSize, vec2(0.0)));
        float edge = 1.0 - smoothstep(0.0, Feather, outside);
        float density = smoothstep(0.0, 0.10, altitude) * exp(-altitude * 3.5) * (1.0 - smoothstep(0.55, 1.0, altitude));
        opticalDepth += density * edge * stepLength;
    }
    float scattering = 1.0 - exp(-opticalDepth * 0.0045);
    if (scattering < 0.0001) discard;
    vec4 front = ModelViewMat * vec4(origin + ray * entry, 1.0);
    // Native depth belongs to physical metres; the distant/map target uses compressed metres.
    if (UsePlanetDepth != 0) front.xyz /= SceneScale;
    vec4 physical = ProjMat * front;
    gl_FragDepth = clamp(physical.z / physical.w * 0.5 + 0.5, 0.0, 1.0);
    vec3 tint = mix(vec3(0.12, 0.38, 0.90), vec3(0.40, 0.64, 1.0), scattering);
    float day = smoothstep(-0.06, 0.15, SunDirection.y);
    float forward = pow(max(0.0, dot(ray, SunDirection)), 8.0);
    float twilight = (1.0 - abs(day * 2.0 - 1.0)) * forward;
    tint = mix(tint, vec3(1.0, 0.34, 0.09), twilight * 0.65);
    fragColor = vec4(tint * (0.035 + 0.965 * day), scattering * 0.65) * ColorModulator;
}
