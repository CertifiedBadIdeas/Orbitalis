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

uniform float AtmosphereBottom;
uniform float AtmosphereTop;
uniform float AtmosphereHalfSize;
uniform float AtmosphereFeather;
uniform vec3 RayleighCoefficient;
uniform float MieCoefficient;
uniform float RayleighHeight;
uniform float MieHeight;

// All lengths are Earth-local metres; the CPU supplies the same density and extinction parameters.
bool atmosphereBoxInterval(vec3 origin, vec3 ray, vec3 lower, vec3 upper, out float entry, out float exit) {
    entry = -1e20;
    exit = 1e20;
    for (int axis = 0; axis < 3; ++axis) {
        if (abs(ray[axis]) < 0.000001) {
            if (origin[axis] < lower[axis] || origin[axis] > upper[axis]) return false;
        } else {
            float a = (lower[axis] - origin[axis]) / ray[axis];
            float b = (upper[axis] - origin[axis]) / ray[axis];
            entry = max(entry, min(a, b));
            exit = min(exit, max(a, b));
            if (entry > exit) return false;
        }
    }
    return true;
}

vec3 atmosphereEarthMin() { return vec3(-AtmosphereHalfSize, -AtmosphereBottom, -AtmosphereHalfSize); }
vec3 atmosphereEarthMax() { return vec3(AtmosphereHalfSize, AtmosphereBottom, AtmosphereHalfSize); }

// Exact exponential column along a finite straight segment; its small-slope limit is horizontal.
vec2 atmosphereColumns(float altitude, float vertical, float distance) {
    vec2 slope = vertical * distance / vec2(RayleighHeight, MieHeight);
    vec2 average;
    for (int component = 0; component < 2; ++component) {
        float s = slope[component];
        average[component] = abs(s) < 0.001 ? 1.0 - s * 0.5 + s * s / 6.0 : (1.0 - exp(-s)) / s;
    }
    return exp(-altitude / vec2(RayleighHeight, MieHeight)) * distance * average;
}

vec3 atmosphereSunTransmission(vec3 point, vec3 sunDirection) {
    float entry;
    float exit;
    if (!(point.y >= AtmosphereBottom && sunDirection.y >= 0.0) &&
        atmosphereBoxInterval(point, sunDirection, atmosphereEarthMin(), atmosphereEarthMax(), entry, exit) && exit > 0.001 && entry <= exit) return vec3(0.0);
    float extent = AtmosphereHalfSize + AtmosphereFeather;
    if (!atmosphereBoxInterval(point, sunDirection, vec3(-extent, AtmosphereBottom, -extent), vec3(extent, AtmosphereTop, extent), entry, exit)) return vec3(1.0);
    float start = max(0.0, entry);
    float distance = max(0.0, exit - start);
    vec2 column = atmosphereColumns(point.y + sunDirection.y * start - AtmosphereBottom, sunDirection.y, distance);
    return exp(-RayleighCoefficient * column.x - vec3(MieCoefficient * column.y));
}

