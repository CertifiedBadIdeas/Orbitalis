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

The Minecraft common layer owns the cubic planet proof of concept and its operator commands;
NeoForge registers those commands and shutdown cleanup. The existing dimension ID
`orbitalis:planet_lab` remains stable. Earlier surface charts, particle orbits and Sable
coasting/orbit/flight/platform laboratory scenes have been retired: their commands and runtime
controllers are removed. Existing world blocks and ships are not deleted. Independent core
coordinate/orbit math remains available, and `flight_test`, `orbit_test`, `space_test` and the
journaled ship-transfer system remain supported experiments.

The default cube uses six Sable plots as native block-coordinate charts. Each chart has local
`+Y` pointing outwards and native `XZ` terrain, so horizontal-only mod blocks can use ordinary
placement and neighbour queries within a face. `SableCubePlanet` in the optional common integration
owns construction and fixed chart transforms; `CubePlanetTerrain` keeps the command layer independent of
Sable classes. Sable is required to prepare this cube. Create is an optional, pinned compile-only dependency of the loader compatibility layer; core and shared/common do not import its APIs.
The existing finite voxel fixture is partitioned by the greatest signed cell-centre component,
with deterministic face order resolving ties: every occupied world cell belongs to exactly one chart.
This preserves the full interior, tapered relief and solid convex edges.

Native force-load tickets and per-body user data persist the charts. Version 1 uses
`orbitalis_cube_frame_version=1` and `orbitalis_cube_face` with Minecraft direction names. Blocks,
block entities and inventories remain native Sable save data, not copied orbital data. `SablePlanetCollisionPipeline` decorates the native pipeline only in `planet_lab`: tagged planet
charts have fixed poses and no dynamic rigid bodies, mass integration or anchoring joints. Resident
plot chunks provide world-space kinematic collision geometry for ordinary ships. Native plot blocks,
player voxel contacts and presentation remain in Sable. Create contraptions attached to those charts
have their physical poses transformed to the parent world before native kinematic registration.
The static mass descriptor is explicitly valid without mass, preventing native invalid-ship teardown.
Scoped native load handling preserves chart identities and poses without rebuilding dynamic mass.
The Rapier scene accessor unwraps this adapter for native integrations. Charts cannot be ordinary
rigid-body constraint endpoints; dedicated mooring is outside this experiment. Planet charts do not receive free-body gravity
and cannot enter ship transfer. Normal entry reuses the six identities and never fills excavations;
an incomplete set refuses reconstruction. Legacy parent-world terrain is not silently converted:
explicit `/orbitalis lab cube rebuild` removes disposable fixture contents and builds the replacement.
Ordinary shutdown does not delete chart contents.

`SablePlanetChunks` provides an opt-in server-thread chunk lifecycle for fixed charts. A cold chunk
is one compressed native plot fragment in version-1 `orbitalis_planet_cold_chunks` body user data;
resident chunks and these cold fragments are persisted by the same native body checkpoint. There
is no separately committed chunk journal. Snapshot creation and bounded decoding finish before
resident detachment. Native plot unloading handles ticking, entity registration and light; the
adapter also clears native holder indexes, actor/lift/reaction-wheel callbacks and physical terrain.
Restoration validates plot/chunk/section/entry bounds and retains the cold record on failure. Published
user data is replaced rather than mutated. Native expansion remains disabled across full reloads,
so a neighbour query cannot fill a cold chunk with authoritative air. One nonempty chunk remains
resident because native full loading requires valid plot bounds. Currently eviction refuses tracked
observers: client chunk delivery and readiness are not installed, and ordinary cube play does not
activate this lifecycle. Cold bytes remain in the body checkpoint and memory; this is not external
region storage or evidence of bounded total save size.

