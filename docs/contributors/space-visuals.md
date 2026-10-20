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
rain rendering and sunrise/sunset gradients are suppressed; the planet retains a distant cloud layer. Unobstructed exposed skylight uses daytime
brightness; the direct-solar visibility layer below attenuates dynamic external light in celestial shadows.
Native cabin sky occlusion and warm block lights remain, followed by vanilla gamma, night vision
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

`PlanetLimbRenderer` integrates a bounded atmospheric volume shared by map and world views.
The volume starts at sea level and fades out 24 metres above the synchronized native cloud height;
it no longer floats above the build ceiling. Density decreases exponentially with altitude and
smoothly at the top and rounded finite-body edges. A 32-sample view-ray integral produces optical
thickness and blue single-scatter colour; additive RGB blending preserves destination alpha.
The static bounding mesh is cached by body size and cloud height and released on disconnect.
There is no bloom target or image postprocessing. This is an illustrative finite-slab atmosphere,
not a solar illumination, weather or multiple-scattering simulation. No collision, terrain generation,
gravity, coordinate frame or transfer altitude changes are introduced.

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

At transition heights the camera's far plane expands for the reference body up to 65,536 metres.
Astronomical distances must stay in the private sky projection: a larger native float projection
can normalize a zero-length far culling plane and trap vanilla's camera-cube offset loop.
Deferred clouds/atmosphere retain native depth for nearby occlusion and the sky projection for
celestial depth reconstruction. This changes projection, not terrain tickets or render distance. Each cloud frame is consumed once and rejects changed levels; fluid and
effect restrictions cannot reuse a stale frame. This is presentation, not cloud weather or shadows.

In the world, the atmosphere has a dedicated post-level pass. Its private planet-depth texture
clips integration at the distant terrain rather than discarding all haze above terrain silhouettes.
Physical depth uses the ray's front intersection with the finite atmosphere; foreground objects
before that entry occlude it. Objects inside the atmosphere receive approximate front haze rather
than a per-object optical-depth integration. The map uses the same volume shader with parallel
view rays and its existing scene depth. It does not sample the current render target's depth texture.

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


## Procedural solar presentation

`SolarRenderer` ray-intersects an analytical rotating cube in the existing sky target and map viewport.
`SolarScene` samples the authoritative Earth-relative Sun ephemeris and fractional simulation clock.
`SolarSystemProfile.sunRadius` is derived as 0.004650401069518716 times Earth orbit radius, about
95,344 m half-edge at 10,000 m Earth width and 11 m/s². This existing derived property now names
the inscribed radius: the cube edge equals the previous spherical diameter. Diagonal views are
wider; mass, orbit and stored profile fields do not change. No profile/NBT/packet field is added. The map uses the
same half-edge. The former fixed 30 km map marker radius is retired.

All camera-relative subtraction happens in Double before GPU conversion into solar-radius units.
The shader reconstructs perspective or parallel rays using the caller's projection; no tessellated
sphere, chunks, remote ClientLevel or Sable construction is allocated. Position-based 3D cellular
convection has no UV seam: a bounded 27-neighbour Voronoi search
produces bright granules and dark intergranular lanes. Derivative filtering fades unresolved
cells before their expensive evaluation at long distance. Derivatives are evaluated before the
photosphere/corona branch to keep tangent helper fragments defined at the limb. Periodic simulation-time
animation remains continuous across its bounded phase wrap. Cell feature points deform and their
individual heat cycles evolve on different harmonics. A shared apparent axial rotation transports
surface active regions and coronal structures without moving the physical Sun frame. Coronal phase
fronts propagate outwards; prominence knots travel along three anchored strands per arch, whose
height and displacement evolve more slowly. These intentionally accelerated presentation timescales
are not the physical rotation period or plasma velocities. The same simulation clock drives the map
and sky, including fractional ticks; pausing the simulation freezes the presentation.
Four prescribed active regions contain
paired spots with dark umbrae, ragged penumbrae and
resolved radial filaments. These are illustrative patterns, not a magnetohydrodynamic simulation.
Wavelength-dependent limb darkening and photographic compression replace flat yellow vertex colour.
A detail-preserving map exposure and brighter perspective exposure share the same radiance shader.
A bounded convex distance search gives soft glare the projected cube silhouette; it does not retain
an old spherical rim at edges/corners. Perspective optical glare extends to 20 half-edges, beyond
the bounded 3.5-half-edge coronal volume; this is a presentation exposure effect, not additional
physical plasma or a change in stellar radius. Surface plasma and loop anchors use the same stellar chart.
The transparent native sky target accumulates solar coverage, then composites premultiplied colour;
otherwise preserving zero target alpha hides the Sun or double-attenuates its faint corona.
A dedicated `sky_composite` shader preserves all alpha levels: native `position_tex_color` discards
texture alpha below 0.1, turning otherwise smooth solar glare into a visibly bounded disc. Opaque
scene composition and held transition screenshots retain their existing native shader.
The opaque photosphere and an eight-sample optically thin coronal volume share one pass.
Angular density produces radial streamers and a fainter polar corona; smooth radial support ends
at 3.5 half-edges. Within 2.2 half-edges, a bounded 40-step local SDF march adds
two small, asymmetric red prominence arches anchored above those active regions.
Their nonplanar shape, varying thickness and noise-modulated emission avoid uniform wire rings.
Staggered one-minute presentation events stretch each arch upwards and fade its emission before
resetting its geometry. More prominent plasma knots move along the arch on a separate, faster
harmonic. The event envelope reaches zero before the periodic reset; no flash or abrupt visible
collapse is intended. These are local illustrative eruptions, not globally flashing coronal light.
The pass renders off-disc emission, not absorption filaments across the photosphere or a physical
magnetic field.
White coronal emission is deliberately exposure-compressed for presentation,
not a prediction of the real corona/photosphere luminance ratio. Depth uses the planetary scene's
scale and projection, clamped just inside the far plane when the Sun is outside that sky volume.
The current Earth/Moon operating envelope keeps the Sun beyond those bodies. Near-Sun travel and
universal celestial depth ordering need the future local-sector architecture; this renderer does
not make native physics at astronomical coordinates safe.

