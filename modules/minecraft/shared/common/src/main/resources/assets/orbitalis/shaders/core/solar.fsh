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
uniform float Exposure;
in vec2 clipPosition;
out vec4 fragColor;

vec3 hash3(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}
float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(i).x, hash3(i + vec3(1,0,0)).x, f.x),
                   mix(hash3(i + vec3(0,1,0)).x, hash3(i + vec3(1,1,0)).x, f.x), f.y),
               mix(mix(hash3(i + vec3(0,0,1)).x, hash3(i + vec3(1,0,1)).x, f.x),
                   mix(hash3(i + vec3(0,1,1)).x, hash3(i + vec3(1,1,1)).x, f.x), f.y), f.z);
}

// A 3-D cellular field sliced by the photosphere: bright convection interiors and dark lanes.
// Feature points move periodically within their cells; no UV chart, seam or polar singularity.
float granules(vec3 n, vec3 flow, float footprint) {
    const float frequency = 150.0;
    float resolved = 1.0 - smoothstep(0.45, 1.5, footprint * frequency);
    if (resolved < 0.002) return 0.91;
    vec3 warp = vec3(noise(n * 18.0 + flow), noise(n * 18.0 + flow + 17.0), noise(n * 18.0 + flow + 39.0));
    vec3 p = n * frequency + (warp - 0.5) * 0.65;
    vec3 cell = floor(p), f = fract(p);
    float nearest = 10.0, second = 10.0, seed = 0.0;
    for (int z = -1; z <= 1; z++) {
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                vec3 offset = vec3(x,y,z);
                vec3 random = hash3(cell + offset);
                vec3 centre = offset + 0.18 + random * 0.64;
                centre += 0.18 * sin(vec3(Phase * 23.0) + random.yzx * 6.283185);
                vec3 d = centre - f;
                float distance2 = dot(d,d);
                if (distance2 < nearest) {
                    second = nearest;
                    nearest = distance2;
                    seed = random.x;
                } else {
                    second = min(second, distance2);
                }
            }
        }
    }
    float width = max(0.045, footprint * frequency * 0.16);
    float interior = smoothstep(0.015, 0.10 + width, sqrt(second) - sqrt(nearest));
    float hot = exp(-nearest * 3.0);
    // Cell-specific heating/cooling evolves independently of the domain flow. Integer
    // temporal harmonics keep values and their derivatives continuous at the clock wrap.
    float heat = 0.88 + 0.24 * (0.5 + 0.5 * sin(Phase * 31.0 + seed * 6.283185));
    float brightness = (0.58 + 0.42 * interior + 0.14 * hot) * heat;
    return mix(0.91, brightness, resolved);
}

vec3 solarCoordinates(vec3 p) {
    float angle = Phase * 2.0;
    return vec3(cos(angle) * p.x + sin(angle) * p.y,
                -sin(angle) * p.x + cos(angle) * p.y, p.z);
}

vec3 activeCentre(int i) {
    float longitude = 0.28 + float(i) * 1.59 + 0.17 * sin(float(i) * 3.1);
    float latitude = ((i == 0 || i == 2) ? 0.20 : -0.25) + 0.04 * sin(float(i) * 1.31);
    return normalize(vec3(cos(longitude), sin(longitude), latitude));
}

// Umbra, ragged penumbra and radial penumbral filaments within a small active region.
vec2 sunspot(vec3 n, vec3 centre, float radius, float footprint) {
    float distance = length(n - centre) / radius;
    if (distance > 2.8) return vec2(0.0);
    vec3 tangent = normalize(cross(centre, vec3(0,0,1)));
    vec3 bitangent = cross(centre, tangent);
    vec3 offset = n - centre;
    float angle = atan(dot(offset, bitangent), dot(offset, tangent));
    float rough = noise(n * 420.0);
    float r = distance * (0.93 + rough * 0.14);
    float aa = max(0.035, footprint / radius);
    float penumbra = 1.0 - smoothstep(0.86 - aa, 1.12 + aa, r);
    float umbra = 1.0 - smoothstep(0.36 - aa, 0.52 + aa, r);
    float filaments = sin(angle * 64.0 + rough * 4.0 + r * 5.0);
    filaments *= 1.0 - smoothstep(0.10, 0.45, footprint / radius);
    float absorption = penumbra * (0.46 + filaments * 0.045) + umbra * 0.43;
    float faculae = smoothstep(0.95, 1.3, r) * (1.0 - smoothstep(1.7, 2.8, r));
    return vec2(absorption, faculae * (0.4 + rough * 0.6));
}

