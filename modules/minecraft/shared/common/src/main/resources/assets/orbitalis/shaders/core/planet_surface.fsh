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
uniform vec3 SunDirection;
uniform float EclipseEnabled;
uniform vec3 ShadowSun;
uniform vec3 ShadowSunX;
uniform vec3 ShadowSunY;
uniform vec3 ShadowSunZ;
uniform vec3 ShadowHalfSize;
in vec3 shadowPosition;
in vec4 surfaceColor;
in vec3 surfaceNormal;
out vec4 fragColor;

// Segment/slab intersection: an occluder behind either endpoint cannot cast a shadow.
float blocked(vec3 start, vec3 target) {
    vec3 delta = target - start;
    float near = 0.0;
    float far = 1.0;
    for (int axis = 0; axis < 3; axis++) {
        float d = delta[axis];
        float h = ShadowHalfSize[axis];
        if (abs(d) < 0.0000001) {
            if (abs(start[axis]) > h) return 0.0;
        } else {
            float a = (-h - start[axis]) / d;
            float b = (h - start[axis]) / d;
            near = max(near, min(a, b));
            far = min(far, max(a, b));
        }
    }
    return float(far >= near && far > 0.0 && near < 1.0);
}

// Equal projected-area quadrature on visible cube faces, matching core SolarOcclusion.
vec3 sourceSample(int i) {
    vec3 toward = shadowPosition - ShadowSun;
    vec3 facing = vec3(dot(toward, ShadowSunX), dot(toward, ShadowSunY), dot(toward, ShadowSunZ));
    vec3 weight = abs(facing);
    float cursor = (float(i) + 0.5) / 32.0 * (weight.x + weight.y + weight.z);
    float v = 2.0 * fract((float(i) + 0.5) * 0.6180339887498949) - 1.0;
    if (cursor < weight.x) {
        return ShadowSun + ShadowSunX * sign(facing.x) + ShadowSunY * (2.0 * cursor / weight.x - 1.0) + ShadowSunZ * v;
    }
    if (cursor < weight.x + weight.y) {
        return ShadowSun + ShadowSunX * v + ShadowSunY * sign(facing.y) + ShadowSunZ * (2.0 * (cursor - weight.x) / weight.y - 1.0);
    }
    return ShadowSun + ShadowSunX * (2.0 * (cursor - weight.x - weight.y) / weight.z - 1.0) + ShadowSunY * v + ShadowSunZ * sign(facing.z);
}

float visibility() {
    if (EclipseEnabled < 0.5) return 1.0;
    vec3 direction = normalize(ShadowSun - shadowPosition);
    float along = -dot(shadowPosition, direction);
    vec3 closest = shadowPosition + direction * max(0.0, along);
    float spread = length(ShadowSunX) * 1.732051 * max(0.0, along + length(ShadowHalfSize)) / length(ShadowSun - shadowPosition);
    if (along < 0.0 || length(closest) > length(ShadowHalfSize) + spread) return 1.0;
    float light = 0.0;
    for (int i = 0; i < 32; i++) {
        light += 1.0 - blocked(shadowPosition, sourceSample(i));
    }
    return light / 32.0;
}

void main() {
    float direct = max(0.0, dot(normalize(surfaceNormal), SunDirection));
    float visible = visibility();
    float ambient = min(surfaceColor.a, 0.075);
    // Artistic approximation of atmosphere-filtered light, not a refraction simulation.
    vec3 transmitted = vec3(0.075, 0.009, 0.003);
    vec3 illumination = mix(transmitted, vec3(1.0), visible);
    fragColor = vec4(surfaceColor.rgb * illumination * (ambient + (1.0 - ambient) * direct), 1.0);
}
