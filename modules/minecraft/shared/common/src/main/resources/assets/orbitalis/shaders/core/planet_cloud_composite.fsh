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

uniform sampler2D CloudColor;
uniform sampler2D CloudDepth;
uniform mat4 InverseProjection;
uniform mat4 ProjMat;
uniform float SceneScale;
in vec2 cloudUv;
out vec4 fragColor;

void main() {
    vec4 cloud = texture(CloudColor, cloudUv);
    if (cloud.a < 0.1) discard;
    float depth = texture(CloudDepth, cloudUv).r;
    vec4 compressed = InverseProjection * vec4(cloudUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 physicalView = compressed.xyz / (compressed.w * SceneScale);
    vec4 physical = ProjMat * vec4(physicalView, 1.0);
    gl_FragDepth = clamp(physical.z / physical.w * 0.5 + 0.5, 0.0, 1.0);
    fragColor = cloud;
}
