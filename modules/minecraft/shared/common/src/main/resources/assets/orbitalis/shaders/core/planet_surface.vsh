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
in vec3 Position;
in vec4 Color;
in vec3 Normal;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ShadowOrigin;
uniform vec3 ShadowAxisX;
uniform vec3 ShadowAxisY;
uniform vec3 ShadowAxisZ;
out vec3 shadowPosition;
out vec4 surfaceColor;
out vec3 surfaceNormal;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    shadowPosition = ShadowOrigin + ShadowAxisX * Position.x + ShadowAxisY * Position.y + ShadowAxisZ * Position.z;
    surfaceColor = Color;
    surfaceNormal = Normal;
}