vec2 activeRegions(vec3 n, float footprint) {
    vec2 regions = vec2(0.0);
    for (int i = 0; i < 4; i++) {
        vec3 centre = activeCentre(i);
        vec3 tangent = normalize(cross(centre, vec3(0,0,1)));
        regions += sunspot(n, normalize(centre + tangent * 0.038), 0.021, footprint);
        regions += sunspot(n, normalize(centre - tangent * 0.035 + vec3(0,0,0.008)), 0.014, footprint);
    }
    float resolved = 1.0 - smoothstep(0.012, 0.055, footprint);
    return regions * resolved;
}

// A small collection of radial magnetic-loop shapes, evaluated only just outside the limb.
// Distances are local stellar radii; marching this SDF never queries native terrain or physics.
float loopDistance(vec3 p) {
    float distance = 10.0;
    for (int i = 0; i < 4; i++) {
        if (i == 0 || i == 3) continue;
        vec3 radial = activeCentre(i);
        vec3 tangent = normalize(cross(radial, vec3(0,0,1)));
        vec3 side = cross(radial, tangent);
        vec3 offset = p - radial * 1.002;
        float height = dot(offset, radial);
        float radius = 0.066 + float(i) * 0.008 + 0.008 * sin(Phase * 10.0 + float(i));
        float u = dot(offset, tangent);
        float angle = atan(height, u);
        float arch = radius * (0.95 + 0.09 * sin(angle * 3.0 + float(i)));
        float sway = 0.017 * sin(u * 19.0 + float(i)) * sin(height * 23.0);
        // A bundle of separate strands, anchored at the surface; displacement grows with height.
        float anchored = smoothstep(0.0, 0.025, height);
        sway += 0.004 * sin(angle * 4.0 - Phase * 17.0 + float(i)) * anchored;
        for (int strand = 0; strand < 3; strand++) {
            float layer = float(strand) - 1.0;
            float ring = length(vec2(u + height * 0.24, height * 1.16)) - arch - layer * 0.004 * anchored;
            float width = 0.0012 + 0.0006 * (0.5 + 0.5 * sin(angle * 7.0 - Phase * 29.0 + float(i)));
            float tube = length(vec2(ring, dot(offset, side) - sway - layer * 0.004 * anchored)) - width;
            distance = min(distance, max(tube, -height));
        }
    }
    return distance;
}
float prominences(vec3 closest, vec3 ray, float impact, float along) {
    if (impact > 1.13) return 0.0;
    vec3 p0 = solarCoordinates(mat3(InverseView) * closest);
    vec3 direction = solarCoordinates(mat3(InverseView) * ray);
    float chord = sqrt(max(0.0, 1.13 * 1.13 - impact * impact));
    float travel = max(-chord, -along);
    float nearest = 10.0;
    vec3 nearestPoint = vec3(0.0);
    for (int i = 0; i < 20; i++) {
        if (travel > chord) break;
        vec3 p = p0 + direction * travel;
        float distance = loopDistance(p);
        if (distance < nearest) { nearest = distance; nearestPoint = p; }
        if (distance < 0.0008) break;
        travel += max(0.001, distance * 0.65);
    }
    float emission = 0.0;
    for (int i = 1; i <= 2; i++) {
        vec3 radial = activeCentre(i);
        vec3 tangent = normalize(cross(radial, vec3(0,0,1)));
        vec3 offset = nearestPoint - radial * 1.002;
        float height = max(0.0, dot(offset, radial));
        float angle = atan(height, dot(offset, tangent));
        // Bright plasma knots travel along the arch rather than flashing the whole loop.
        float knots = pow(0.5 + 0.5 * sin(angle * 9.0 - Phase * 120.0 + float(i)), 3.0);
        float footpoints = exp(-height * 100.0);
        float local = exp(-length(offset) * 5.0) * (0.18 + 0.65 * knots + 0.45 * footpoints);
        emission = max(emission, local);
    }
    float strands = noise(nearestPoint * 190.0 + vec3(sin(Phase * 37.0), cos(Phase * 41.0), 0.0) * 0.35);
    return exp(-max(nearest, 0.0) * 270.0) * emission * (0.55 + 0.45 * strands);
}

