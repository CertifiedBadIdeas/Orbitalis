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

## 0.1.0-SNAPSHOT — In development

- Keep Creative free-flight input in the visible lunar horizon across surface/space transitions,
  retaining orbital transport velocity while Space/Shift and directional steering accelerate in
  the same local directions. Preserve transported camera roll when looking directly along local up.
- Add a per-world Earth-size selection to the native world-creation More tab, with a 1000-block
  preview preset and a 10000-block default. Save creation intent in native world metadata and
  preserve legacy absence and unsupported records for the physical-system adapter.

- Derive Earth/Moon orbit distances from unchanged mass parameters and physical periods: a 365.24-day
  year and 27.32-day lunar revolution, using 20-minute mean solar days. Define the corresponding
  sidereal spin period without changing vanilla daylight. Migrate existing catalogues at preserved
  phases, retain frozen legacy transfer profiles and inertial station positions, and extend lunar
  transfer/trajectory bounds and map framing to the new scale.
- Begin lunar approach-camera alignment inside the model-derived Hill sphere, matching the local
  surface horizon before entry and retaining inertial orientation outside lunar capture.
- Move optional Voxy WorldGenV2 finite-terrain search and shutdown compatibility into the main mod.
  Keep Voxy and WorldGenV2 separately installed, with optional client dependency declarations;
  the flight consumer no longer owns these compatibility hooks.

- Replace distant rectangular-volume quadrature with an exact point-mass field of the same total
  mass. Blend scalar potentials conservatively at 8–12 maximum half-extents (40–60 km for Earth),
  including the blend gradient in acceleration; retain the analytic rectangular near field.

- Interpolate the moving Moon, lunar camera frame and map focus between client ticks to prevent
  visible 20 Hz stepping during approach. Raise lunar entry/exit clearance to 1500/1800 metres,
  with shared minimum native approach and hysteresis distances for small bodies.

- Preserve free-player velocity through lunar frame rotation and transport motion, avoiding an
  outward kick after fast arrivals. Use one player gravity field on both sides, align the camera
  horizon before entry, and extend the held-frame loading bridge and Earth atlas stream to Moon.
  Track Moon-centred orbital-map views between snapshots; panning releases the body lock.
  Remove the placeholder Sun square from the space sky while retaining the map catalogue.

- Apply all-face flight envelopes to the moving Moon, including side/underside chunk preloading and
  unloaded ship wake-up. Share Earth's 16-metre sector LOD, stepped relief, layered cliffs and shaded
  underside with the gray cratered Moon; prepare sectors in the background without generating chunks.
  Keep the lunar overview visible from the native world's sides and underside. Prepare source entity
  data while the durable transfer is paused, rather than chasing a moving ship; retain loose-entity checks.

- Add a finite native lunar surface with seeded craters, all-face ship arrival and departure,
  seated passenger/cargo transport, and a tidally locked frame facing Earth. Share moving gravity
  between live/unloaded ships and trajectory previews, and draw seeded lunar relief in space/map.
  Lunar transfer receipts persist their frozen frame; Earth receipt and coordinate formats remain readable.

- Show the synchronized Sun/Earth/Moon catalogue and closed celestial orbit curves on the orbital map.
  Add Earth, Moon, Earth–Moon and solar-system views, with relief occluding far-side orbit lines.
  The map uses the server's saved simulation epoch.

- Persist a compact Sun/Earth/Moon catalogue alongside the existing orbital simulation clock. Migrate
  version-one indexes in place, preserve unknown data, and keep celestial phases independent of
  daylight commands. Share one tick advance with unloaded stations.

- Add prescribed elliptic ephemerides, hierarchical and rotating-frame transforms, and numerical
  evolution with time-dependent forces. Keep native daylight and `/time set` behavior unchanged.

- Pan the orbital map with middle-button drag, Shift+left drag or held WASD/arrow keys, using
  camera-relative movement at the current zoom. Preserve ordinary rotation, selection and text input.

- Give the orbital planet layered stone cliffs that follow 16-metre perimeter relief, a shaded
  underside at the actual Y=-64 boundary and a cached luminous blue atmospheric rim. Share this
  appearance with the orbital map and rebuild only affected sectors.

