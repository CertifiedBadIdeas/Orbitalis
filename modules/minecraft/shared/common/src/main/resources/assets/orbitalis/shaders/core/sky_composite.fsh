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
uniform vec4 ColorModulator;
in vec2 screenUv;
in vec4 screenTint;
out vec4 fragColor;
void main() {
    // Optical emission must fade through zero; an alpha-test threshold leaves a visible disc.
    fragColor = texture(Sampler0, screenUv) * screenTint * ColorModulator;
}