vec3 displayLight(vec3 radiance) {
    // Photographic compression only, not a physical irradiance or solar-panel energy model.
    return vec3(1.0) - exp(-max(radiance, vec3(0.0)) * Exposure);
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
    const float outerRadius = 2.8;
    if ((along < 0.0 && length(relative) > 1.0) || impact > outerRadius) discard;
    vec3 point;
    vec3 closest = ray * along - relative;
    bool inside = dot(relative, relative) < 1.0;
    float chord = sqrt(max(0.0, 1.0 - impact * impact));
    // Keep the sign selection separate: Veil's dynamic shader rewrite drops parentheses
    // around a conditional expression nested inside this vector multiplication.
    float signedChord = -chord;
    if (inside) signedChord = chord;
    vec3 normal = normalize(closest + ray * signedChord);
    vec3 n = mat3(InverseView) * normal;
    // Evaluate derivatives before the divergent photosphere/corona branch. Neighbouring
    // helper fragments use the tangent normal outside the limb instead of an undefined value.
    float footprint = max(length(dFdx(n)), length(dFdy(n)));
    // Apparent rotation transports the surface and corona together. It is presentation
    // motion, independent of the physical Sun frame and ephemeris.
    n = solarCoordinates(n);
    vec3 flow = vec3(sin(Phase * 19.0), cos(Phase * 23.0), sin(Phase * 17.0)) * 0.65;
    if (impact <= 1.0) {
        float t = along - sqrt(max(0.0, 1.0 - impact * impact));
        if (t < 0.0) t = along + sqrt(max(0.0, 1.0 - impact * impact));
        if (t < 0.0) discard;
        point = origin + ray * t;
        float grain = granules(n, flow, footprint);
        vec2 regions = activeRegions(n, footprint);
        float mu = max(0.0, dot(normal, -ray));
        // Wavelength-dependent limb darkening; the photosphere emits instead of Lambert shading.
        float edge = 1.0 - mu;
        vec3 limb = vec3(1.0) - vec3(0.38,0.44,0.52) * edge - vec3(0.12,0.14,0.16) * edge * edge;
        float convection = 0.97 + 0.06 * noise(n * 32.0 + flow * 0.4);
        vec3 radiance = vec3(1.0,0.965,0.88) * limb * grain * convection * 2.65;
        radiance *= 1.0 - clamp(regions.x, 0.0, 0.96);
        radiance += vec3(1.0,0.95,0.82) * regions.y * edge * 0.12;
        fragColor = vec4(displayLight(radiance), 1.0);
    } else {
        // Integrate a bounded optically thin volume. Angular structure persists along radial
        // streamers, while the polar regions remain fainter than the equatorial streamer belt.
        float halfChord = sqrt(max(0.0, outerRadius * outerRadius - impact * impact));
        float start = max(-halfChord, -along);
        float stepLength = (halfChord - start) / 8.0;
        float column = 0.0;
        for (int i = 0; i < 8; i++) {
            vec3 p = closest + ray * (start + (float(i) + 0.5) * stepLength);
            float radius = length(p);
            vec3 direction = solarCoordinates(mat3(InverseView) * (p / radius));
            float latitude = abs(direction.z);
            float belt = 0.22 + 0.78 * pow(1.0 - latitude * latitude, 2.0);
            float field = noise(direction * 11.0 + flow * 0.4);
            float streamers = pow(smoothstep(0.32, 0.79, field), 3.0);
            float filaments = noise(direction * 43.0 + flow * 0.35);
            // Phase fronts propagate outwards; each angular flux tube has its own phase.
            float packet = 0.5 + 0.5 * sin(radius * 24.0 - Phase * 96.0 + field * 9.0);
            float density = pow(1.0 / radius, 6.0) * (0.16 + 2.4 * streamers) * belt;
            density *= (0.50 + 0.50 * filaments) * (0.65 + 0.55 * packet);
            density *= 1.0 - smoothstep(2.2, outerRadius, radius);
            column += density * stepLength;
        }
        float loops = prominences(closest, ray, impact, along);
        vec3 emission = vec3(0.96,0.975,1.0) * column * 0.32;
        emission += vec3(1.45,0.16,0.12) * loops;
        emission += vec3(0.11,0.008,0.012) * exp(-(impact - 1.0) * 330.0);
        vec3 corona = displayLight(emission);
        float strength = max(corona.r, max(corona.g, corona.b));
        if (strength < 0.0005) discard;
        // Premultiply the emitted colour through straight-alpha blending, preserving target alpha.
        fragColor = vec4(corona / max(strength, 0.0001), strength);
        point = origin + ray * along;
    }
    vec4 clip = SolarProjection * vec4(point * RadiusScale, 1.0);
    // A far-plane Sun remains behind the bounded planetary sky meshes, never a near billboard.
    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 0.9999999);
}
