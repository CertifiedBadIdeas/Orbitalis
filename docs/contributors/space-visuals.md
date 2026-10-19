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

`orbitalis:space` uses black vacuum fog and a fixed star field. Native nearby clouds,
rain rendering and sunrise/sunset gradients are suppressed; the planet retains a distant cloud layer. Exposed skylight has constant daytime
brightness; native sky occlusion and warm block lights remain, followed by vanilla gamma, night vision
and darkness handling. Minecraft's ordinary face shading remains: this is not directional shadow
mapping or a physical sunlight simulation. Server time and weather simulation are unchanged. The ordinary ground-level Overworld sky remains
native; high-altitude presentation blends towards the same orbital scene before dimension travel.

A single client-only `ClientLevel.effects()` selector supplies NeoForge's native dimension-effects
extension for this dimension ID. NeoForge owns the hook and effects adapter; shared/common owns the actual sky and body mesh. No Sable
or Create APIs participate in rendering, and the selector never loads on a dedicated server.

The reference body remains a 10,000-by-10,000-block flat slab centred at surface X/Z zero,
with a fallback top at Y=64 and its actual flat lower boundary at Y=-64.
Its orbital top now uses a server-owned
surface atlas: each naturally loaded Overworld chunk contributes sixteen height/map-colour probes,
averaged to one chunk sample. Water, grass and leaves use native biome colours. The client combines
known samples into 16-metre overview cells, including large terrain changes and buildings; this is
an overview, not block-resolution geometry or a collision surface. Vertical skirts join differing
heights, unknown neighbours and the slab edge. Administrator border changes do not resize the
physical reference body or its atlas. Side strata and the underside remain illustrative.

`PlanetRockShell` shades the underside using a deterministic 64-by-64 triangulated grid, flat at
Y=-64 (8,192 triangle faces encoded as quads with a repeated final vertex). Its jitter is horizontal
only, so no decorative volume protrudes beyond the real bedrock floor. Independently rebuilt surface
sectors own their outer cliffs: each 16-metre perimeter cell extends from its known atlas height
(or fallback Y=64) down to Y=-64. Absolute strata levels align beside neighbouring cells, with a thin
sample-coloured top band and deterministic stone colour variation. The static body buffer contains
only the underside; there is no overlapping fixed-sea-level wall. Coarse initial sectors include
matching fallback walls before their fine replacements arrive. Rock uses modest illustrative ambient
fill to keep the unlit underside readable; this is not new stellar lighting. No bitmap assets or
runtime rock textures are added. Cliff interiors/caves/materials remain illustrative: the atlas stores
only a top height/colour, not a full side scan. Loaded native chunks render in front of the sky model.

`PlanetLimbRenderer` adds a luminous blue feathered upper-edge halo to the shared map/world scene
after opaque geometry: a narrow pale core fades into a broad blue outer glow, using additive RGB
blending while preserving destination alpha for the later sky composite. No post-process bloom target
is allocated. Its static vertex buffer is uploaded once, shared by map/world and released on
disconnect; rendering does not rebuild or upload the rim each frame. The map tests existing scene depth without writing depth. In the world, a post-level pass
combines the private planet mask with physical front-atmosphere depth, as detailed below;
clouds remain a separate pass and nearer foreground ships retain depth occlusion. This bounded geometric halo is not
physical atmospheric scattering. The shell is decoration, with no collision or terrain generation;
it does not change the mass model, gravity field, coordinate frames or transfer altitudes.

Unknown overview cells keep the existing deterministic water/coast/land mosaic. Each known chunk replaces its 16-metre cell.
The sampler never requests chunk generation or loading. Each normal full-chunk load/unload captures
sixteen probes immediately on the server thread, so a lifecycle burst cannot lose chunks behind a
refresh budget. Two still-loaded chunks per tick are additionally refreshed in round-robin order.

Existing saved terrain is backfilled independently of live chunk loading and Voxy. A background scan
reads only existing region location tables inside the footprint, nearest the origin first. Up to eight
read-only snapshot reads may be pending on two dedicated disk/decompression workers; the server consumes up to eight ready records
per tick, stopping between records after a two-millisecond decode budget. Native block-state and biome
palette codecs decode only sampled sections; no live chunk, ticket, entity, POI or lighting is installed.
The scan repeats at most once per minute after draining, so generation that bypasses full-chunk events
can still contribute after saving. Complete current-version chunks with a WORLD_SURFACE heightmap are
eligible; malformed, incomplete and older/newer-version terrain retains fallback until opened normally.
Readers open only existing Anvil region/external files in READ mode, use native gzip/deflate/plain/LZ4
codecs and an 8 MiB compressed/allocation bound per entry, and recheck location/timestamp after reading.
Custom codecs, changed or malformed entries retain fallback and may be retried on a later scan. Native
chunk writes remain authoritative; the readers never share a writable storage handle or flush world I/O.
Disk samples only fill unknown entries and cannot overwrite newer live observations. Backfill is
incremental and may take minutes for a large existing world. Server stopping cancels the bounded queue and interrupts its workers without waiting for disk reads.
Unloaded terrain retains its saved sample.

