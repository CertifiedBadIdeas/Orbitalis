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

# Orbital flight and surface transition feasibility

Status: source investigation, independent mathematics and an opt-in Minecraft laboratory, 2026-10-15.
An optional [Sable laboratory](sable-laboratory.md) now exercises native rigid-body physics, including bounded central-gravity orbits.
Production orbital flight and seamless transitions remain unimplemented. A bounded diagnostic
cross-level transfer now exists; see [ship transfer](ship-transfer.md).

## Accepted direction

Exploration comes first. Orbital mechanics and support for Sable block constructions are
foundational; survival systems can be designed for individual destinations later.
The transition from an ordinary Minecraft surface to orbital flight should feel seamless.
Test its feasibility before promising uninterrupted rendering or choosing the final world layout.

The proposed initial orbital model uses successive two-body trajectories with explicit changes
of reference body. This is a design recommendation, not an implemented physics contract.
The selected physical shape is now a **cube**, with flat finite faces, face-normal gravity near the
surface and a gradual central far field; see [cubic planet gravity](cubic-planet-gravity.md).
The [finite cubic planet design](cubic-planet-design.md) records the 2026-10-16 decisions about a
connected interior, edge traversal, sharp near-surface gravity changes, terrain cuts and adaptive handoff
preparation. It supersedes edge smoothing as a gameplay goal without changing the current experiment.
The cube-sphere experiment below is retained as mathematical reference, not production geography.
New Orbitalis worlds should start on a separate finite planet; preserve the ordinary Overworld
for compatibility. Existing worlds are not migrated by the experiment. Access to the Overworld
and final planet geography remain undecided. Manual control versus navigation assistance, scale,
planet rotation and time acceleration remain undecided. Multiplayer time must be server-authoritative.

## Implemented mathematical experiment

Run `./gradlew-sandbox-dev-parallel-summary :core:test :core:runPlanetExperiment`.
The experiment writes `trajectory.csv` and `report.md` under
`modules/common/core/build/experiments/planet/`. Its executable lives in test sources and is
excluded from the production JAR. Core contains the candidate geometry and two-body stepper;
the Minecraft [planet laboratory](planet-laboratory.md) now provides a separate diagnostic dimension,
surface-coordinate samples and a particle reference orbit. The optional Sable adapter is separate from this particle demonstration.

The earlier spherical candidate uses six finite square charts projected radially from cube faces onto a sphere.
They describe one closed surface, not six planets or six committed Minecraft dimensions.
Chart coordinates u/v are dimensionless in [-1, 1], altitude is in metres, and the velocity
conversion includes the position mapping's derivatives. Raw chart rates are not physical
Cartesian speeds. Shared edges/corners have deterministic ownership; converting an orbital
position back to a chart does not integrate or teleport the physical state.

The reference body is smooth, airless and nonrotating, with a 10000 m radius and mu = 981000000
m^3/s^2. These are test parameters, not production defaults. The reference flight is an ellipse
with surface periapsis and apoapsis 4000 m above the surface. Its initial tangential velocity
is assigned instantly. It checks circumplanetary return and chart continuity; it does not
demonstrate a powered ascent, terrain clearance, collision, landing, attitude or camera behavior.

The fixed-step velocity-Verlet run uses 12000 steps over one analytic orbital period. Automated
acceptance requires all six charts, return position error below 0.02 m, return velocity error
below 0.001 m/s, altitude extrema within 0.02 m of the reference ellipse, relative specific-energy
error below 1e-6, and chart round-trip errors below 1e-8 m / 1e-9 m/s. A step-halving check tests
convergence. Separate tests cover a circular orbit, angular momentum, an impulsive manoeuvre,
invalid values and chart derivatives. These tolerances apply only to this bounded experiment.

Cube projection distorts distances and areas. Its geometric closure does not establish a
consistent block grid: chart-edge block orientation, terrain sampling, collision, lighting,
neighbour updates and chunk ownership still need an integration design and game evidence.
In particular, mapping an equal block distance to unequal physical distances across charts
cannot silently become the flight metric. Do not freeze chart enum names as save/network IDs.

