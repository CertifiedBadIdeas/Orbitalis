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
uniform mat4 ProjMat;
uniform vec2 Viewport;
uniform vec4 ColorModulator;
uniform vec3 Observer;
uniform vec3 BoundsMin;
uniform vec3 BoundsMax;
in vec3 earthPosition;
in vec3 actualView;
in vec4 limbTint;
out vec4 fragColor;

void main() {
    // Retain the distant body's own near/far-side visibility before composing over native terrain.
    if (gl_FragCoord.z > texture(PlanetDepth, gl_FragCoord.xy / Viewport).r + 0.000001) discard;
    vec3 ray = earthPosition - Observer;
    float entry = 0.0;
    float exit = 1.0;
    for (int axis = 0; axis < 3; ++axis) {
        if (abs(ray[axis]) < 0.000001) {
            if (Observer[axis] < BoundsMin[axis] || Observer[axis] > BoundsMax[axis]) discard;
        } else {
            float a = (BoundsMin[axis] - Observer[axis]) / ray[axis];
            float b = (BoundsMax[axis] - Observer[axis]) / ray[axis];
            entry = max(entry, min(a, b));
            exit = min(exit, max(a, b));
        }
    }
    if (entry > exit) discard;
    // Scatter starts at the atmosphere's front boundary, ahead of surface mountains. A nearer
    // foreground ship still occludes it. Inside the layer, nearby objects receive front haze.
    vec4 physical = ProjMat * vec4(actualView * entry, 1.0);
    gl_FragDepth = physical.w > 0.0 ? clamp(physical.z / physical.w * 0.5 + 0.5, 0.0, 1.0) : 0.0;
    fragColor = limbTint * ColorModulator;
}