- Refine the shared orbital/map surface LOD from 32 to 16 metres per cell, retaining the existing
  atlas/network formats. Rebuild/upload independently dirty 512-metre sectors with local boundary
  snapshots and bounded background work; read saved terrain on two dedicated read-only workers.

- Allow flight beyond the finite Overworld edge and underneath it. Persist generation bounds separately
  from the distant movement border; prepare and transfer players/ships by distance to the body in all
  directions, with hysteresis. Keep native terrain/bedrock and ordinary damage; remove the managed
  below-world flight floor and extend atmospheric presentation around the edges.

- Show a finite drifting cloud layer over the planet in space and the orbital map, using the native
  Minecraft texture, cloud setting and renderer clock. Preserve terrain occlusion without loading
  surface chunks or adding weather/lighting simulation. Replace the elevated native/Sodium layer
  to prevent overlapping cloud flicker, and keep distant terrain in the same physical depth range.

- Blend high-altitude Overworld sky, fog, clouds, rain and exposed light towards the shared orbital
  scene before travel, streaming the existing bounded terrain atlas on ascent. Hold a fresh gameplay
  frame through native Overworld/space loading and fade into the ready destination; pauses remain,
  with ordinary-loading fallbacks. Preserve player and ship skylight above the terrain ceiling without
  changing plot light or introducing a physical Sun/shadow model.

- Add personal timed maneuver nodes with prograde/normal/radial delta-v, a map editor and separate
  real-field impulse previews. Persist revisioned plans in Orbitalis; the removable flight consumer
  executes loaded orbital ships using native Propulsion engines and measured delta-v feedback.
  Existing orbit/land modes remain separate and no dormant ships are automatically loaded.
  Planet relief occludes trajectory arcs on the far side in the orbital map.

- Keep the F6 observation camera at its last view while the same ship is being received after an
  Overworld/space handoff. Rebase the held position into the destination frame instead of briefly
  falling back to the passenger camera. Native loading screens and transfer checkpoints remain.

- Retain already loaded ships while consumer autopilots are active, including uncrewed flight and
  orbital coast. Follow native bodies across handoffs and release session tickets on stop/landing/error;
  listing and commands for unloaded UUIDs still do not wake ships or load orbital terrain.

- Show ballistic trajectories on the orbital map, highlighting the selected construction. Extend coast
  forecasts beyond the former 120-second limit through a full revolution: periodic orbits close,
  precessing turns remain open and suborbital arcs end on the reference body. Map previews use native
  gravity, run on a bounded screen worker and never load orbital objects or terrain.

- Keep WorldGenV2 searches moving past exhausted partial batches at the finite surface border and
  circular generation radius, without changing completion records or generating exterior terrain.

- Let consumer autopilots fly loaded ships without a player aboard. Add a paginated ship directory
  and UUID-addressed orbit, landing, status and stop commands with independent controllers and
  per-ship launch bookmarks. Directory and UUID selection never force-load ships or terrain.

- Add a rebindable M orbital map on the surface and in space, with the shared 3D planet relief,
  orbit/zoom controls, viewer marker and selectable loaded/unloaded constructions with brief telemetry.
  Bounded screen subscriptions never load stations or orbital terrain.

- Separate consumer autopilot orbit insertion from landing: `orbit` coasts until an explicit `land`
  request, and `land` searches the next safe return window without requiring a complete revolution.

- Add hierarchical inertial frame transforms and a persisted game-time motion index for unloaded,
  independent Sable stations in sparse space. Evolve ballistic motion without terrain tickets and
  restore the native station's position, exact velocity and existing cargo through its holding map.

- Bound new Overworld terrain to the native world border, including partial edge chunks and
  generation-time structures/spawns. Keep empty technical neighbours loadable and limit the flight
  consumer's optional WorldGenV2 search to the surface footprint. Existing worlds and caches are not trimmed.
- Allow up to three concurrent development tasks in separate worktrees. Managed Gradle launches
  share three workspace slots while keeping each worktree, including its consumer, exclusive until
  the build or client exits. Preserve compatibility with older single-slot checkouts.

