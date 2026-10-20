<!--
The Orbitalis Developers

Copyright 2026 Vsevolod Petrov (lazyhat)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Changelog

## 0.2.0 — In development

- Procedural Sun in orbital/lunar skies and the orbital map, using the existing celestial ephemeris.
  A ray-intersected photosphere has animated cellular granulation, paired sunspot groups with
  umbrae/penumbrae, wavelength-dependent limb darkening, a volumetric white streamer corona
  and small red prominence loops above active regions. Surface cells evolve, coronal flows travel
  outwards and plasma knots move along slowly deforming prominence strands, on a shared simulation clock.
  Photographic exposure preserves map detail and a white-hot distant disc.
  Solar radius scales with the selected system to retain an apparent diameter near 0.53 degrees.
  Overworld daylight and existing ship/planet illumination are unchanged.

## 0.1.0 — Alpha

First Alpha. Version frozen on 2026-10-20; publication is a separate step.

### Worlds and physics

- Finite Overworld terrain with a per-world Earth width of 1,000, 2,000, 5,000, 10,000 or 20,000 blocks.
  Native Minecraft block, fluid and neighbour logic is retained. The terrain footprint is separate
  from the movement border, allowing flight around the sides and underneath the world.
- A scaled Sun–Earth–Moon catalogue with hierarchical moving frames, physical Earth rotation,
  a tidally locked Moon and a simulation clock independent of vanilla daylight.
- Finite-body gravity with a near-surface compatibility layer and weaker distant attraction for
  players and Sable constructions. Atmospheric effects fade into vacuum; creative flight retains momentum.
- A finite lunar dimension with seeded craters and surface, side and underside approaches.

### Travel and constructions

- Automatic two-way surface/space travel for individual players and supported Sable constructions,
  including unmanned ships, seated passengers and cargo.
- Journaled ship reconstruction and interrupted-transfer recovery, preserving native motion,
  serialized blocks, supported entities, glue and cargo. Occupied plot addresses can be relocated.
- Sparse orbital space and background ballistic evolution of eligible unloaded independent stations.
- Optional Propulsion engine control in the main mod: hold, target altitude, orbital insertion,
  return to the launch pad, maneuver execution, ship selection and saved launch bookmarks.
  Active controllers retain their ships across handoffs and release their loading tickets on exit.

### Navigation and presentation

- An orbital map (M) with Earth, Moon and system views, rotation, zoom and camera-relative panning.
  Select loaded/unloaded constructions and inspect brief telemetry and predicted coast trajectories.
- Personal navigation markers and timed maneuver plans with prograde, normal and radial delta-v.
- External ship camera (F6), local-gravity navball with velocity markers and switchable nose axes (F7).
- Shared planetary relief in space and on the map, sampled from saved terrain at a 16-block Earth
  render grid, with sector updates and bounded background disk reads. Unknown regions use a fallback.
- Layered cliffs, shaded undersides, drifting native-texture clouds and a finite atmospheric halo.
- A shared high-altitude sky and held-frame world transitions that fade into the ready destination.

### Compatibility and limits

- Minecraft 1.21.1 and NeoForge 21.1.252+ within the 21.1 series; Kotlin runtime included.
- Optional Sable 2.0.6 construction integration and Propulsion 1.1.5 engine adapter. External mods
  and their dependencies are installed separately and are not bundled.
- Optional client-only Voxy 0.2.16-beta / WorldGenV2 2.4.2 compatibility for finite-terrain searches
  and shutdown. The supported preview stack uses Roxy and has separate installation requirements.
- Ship transfer remains bounded to four native plot chunks with size, passenger and connected-body
  restrictions. Autopilot targets tested creative-engine layouts and Earth launch/return routes;
  it does not provide survival fuel management, lunar landing guidance or obstacle avoidance.
- World transitions may pause. Planetary lighting is illustrative; physical solar lighting is pending.
  Overview terrain does not represent unexplored chunks exactly.
