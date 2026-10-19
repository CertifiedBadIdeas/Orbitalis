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

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform mat4 ProjMat;
uniform vec2 Viewport;
uniform vec4 ColorModulator;
uniform int WorldDepth;
uniform float CloudHalfSize;
uniform float FogStart;
uniform float FogEnd;
uniform int FogShape;
uniform vec4 FogColor;
in vec2 cloudUv;
in vec4 cloudTint;
in vec3 actualView;
in vec3 cloudPosition;
out vec4 fragColor;

void main() {
    if (abs(cloudPosition.x) > CloudHalfSize || abs(cloudPosition.z) > CloudHalfSize) discard;
    vec4 cloud = texture(Sampler0, cloudUv) * cloudTint * ColorModulator;
    if (cloud.a < 0.1) discard;
    if (WorldDepth != 0) {
        // Private planet depth and these vertices use the same compressed sky coordinates.
        if (gl_FragCoord.z > texture(Sampler1, gl_FragCoord.xy / Viewport).r + 0.000001) discard;
        // Compare physical distance with native terrain/ships, including the depth-only pass.
        vec4 physical = ProjMat * vec4(actualView, 1.0);
        gl_FragDepth = clamp(physical.z / physical.w * 0.5 + 0.5, 0.0, 1.0);
    } else {
        gl_FragDepth = gl_FragCoord.z;
    }
    // Read the actual native cloud-pass fog, including the gradual atmospheric fade.
    // Per-fragment distance also works for period-sized caps without distant corner fog.
    float distance = FogShape == 0 ? length(actualView) : max(length(actualView.xz), abs(actualView.y));
    float fog = FogEnd > FogStart ? smoothstep(FogStart, FogEnd, distance) : step(FogEnd, distance);
    cloud.rgb = mix(cloud.rgb, FogColor.rgb, fog * FogColor.a);
    fragColor = cloud;
}