- Increase orbital surface detail to 32-block cells. Backfill existing saved terrain without loading
  chunks, retain samples during chunk lifecycle bursts, and prepare updated overview meshes in the background.
- Let the flight consumer cancel WorldGenV2 2.4.2's interrupted graph search during world shutdown;
  keep this optional client-stack compatibility hook outside the Orbitalis mod.
- Show sampled Overworld relief and native map/biome colours on the orbital planet, retaining the
  illustrative model for unknown regions. Persist a bounded surface atlas, stream updates to orbital
  players without generating/loading terrain, and cache overview geometry with independent depth
  for self-occlusion while ships remain in front of the sky model.

- Validate rocket launch-pad margins against the current physical planet dimensions instead of
  the retired 2,000-block footprint, allowing launches across the 10,000-block surface.

- Accept identical prepared client mod contents across IDE and managed Loom caches even when ZIP
  timestamps, entry order or generated access-rule order differ; continue preserving files whose
  actual contents differ.

- Keep native RCS prograde control active while reconnecting the consumer's return autopilot and
  calculating its background reference orbit, including in sparse orbital worlds.

- Isolate each Gradle GameTest launch in an owned disposable world, retain failed worlds and
  successful logs/reports/region metrics, and remove successful worlds only after normal exit and
  report validation. Bound flight-consumer test server chunk distances and record loaded-chunk peaks
  and shutdown duration without changing production server settings or existing saves. Release the
  occupied-plot fixture immediately after outbound relocation, preventing its unrelated falling
  body from loading chunks or triggering automatic travel during the rocket mission.

- Relocate Propulsion vector engines’ embedded directional link addresses with their native plot,
  preserving frequencies and exact block-entity verification. Log preparation cancellation reasons
  on the server as well as reporting them to the pilot.

- Limit consumer autopilot RCS commands to 20% of configured thrust, include the cap in force/torque
  allocation, and use bounded angular-rate control with anticipatory braking. Reserve a longer
  engine-off turn lead before the return burn; keep main-engine throttle authority separate.
  Prioritize attitude when side force conflicts with limited control torque, and validate the actual
  ballistic insertion instead of burning to chase a moving reference point after ascent.

- Replace the consumer rocket's fixed return trigger with a forecast burn-window search that includes
  RCS turn lead and compares continuous candidate manoeuvres by required delta-v. Reject crossing past
  the pad along the orbital approach axis; retain real Propulsion force application and native landing checks.

- Add navball pitch/heading grid, numeric pitch labels and bank graduations. Continue live ballistic
  forecasts across the dimension handoff to the reference-body intersection or forecast limit; keep
  trajectory prediction independent of autopilot commands and future thrust.

- Keep native RCS prograde control active during the rocket's ballistic orbit, damping residual roll
  with the main engines off. Transport the attitude's existing roll and smooth requested thrust
  acceleration across feedback/phase changes; retain native force application and engine fade.

- Add the main-mod external ship camera (F6, mouse orbit and wheel zoom) and a local-gravity navball
  with prograde/retrograde markers. F7 switches the instrument between +Y rocket and +Z ship axes.
  Use the same native deck/seat contact for trajectory subscriptions.

- Remove ship-transfer block-entity allowlists, including plot relocation. Use native serialization,
  registry identity and exact staged reconstruction to validate vanilla and modded payloads.
- Use the same passenger preflight for guidance and transfer, and resolve plot-owned seats as ship
  contact for pilot commands, autopilot continuity and handoff readiness.
- Make new orbital worlds sparse vacuum with construction owned by native Sable objects. Stop native
  ship terrain tickets there and omit only new, known empty terrain writes; preserve existing entries,
  entities, cargo and unknown mod data. Retain a conservative terrain mode for old saves and custom
  generators. Add native collision, storage and persisted-model verification.

- Install a pinned client-only Voxy/WorldGen/Roxy, Sinytra Connector, Forgified Fabric API and Sodium stack for the flight consumer; keep server/test classpaths and Orbitalis archives independent.

- Scale consumer flight planning and return windows to the new footprint, reserve thrust/steering authority and keep powered attitude changes continuous. Clear residual native Propulsion commands across control ownership changes so RCS-only manoeuvres keep main engines fully off.