`CubicPlanetVolume` in core owns the solid cubic volume partition and conservative world-box interest.
It includes tie planes, all twelve edges, eight corners and the centre, with native plot origin offsets
(including Sable's eight-block chunk-centre offset). It does not generate or load a 512-block game scene.

The accepted [planet connectivity criterion](cubic-planet-design.md#logical-connectivity-across-face-coordinates)
requires ordinary block interactions across face boundaries; pipes are one example, not the complete scope.
The following limitation of the current experiment does not satisfy that criterion, and the production
storage/coordinate boundary remains under investigation.

Charts remain distinct native neighbour spaces. An opt-in experiment, enabled for the current small
cube with `/orbitalis planet seams true`, resolves physical adjacent cells to an actual native plot
position and direction. MixinExtras hooks adapt queued neighbour dispatch, native signal queries and
NeoForge's shared hopper/dropper/crafter inventory lookup. Native blocks, capabilities and inventories
remain authoritative; the hooks introduce no transfer buffers or mirrored block entities. The switch
is server-side, transient and disabled by default. It does not change placement or client rendering.

Optional Create 6.0.10 adapters also convert native kinetic adjacency/source directions and pipe pressure,
flow traversal and sided endpoint addresses. Create retains its actual block-entity addresses, network
membership, stress, pressure and fluid inventories. No transfer buffer, mirrored entity or replacement
simulation is introduced. The typed hooks live in shared/neoforge; the target leaf pins Create and Ponder
as compile-only dependencies and never bundles their implementation. Missing Create skips its pseudo mixins.
Removed pipe owners invalidate retained endpoint providers before another network queries their disabled
NeoForge cache. Restored fluid behaviours invoke native pressure discovery after initialization; native
block loading does not call pipe placement logic.

This remains a bounded connectivity experiment, not automatic support for a multiblock or arbitrary mod
coordinate algorithm. Cardinal shafts, small cogs and ordinary pipe graphs are addressed; diagonal/large
cog transmissions, encased chain connections and custom propagation contracts need separate adaptation.
Shape updates, redstone dust topology and general replay of missed cold notifications are not implemented.
Geometric face ownership is only the fallback address for empty cells inside the reference cube.
An actual uniquely occupied voxel takes precedence, regardless of its storage chart: a shaft or pipe may
extend from one chart several blocks inside another geometric sector before connecting. All projected
columns must be resident to establish uniqueness; unknown foreign columns and overlapping occupied
voxels reject the lookup without loading chunks. This resolver is deliberately bounded to six small charts;
it is not a measured solution for arbitrary large-planet neighbour queries. Only the expected six fixed
laboratory poses participate; normal ships and unrelated
levels retain native behaviour. Keep the six-chart model while testing this path; a custom replacement
is a fallback if required connectivity cannot be achieved. This does not establish general world
generation or a production-size planet.

An isolated NeoForge GameTest source set exercises native adapters in a real headless server.
Its generated empty structure and flat world preset are excluded from normal runs and production
archives. The test preset incorporates canonical dimension JSON because Minecraft 1.21.1 otherwise
bakes it against an empty dimension registry.

All loaded ordinary Sable constructions in `planet_lab` receive the same conservative gravity
field and centre as cube players (owned by `PlanetLabCoordinates`), without enrollment tags, distance cutoffs, or imposed attitude.
`SableFlightGravity` applies a mass-scaled impulse at each native pre-physics substep in body axes;
Sable owns rigid-body integration, terrain contact, loading and persistence. Native base gravity,
drag and pressure remain zero there. Only explicit cube preparation/rebuild creates or removes planet charts; ordinary ships are not auto-created or destroyed.

`CubePlayerContacts` is the optional moving-world boundary used by planetary locomotion.
Its optional `supportPosition(entity, worldProbe)` resolves support into native block coordinates;
null keeps parent-world lookup. Sable produces this transient mapping from the tracked body's logical
pose, and planetary `getOnPos` queries consume it. No save/network schema is added. The internal
`CubePlayerLocomotion` bridge calls Minecraft's native acceleration and NeoForge block-friction hooks,
so shared locomotion does not copy loader-specific attribute/block logic.
`SableCubePlayerContacts` invokes Sable's native oriented contact solver and native tracking
protocol, applies deck motion once, and retains inherited inertial velocity on departure.
Narrow loader mixins adapt only active cube players inside that solver: unrotated dimensions,
accepted physical quaternion, centre and temporary network-anchor conversion. The native solver
itself is not forked. Terrain retains the conservative world-axis envelope and tangent/normal
clipping. Gravity is planetary even aboard a rotating construction; no ship-defined down is added.
Server players retain Sable's client-authoritative contact path. Dedicated servers never load
client camera code. See [cubic planet PoC](cubic-planet-poc.md) for verification limits.

Ordinary Sable constructions in the experimental `orbitalis:flight_test` dimension receive a separate
server pre-physics cube-to-central gravity impulse. Core `CubicGravity` supplies the conservative field; common
`SableFlightGravity` transforms each loaded body's centre of mass and converts world force into body axes.
`FlightTestSpace` owns shared geometry/field constants and dimension keys without optional Sable types.
A client-only orbit reference renderer uses that same geometry for a coloured gridded cube and centre
marker in the sky pass; the NeoForge physical-client event adapter owns render event/state restoration.
It adds no collision terrain, chunk loading, saved data or surface/orbit rendering continuity.
This adapter has no ship ownership tags, persistent registry, loading tickets, cleanup of player ships or
Propulsion API dependency. Native Sable owns integration, collisions and ship persistence. Its base gravity,
drag and pressure are zero in the flight frames and cube dimension; the Overworld retains its own behavior. The dimension uses ordinary terrain as a local surface stand-in, not finite cubic worldgen.
The accepted planet shape is a cube with face-normal near-surface gravity and a central far field;
see [cubic planet gravity](cubic-planet-gravity.md). Earlier `CubeSphere` charts remain a spherical
mathematical reference, not the selected production surface geometry.
Operator enter/leave travel transfers players only, with transient return points and an Overworld spawn fallback after
restart. The dimension ID is stable for saved worlds; geometry and field parameters remain experimental.
See [flight gravity](flight-gravity.md) for constants, commands and the limits of this test space.

The diagnostic `SableShipTransfer` coordinator reconstructs one independent ship and pilot between
`orbitalis:flight_test` and stable `orbitalis:orbit_test`. Core `OrbitalTestFrame` owns the fixed
translation. Common owns native snapshots, exact-motion restoration and the versioned asynchronous
journal and plot-chunk block-entity tick leases; NeoForge owns startup/tick/interaction wiring and
a narrow Minecraft ticker hook that honors those leases without loading optional Sable classes.
A second loader hook exposes vanilla's pending teleport acknowledgment without a custom network protocol.
Common orders source tracking removal before dimension travel, then waits for the destination ship bundle,
a subsequent teleport acknowledgment and native deck contact before releasing physics.
PREPARED selects source authority, COMMITTED
selects destination authority, and DONE follows a verified native checkpoint. Completed records contain
receipts rather than stale ship payloads. Recovery does not make the journal a permanent ship store.
See [ship transfer](ship-transfer.md) for limits, failure handling and the server-thread checkpoint cost.

The [flight consumer](flight-consumer.md) is an independent Gradle composite build, included only when
explicitly selected with `-p consumers/flight`. The dependency points from consumer to Orbitalis through
`developmentModJar`; production projects never include or depend on the consumer. Aeronautics and
Propulsion APIs remain confined to the consumer adapter and its isolated GameTests. Create APIs also
appear in the optional NeoForge seam adapters, with compile-only dependencies owned by the target leaf;
no Create types enter core or canonical shared/common gameplay. Hold guidance, bounded thrust allocation, commands and their tests
all belong to the disposable consumer. It reuses existing Orbitalis vector/gravity/frame types but adds
no autopilot API to production. The consumer assembles no release and does not alter the ordinary
Sable-only laboratory runs.

There is no dependency on Compukters, its VM, Rust runtime or addon SDK. There is no committed choice
of production orbital physics, rendering engine, rocket mechanics or external space integration.
The exploration-first direction, Sable source investigation and proposed seamless surface/orbit
experiments are recorded in [orbital-flight-feasibility.md](orbital-flight-feasibility.md).
That note distinguishes desired behavior and proposals from implemented or runtime-verified support.
The [finite cubic planet design](cubic-planet-design.md) records the accepted connected cube volume,
sharp face-region gravity direction changes near the surface, eased player orientation, convex edge
terrain and adaptive surface/orbit preparation. Planet storage, player collision handling and
seamless client presentation remain undecided; the existing ship gravity diagnostic still smooths edges.
The [player proof of concept](cubic-planet-poc.md) adds a bounded solid cube in the existing planet lab,
zero-width near-surface gravity, gravity-aligned player/glider frames and client camera/model easing.
Opted-in player membership uses native synced entity data and an optional version-1 player NBT record;
there is no radius cutoff. Common owns gravity-frame heading transport and native-collision AABB
envelopes; core owns local glider integration. Scene members bypass global-Y void damage, not other
damage or world build limits. This remains a player flight experiment rather than production orbital
storage, exact oriented collision or atmospheric simulation. Common owns
geometry placement and locomotion; loader mixins adapt movement, eye queries, camera and rendering.
Its temporary centre marker prevents refilling excavations, without introducing a planetary save schema.

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

## Full-space diagnostic frame

`orbitalis:space_test` adds a void representation for full free orbits. `SpaceTestFrame` translates
physical/surface coordinates by the fixed origin (0,-32000,0), preserving axes and inertial velocity.
The reference planet centre is consequently at local Y=26064. A radius-18000-m circle remains above
Y=8064 and wholly in the current field's central far region, including diagonal portions of the orbit.
The existing `orbitalis:orbit_test` retains its (0,256,0) origin; existing saves and version-1 transfer
journals must never be reinterpreted as this new frame. New frame resources carry a distinct stable ID.
Gravity and reference rendering select the same frame by dimension. This is a bounded diagnostic,
not a chosen finite-planet storage topology, floating origin or seamless rendering implementation.
