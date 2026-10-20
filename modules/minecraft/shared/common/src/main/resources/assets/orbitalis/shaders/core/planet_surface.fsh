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
in vec4 surfaceColor;
in vec3 surfaceNormal;
out vec4 fragColor;
void main() {
    float direct = max(0.0, dot(normalize(surfaceNormal), SunDirection));
    // Retain a small readability floor in shadow, independent of surface albedo.
    float ambient = min(surfaceColor.a, 0.075);
    fragColor = vec4(surfaceColor.rgb * (ambient + (1.0 - ambient) * direct), 1.0);
}