- Expand the default flat Overworld and reference mass model to 10,000×10,000 blocks; migrate only the exact old default border and preserve administrator edits. Scale ascent/return altitudes to 3000/2000 m above sea level and nominal orbit altitude to 5000 m.
- Prepare destination terrain from predicted time to the handoff altitude, using observed loading time plus margin and bounded native tickets. Preserve exact player velocity across dimension teleports and log transfer stage durations.
- Apply finite-body attraction to players, preserving Minecraft jumps at the surface and weaker attraction far away. Fade player air drag, elytra lift and airborne survival steering out with the atmosphere; preserve creative momentum in vacuum. Remove the artificial terrain void floor only in orbital space.

- Add independent, bounded coast-trajectory prediction using the live finite-body field, a world-space
  path overlay with surface-entry/body markers, operator navigation commands and personal persistent
  surface markers. Keep navball, propulsion and consumer autopilot ownership independent. Add pure
  numerical and native Sable/packet/bookmark verification; client visual acceptance remains separate.

- Add a consumer-only vector rocket fixture, transient native gimbal control, bounded thrust allocation
  and native assembly/hold/cutoff checks. Add balanced native RCS orientation with the main engines off
  and bounded direct-ascent reference planning for centre and off-axis pads. Add consumer rocket guidance for a direct ascent into a 900-metre finite-body orbit, full engine-off
  revolution, RCS retrograde turn and smooth powered return to the saved launch pad.


- Remove the retired `flight_test`, `orbit_test` and `space_test` dimensions, player-only travel commands
  and spherical consumer autopilot. Keep Overworld/`orbitalis:space` as the sole flight route.
  Preserve old save directories; ignore completed retired receipts, reject unfinished legacy transfers
  with recovery instructions, and skip retired launch bookmarks without converting their coordinates.
  Retain native transfer, cargo, gravity and propulsion coverage on the active route.
- Automatically transfer supported loaded ships and independent players between Overworld and low space, including survival. Preserve a seated passenger's minecart and mount relationship; unmanned ships transfer and recover without a pilot. Preload source entities before selection and reserve guidance-controlled handoffs.
- Extend the transfer journal to version 4 with optional pilots and bounded native entity/mount manifests, while retaining active version-3 records.

- Add finite rectangular-body potential and gravity, a conservative native-gravity surface layer, and numerical low-orbit shooting with collision/clearance and energy checks.

- Add low free orbital flight around a finite 2000×2000×128 mass model, with a conservative transition from native surface construction gravity through Y=320. Keep Minecraft players, fluids and block logic unchanged; use native Sable motion with symmetric gravity impulses.
- Connect ordinary Overworld to its own low space frame. The consumer autopilot transfers while moving above 600 m, captures a numerical orbit about 1000 m above sea level, coasts with engines off and returns to the saved launch pad. Preload destination terrain from 400 m; explicitly identify the active route in transfer journals and launch bookmarks.


- Choose a free native Sable plot when a ship transfer's original address is occupied, including by an unloaded ship. Relocate supported block entities and glue while preserving physical pose, motion, cargo and foreign ships; retain journal compatibility and per-snapshot recovery addresses.

- Continue consumer orbital ascent/return across the dimension handoff without braking to a boundary waypoint. Preserve moving journal snapshots and automatically resume the next leg; retain braking for insertion and actual touchdown. Finish landing within 0.5 m only after native terrain supports the ship with engines off.

- Add a flat 2000×2000 reference body with side strata and an underside in diagnostic space. Replace its Overworld sky with black vacuum, fixed stars/Sun and time-independent skylight; retain ordinary surface skies and existing saved dimension types. The body is illustrative, without live terrain sampling or new orbital physics.

- Replace the cube surface experiment with a native flat Overworld border, initially 2000×2000 centred at zero; preserve administrator border edits and isolate diagnostic space borders and client updates.
- Retire the cube dimension, six Sable charts, player gravity/camera, seam/fluid/Create compatibility hooks and fixtures. Remove production Create/Ponder dependencies. Old save files are untouched; start a new world.
- Preserve ship-transfer journals and diagnostic dimension IDs; replace their retired cube field with the existing spherical diagnostic field. Retain those spherical diagnostics separately from the later finite-body route.