The loader registers/reloads the client shader; it owns no persistent mesh or framebuffer resources.
The pinned Veil 4.3.2/glsl-processor 0.2.3 dynamic rewrite loses parentheses around a ternary
nested in vector multiplication. Avoid such nested ternaries;
check both original GLSL and rewritten shader compilation when changing those expressions.
Rendering restores depth, cull, blend factors and shader state. The sky target is composed before
native terrain/hulls, so opaque native objects hide the Sun. Planet meshes cover it through their
existing target depth. Overworld uses native celestial bodies by default; the optional `cosmic` mode draws the same
physical Sun/Moon at the surface and during the ascent blend.
The orthographic map uses the same shader and ephemeris. The small selection marker remains an
interface affordance at system scale.

Solar emission does not implement heating, irradiance, construction shadows or an accessible solar
neighbourhood. Distant Earth/Moon surfaces now use a normal-bearing `planet_surface` shader with
Earth-/Moon-local ephemeris sun direction. Sector buffers retain albedo and ambient floor rather
than a baked direction; changing light does not rebuild or upload sector geometry. Overview clouds
and atmospheric scattering use the same Earth-local solar elevation. A small shadow floor preserves
readability. Native ship/block face shading and directional construction shadows remain separate work;
space's exposed native lightmap is still the existing readability approximation.

### Optional physical Overworld sky

Operator commands `/orbitalis sky vanilla` and `/orbitalis sky cosmic` select the saved world mode;
`/orbitalis sky` reports it. Space always uses physical stellar presentation and LOD illumination.
New and legacy worlds default to vanilla. The common `SkyLightingData` SavedData lives in Overworld
as `orbitalis_sky_lighting`, schema1 with `Version` integer and `Overworld`=`vanilla`/`cosmic` string.
Unknown versions/malformed records retain their original compound, fall back to vanilla and reject
mutation. Selection marks data dirty; login and changes send a dedicated version1 `sky_lighting`
S2C payload, with one validated0/1 byte. The receiver runs on the main thread; disconnect clears
client mode so it cannot leak between servers. Existing physical-state packet schemas are unchanged.

A common-side `LevelTimeAccess` hook replaces only the opted-in Overworld sky angle with the solar
hour angle in Earth's native rotating chart. Native sky colour, fog, cloud colour and daylight
brightness therefore follow the same physical Sun on server and client. Sleep and `/time` change
only vanilla time; they never move ships, ephemeris or physical sky. Switching back restores native
time immediately. The renderer suppresses the two native celestial texture draws by texture identity
and draws the actual Sun/Moon through the same root-view transform. Native atmospheric background,
weather and stars stay in their own renderer. Distant celestial bodies use the same private colour/depth target as space, then composite colour
without writing native terrain depth. Solar/lunar self-occlusion is resolved in that private depth
buffer; compressed sky depth must not hide native blocks. Arbitrary third-party sky replacements and shader packs require separate compatibility checks.

Client acceptance: inspect the distant disc from space, a close view through the system map,
an Earth/Moon occultation, native hull occlusion, resource reload and a return to Overworld.
`SolarSceneTest` covers fractional ephemeris, camera-relative precision, apparent-size scaling,
near/far geometry and phase wrap; it does not prove shader compilation or framebuffer appearance.


