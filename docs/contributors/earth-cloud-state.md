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

# Earth cloud state synchronization

The Overworld owns Earth weather for ground, map and orbital views. `EarthCloudEvents` samples its
native rain and thunder strengths without loading terrain. All connected players receive one immutable
snapshot every 20 server ticks, on login and after dimension changes, irrespective of their current world.
The snapshot contains a stable mixed cloud seed, native weather game tick, weather strengths and a
192-metre base height. It introduces no SavedData and sends no raw world seed.

`orbitalis:earth_cloud_state` is a dedicated clientbound payload, registrar version `1`, with an explicit
wire version byte `1`. Existing physics and sky-lighting payloads are unchanged. The bounded layout is
version byte, signed seed long, rain float, thunder float, nonnegative weather VarLong and base-height
double (at most 34 bytes). Unknown versions, nonfinite/out-of-range weather and heights outside
64–1024 metres fail validation; truncated buffers fail native decoding. The client handler schedules
application on its main executor. Both ends require this payload registration; older clients without it
are intentionally incompatible with the new cloud-state feature.

`EarthCloudState.client` exposes the latest authoritative snapshot; `sampleClient()` interpolates rain
and thunder over one second. Duplicate or older weather epochs do not restart interpolation. Dimension
changes preserve the same Earth state; disconnect clears it. A different cloud identity installs a fresh
weather state without blending between worlds.

Wind uses the synchronized persisted physical clock supplied by the renderer, never the recipient
world's game time or camera location. `wind(tick, seconds, period)` returns wrapped translation at
0.6 m/s along X and 0.2 m/s along Z. The caller must supply its genuinely periodic cloud-field size;
wrapping an aperiodic field would cause visual jumps. Whole ticks use integer-centimetre modular
arithmetic before floating-point conversion, preserving phase at large signed tick values and with
negative interpolation seconds. The period must fit a centimetre grid and be within 0.01–1e9 metres.

Unit tests cover interpolation, ordering, disconnect, identity, large-epoch wind continuity and malformed
payloads. `nativeEarthCloudWeatherSnapshot`, registered in the `settings` and `all` GameTest suites,
checks the native Overworld producer and packet round trip with a different world's weather.
Network delivery and rendered ground/map/orbit agreement still require a real client observation.

## Rendering

`core.cloud.ProceduralCloudField` produces deterministic 16-metre columns with eight-metre height
steps in an 80-metre layer above the snapshot base. The 65,536-metre field is exactly periodic,
including negative coordinates, so wrapped physical-time advection has no pattern reset. Sector meshes
contain exterior faces only; a neighbor halo subtracts internal side intervals across sector edges.

The client keeps one detail step (1, 2, 4 or 8) across each view. Native surface views use distance and
map views use projected pixel density; FAST increases the step and OFF suppresses rendering. Cloud
coverage is clipped and feathered at the finite Earth footprint, with a six-kilometre surface distance
fade. Volume geometry is independent of the surface atlas and never requests native chunks.

`CloudSectorCache` runs at most two pure CPU workers with 16 pending jobs. At most eight completed
meshes are uploaded in a frame. The render-thread LRU is bounded by 2048 sector entries, two million
vertices and 64 MiB of estimated vertex storage; empty sectors retain markers. Uploads and eviction
precede a frame's buffer selection. Reset cancels queued work and discards results from the old
cache generation. Seed and detail are cache keys; wind and lighting are uniforms, not rebuild triggers.

Finite Earth always uses these volumes in both sky modes. The vanilla mode uses the native Sun angle
for ground cloud lighting; the cosmic mode, orbital views and the map use physical ephemerides. All
views use the same weather snapshot and volume thickness. The nearest translucent cloud surface is
resolved before composition against native terrain/ships, with separate compressed celestial depth
when available. Other dimensions retain their own native clouds.