- Project confirmed cross-chart ordinary/glass pipe attachments through Create's native client update tags, hiding extra glass end rims while retaining native connectors, mixed collars and disconnected ends. Keep the projection out of world saves and invalidate client model data when it changes.

- Recognize existing fixed cube charts with centre-of-mass pivots and float-rounded native poses by their actual block-grid transform. Preserve saved blocks and poses while allowing cross-chart Create connections; reject genuinely moved, rotated or scaled charts.

- Fix actual bucket use on all cube faces by using the gravity-aware item ray. Distinguish never-created native empty plot columns from explicitly cold chunks so normal gameplay does not require test-prepared foreign air columns.

- Adapt native queued shape updates and Create's preferred shaft axis across charts. Exercise actual BlockItem placement, automatic elbow-pipe ports, motor rotation and conserved pump transfer without authored connection states.

- Enable native chart interactions and fluid simulation by default in the cube planet dimension; retain transient explicit seam overrides and leave other dimensions unchanged.

- Extend the opt-in small-cube seam experiment to native water/lava ticks, physical gravity and rotated collision faces, retaining Minecraft source/decay/contact rules and deferring cold queries. Keep client liquid rendering, swimming and fire spread outside this stage.

- Allow the Aeronautics/Simulated Physics Assembler to assemble new ordinary ships from fixed planet charts without trying to disassemble the planet; preserve native cargo and physical pose. Reverse disassembly onto charts remains a separate boundary.

- Add native Create closed-network experiments across all six Sable charts: coordinate-cycle closure, signed kinetic rotation, alternate routes, source cutoff, fluid conservation and chart-group save/unload/reload. Keep pipe placement/rendering, full server restart and large-planet performance outside this evidence.

- Extend the opt-in small-cube seam experiment to native Create shaft rotation and pump/pipe fluid graphs, translating actual addresses and local port directions without replacing Create networks. Resolve uniquely occupied blocks independently of their chart of origin, including authored shafts continuing five blocks into another geometric face. Keep unvalidated custom/diagonal transmissions and client connection presentation explicit.

- Add a disabled-by-default MixinExtras experiment for cross-face neighbour notifications, redstone signal queries and native NeoForge inventory lookup on the small cube, using exact position/side transforms and rejecting cold or ambiguous foreign blocks.

- Clarify the planet design requirement for direct cross-face block connectivity: pipes are an example alongside neighbour updates, redstone, item/fluid interactions and multiblock discovery; the current experiment does not yet meet its full scope.

- Add an opt-in native planet chunk unload/restore adapter with compressed cold chunks in the same Sable checkpoint, preserving inventories and scheduled ticks. Add solid 512-block volume ownership and conservative chunk-interest geometry; automatic 512-block scene streaming is not enabled yet.

- Treat cube face plots as static block-coordinate charts rather than dynamic ships. Keep native Sable presentation and player contacts; register resident terrain through the kinematic collision path for ordinary ships.

- Restore vanilla creative flight acceleration, horizontal drag and sprint speed on all cube faces.

- Restore native ground/air acceleration and friction, preventing excessive speed from repeated sprint jumps; resolve slippery support blocks in each face’s native coordinates.

- Restore native Minecraft jump strength, effects, sprint impulse and jump events on the cube. Match ordinary jump timing with move-before-gravity integration and normal air drag; use a shared 32 blocks/s² constant surface band for players and Sable constructions in the small fixture, with conservative distant falloff.

- Double the cube scene surface gravity from 9.81 to 19.62 blocks/s² for both players and ordinary Sable constructions.

- Align cube block/entity picking with the rendered eye and eased view on all faces; adapt Sable’s interpolated-eye helper so native decks no longer restore a global-Y eye offset.

- Replace the default cube terrain with six persistent Sable coordinate charts, each with native local `XZ` surfaces and outward `+Y`; retain the solid interior, convex edges and unequal relief. Add direct entry to each face, preserve edits on ordinary entry, and require explicit rebuild for legacy terrain. Keep Create acceptance fixtures in the consumer stand. Reject obstructed entries while allowing nearby non-intersecting blocks.

