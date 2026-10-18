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

# Finite flat surface

The first playable surface is the normal Overworld. Its default square border is 10,000 blocks wide,
centred at X=0/Z=0: planes at -5000 and +5000. Native Minecraft terrain generation, height limits,
placement, fluids, block entities and mod connections apply. This avoids face seams and
per-mod coordinate adapters. No Sable construction represents the ground.

Create a new world. The previous cube and `planet_lab` are retired; no cube commands, chart code,
player camera/gravity mixins or Create/fluid seam adapters remain. Existing save files are preserved,
but this version does not migrate or support playing old cube saves. Git retains the experiments.

## Border authority and persistence

Before initial spawn generation, `FlatOverworld.prepare` restores native saved border settings and
`FlatOverworld.initialize` installs centre (0,0) and size 10,000 only when the
Overworld's `data/orbitalis_flat_surface.dat` initialization marker is absent. Its payload is a
compound with integer `Version=2`. The marker contains no copy of border size or centre. Native
world data saves the actual settings. Administrator `/worldborder` edits survive subsequent starts.
A version-one marker with the exact stationary default border (2000 wide, centred at zero)
upgrades to 10,000 and version two. Customized version-one borders keep their native settings and
upgrade only the marker. Unknown versions or malformed fields mean initialization already happened: leave native settings unchanged and do not rewrite that marker. Deleting the marker
explicitly opts back into default initialization. A completely corrupt/unreadable SavedData file follows
Minecraft's native missing-data handling. This is not a migration scheme for old experimental saves.

The normal border applies at all heights and provides native collision/damage/visual behavior. There
is no top cap. New terrain also ends at this border: whole block columns must fit inside its current
rectangle. At the default size, columns -5000 through 4999 generate normally. Partially intersecting
chunks are trimmed after terrain-producing stages, and generation-region feature/structure writes and
initial entity spawning outside the rectangle are rejected. Entirely exterior chunks skip terrain,
structures and spawning and use the void biome, while native status completion and lighting continue.
Empty technical neighbour chunks remain loadable and save normally; rejecting their requests would
break native generation dependencies. This is a terrain cutoff, not a replacement chunk scheduler.

Shared/common publishes immutable border snapshots for workers. Size/centre changes update them
immediately; server ticks update moving borders. Load initialization also updates the native saved
border settings so Minecraft's later startup application cannot restore the old default size. The
marker format stays unchanged. Only Overworld is bounded; Nether, End, space and Sable storage retain
their own generation behavior. Already-generated chunks and ordinary runtime block placement are not
trimmed. Shrinking a populated world does not erase old terrain, and expanding into previously completed
empty chunks does not regenerate them. Use a new world for this experiment; no existing save/cache is
rewritten or migrated.

Behavior of arbitrary Sable hulls crossing the wall needs a separate
native gameplay check; do not infer rigid-body confinement from player border collision alone.

`orbitalis:space` does not subscribe to Overworld border changes and does not
receive surface-border update packets. The native login/dimension-change initialization path is adapted to use its own border too.
Vanilla Nether/End inheritance remains intact. Space border edits have no separate persistent
world-data authority in this stage; their default is Minecraft's large border on each load.

## Flight and next work

The Overworld now has its own low [finite-body orbital route](flat-gravity.md). Block and fluid behavior stays native; player and construction gravity stay vertical through Y=320 and transition
to the finite-body field above it. The former diagnostic flight dimensions and their commands are removed; old save directories
are preserved. Live terrain imaging, seamless ship preloading and general edge/underside travel
remain future work.

Run `:v1_21_1-neoforge:runGameTestServer` for surface boundary, marker persistence, border delegation,
packet recipient isolation, ordinary ship forces, full-space orbit and durable transfer recovery.
Use `-PorbitalisGameTestSuite=surface` for the nine border, preload, void-floor and player-motion scenarios. A headless test does
not establish visual correctness; inspect a new world and `/worldborder get` in an interactive client.

Use `-PorbitalisGameTestSuite=terrain` for native generation at all four corners, exterior completion,
heightmaps, void biomes, rejected feature/entity writes and unaffected runtime placement.