Solar morphology references (observation only, no imported images or shader code):
[NSO Inouye photospheric granulation and sunspots](https://nso.edu/press-release/new-images-released-by-nsfs-inouye-solar-telescope/)
and [NASA solar layers and visible white corona](https://science.nasa.gov/sun/facts/).

### Forward time acceleration

`/orbitalis time add <seconds>` (1..1200) requests a native tick sprint, processing every real
simulation tick forwards. `/orbitalis time set sunrise|day|noon|sunset|night|midnight` and
`/orbitalis time set <0..23999>` compute the next matching physical solar time, at most one solar day
ahead. `set` never assigns a past epoch or rolls objects backwards. `/orbitalis time stop` ends the
sprint. Ordinary `/time` remains a separate native-time command.

Acceleration requires no loaded Sable ships in the supported flight dimensions and no paused
physical simulation/current sprint. This is a first-stage restriction; it is not on-rails ship warp.
Native ticks process players, weather, fluids, crops and unloaded-orbit services as usual, as fast
as the server can run. The command therefore affects the whole world, not just sky animation.
There is no instantaneous clock jump and no promised wall-clock duration. A server already using
50ms or more per tick has no spare tick throughput, so the sprint cannot materially accelerate time. Native tick sprint can
still be controlled by its existing administrative commands.

Client ephemeris correction smoothing has a cumulative10-tick budget. A tick sprint can deliver
many individually small corrections between frames; exceeding the budget snaps to the new epoch
instead of accumulating minutes of display delay. Ordinary early/late snapshots keep their smooth
interpolation. A regression test feeds a full solar day's worth of burst snapshots.

### Lunar eclipses

The cosmic Overworld sky already displays the catalogue Moon through `LunarPlanetRenderer`,
shared with space and the orbital map. The catalogue also contains Mercury and Venus; they use their native seeded surface overview meshes,
not illustrative sky sprites. The lunar eclipse shader described here specifically models Earth occluding the Moon.

`SolarOcclusion` in core samples 32 points on the visible faces of the emitting cube against
Earth's opaque rectangular gravity envelope. Visible source faces are weighted by projected area;
this distant-source approximation ignores within-face irradiance variation. The renderer transforms
Moon mesh positions and solar half-edge axes into Earth's rotating local chart, normalized by Earth
half-width. `SolarShape` supplies the same wrapped phase/orientation to the visible cube and eclipse
sampling. The surface shader mirrors the bounded face quadrature and segment/slab intersections. Source size creates a penumbra;
Earth's rotation and thin edge-on silhouette affect the shadow. No spherical Earth proxy is used.
The shader tests a conservative bounding sphere first; it does not rebuild sector meshes.
Earth draws explicitly disable the eclipse uniforms to prevent shared-program state leakage.
The cosmic native-sky pass uses the same unculled slab drawing and private depth target as space.
Back-face culling discarded shared top-face winding; disabling depth also let the underside paint
over the lit relief. The shared pass fixes both without clearing or writing native terrain depth.

The muted red umbra is an artistic approximation of atmosphere-filtered sunlight, not atmospheric
ray tracing or spectral refraction. This pass does not cast Moon shadows on Earth, eclipse native
lunar terrain lighting, shade Sable hulls or accelerate time analytically. Core tests cover umbra,
penumbra, source/observer ordering, edge-on geometry, scaling and actual catalogue alignment.
Geometry tests and GLSL validation do not replace live visual review.

Scientific reference (no imported assets): [NASA lunar eclipses](https://science.nasa.gov/moon/eclipses/).

### Multiple bodies and direct solar visibility

`SolarVisibility` samples the same projected emitting cube as the lunar eclipse model and computes
an opaque union of oriented body boxes. Conservative bounding-volume/depth culling rejects off-axis
and behind-source bodies before source quadrature. Overlapping bodies block a sample once, rather
than multiplying independent visibility fractions. Current opaque volumes use each body's finite
rectangular gravity envelope and the same sampled orientation as its visible terrain.

`SolarLightScene` resolves one immutable geocentric sample. `CelestialSkylight` caches queried physical
points per client tick (bounded to 4,096 points), with a per-thread cache and world/profile invalidation.
Only dynamic external sky sampling is attenuated; block light and plot coordinates are not rewritten.
Sable's existing native external-light scale combines this with cabin skylight for its vanilla/fancy
hulls, entities and block entities. Thus separate ships can be illuminated and eclipsed in one frame,
without a camera-wide lightmap reduction or rebuilding hull meshes. The Overworld surface retains
ordinary light and blends into this field through the existing altitude/atmosphere transition.

The native integer skylight scale quantizes partial visibility. This is celestial visibility, not
per-block directional shadow tracing or atmospheric transport. Native static terrain chunk lighting
still follows its existing dimension/lightmap path. Thermal effects and solar generators are not
introduced by this calculation. See [inner planets](inner-planets.md) for LOD and depth projection.