- Apply cube gravity to ordinary Sable constructions in the cube dimension and connect planetary player movement to native oriented ship contacts and moving decks.

- Retire earlier chart, particle-orbit and Sable probe/orbit/flight/platform laboratory scenes and commands; preserve saved content, the cube, flight/orbit/space dimensions and ship transfers.

- Prevent unpowered elytra steering from creating kinetic energy as planetary gravity turns; interpolate camera/model orientation between game ticks and retain tiny far-field turns.

- Preserve weak planetary free-fall velocity across native ticks so high-altitude gravity can accumulate instead of being erased by vanilla resting thresholds.

- Extend the cubic player experiment with continuous gravity-aligned flight, transported heading, local elytra lift/steering and native rocket direction, conservative tilted colliders and safe glider-to-standing resizing.

- Keep cubic-player scene membership beyond the old 64-metre cutoff, synchronize and save it explicitly, and exempt only active scene players from global-Y void damage.

- Preserve player heading around the shared cube edge, rebase local yaw without a second server rotation, and compensate camera/model easing to remove the extra twist between side faces.

- Migrate the flight consumer adapter, guidance and tests to Kotlin with pinned tooling, Java 21 targets and Kotlin lint in its default checks.

- Add consumer-only orbital insertion and return-to-launch guidance using real Propulsion engines, persisted launch bookmarks and the journaled full-space handoff, with a native insertion/coast/return/landing scenario.

- Extend the journaled ship handoff to the full-space frame with an explicit version-2 route, legacy version-1 reading and recovery coverage for both orbital representations.

- Add a separate positive-height space frame, shared gravity/reference rendering and a native full-revolution coast test without changing legacy orbital world coordinates.

- Adopt the Compukters worktree completion policy: integrate verified tasks into local `dev`, preserve evidence and remove completed task checkouts by default; keep remote publication separately authorized.

- Add an opt-in cubic planet player proof of concept with unequal face relief, solid convex edges, sharp gravity, local-axis locomotion/collision with contact clearance for slow edge turns, creative flight and eased camera/model orientation; keep ordinary worlds and existing ship diagnostics unchanged.

- Document the finite connected cubic planet direction, through-tunnels, sharp near-surface gravity changes, eased player orientation, convex edge terrain and adaptive surface/orbit preparation; distinguish these decisions from the current diagnostics.

- Replace the flight platform with a walkable 24-engine shuttle, correct Create glue placement around the complete craft, and add native blueprint assembly, attitude/position recovery and multi-height/frame gravity measurements on the consumer stand.

- Make flight fixture functions available automatically through the consumer mod, alongside the default flight stack and test autopilot; no manual datapack installation is needed.

- Add consumer-only test autopilot commands for hold, target height, status and stop, controlling actual creative Propulsion engine throttle with consumer-local guidance/force allocation and isolated native-engine GameTests.

- Draw a client-only gridded cube in the orbital test sky, using the gravity model's centre/size, with markers for the gravitational centre, surface origin and transfer patch.

- Select a cubic planet geometry and replace diagnostic ship gravity with a conservative field that is face-normal near the surface, continuous at edges and central far away; report cube-shell height and verify off-centre native acceleration.

- Remove source ship tracking before switching the pilot's world, then keep diagnostic physics paused until the destination ship is sent, the follow-up vanilla teleport is acknowledged and native deck contact is reported; use the same barrier for recovered pilots.

- Compare reconstructed block entities by plot coordinates, accepting Sable's unordered serialization and the pinned creative thruster's connectivity rebuild flag while preserving cargo/configuration checks; report the block and changed fields for actual payload differences.

- Pause block-entity ticks in source/staged ship plot chunks during diagnostic transfer, preserving ticking engine payloads without ignoring NBT changes; report the specific preparation cancellation reason.

- Use Propulsion's actual creative-thruster block-entity registry ID in ship transfer preflight, allowing the intended five-engine consumer fixture.

- Reject terrain ship fixture setup outside its full build-height range before giving items, moving the player or reporting success.

- Report all unsupported ship block-entity types and their counts in one transfer preflight rejection.

