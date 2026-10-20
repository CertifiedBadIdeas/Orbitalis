<!--
The Orbitalis Developers

Copyright 2026 The Compukters Developers
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

# Mercury and Venus

The canonical physical catalogue includes the Sun, Earth, Moon, Mercury and Venus. New planets
use derived `InnerPlanetDefinition` values rather than adding mutable parameters to profile saves.
The existing version-three profile, epoch and selected Earth width remain the source of truth.
Default phases are deterministic at simulation zero and do not reset during mass retuning.

| Quantity relative to Earth | Mercury | Venus |
| --- | --- | --- |
| Terrain width | 0.383 | 0.949 |
| Surface acceleration | 3.70 / 9.81 | 8.87 / 9.81 |
| Orbital period in 1,200-second Earth solar days | 87.969 | 224.701 |
| Signed sidereal spin period in Earth solar days | 58.646 | -243.025 |

These ratios follow the [NASA Mercury fact sheet](https://nssdc.gsfc.nasa.gov/planetary/factsheet/mercuryfact.html)
and [NASA Venus fact sheet](https://nssdc.gsfc.nasa.gov/planetary/factsheet/venusfact.html).
Orbits initially remain circular and coplanar, matching the existing prescribed catalogue. Orbital
radii follow the Sun's gravitational parameter and those periods; they are not independently scaled
real astronomical distances. Native surfaces remain finite rectangular slabs, not spherical worlds.
Their local origins and derivatives come from the same sampled hierarchy as the map and forces.

`orbitalis:mercury` and `orbitalis:venus` are stable native surface dimension identifiers. Their finite
chunk generator uses shared deterministic terrain samplers: a cratered grey/brown Mercury and an
undulating volcanic ochre Venus. Natural basalt seals the underside; no bedrock floor is added.
Generation is bounded by the selected width, while exterior flight and ordinary runtime construction
remain possible. Each generator is configured before workers start and retains its derived seed.

The existing client gravity/ephemeris payload protocol is now version five. It adds a validated
2-bit presence mask and at most two derived 64-bit terrain seeds. Unknown bits are rejected; missing
seeds deliberately retain coarse LOD rather than inventing a seed. The world seed itself is not sent.
Mixed protocol versions are rejected by the network registration. Persisted profile data is unchanged.

LOD shares the native height sampler and keeps independent body-sector caches. Four background
mesh jobs and two uploads per update bound refinement; existing fine meshes are retained. Camera
relative offsets are subtracted in double precision. The shared renderer sorts nonintersecting bodies
far to near in one depth domain, including ties at the precision limit of the private sky target.
The private perspective near plane adapts to the nearest surface. Deferred Earth clouds/atmosphere
retain the original native projection for interactions with terrain, while decoding the sky buffer
using its scene projection. Orthographic map projection is unchanged.

The first surface implementation does not add pressure, heat damage, chemistry or survival propulsion
management. Those systems require separate gameplay decisions; visual terrain and physical travel do
not imply a realistic atmospheric simulation. See [local orbital sectors](orbital-sectors.md) for native
coordinate bounds, route journals, persistence and current background-coast limits.
