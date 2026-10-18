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

# Unloaded orbital stations

Orbitalis evolves independent ballistic constructions after native Sable unloading in objects-mode
`orbitalis:space`. Loaded constructions remain exclusively under Sable physics. No terrain chunks
are requested along a background orbit. This first slice supports the existing Overworld body; it
does not add a second planet or claim a complete solar-system simulation.

## Frames and time

Core `InertialFrames` describes a rooted hierarchy of named, non-rotating frames. Each edge contains
an origin position, constant relative velocity and epoch. Transforming a state includes both position
and velocity and cancels shared ancestors before arithmetic. The existing Overworld physical frame
`orbitalis:overworld_body` and native space frame `orbitalis:space` use this model; their established
4096-block Y offset and dimension identifiers remain unchanged. Rotating frames and ephemerides for
moving celestial bodies require additional implementations rather than reinterpretation of these IDs.

The per-space orbital registry owns a nonnegative 20 Hz simulation clock. It advances only while
server ticks and native physics/transfer ownership permit progress. Shutdown pauses this clock;
wall-clock downtime and `/time set` do not advance an orbit. Numerical evolution uses fixed 0.05 s
velocity-Verlet steps and the same rectangular-body surface gravity field as loaded constructions.
It does not substitute a Kepler ellipse for that field.

## Ownership and persistence

Sable remains the only owner of native blocks, block entities, inventories, plot occupancy, entity
storage and ship UUIDs. Orbitalis stores only bounded motion metadata and a native storage pointer in
version-two `orbitalis_orbital_objects` SavedData (version-one indexes migrate in place). The same
file owns the simulation clock and optional [celestial profile](celestial-orbits.md); common lifecycle
advances that clock once and the Sable worker consumes it. Motion records remain version one. Each record names its body and physical frame,
snapshot token, snapshot epoch, current motion epoch, centre-of-mass position and velocity, and hull
radius. The native `user_data` key `orbitalis:orbit` contains the matching snapshot token and motion
anchor plus angular velocity. Unknown user data remains intact.

Hooks are limited to Sable's holding-map save, unload and load boundaries. Ordinary ship-transfer
snapshot serialization and staged reconstruction retain their existing coordinator. Saving a live
body produces a fresh anchor; removing a construction removes its motion index. A temporarily retained
native holding reference supplies the final storage pointer when Sable saves and evicts that holding
chunk; the reference is released after checkpoint, leaving no background copy of its block payload.
The pinned native save hook runs before vanilla `saveLevelData`, so pointer changes dirty the index
before that level's normal SavedData write.

A single worker evolves immutable copies in oldest-first batches of at most 64 records. An unsupported
record is quarantined without blocking other stations or discarding its native payload. Server
publication merges motion only, preserving any newer native pointer, and checks the snapshot token, epoch and
absence of a live native body. Work is segmented into at most 1200 ticks per object and interruption
cancels the worker on shutdown. On native loading, the current motion replaces the serialized pose
before construction creation. Native linear/angular velocity is then restored before the first
physics step, independently of Sable's ordinary velocity-retention setting and within the backend's
floating-point precision. Snapshot tokens keep a
stale motion record from being applied to a different native payload generation.

Player or loaded-ship proximity can reload the object at its evolved position through the existing
native holding map. Both resident and cold holding entries are detached from their native owning
chunk before reconstruction, preventing a stale saved payload from overwriting later live cargo.
Deferred catch-up returns ownership to that native chunk before postponing the load. A temporary `orbitalis:orbital_wake` ticket gives native readiness time without
modifying another owner's force-load ticket. Orphaned wake tickets are released after restart.

## Compatibility and present limits

Existing native constructions without the motion key retain native behavior until their next native
save/unload adopts them. Unknown/future or malformed registry data is retained unchanged and background
simulation is disabled for that index. Unknown motion schemas and body/frame IDs are never silently
reinterpreted. The index is bounded to 4096 objects, position magnitude 100 million metres, speed
10,000 m/s and hull radius 2048 m; these limits do not change the separate ship-transfer limits.

Only independent constructions participate. Connected loading-dependency chains opt out. Native
block/entity ticking, engines and machinery are paused while unloaded. Orientation extrapolation uses
constant world angular velocity; it is not a full torque-free asymmetric rigid-body solver. General
background object/object collision and arbitrary terrain encounter resolution remain future work.
An approaching upper-surface route requests native reactivation, but this is not a general continuous
collision guarantee for high-speed suborbital objects. Use a clear ballistic orbit for station tests.
A complete multi-body ephemeris and frame migration are separate stages.

## Evidence

Core tests cover hierarchical velocity transforms, malformed frame graphs, segmented evolution,
comparison with the existing coast predictor and ten-minute energy preservation in the slab field.
`-PorbitalisGameTestSuite=objects` runs `nativeunloadedstation`, `orbitalmetadatacompatibility`,
`celestialclockanddaylight` and `celestialprofilepersistence`; all four are required by the default
native suite. The station scenario
uses real native storage, checks unloaded movement without a terrain trail, reopens SavedData from
disk, reconstructs from a freshly read native payload and checks exact velocity, UUID uniqueness and
chest cargo. The native scenario also checks a 1300-tick deferred catch-up, automatic nearby-ship wake and a warm
holding wake followed by cargo mutation and a fresh native save/read. Schema checks cover unknown
versions, bad clocks, duplicate IDs, non-finite positions and
unknown frames. A fresh storage read exercises the persistence boundary; it is not an observed second
Minecraft process launch. Real-client approach/reacquisition and a complete stop/start observation
must be recorded separately before claiming those interaction scenarios as verified.