- Preserve Simulated Honey Glue alongside Create Super Glue throughout ship transfer, journal validation and interrupted-transfer recovery.

- Allow a ship's own Create Super Glue through transfer preflight when Sable projects plot entities into world-space queries; continue rejecting loose entities and glue outside the ship's plot.

- Add a bounded diagnostic Sable ship/pilot handoff between terrain and an orbital test level, with exact motion, cargo checks, a versioned authority journal and interrupted-transfer reconciliation.

- Load destination terrain before selecting flight entry or fallback return height, avoiding underground arrival in an initially unloaded chunk.
- Apply central gravity to ordinary loaded Sable constructions in a separate terrain test dimension, with player travel, ship telemetry and real native-physics coverage; keep Propulsion isolated in the consumer.
- Add an explicitly invoked consumer ship fixture with native assembly, linked-controller controls and cargo markers; record user-observed Overworld ascent and persistence.

- Provide the flight consumer's own Gradle 9.8.1 wrapper for IDE import and direct launch; reject mismatched Gradle versions early and verify wrapper parity with the root build.

- Add an independent flight consumer build for the current Orbitalis checkout with pinned Create, Sable, Aeronautics and Propulsion; isolate its dependencies/worlds and reject consumer implementations or dependencies in the production JAR.

- Add a native 3×3 rider platform, one-time boarding, short/orbital launches, support/drift telemetry and bounded airborne frame translation including its owned landing pad; distinguish declared-contact server fixtures from real client evidence.
- Add a bounded short-hop itinerary with hover and physical touchdown to the independent flight diagnostic.

- Add a real single-level Sable takeoff/orbit/return diagnostic, temporary terrain with safe cleanup, flight-phase measurements and native physical-landing tests at one/four substeps.
- Add a bounded, independent diagnostic flight controller for takeoff, orbital insertion, a measured unpowered revolution and return to a launch pad.

- Install pinned Sable automatically for standard development client/server/GameTest runs; remove the separate Gradle opt-in flag and duplicate CI profile.

- Add bounded prograde/retrograde Sable diagnostic manoeuvres, per-probe orbital apsides, burn-aware reference errors and native post-burn flight checks.
- Derive instantaneous bound-orbit periapsis and apoapsis in the independent two-body core, including escape and radial-fall handling.

- Add a physical Sable orbit diagnostic: central gravitational impulses evaluated at each construction’s centre of mass, independent trajectory/error measurements and complete-revolution tests across physics timesteps and frame shifts.

- Add optional Sable 2.0.6 diagnostics with isolated runtime installation, native impulse/coasting and same-level frame-shift scenarios, zero-drag laboratory settings and upstream license provenance; keep Sable binaries outside the Orbitalis JAR.

- Keep all production layers in Loom's single `main` development mod group; prevent client world-creation crashes caused by shared classes being assigned to a separate group.
- Add an opt-in Minecraft planet laboratory with six bounded chart pads, operator enter/sample/orbit/leave commands, transient return points and a server-ticked particle orbit.
- Add isolated headless laboratory GameTests, fresh XML result validation, development Kotlin runtime wiring and production checks against leaked test resources.
- Translate the remaining Russian README into English and document standard Gradle commands and the planet experiment entry point; keep agent-specific checkout/build helpers in contributor guides.
- Add an independent finite-planet coordinate and two-body trajectory experiment, behavioral tests and a repeatable CSV/report task; no Minecraft/Sable flight implementation yet.
- Record the exploration-first orbital flight direction, Sable source findings and surface/orbit feasibility experiments.
- Initialize the Minecraft 1.21.1 / NeoForge development scaffold with independent Kotlin core and shared Minecraft layers.
- Add isolated task worktrees, reflink Gradle caches, managed build scheduling, complete build logs and tooling verification.
- Establish project-specific local agent workflows, optional shared ai-skills setup, version migration guidance and full Apache-2.0 source headers, attribution and packaging rules.
- Add a GitHub verification workflow with retained build logs and artifacts.
- Complete Apache-2.0 copyright headers in project documentation, local skills and editor/ignore configuration.
- Identify Vsevolod Petrov (lazyhat) as the copyright holder in original Orbitalis files and NOTICE.