The Overworld owns `data/orbitalis_surface_atlas.dat`, SavedData version 1. `Samples` is an int array
of `(chunkX, chunkZ, height, rgb)` tuples, bounded to the physical footprint (626 by 626 partial/full
chunks), heights -4096..4096 and 24-bit RGB. Invalid records are skipped; invalid array lengths
produce an empty cache. An unsupported version is read-only and presents fallback, preserving its
file. Missing data starts empty. This cache never edits terrain or physics.

Clientbound `orbitalis:surface_atlas`, registrar version 1, carries a server-session UUID, reset bit,
nonnegative revision and at most 256 validated samples. Orbital players, surface players within the upper/lateral/lower visual transition, and leased map viewers receive it, with at
most one packet/player/server tick. The server retains only each cell's current revision, not an
unbounded history; a cursor streams current values and later replacements. Login/reconnection
starts a reset; disconnect clears client data and GPU resources. Data survives dimension travel on
the same connection, and never crosses save/server sessions. No client request or serverbound packet
is needed. Client data does not require an Overworld ClientLevel while orbiting.

The camera position is converted through `FlightSpaceCoordinates.globalPosition`, using the same existing
frame translations as transfer and force evaluation. Body vertices and the observer use surface
coordinates. A single uniform scale fits the entire camera-relative mesh within a 60-unit sky shell,
preserving every viewing angle and perspective size. The model therefore remains visible beyond
chunk render distance and shrinks naturally with distance. A private opaque sky colour/depth target resolves terrain self-occlusion without putting compressed
sky depth in front of ships. Stars are drawn first, the body afterward, and the whole sky is
composited once with the visual blend weight. Normal world geometry draws afterward, so ships occlude
the background. Shared map rendering continues to use the same cached body mesh with its own camera.
The renderer restores incoming read/draw framebuffers, viewport, clear colour, shader, shader colour
and depth/culling/blend state. Overview geometry is cached in 400 independent 512-by-512-metre
sectors plus a static body buffer. A changed sample marks its sector and, when needed, the sectors
owning its negative-X/negative-Z height walls. Each immutable job snapshot includes only local samples
and the positive-edge halo; unchanged sectors are neither copied nor rebuilt. Up to four CPU jobs
run on the background executor, with at most two completed sector uploads per rendered frame and fair
round-robin scheduling. The last complete sector remains visible while its replacement builds; a few
coarse fallback quads cover each sector before its initial fine mesh is ready. CPU jobs never perform
OpenGL calls. Session resets/disconnects close old GPU buffers, reject stale jobs and release their CPU
buffers on completion without blocking. Map and world share these same buffers.

The 16-metre top is bounded to 391,876 quads, with at most two interior skirts per cell and bounded
perimeter skirts. Persisted atlas and packet formats are unchanged. Base top geometry uses about
24 MiB of vertex data; a maximal alternating-height surface including skirts is below 1.2 million
quads (about 74 MiB). A full interior sector has at most 1,024 tops and 2,048 interior walls (roughly
192 KiB of vertex data), so a local update avoids preparing/uploading the whole planetary mesh.
No terrain requests are introduced. Sky is hidden in fluids, powder snow and under blindness/darkness;
medium-specific fog is retained.

`PlanetCloudRenderer` shares a cached periodic mesh between the map and world. FANCY uses
native-height four-metre columns with shaded tops, undersides and sides; FAST remains a flat sheet
and OFF hides clouds. The runtime `minecraft:textures/environment/clouds.png` supplies the mask.
Nearest sampling without mipmaps keeps its native pixel boundaries sharp. A private texture instance
preserves the surface texture's state, reloads with resource packs and is released on disconnect;
no Minecraft texture is copied or bundled.

One 3,072-metre period contains two caps and 1,024 textured side slices. Runtime alpha masks those
slices, matching native 12-metre columns without decoding or copying the texture on the CPU. The same
period buffer repeats over at most 64 tiles for a 20,000-block planet; fragment clipping keeps the
finite footprint. The native float clock supplies 0.03-metre/tick drift and the one-third-cell Z phase.
The last observed Overworld cloud height persists through travel, with bottom at height + 0.33 and
native FANCY thickness, top inset, side inset, face shading and alpha. Four cached perimeter
quads close column cuts at the finite world boundary. Their runtime UVs follow the same moving
mask, sampled just inside the footprint; empty columns remain empty. The perimeter buffer is
rebuilt only when the selected body width changes. Fragment footprint clipping applies only to
the oversized repeating period mesh: the already-bounded caps must bypass it because perspective
interpolation can round a fixed boundary coordinate just outside its plane.

