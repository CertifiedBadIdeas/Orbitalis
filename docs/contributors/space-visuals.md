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

# Space presentation

`orbitalis:space` uses black vacuum fog, a fixed star field and a fixed Sun. Clouds,
rain rendering and sunrise/sunset gradients are suppressed. Exposed skylight has constant daytime
brightness; native sky occlusion and warm block lights remain, followed by vanilla gamma, night vision
and darkness handling. Minecraft's ordinary face shading remains: this is not directional shadow
mapping or a physical sunlight simulation. Server time, surface weather and the ordinary Overworld
sky are unchanged.

A single client-only `ClientLevel.effects()` selector supplies NeoForge's native dimension-effects
extension for this dimension ID. NeoForge owns the hook and effects adapter; shared/common owns the actual sky and body mesh. No Sable
or Create APIs participate in rendering, and the selector never loads on a dedicated server.

The reference body remains a 10,000-by-10,000-block flat slab centred at surface X/Z zero,
with a fallback top at Y=64 and underside at Y=-64. Its orbital top now uses a server-owned
surface atlas: each naturally loaded Overworld chunk contributes sixteen height/map-colour probes,
averaged to one chunk sample. Water, grass and leaves use native biome colours. The client combines
known samples into 64-metre overview cells, including large terrain changes and buildings; this is
an overview, not block-resolution geometry or a collision surface. Vertical skirts join differing
heights, unknown neighbours and the slab edge. Side strata and the underside remain illustrative.
Administrator border changes do not resize the physical reference body or its atlas.

Unknown overview cells keep the existing deterministic water/coast/land mosaic. Any known samples
in a cell provide its overview; a partially sampled cell is approximate until its other chunks load.
The sampler never requests chunk generation or loading. Previously generated chunks that have not
been observed since installing this feature retain fallback until their next normal load. Voxy and
other terrain loaders may populate the atlas through ordinary chunk lifecycle events, but are not
required. At most two chunks per server tick are sampled in round-robin order; unload may capture the
last available sample on the server thread within that same budget. Changes to still-loaded terrain are picked up on the
next sampling pass. Unloaded terrain retains its saved sample.

The Overworld owns `data/orbitalis_surface_atlas.dat`, SavedData version 1. `Samples` is an int array
of `(chunkX, chunkZ, height, rgb)` tuples, bounded to the physical footprint (626 by 626 partial/full
chunks), heights -4096..4096 and 24-bit RGB. Invalid records are skipped; invalid array lengths
produce an empty cache. An unsupported version is read-only and presents fallback, preserving its
file. Missing data starts empty. This cache never edits terrain or physics.

Clientbound `orbitalis:surface_atlas`, registrar version 1, carries a server-session UUID, reset bit,
nonnegative revision and at most 256 validated samples. Only orbital players receive it, with at
most one packet/player/server tick. The server retains only each cell's current revision, not an
unbounded history; a cursor streams current values and later replacements. Login/reconnection
starts a reset; disconnect clears client data and GPU resources. Data survives dimension travel on
the same connection, and never crosses save/server sessions. No client request or serverbound packet
is needed. Client data does not require an Overworld ClientLevel while orbiting.

The camera position is converted through `FlightSpaceCoordinates.globalPosition`, using the same existing
frame translations as transfer and force evaluation. Body vertices and the observer use surface
coordinates. A single uniform scale fits the entire camera-relative mesh within a 60-unit sky shell,
preserving every viewing angle and perspective size. The model therefore remains visible beyond
chunk render distance and shrinks naturally with distance. A private colour/depth render target resolves terrain self-occlusion without putting compressed
sky depth in front of ships. Its transparent result is composited over the stars. Stars and Sun are drawn first, the body afterward, and normal
world geometry afterward, so ships occlude the background. The renderer restores its shader,
shader colour, framebuffer and incoming depth/culling/blend state. Overview geometry is cached in a
GPU vertex buffer and rebuilt at most twice per second when samples change; the top is bounded to
24,964 quads, with at most two interior skirts per cell and bounded perimeter skirts. Sky is hidden in fluids, powder snow and under blindness/darkness;
medium-specific fog is retained.

The new [finite-body route](flat-gravity.md) uses `orbitalis:space`, with represented Y equal to
physical Y+4096. Its geometry shares the initial surface size and sea-level constants with this mesh.
The ship can view the slab from above, alongside and below. Higher-resolution terrain textures, atmosphere transitions, eclipses
and directional ship shadows remain separate work.

## Verification

`SurfaceAtlasTest` protects updates, bounded revision streams, coordinates and mixed known cells.
`SurfacePlanetMeshTest` verifies complete fallback coverage, real relief replacement, edge/unknown
skirts and the mesh budget. `-PorbitalisGameTestSuite=atlas` requires native sampling/terrain-change,
real SavedData disk reload, future/malformed data handling and packet bounds/roundtrip scenarios.
In a disposable client world, inspect generated land from above, then view the slab edges/underside
and return to the same save after restart. Unknown regions should retain the mosaic; known terrain
should retain location, height and map colours after unload and dimension travel. Change a large
roof while loaded and verify the next pass updates its orbital overview. Compilation does not prove
framebuffer compositing or actual client receipt.

`SpaceSceneTest` exercises angular preservation at near/far and opposite-side viewpoints, side and
underside visibility, and distance-dependent apparent size. Run target checks and inspect a disposable
client world; compilation alone does not demonstrate appearance. Suggested manual views:

- `/gamemode spectator`, then `/execute in orbitalis:space run tp @s 0 6160 0 0 90`
  shows the surface from physical Y=2064.
- `/execute in orbitalis:space run tp @s 6500 10160 6500 135 15`
  shows the top and two sides.
- `/execute in orbitalis:space run tp @s 0 2032 0 0 -90`
  shows the underside from physical Y=-2064.
- Compare `/time set noon` and `/time set midnight`: vacuum sky, Sun, body and skylight stay fixed.
- `/execute in orbitalis:space run tp @s 0 9160 0 0 90` shows the body from 5000 m above sea level
  through the new low frame.
- Return to `minecraft:overworld` to confirm the ordinary sky still follows time and weather.
