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

# Architecture and Minecraft version boundaries

The initial target is Minecraft 1.21.1 / NeoForge 21.1.252. Versions are pinned in
 gradle/libs.versions.toml, based on Compukters' working 1.21.1 toolchain.
Architectury Loom 1.17.491 and Kotlin 2.4.10 are deliberately pinned, not dynamically updated.
Gradle runs on JDK 25, while shared/domain and 1.21.1 code compiles to Java 21.

## Ownership

| Area | Owns | Dependencies |
| --- | --- | --- |
| modules/common/core | Domain models, simulation and independent contracts as added | Kotlin/JDK 21 only |
| modules/minecraft/shared/common | Canonical Minecraft behavior and presentation | Core and Minecraft; no NeoForge |
| modules/minecraft/shared/neoforge | Registration, events, networking and loader adapters | Core, common and NeoForge |
| modules/minecraft/v1_21_1 | Target-specific compatibility and archive composition | Shared sources and target libraries |
| consumers/flight | Independent development run composition and test autopilot adapter; no distributable mod | Consumes the current Orbitalis development JAR and external Create/Sable/Aeronautics/Propulsion runtime |
| build-scripts | JVM conventions and canonical shared source wiring | Gradle plugins |
| tools | Isolated builds, cache publication, worktree lifecycle and verification | Python, Git and GNU tools |

The surface is an ordinary finite flat Overworld with a native square world border, initially
10,000 by 10,000 blocks centred at (0,0). Minecraft and other mods retain normal coordinates,
block storage, fluids and neighbour logic. No projection, rotated player frame or Sable
planet chart is installed. The cube experiment, its dimension `orbitalis:planet_lab`, commands,
resources and compatibility mixins have been retired. Old save directories are never deleted or
converted. Start a new world; opening an old cube save with this version is unsupported.
See [flat surface](flat-surface.md) for border initialization, finite terrain generation and persistence.

`FlatOverworld` in shared/common initializes the native border once per save. NeoForge invokes it
before initial spawn generation. The native world border remains the only authority for centre, size and lerp;
a small SavedData marker prevents startup from overriding administrator changes. Two narrow loader
hooks omit vanilla border delegation into Orbitalis diagnostic dimensions and stop surface-border
broadcasts reaching their players. Vanilla Nether/End delegation and recipients remain unchanged.

`FiniteSurfaceTerrain` in shared/common bounds new Overworld terrain against immutable native-border
snapshots. Two loader mixins adapt native `ChunkStep` work and `WorldGenRegion` writes/spawns. They
preserve native dependency/status/light completion, allow empty exterior chunks and leave runtime
placement and completed terrain untouched. Other dimensions and Sable plot generation stay native.
The optional WorldGenV2 search adapter belongs only to the flight consumer.

The current surface/orbital pair is ordinary Overworld and `orbitalis:space`. Core `InertialFrames`
provides hierarchical position/velocity transforms; `FlatPlanetFrame` represents the current two
frames through that graph and adds
4096 to physical Y, preserving axes and inertial velocity. Independent core `Rotation3`/`FrameKinematics` now provide
[tested rotating-frame transforms](rotating-frames.md) for subsequent Earth–Moon integration; the
current native route still uses the established fixed frame. Core also provides
[prescribed celestial ephemerides](celestial-orbits.md) and sampled body hierarchies. Common lifecycle
advances the existing persisted orbital clock and stores a versioned compact catalogue with that
index; the orbital map displays its synchronized bodies and orbit curves. They do not yet install
moving gravity sources, a lunar surface or a new native frame interpretation. `SableFlightGravity` applies core
rectangular-body gravity and a conservative surface compatibility layer to constructions and players.
Gravity is vertical through Y=320 within the initial footprint and finite-body gravity above Y=3064.
Players retain native jumps with 32 m/s² at the surface; the exterior field shares the ship mass
model. Native collisions remain, with split field impulses and vacuum inertia above the atmosphere.
Atmospheric drag and elytra lift fade to zero at ascent altitude; creative flight coasts in vacuum.
Surface block/fluid logic and native Sable settings remain unchanged. The space frame uses the
flat-body [space presentation](space-visuals.md), combining a persisted
server-owned coarse Overworld surface atlas with illustrative fallback for unknown cells. Shared/common
owns native sampling, SavedData, bounded packets and the client relief renderer; NeoForge owns chunk/
player/tick lifecycle and network wiring. This presentation never requests terrain loads or generation; bounded asynchronous native storage reads
backfill already saved terrain without installing chunks, and CPU overview meshing runs off the render thread.
The former `flight_test`, `orbit_test` and
`space_test` IDs, their resources and spherical mission have been retired; save directories remain
untouched. Completed old receipts are ignored; unfinished old transfers require previous-version
recovery. Old launch bookmarks are skipped, never reinterpreted as Overworld sites.

