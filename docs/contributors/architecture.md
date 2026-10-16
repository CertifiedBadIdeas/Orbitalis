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

The Minecraft common layer owns the opt-in planet laboratory: bounded surface pads, coordinate adapters,
operator commands and server-owned particle orbit simulation. Shared NeoForge code registers commands,
ticks and shutdown cleanup. The laboratory dimension resource is shared common data; its ID
`orbitalis:planet_lab` must remain stable for worlds that have entered it. The chart layout is experimental.
Core now contains an experimental finite-planet coordinate model and two-body stepper. A test-source
laboratory measures a full reference orbit through all six surface charts and back to its start.
An isolated NeoForge GameTest source set exercises the Minecraft adapters in a real headless server.
Its generated empty structure and flat world preset are excluded from normal runs and production archives.
The test preset incorporates the canonical dimension JSON because the 1.21.1 GameTest server otherwise
bakes its flat preset against an empty dimension registry. The [laboratory guide](planet-laboratory.md)
describes the scene and its remaining evidence gaps.
The optional Sable adapter stays in shared Minecraft common code; NeoForge alone wires its physics
callbacks and commands after detecting the mod. Sable types do not enter core. The pinned external
release is compile-only and is installed intact in every standard development game run. Its physics backend
integrates the probes. In the explicit orbital diagnostic, Orbitalis supplies central-gravity impulses
at the transformed centre of mass, reconstructing inertial position across frame shifts. A separate finer
core trajectory measures numerical error and never corrects the actual Sable state. Diagnostic
prograde/retrograde kicks apply at physics boundaries and update that reference without resetting it
to native state; the independent core derives instantaneous orbital apsides. Coasting remains a
separate mode; no general ship registry or celestial gravity for ordinary worlds is installed.
The surface-flight diagnostic adds an independent bounded thrust controller in core and temporary
Minecraft terrain in common. One Sable body performs takeoff, insertion, an unpowered revolution and
a physical landing without state corrections. It remains in one Level and introduces no ship save schema.
See [Sable laboratory](sable-laboratory.md) for the bounded frame-shift experiment and transfer gate.

Ordinary Sable constructions in the experimental `orbitalis:flight_test` dimension receive a separate
server pre-physics cube-to-central gravity impulse. Core `CubicGravity` supplies the conservative field; common
`SableFlightGravity` transforms each loaded body's centre of mass and converts world force into body axes.
`FlightTestSpace` owns shared geometry/field constants and dimension keys without optional Sable types.
A client-only orbit reference renderer uses that same geometry for a coloured gridded cube and centre
marker in the sky pass; the NeoForge physical-client event adapter owns render event/state restoration.
It adds no collision terrain, chunk loading, saved data or surface/orbit rendering continuity.
This adapter has no ship ownership tags, persistent registry, loading tickets, cleanup of player ships or
Propulsion API dependency. Native Sable owns integration, collisions and ship persistence. Its base gravity,
drag and pressure are zero only in this test dimension; the Overworld and existing laboratory retain their
own behavior. The dimension uses ordinary terrain as a local surface stand-in, not finite cubic worldgen.
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
`developmentModJar`; production projects never include or depend on the consumer. Create, Aeronautics
and Propulsion APIs are confined to the consumer adapter and its isolated GameTests. Their types do
not enter canonical gameplay or core. Hold guidance, bounded thrust allocation, commands and their tests
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
zero-width gravity, discrete standing-player collider frames and client camera/model easing. Common owns
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
