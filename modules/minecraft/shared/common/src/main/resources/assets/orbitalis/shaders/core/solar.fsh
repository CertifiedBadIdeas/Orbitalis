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
uniform mat4 InverseProjection;
uniform mat4 SolarProjection;
uniform mat4 InverseView;
uniform vec3 Centre;
uniform float RadiusScale;
uniform float Phase;
in vec2 clipPosition;
out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1,0,0)), f.x),
                   mix(hash(i + vec3(0,1,0)), hash(i + vec3(1,1,0)), f.x), f.y),
               mix(mix(hash(i + vec3(0,0,1)), hash(i + vec3(1,0,1)), f.x),
                   mix(hash(i + vec3(0,1,1)), hash(i + vec3(1,1,1)), f.x), f.y), f.z);
}
void main() {
    vec4 a = InverseProjection * vec4(clipPosition, -1.0, 1.0);
    vec4 b = InverseProjection * vec4(clipPosition, 1.0, 1.0);
    vec3 origin = (a.xyz / a.w) / RadiusScale;
    vec3 ray = normalize(b.xyz / b.w - a.xyz / a.w);
    vec3 relative = Centre - origin;
    float along = dot(relative, ray);
    // Cross product avoids cancellation of two large squared distances at Earth.
    float impact = length(cross(relative, ray));
    if ((along < 0.0 && length(relative) > 1.0) || impact > 1.9) discard;
    vec3 point;
    if (impact <= 1.0) {
        float t = along - sqrt(max(0.0, 1.0 - impact * impact));
        if (t < 0.0) t = along + sqrt(max(0.0, 1.0 - impact * impact));
        if (t < 0.0) discard;
        point = origin + ray * t;
        vec3 normal = normalize(point - Centre);
        vec3 n = mat3(InverseView) * normal;
        vec3 flow = vec3(sin(Phase), cos(Phase), sin(Phase * 2.0));
        float activity = smoothstep(0.68, 0.86, noise(n * 4.0 + flow * 0.2));
        // Derivative fade prevents unresolved granulation from sparkling at orbital distances.
        float footprint = max(length(dFdx(n)), length(dFdy(n)));
        float detail = 1.0 - smoothstep(0.008, 0.06, footprint);
        float grain = mix(0.5, noise(n * 145.0 + flow * 1.8), detail);
        float spots = activity * smoothstep(0.76, 0.91, noise(n * 32.0 + flow * 0.4)) * detail;
        float limb = 0.42 + 0.58 * pow(max(0.0, dot(normal, -ray)), 0.55);
        float brightness = limb * (0.94 + grain * 0.17) * (1.0 - spots * 0.55);
        fragColor = vec4(vec3(1.0, 0.88, 0.64) * brightness, 1.0);
    } else {
        float edge = impact - 1.0;
        vec3 n = normalize(mat3(InverseView) * (ray * along - relative));
        float stream = 0.75 + 0.25 * noise(n * 19.0 + vec3(sin(Phase), cos(Phase), 0.0));
        float glow = (0.38 * exp(-edge * 22.0) + 0.085 * exp(-edge * 4.5)) * stream;
        glow *= 1.0 - smoothstep(0.6, 0.9, edge);
        if (glow < 0.001) discard;
        fragColor = vec4(1.0, 0.62, 0.24, glow);
        point = origin + ray * along;
    }
    vec4 clip = SolarProjection * vec4(point * RadiusScale, 1.0);
    // A far-plane Sun remains behind the bounded planetary sky meshes, never a near billboard.
    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 0.9999999);
}