Clouds use the map's shared scene depth. In the world, their pass follows level rendering and samples
the private planet depth while writing physical projected fragment depth for native terrain and
ship occlusion. In the world, FANCY resolves its colour and nearest depth together in a separate
full-viewport RGBA/float-depth target using compressed scene coordinates. Blending is disabled in
that target: the nearest fragment replaces earlier interior volume sheets with straight-alpha colour.
A fullscreen pass then reconstructs physical depth from the compressed depth and compares it against
native terrain/ships while blending the resolved colour once. Volume faces are never rerasterized
against a different depth format. A physical-depth prepass alone loses precision between overlapping
sheets; rerasterizing a float-depth selection into a depth24 target can also leave holes. The layer
samples the separate planet mask, never its own active attachment. Empty mask fragments never write
depth. The target is reused between frames, recreated on resize and destroyed on disconnect.
Map clouds retain their shared compressed scene-depth pass. The pass restores framebuffer bindings,
viewport, clear colour, depth, colour-write, culling and blending state.

At the beginning of the elevated transition, the original native cloud method is replaced once,
including Sodium replacements. Geometry, phase and nearest texture sampling coincide instead of
cross-fading overlapping volumes. Ordinary terrain distance fog is disabled throughout Overworld,
Moon and space with finite fog distances beyond the playable volume, independently of altitude.
The distant cloud shader likewise adds no distance haze, and native clouds inherit the no-fog
terrain range, avoiding a fog cutover between the two renderers. Fluid, blindness and darkness
fog remain native, as do unrelated dimensions. Native rendering remains below the transition;
custom cloud renderers, shader packs and independent third-party weather fog need separate
compatibility checks.

At transition heights the camera's far plane encloses the reference body, keeping compressed planet
geometry and physical clouds in a valid native depth range. This changes projection, not terrain
tickets or render distance. Each cloud frame is consumed once and rejects changed levels; fluid and
effect restrictions cannot reuse a stale frame. This is presentation, not cloud weather or shadows.

In the world, the limb feather also has a dedicated post-level pass. Its private planet-depth mask
retains near/far-side visibility. The entire mesh is elevated above the actual Overworld build
ceiling (bright core at ceiling + 96 metres), with the same height used by map/world meshes,
cache invalidation and atmospheric bounds. Physical depth uses the ray's front intersection with the
finite atmospheric envelope. Loaded terrain within the build envelope therefore does not cut the feather off at its chunk
silhouettes, and objects closer than the atmospheric entry still occlude it. Objects inside the
layer receive front haze. The map retains its ordinary scene limb pass. This geometric front-scatter
model remains illustrative, rather than a physical atmospheric scattering simulation.

The new [finite-body route](flat-gravity.md) uses `orbitalis:space`, with represented Y equal to
physical Y+4096. Its geometry shares the initial surface size and sea-level constants with this mesh.
The ship can view the slab from above, alongside and below. Higher-resolution terrain textures, physical atmospheric scattering, eclipses
and directional ship shadows remain separate work. Physical illumination is intended to share the
future solar-system light source; this transition does not introduce a star, inverse-square lighting
or planet shadows.

## Verification

`SurfaceMeshSectorsTest` protects local invalidation, immutable halo snapshots, negative coordinates,
shared-wall ownership and coarse/fine footprint coverage. `SavedSurfaceReaderTest` covers native
compression modes, external entries, read-only access and malformed/missing snapshots.
`SurfaceAtlasTest` protects updates, bounded revision streams, coordinates and mixed known cells.
`SurfacePlanetMeshTest` verifies complete fallback coverage, real relief replacement, edge/unknown
skirts, real-height perimeter wall coverage, coarse exterior walls and the mesh budget. `-PorbitalisGameTestSuite=atlas` requires native sampling/terrain-change,
real SavedData disk reload, saved-terrain backfill, lifecycle bursts, future/malformed data handling
and packet bounds/roundtrip scenarios.
In a disposable client world, inspect generated land from above, then view the slab edges/underside
and return to the same save after restart. Unknown regions should retain the mosaic; known terrain
should retain location, height and map colours after unload and dimension travel. Change a large
roof while loaded and verify the next pass updates its orbital overview. Compilation does not prove
framebuffer compositing or actual client receipt.