New orbital worlds use [sparse vacuum](sparse-orbital-world.md): the dedicated `orbitalis:vacuum`
generator, a versioned objects/legacy world model, construction restricted to native Sable plots,
and native terrain collision tickets disabled only in objects mode. A narrow region-worker hook omits
new, known regenerable vacuum while preserving existing entries and unknown mod data. Legacy saves
keep terrain collision and storage. Entities and native Sable storage remain authoritative. Independent constructions can retain
[unloaded orbital motion](unloaded-orbital-objects.md): common owns the versioned motion index and
worker, narrow loader hooks connect native holding-map persistence, and core owns numerical evolution.
This introduces frame hierarchy foundations without adding another celestial body or dimension.

The `SableShipTransfer` coordinator reconstructs one independent ship and an optional pilot on the explicit
Overworld/`orbitalis:space` route. Journal version 4 names both dimensions, an optional pilot and bounded entity/mount manifests;
active version-3 records remain readable. `FlatPlanetFrame` owns
the fixed translation. Common owns native snapshots, exact-motion restoration and the versioned asynchronous
journal and plot-chunk block-entity tick leases; NeoForge owns startup/tick/interaction wiring and
a narrow Minecraft ticker hook that honors those leases without loading optional Sable classes.
A second loader hook exposes vanilla's pending teleport acknowledgment without replacing its
acknowledgment protocol. Separate clientbound payloads synchronize surface gravity and
preserve exact local-player velocity across the native respawn/teleport reset. The native short motion
packet alone clamps each component to about 78 m/s.
Common owns automatic height triggers, guidance reservations and independent native player travel.
Source entity chunks are loaded before participant selection. The transfer orders source tracking removal before dimension travel, then waits for the destination ship bundle,
a subsequent teleport acknowledgment and native deck contact (or available vehicle plus restored
passenger seat) before releasing physics. Travelling entities use a native entity-only checkpoint
hook before DONE, and dimension-change removal preserves container contents without dropping them.
PREPARED selects source authority, COMMITTED
selects destination authority, and DONE follows a verified native checkpoint. Completed records contain
receipts rather than stale ship payloads. Recovery does not make the journal a permanent ship store.
See [ship transfer](ship-transfer.md) for limits, failure handling and the server-thread checkpoint cost.
Block-entity types are not allowlisted: native serialization and exact staged reconstruction validate
saved payloads. Client [surface/space presentation](space-visuals.md) blends the same sky by physical altitude and
holds one fresh frame through native loading, then reveals the ready destination. Client-only hooks
observe respawn destinations and suppress loading-screen drawing without replacing ticks, receipts or
acknowledgments. Optional client [ship observation](ship-camera.md) uses native contact/interpolation,
a shared gravity-field factory and client-only input/render hooks; it never controls ship physics.

