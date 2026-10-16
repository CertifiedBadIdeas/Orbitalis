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