`SpaceSceneTest` exercises angular preservation at near/far and opposite-side viewpoints, the rock
underside's manifold joins, exact lower plane, outward unit normals, area/geometry bounds and
distance-dependent apparent size. Run target checks and inspect a disposable
client world; compilation alone does not demonstrate appearance. Suggested manual views:

- `/gamemode spectator`, then `/execute in orbitalis:space run tp @s 0 7160 0 0 90`
  shows the surface from physical Y=3064, outside the automatic return band.
- `/execute in orbitalis:space run tp @s 6500 10160 6500 135 15`
  shows the top and two sides.
- `/execute in orbitalis:space run tp @s 0 -1904 0 0 -90`
  shows the underside from physical Y=-6000.
- Compare `/time set noon` and `/time set midnight`: vacuum sky, body and skylight stay fixed.
- `/execute in orbitalis:space run tp @s 0 9160 0 0 90` shows the body from 5000 m above sea level
  through the new low frame.
- Return to `minecraft:overworld` to confirm the ordinary sky still follows time and weather.

## Surface/space presentation transition

`FlightAtmosphere` defines a smooth height weight in physical surface coordinates. It starts at
Y=364 and reaches full vacuum at Y=1564 for the current planet size, before both the descent (2064)
and ascent (3064) triggers. Both frames therefore show the same sky at their handoff. The weight
uses the existing route coordinate transform and scale constants, not distance from a dimension's
native Y origin. Side and lower presentation additionally follow the existing compact atmospheric
falloff, reaching vacuum within 256 m outside the footprint/below the body. Atlas subscriptions and
held-frame forecasts include these edge/underside approaches. At and below the start, native sky rendering remains unchanged. Sky colour, native cloud
presentation, rendered rain strength and exposed lightmap colours approach the orbital presentation with
the same weight. Fluid and effect-specific sky restrictions remain. A lightmap adapter wraps only
the native Overworld effects and delegates its render hooks; unrelated effects implementations stay
native. Custom sky/weather renderers which bypass these hooks require their own verification.

Client skylight sampling above the native building ceiling returns full sky in this route, where
static terrain cannot occlude it. Native chunk-light storage may be absent there, producing zero
light for both standalone players and Sable hulls. The correction applies before Sable combines its
external scene light with plot light, preserving native cabin occlusion and block lights. Queries
inside the building range, server lighting and other dimensions remain native. It is not a physical
sunlight or directional-shadow simulation. Physical lighting is deferred to the solar-system model:
light direction, distance and planetary occlusion must derive from an explicit shared light source
and celestial reference frames, rather than another local day/night override.

`FlightTransitionView` owns at most one full-resolution colour texture of the last rendered frame.
A bounded three-second all-face arrival estimate enables GPU copies near the appropriate boundary;
there is no CPU pixel readback, terrain load or network request. An unused capture expires after one
second. Native respawn destination observation arms a hold only for the explicit Overworld/space
and Moon/space routes, on the same live connection with a fresh source image. Unexpected travel without a fresh
capture retains ordinary loading.

The native `ReceivingLevelScreen` continues ticking, closing itself and driving normal readiness;
only its rendering is suppressed while a valid hold exists. All native network acknowledgments,
ship transfer journals/checkpoints, physics pauses and player input authority are unchanged. A
travelling Sable passenger also waits for contact with the same finalized UUID and a drawable native
scene with compiled geometry. Invisible sections need not all compile. Once readiness remains stable
for 200 ms, the old frame fades over the live destination for 250 ms. This deliberately freezes the
picture; it does not simulate continuous motion or retain a second ClientLevel.

A ten-second deadline, another respawn/recovery, unrelated screens, death, connection changes and
framebuffer resize cancel the hold and expose native presentation. Disconnect releases frame and sky
GPU resources. Readiness loss restarts the settle interval without extending the deadline. A future
continuous handoff remains a separate transfer/renderer design, not a guarantee of this bridge.

`FlightAtmosphereTest` checks the blend endpoints, monotonicity and invariance at both frame handoffs.
`HeldFrameTimingTest` checks stable readiness, fade, readiness loss, timeout and cancellation. Client
acceptance additionally needs a real mounted round trip and independent-player travel in both
directions, with the native loading screen still closing itself. Check an exposed passenger and a
sealed, unlit cabin separately: fixing absent external light must not illuminate cabin interiors.
Observe ground weather/time, intermediate sky, full vacuum and disconnect resource cleanup. The
optional-mod-absent startup check remains separate from flight-client visual acceptance.

The placeholder Sun square is no longer drawn in the flight sky. The prescribed Sun remains part
of the celestial catalogue and orbital map; physical solar illumination remains deferred.
