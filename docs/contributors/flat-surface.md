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

The first playable surface is the normal Overworld. Its finite terrain footprint is initially
10,000 by 10,000 blocks, centred at X=0/Z=0: columns -5000 through 4999. The native movement border
is distant (Minecraft's MAX_SIZE), so players and Sable ships can fly beyond the edge, around its
sides and underneath. Blocks, fluids, neighbours and other mods retain native coordinates and logic.
Bedrock and the native building height are unchanged. No Sable construction represents the ground.

## Terrain authority and persistence

Before initial spawn generation, `FlatOverworld.prepare` restores native saved movement-border
settings and initializes `data/orbitalis_flat_surface.dat`. Version 3 stores integer `Version=3`
and an integer array `Bounds=[minX,minZ,maxX,maxZ]`, with inclusive minima and exclusive maxima.
Bounds must contain four integers within +/-30,000,000 and have nonnegative width/depth (an empty legacy footprint stays empty). Generation
workers consume immutable snapshots of this saved footprint. Native `/worldborder` controls movement
and its normal collision, damage, warning, centre and lerp behavior; it no longer changes generation.
Administrator edits to that movement border persist normally, including deliberately close walls.

A missing record installs the default footprint and MAX_SIZE movement border. Version-one exact
legacy defaults (2000 or 10000 at zero) and version-two exact 10000 defaults migrate to that same
footprint and distant movement border. Customized v1/v2 borders are captured as the previous generation
footprint, rounded inward to whole block columns; their movement settings are preserved. Existing
version-three records never reset native border settings. Unknown versions and malformed records
are read-only and use the bounded default footprint; their file is not rewritten. Deleting the
record explicitly opts back into default initialization. A corrupt/unreadable SavedData file follows
Minecraft's native missing-data handling. No existing terrain or cache is erased or regenerated.

New terrain ends at the saved footprint. Partially intersecting chunks are trimmed after terrain-
producing stages; generation-region feature/structure writes and initial entity spawning outside it
are rejected. Entirely exterior chunks skip terrain, structures and spawning and use the void biome,
while native status completion and lighting continue. Empty technical neighbour chunks remain
loadable and save normally; rejecting their requests would break generation dependencies. This
remains a terrain cutoff rather than a replacement chunk scheduler.

The flight consumer's optional WorldGenV2 adapter prunes wholly exterior search cells and only
reserves batches containing unfinished interior work. Native graph leaves can stay partial at the
terrain footprint or the circular generation radius, even after every eligible chunk is finished. Each
search excludes exhausted leaves locally and continues to the next candidate; it does not persist
fake completion, discard native in-flight reservations or prevent a later search from reconsidering
the leaf after player movement or radius changes. The native completion index remains the source of truth. A conservative two-cell search margin
compensates for native distance rounding on four-chunk cells, followed by an exact chunk-coordinate
circle filter so the configured generation radius does not expand.

Only Overworld generation is bounded; Nether, End and Sable plot storage retain native behavior.
Already-generated chunks and ordinary runtime placement are not trimmed. Previously completed empty
chunks do not regenerate automatically. Prefer a new world when changing the experimental footprint.
The retired cube/planet_lab saves remain preserved and unsupported; no conversion is attempted.

`orbitalis:space` does not subscribe to Overworld border changes and does not
receive surface-border update packets. The native login/dimension-change initialization path is adapted to use its own border too.
Vanilla Nether/End inheritance remains intact. Space border edits have no separate persistent
world-data authority in this stage; their default is Minecraft's large border on each load.

## Flight and next work

The Overworld now has its own low [finite-body orbital route](flat-gravity.md). Block and fluid behavior stays native; player and construction gravity stay vertical through Y=320 and transition
to the finite-body field above it. The former diagnostic flight dimensions and their commands are removed; old save directories
are preserved. Close side/underside travel remains in Overworld; distance-based automatic handoff moves distant
players and supported constructions into the orbital frame and back, without a footprint wall.
Below-world removal is suppressed in these two frames, while other hazards remain native.
The decorative orbital underside is still illustrative, not solid generated terrain.

Run `:v1_21_1-neoforge:runGameTestServer` for surface boundary, marker persistence, border delegation,
packet recipient isolation, ordinary ship forces, full-space orbit and durable transfer recovery.
Use `-PorbitalisGameTestSuite=surface` for the ten border, footprint migration, preload, void-floor and player-motion scenarios. A headless test does
not establish visual correctness; inspect a new world and `/worldborder get` in an interactive client.

Use `-PorbitalisGameTestSuite=terrain` for native generation at all four corners, exterior completion,
heightmaps, void biomes, rejected feature/entity writes and unaffected runtime placement.
