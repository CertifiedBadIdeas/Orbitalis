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
in vec2 UV0;
in vec4 Color;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float SceneScale;
uniform vec3 CloudOffset;
out float cloudThickness;
out vec4 cloudTint;
out vec3 actualView;
out vec3 cloudPosition;

void main() {
    cloudPosition = Position + CloudOffset;
    vec4 compressed = ModelViewMat * vec4(cloudPosition, 1.0);
    gl_Position = ProjMat * compressed;
    actualView = compressed.xyz / SceneScale;
    cloudThickness = UV0.x;
    cloudTint = Color;
}
