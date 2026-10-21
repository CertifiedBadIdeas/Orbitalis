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
uniform vec3 SunDirection;
uniform vec3 SunRadiance;
uniform vec3 Observer;
uniform vec3 ViewDirection;
uniform float ParallelView;

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform mat4 ProjMat;
uniform mat4 NativeProjection;
uniform vec2 Viewport;
uniform vec4 ColorModulator;
uniform int WorldDepth;
uniform int UsePlanetDepth;
uniform int CloudBoundary;
uniform float CloudHalfSize;
in vec2 cloudUv;
in vec4 cloudTint;
in vec3 actualView;
in vec3 cloudPosition;
out vec4 fragColor;

void main() {
    // Derivatives must execute before any nonuniform alpha/depth discard.
    vec3 geometryNormal = cross(dFdx(cloudPosition), dFdy(cloudPosition));
    // Caps are already bounded quads. Interpolating a fixed +/-half coordinate can round just
    // outside that plane, so only the oversized repeating period mesh needs footprint clipping.
    if (CloudBoundary == 0 && (abs(cloudPosition.x) > CloudHalfSize || abs(cloudPosition.z) > CloudHalfSize)) discard;
    vec4 cloud = texture(Sampler0, cloudUv) * cloudTint * ColorModulator;
    if (cloud.a < 0.1) discard;
    if (WorldDepth != 0) {
        // Private planet depth and these vertices use the same compressed sky coordinates.
        if (UsePlanetDepth != 0 && gl_FragCoord.z > texture(Sampler1, gl_FragCoord.xy / Viewport).r + 0.000001) discard;
        if (WorldDepth == 2) {
            gl_FragDepth = gl_FragCoord.z;
        } else {
            // FAST clouds compare physical distance directly with native terrain/ships.
            vec4 physical = NativeProjection * vec4(actualView, 1.0);
            gl_FragDepth = clamp(physical.z / physical.w * 0.5 + 0.5, 0.0, 1.0);
        }
    } else {
        gl_FragDepth = gl_FragCoord.z;
    }
    vec3 n = normalize(geometryNormal);
    vec3 view = (ParallelView > 0.5 ? ViewDirection : normalize(Observer - cloudPosition));
    if (dot(n, view) < 0.0) n = -n;
    vec3 light = atmosphereSunTransmission(cloudPosition, SunDirection) * SunRadiance;
    float diffuse = 0.22 + 0.78 * max(0.0, dot(n, SunDirection));
    // A thin cloud also transmits sunlight: silver lining grows toward the source.
    float forward = pow(max(0.0, dot(-view, SunDirection)), 8.0);
    vec3 radiance = light * (diffuse + forward * 0.7);
    radiance += vec3(0.015, 0.025, 0.045) * max(0.0, SunDirection.y) + vec3(0.008);
    radiance *= atmosphereViewTransmission(cloudPosition, ParallelView > 0.5 ? cloudPosition + ViewDirection * (AtmosphereHalfSize * 4.0 + AtmosphereTop) : Observer);
    vec3 mapped = 1.0 - exp(-radiance * 1.35);
    cloud.rgb *= mix(mapped * 12.92, 1.055 * pow(mapped, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), mapped));
    fragColor = cloud;
}