The [flight consumer](flight-consumer.md) is an independent Gradle composite build, included only when
launched from `consumers/flight` using its own wrapper. The dependency points from consumer to Orbitalis through
`developmentModJar`; production projects never include or depend on the consumer. Aeronautics and
Propulsion APIs remain confined to the consumer adapter and its isolated GameTests. Create APIs remain confined to this consumer; production has no Create or Ponder compile dependency or adapters. Hold guidance, bounded thrust allocation, commands and their tests
all belong to the disposable consumer. It reuses Orbitalis vector/gravity/frame types. Shared `ManeuverPlan` owns player navigation intent and real-field impulse previews; versioned common SavedData and bounded map messages own personal plans. Optional `FlightPlanHooks` connect execution to the consumer without introducing engine dependencies into production. The consumer assembles no release and does not alter the ordinary
Sable-only laboratory runs.

The [orbital map](orbital-map.md) uses the shared planet scene with an independent screen camera.
Leased server subscriptions provide bounded atlas/object snapshots from the surface or space, without
loading orbital bodies or terrain. Its optional Sable adapter reads native live state and the existing
motion index; no second simulation or native payload cache is created. A screen-owned worker derives
bounded coast previews from immutable motion snapshots and native gravity using core's real-field
predictor; periodic paths close after one revolution, while precessing or truncated paths remain open.

Independent [coast trajectory navigation](trajectory-navigation.md) copies native Sable state on the server,
forecasts with the same finite field on a bounded worker and sends validated physical path samples
to a separate client world overlay. Personal navigation markers have their own version-one SavedData;
consumer autopilot launch bookmarks retain their existing ownership. Neither the predictor nor overlay
owns engines, navball instruments or native ship movement.

There is no dependency on Compukters, its VM, Rust runtime or addon SDK. The first bounded planetary
force model is a finite uniform slab with a conservative surface compatibility layer; it does not
commit to a general planet system, rendering engine, rocket mechanics or external space integration.
The exploration-first direction, Sable source investigation and proposed seamless surface/orbit
experiments are recorded in [orbital-flight-feasibility.md](orbital-flight-feasibility.md).
That note distinguishes desired behavior and proposals from implemented or runtime-verified support.
The target leaf compiles shared sources directly. When a second target is implemented, the selected
orbitalisActiveMinecraftVersion owns canonical IDE roots; inactive targets compile generated mirrors.
Do not edit mirrors or configure a leaf to consume another version's source roots. Version differences
belong behind small compatibility contracts with implementations only in target leaves; introduce those
contracts when a real difference exists rather than predicting future APIs.

NeoForge's development runs group core, common and loader source sets as one mod. Metadata declares
the actual `orbitalis` mod ID, while development source grouping uses Loom's existing `main` group.
Do not add a second group for the same entry point: the loader may select the incomplete group and lose
access to shared classes/resources. `verifyDevelopmentModGroups` checks every configured game run.
Configure each run directly: Forge's run-local groups take precedence over global `loom.mods`.
Production packaging merges project outputs and lets Loom nest Kotlin stdlib and remap the 1.21.1 JAR. verifyProductionJar
inspects the resulting archive, including Java bytecode ceilings and legal notices.

## Adding the 26.1 family

1. Confirm the exact supported 26.1 patch, NeoForge and official toolchain requirements from pinned
   sources/documentation. Compukters uses a separate no-remap Loom leaf for this family; retain that
   separation because 1.21.1's remapping pipeline is not a generic future-version contract.
2. Add modules/minecraft/v26_1/v26_1-common and v26_1-neoforge plus version-specific pins. Include them
   explicitly in settings. Keep JVM 21 for the independent core and configure the new leaf's required
   bytecode/toolchain independently.
3. Extend the supported active-version validation in orbitalis-minecraft-shared; verify canonical versus
   generated ownership in both IDE selections and compile both targets. Never copy gameplay into leaves.
4. Extract only actual API differences into explicit compatibility interfaces. Keep server authority,
   dimension/resource IDs, networking and saved-data schemas stable or supply versioned migrations.
5. Give the new leaf its own metadata ranges, run directories and archive verification. Check both
   distributions and execute equivalent real world/server scenarios before claiming dual support.

This scaffold reduces ownership and build migration work; it does not establish source/API compatibility
with 26.1. Client-only classes must not load on dedicated servers. World authority belongs to the logical
server; validate packet bounds and identifiers at receipt and keep blocking work off the tick thread.