The single-Level [surface-flight diagnostic](sable-laboratory.md#surface-flight-and-physical-landing)
now verifies takeoff from a small flat Minecraft pad, thrust-based orbital insertion, an unpowered
revolution and return to native terrain contact. It preserves the same Sable body. This is not yet the
surface-chart or cross-Level handoff described below; no solid spherical planet or passenger transport
has been implemented.

## Sable source evidence

Inspected upstream revision
[`d501fb3103c3247e20cb2151a99182ad5dd61adb`](https://github.com/ryanhcode/sable/tree/d501fb3103c3247e20cb2151a99182ad5dd61adb).
Its [build properties](https://github.com/ryanhcode/sable/blob/d501fb3103c3247e20cb2151a99182ad5dd61adb/gradle.properties)
declare Sable 2.0.6 and Minecraft 1.21.1. The runtime experiment separately pins Modrinth release `fg9dTRz9`; see its provenance record.

- [RigidBodyHandle](https://github.com/ryanhcode/sable/blob/d501fb3103c3247e20cb2151a99182ad5dd61adb/common/src/main/java/dev/ryanhcode/sable/api/physics/handle/RigidBodyHandle.java)
  exposes impulses, linear/angular velocity reads and changes, and pose teleportation.
  [BlockEntitySubLevelActor](https://github.com/ryanhcode/sable/blob/d501fb3103c3247e20cb2151a99182ad5dd61adb/common/src/main/java/dev/ryanhcode/sable/api/block/BlockEntitySubLevelActor.java)
  provides a physics callback with a timestep; a game tick can contain multiple physics ticks.
- [SubLevel](https://github.com/ryanhcode/sable/blob/d501fb3103c3247e20cb2151a99182ad5dd61adb/common/src/main/java/dev/ryanhcode/sable/sublevel/SubLevel.java)
  holds a final parent Level. The inspected API does not establish a supported live dimension-transfer
  operation. Pose teleportation alone does not prove dimension-transfer support.
- [The teleport command](https://github.com/ryanhcode/sable/blob/d501fb3103c3247e20cb2151a99182ad5dd61adb/common/src/main/java/dev/ryanhcode/sable/command/SableSubLevelCommands.java)
  explicitly resets velocity before changing the pose. It cannot serve as a velocity-preserving transition.
- [SubLevelSerializer](https://github.com/ryanhcode/sable/blob/d501fb3103c3247e20cb2151a99182ad5dd61adb/common/src/main/java/dev/ryanhcode/sable/sublevel/storage/serialization/SubLevelSerializer.java)
  supports serialization and loading into a supplied ServerLevel, but omits small velocities and scales
  restored velocities by VELOCITY_RETAINED_ON_LOAD. A transfer must capture exact kinematics separately.
- [ServerLevelPlot](https://github.com/ryanhcode/sable/blob/d501fb3103c3247e20cb2151a99182ad5dd61adb/common/src/main/java/dev/ryanhcode/sable/sublevel/plot/ServerLevelPlot.java)
  serializes chunk/block data, block entities and scheduled ticks. That is useful evidence for a transfer
  experiment, not proof that passenger entities, constraints, plot allocation and external references survive.
- Actor connection/loading dependencies and container storage need lifecycle handling as a group.
  A schematic import or save/load call is not an atomic ship transfer protocol.

Use upstream APIs through an integration adapter. Do not copy Sable implementation into the Apache-2.0
core or assume its availability on 26.1. Record its actual license and distribution terms before adding
or bundling dependencies. The optional adapter now records these in [Sable provenance](../../licenses/sable/PROVENANCE.md)
and keeps upstream binaries outside the Orbitalis archive.

## Proposed simulation boundary

Core owns celestial states, simulation time and transformations between explicitly named reference
frames, using doubles and explicit physical units. Minecraft coordinates are a local representation.
Sable types and Minecraft world objects stay outside core.

For an actively simulated local scene, Sable integrates local rigid-body motion; Orbitalis supplies the
celestial reference and the required accelerations. Reconstruct the global state from that local motion.
For an unloaded/coasting craft, an orbital propagator advances the global state. These modes must have
an explicit handoff: never independently integrate the same translation in both systems.
Nearby interacting ships need a common local reference frame, including during rendezvous and docking.
Recentring that frame must move all affected craft and players consistently.

For the first mathematical experiment use an inertial local frame with origin O and fixed rotation Q:

    r_global = O + Q * r_local
    v_global = O_dot + Q * v_local

A rotating surface frame additionally needs the angular-velocity contribution to velocity and the
appropriate fictitious accelerations. Keep it a separate explicit case; rotating positions alone is wrong.
Transform orientation, angular velocity, camera direction and passenger-relative motion consistently.
Use SI units in core and an explicit block-to-metre conversion in the adapter, rather than silently
choosing a planetary scale. No permanent save/network contract is introduced by this note.

## Surface and orbit representation

A flat Minecraft region and a closed spherical surface cannot have a globally one-to-one,
distance-preserving mapping. A high-altitude transition trigger does not resolve this geometry.

Compare these options before choosing the production layout:

| Approach | What it preserves | What must be demonstrated or decided |
| --- | --- | --- |
| Separate surface/orbit levels with a visually concealed handoff | Ordinary surface world and bounded local scenes | Complete construction/entity transfer, destination preload, cross-level rendering and camera continuity |
| A shared Level with changing local simulation frames | Avoids a dimension transfer for the initial scene | Height/chunk limits, multiple simultaneous frames, player interactions and precision; not automatically seamless |
| Finite planetary surface with wrapping or multiple surface patches | Explicit global landing locations | Changes ordinary-world geography; wrapping, seams, mapping and compatibility require a product decision |

Recommended first experiment: one bounded surface patch with a recorded anchor, ascent into an orbital
frame, and descent back to that same patch. This tests local geometry and transfer without claiming a
solution for the complete Overworld. Keep patch-boundary rejection explicit in the experiment; do not
silently wrap, clamp or alias distant surface coordinates. Production planet radius, world extent,
landing outside the patch and treatment of existing worlds remain open decisions.

## Required handoff behavior

The server owns the handoff. Prepare destination chunks/plot allocation and capture a complete source
manifest before switching authority. Include connected sub-levels, exact poses and velocities, stable
craft identity, blocks/block entities, scheduled ticks, passengers and their riding relationships.
Define which nearby free entities travel with the craft; overlap with its bounding box alone is
insufficient. Reject a transfer involving unsupported external connections.

Use one simulation timestamp for the coordinate change, and suspend duplicate integration during it.
Until destination readiness is confirmed, retain the source as authoritative. On preparation failure,
resume there without deleting or duplicating anything. Crash recovery must distinguish a prepared
destination from a committed transfer; a pair of independently saved dimensions is not a transaction.
Cleanup, networking, loading tickets and client interpolation must follow that single authority change.
This describes required behavior, not a claim that Sable already provides the mechanism.

## Experiments and acceptance evidence

1. **Coordinate laboratory in core:** reversible local/global position, velocity and orientation changes;
   distinguish frame motion from craft motion; circular/coasting orbits and manoeuvres have bounded error.
   Add rotating-frame tests when that case is implemented. Set numerical tolerances with chosen units,
   scale and integration interval before reporting physical accuracy.
2. **Sable laboratory on 1.21.1:** pin a tested dependency outside core; launch a disposable world/server;
   control one construction through physics callbacks and measure impulses versus timestep. Confirm
   vacuum/coasting behavior and an origin shift with two nearby constructions. Compile success is insufficient.
3. **Transfer laboratory:** surface-to-test-level-and-back with blocks, inventory-bearing block entities,
   a passenger, a free entity inside the craft, nonzero linear/angular velocity and connected sections.
   Test plot collisions, unsupported links, failed destination preparation and save/restart around the
   handoff. Source deletion is conditional on verified ownership transfer and recovery behavior.
4. **Visual laboratory:** preload destination and test rendering continuity from cockpit and exterior,
   including a second observer in multiplayer, looking backwards and turning around in the transition
   band. Measure stalls and camera/pose discontinuities. Use separate ascent/descent thresholds to prevent
   repeated handoffs. A hidden loading screen alone does not demonstrate seamless flight.
5. **Orbital playground:** persistent coasting craft, thrust changing its trajectory and rendezvous with
   another construction. Physical docking is a later explicit constraint/identity operation; sharing an
   orbital trajectory does not establish a physical joint.

Run lightweight core tests for mathematics, actual game/server scenarios for Sable/lifecycle behavior,
and packaging checks after adding dependencies. Record exact revisions, configurations and observations.
If transfer or visual continuity requires unsupported internal patches, document the required upstream
extension and resolve that architectural choice before making it a production dependency.

The first implementation plan should cover experiments 1–3 with go/no-go checkpoints. Final rendering,
global planet mapping and time acceleration require their own decisions and evidence.

## Bounded transfer implementation

The [ship transfer diagnostic](ship-transfer.md) now implements a fixed translated surface/orbit frame
and a journaled reconstruction adapter against pinned Sable 2.0.6. Its native GameTest covers a round
trip, cargo, pilot pose, small velocities and phase-based recovery. It deliberately rejects connected
ships, general passengers/entities and scheduled ticks. This is partial evidence for experiment 3,
not completion of its broader acceptance list or the visual continuity experiment. Native checkpoints
can stall; separate-process/power-loss tests and an observed consumer handoff remain pending.
