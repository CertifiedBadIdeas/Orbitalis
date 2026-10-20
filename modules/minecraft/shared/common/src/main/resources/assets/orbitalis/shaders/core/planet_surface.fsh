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
#moj_import <orbitalis:atmosphere_optics.glsl>
uniform float AtmosphereEnabled;
uniform vec3 Observer;
uniform vec3 ViewDirection;
uniform float ParallelView;
uniform vec3 SunRadiance;
in vec3 localPosition;
in float waterCoverage;
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

// Atlas colours are display encoded. BRDF and optical attenuation operate in linear light.
vec3 linearColor(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}
vec3 displayColor(vec3 c) {
    c = 1.0 - exp(-max(c, vec3(0.0)) * 1.35);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}
void main() {
    vec3 n = normalize(surfaceNormal);
    vec3 v = (ParallelView > 0.5 ? ViewDirection : normalize(Observer - localPosition));
    vec3 l = normalize(SunDirection);
    float nl = max(0.0, dot(n, l));
    float nv = max(0.001, dot(n, v));
    vec3 sunlight = SunRadiance * visibility();
    if (AtmosphereEnabled > 0.5) sunlight *= atmosphereSunTransmission(localPosition + n * 0.02, l);
    vec3 albedo = linearColor(surfaceColor.rgb);
    // Small illustrative indirect floor; full multi-bounce transport is not simulated.
    vec3 indirect = vec3(0.020) * min(surfaceColor.a / 0.18, 1.22);
    if (AtmosphereEnabled > 0.5) {
        indirect += vec3(0.018, 0.034, 0.065) * max(0.0, l.y) * max(0.0, n.y);
    }
    float wet = clamp(waterCoverage, 0.0, 1.0);
    vec3 radiance = albedo * (indirect + sunlight * nl * (1.0 - wet * 0.08));
    if (wet > 0.0 && nl > 0.0 && dot(n, v) > 0.0) {
        vec3 h = normalize(l + v);
        float nh = max(0.0, dot(n, h));
        float vh = max(0.0, dot(v, h));
        // Rough GGX water surface; no mirror glints on dry blue blocks or airless rock.
        float a2 = 0.00234256; // roughness .22, squared twice
        float denom = nh * nh * (a2 - 1.0) + 1.0;
        float distribution = a2 / (3.14159265 * denom * denom);
        float k = 0.18605; // (roughness + 1)^2 / 8
        float geometry = nv / (nv * (1.0 - k) + k) * nl / (nl * (1.0 - k) + k);
        float fresnel = 0.02 + 0.98 * pow(1.0 - vh, 5.0);
        radiance += sunlight * wet * distribution * geometry * fresnel / (4.0 * nv);
    }
    if (AtmosphereEnabled > 0.5) radiance *= atmosphereViewTransmission(localPosition, ParallelView > 0.5 ? localPosition + ViewDirection * (AtmosphereHalfSize * 4.0 + AtmosphereTop) : Observer);
    fragColor = vec4(displayColor(radiance), 1.0);
}
