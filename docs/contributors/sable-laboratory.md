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

# Sable physics laboratory

This bounded experiment targets Minecraft 1.21.1 / NeoForge 21.1.252 with Sable 2.0.6.
It creates two small real block constructions, exercises Sable's native Rapier backend, and
translates both constructions and an unmounted creative observer within one level. It does not
implement production orbital ships, surface-to-orbit travel or transfer between dimensions. Use a disposable world.

## Launch and commands

```sh
./gradlew :v1_21_1-neoforge:runClient
```

All standard development game runs verify the pinned release SHA-512 and stage the original Sable JAR
under their `mods` directory in the NeoForge target: `run/client`, `run/server` and
`run/gameTestServer`. No Gradle property is needed. Existing worlds in these directories remain in place;
worlds from the former `run/*-sable` directories are not moved automatically.
The original release carries its own Companion, Rapier and Veil dependencies; Create is not required.
Compilation uses remapped compile-only APIs, because Loom's remapped dependency strips the nested
JARs needed at runtime. Orbitalis does not bundle or patch Sable. See [provenance](../../licenses/sable/PROVENANCE.md)
for the artifact hash, inspected source revision and PolyForm Shield license, distinct from Apache-2.0.

Create a creative world with cheats enabled, then run:

```text
/orbitalis lab enter
/orbitalis lab orbit
/orbitalis lab stop
/orbitalis lab sable start
/orbitalis lab sable sample
/orbitalis lab sable shift
/orbitalis lab sable clear
/orbitalis lab leave
```

`orbit` supplies a nearby observation platform; `stop` removes the independent particle demonstration.
Sable probes begin at (0, 80, 120) and (8, 80, 120). Gold and iron distinguish them. `start` replaces
only marked Orbitalis diagnostic constructions. Replacing a newly created scene again in the same
game tick is rejected before altering it; wait until the next tick. The pinned integration otherwise
fails block placement during immediate plot reuse. After one initialization physics step, a 0.5 second
pulse applies impulse `mass * 2 m/s² * actual timestep`, producing delta-v 1 m/s along positive X.
The second probe also receives angular velocity. Afterwards the bodies coast. Mass is reported in
Sable's units; it is not claimed to be kilograms.

The laboratory dimension alone has zero Sable gravity, pressure and universal drag. Ordinary player
and Minecraft entity gravity remain unchanged. The `start` scene has no celestial gravity; the separate `orbit` scene below adds central attraction.
Do not infer a physical orbit from the particle demonstration.

All commands require operator permission level 2 and the lab dimension. `shift` additionally requires
an unmounted creative observer and two running probes. On the next physics callback, it translates
both body poses and the observer by -32 m along X while increasing the scene origin by +32 m.
Orientation and linear/angular body velocities are retained. The static world and platforms do not move.
Enable creative flight before shifting: translating off the observation platform otherwise leaves
the observer falling under normal Minecraft gravity, including void damage.
A queued shift is canceled if its observer leaves the level or becomes ineligible before that callback.
`clear` and server shutdown remove only constructions carrying this adapter's marker and release their
loading tickets. Restart does not resume the experiment; `start`/`clear` also recover marked leftovers.
The marker `orbitalis_sable_diagnostic` is experimental diagnostic metadata, not a public ship schema.

## Central gravity and physical orbits

`/orbitalis lab sable orbit` replaces the coasting scene with two physical orbiting constructions.
One gold block and two iron blocks give different total masses. Their centres of mass start on
opposite sides of the same circular orbit; the iron construction also spins. After the first native
initialization step, each is assigned its initial tangential velocity once. Subsequent motion comes
only from Sable integrating central-gravity impulses, never teleporting onto a reference trajectory.

| Quantity | Diagnostic value |
| --- | --- |
| Fixed inertial centre | (0, 96, 120) m |
| Gravitational parameter mu | 1024 m³/s² |
| Initial radius / speed | 16 m / 8 m/s |
| Initial radial acceleration | 4 m/s² |
| Analytic circular period | 4 pi seconds, approximately 12.566 s |
| Orbit plane | XZ, at Y = 96 m |

The adapter evaluates `a = -mu * r / |r|³` at each construction's transformed centre of mass and
submits `mass * a * dt` on every pre-physics callback, converting the inertial impulse into
Sable's body-local axes. Rotation must not rotate the direction of celestial attraction. Sable alone advances the real bodies.
No gravity torque, atmosphere, engine/fuel system, terrain collisions, mutual attraction or player gravity is
added. This is a point-field laboratory, without a solid planet. Other Sable constructions are not
automatically enrolled. The existing `sable start` command still selects the zero-gravity force pulse.

`sample` reports orbital time, gold-probe radius/speed, both probes’ periapsis/apoapsis, burn count,
pending state, maximum reference-position error and relative energy error. A separate, eight-times-finer
Verlet reference is initialized from the measured initial state;
post-physics samples compare the native result to it without feeding corrections back into Sable.
Both reference and measured positions use the same inertial centre. A `shift` changes the local origin,
so force evaluation reconstructs global position first; the gravitating centre does not follow a probe.
`clear`, restart and server shutdown dispose the experiment. Invalid probes or radii outside the
4–64 m diagnostic band stop it and remove its marked constructions before singular forces are applied.

To observe the orbit, enter the lab in creative mode, enable creative flight, move near (0, 96, 88),
look towards positive Z, then run `sable orbit`. Watch a complete revolution and use `sample`.
Enable flight before a frame shift; the player retains vanilla gravity.

## Controlled orbital manoeuvres

Start `sable orbit`, wait for physics initialization, then apply a bounded instantaneous kick:

```text
/orbitalis lab sable burn prograde 1 1
/orbitalis lab sable sample
```

The arguments are direction (`prograde` along measured inertial velocity, or `retrograde` against it),
probe (`0` gold, `1` spinning iron) and delta-v in m/s (`0.01` through `1.0`). To compare braking from
the original circular orbit, restart `sable orbit` before issuing `sable burn retrograde 1 1`.
This is an operator diagnostic, without a rocket block, fuel consumption or continuous throttle.

The command queues one kick for the next pre-physics callback, where direction and mass are read again.
It applies `mass * delta-v` at the centre of mass through Sable's body-local impulse API; it does not
teleport the construction or set its post-burn velocity. The other probe receives only central gravity.
A simultaneous frame shift preserves the inertial direction. Pending duplicates, invalid arguments,
non-orbital scenes and uninitialized scenes are rejected. The predicted ellipse must stay within
4–64 m at both submission and execution. An execution-time rejection is reported by `sample`;
`clear`, scene restart and shutdown discard pending burns.

Periapsis and apoapsis are instantaneous two-body distances from the fixed centre, not altitudes above
terrain. From an exact 16 m circular orbit, a +1 m/s kick predicts periapsis 16 m and apoapsis
27.574 m; a -1 m/s kick predicts periapsis 9.924 m and apoapsis 16 m. Native poses and velocities have
finite integration error, so an in-game burn uses the measured state and yields slightly different values.

The independent reference receives the identical inertial kick at the same boundary. Its position and
velocity are never reset to the measured trajectory. Expected energy and angular momentum include the
kick; deliberately changing them is not counted as numerical drift. Maximum errors retain earlier
samples across burns. Radius error now compares measured and reference radii, so a deliberately elliptical
orbit is not incorrectly measured against the initial 16 m circle.

## Automated evidence

```sh
./gradlew verifyLocalFull :v1_21_1-neoforge:runGameTestServer
```

The standard GameTest run executes seven planet lab scenarios and four real Sable
native-physics scenarios, checking impulse across one/four substeps, coasting in vacuum, a frame shift
of two bodies with nonzero linear/angular velocity, relative/global positions and observer orientation. The central-gravity scenario additionally runs a
complete revolution at one and four substeps, shifts the frame halfway through each run, compares both
masses to the independent reference, and bounds radius, energy and angular-momentum error. Four substeps
must improve trajectory accuracy. Acceptance bounds are position error < 1 m, velocity error < 0.5 m/s,
reference-radius error < 0.6 m, relative energy error < 0.01 and relative angular-momentum error < 0.005.
The manoeuvre scenario exercises both directions on the rotating iron probe at one and four substeps,
with an untouched gold control and a simultaneous frame shift. It checks energy change, angular velocity,
reaching the new apsis, predicted/observed apsides, rejection and cancellation, and improved reference
accuracy with smaller steps. Over 14 seconds after each burn, bounds are position error < 1.6 m,
relative energy error < 0.02, relative angular-momentum error < 0.01 and apsis discrepancy < 0.6 m.
The `results.xml` report lives under the target’s `build/test-results/gameTestServer`. CI executes
the same standard run; a missing fresh report or missing required Sable scenario fails the run. Test-only players use an inert channel and packet sink without a login;
these tests establish server state, not a real client's mod negotiation or rendering.

Observed headless results on 2026-10-15: gold-block mass 4.0 Sable units; X velocity
1.000000119 m/s at one substep and 0.999999583 m/s at four (0.0125 s callbacks).
The frame test's relative body-position discrepancy was 0.002473 m within its 0.01 m bound.
These measurements apply to this simple diagnostic scene, not arbitrary ships or docking constraints.

The pinned build loses an impulse submitted before its first completed physics step. The scene waits
for that boundary and then applies the entire measured pulse; it does not compensate by guessing a force.
Sable also logs an unknown `create:flywheel` block from its bundled physics data when Create is absent.
This upstream resource warning is nonfatal in the tested configuration.

Measured central-gravity results on 2026-10-15, including a frame shift during each full revolution:

| Substeps per game tick | Maximum position error | Maximum speed-vector error | Maximum radius error | Maximum relative energy error |
| --- | --- | --- | --- | --- |
| 1 (0.05 s) | 0.77842 m | 0.18921 m/s | 0.20664 m | 0.000652 |
| 4 (0.0125 s) | 0.20544 m | 0.05257 m/s | 0.05159 m | 0.000410 |

The constructions had masses 4 and 8 Sable units. In this earlier circular-orbit measurement, radius error
was deviation from the initial 16 m radius. Maximum relative angular-momentum errors were 0.000055 and
0.000209 respectively. These are finite-duration native-solver measurements with a
first-order gravity kick and floating-point pose readback. They do not establish long-term orbital
accuracy, precision at large world coordinates, docking behavior or general ship support.

Measured manoeuvre results on 2026-10-15, applying a 1 m/s kick to iron after approximately 3 s,
with a simultaneous 32 m frame shift and 14 s of subsequent native flight:

| Substeps | Direction | Predicted periapsis / apoapsis at burn | Observed periapsis / apoapsis after flight | Maximum reference position error | Maximum relative energy error |
| --- | --- | --- | --- | --- | --- |
| 1 | Prograde | 15.800 / 27.976 m | 15.849 / 27.813 m | 0.77841 m | 0.010590 |
| 1 | Retrograde | 10.056 / 15.809 m | 9.974 / 16.014 m | 1.11615 m | 0.011489 |
| 4 | Prograde | 15.949 / 27.669 m | 15.961 / 27.626 m | 0.20559 m | 0.002426 |
| 4 | Retrograde | 9.957 / 15.950 m | 9.940 / 16.010 m | 0.26885 m | 0.003183 |

These apsides use the measured state, which already differs slightly from an exact circle at burn time.
The actual maximum radius reached after the prograde kick was 27.968 / 27.667 m at one/four substeps;
the retrograde minimum was 10.063 / 9.958 m. Gold stayed near its original circle and the COM impulse
preserved iron's spin. These are server/native measurements; the new burn commands have not yet been
observed interactively in a client.

## Observed client scenario

On 2026-10-15, the isolated Linux development client created a fresh creative world, logged into its
integrated server with Sable/Companion/Veil, entered the laboratory, and rendered both probes.
`sample` reported speed 1.000000 m/s after the force pulse and after one 32 m shift; the client
remained connected. `clear` removed the probes and `leave` returned to the Overworld. The observer
fell after being translated off the stationary platform, consistent with unchanged vanilla gravity.
This confirms bounded command/rendering behavior, not cockpit continuity, multiplayer synchronization,
passenger transport, or a seamless camera transition. Those remain unverified.

The central-gravity scene was also observed in the isolated client on 2026-10-15, starting from a
spectator observation point near (0, 100, 88). Both the gold block and spinning two-block iron
construction rendered and circulated; a 14-second recording covered more than the analytic period.
The independent particle reference was also started during inspection; it remains a separate scene
and is not the physical orbit's source of authority. The client logged two missing-sublevel movement
messages during initial construction spawn, then rendered both bodies. This test does not establish
packet-order correctness or seamless presentation; those startup messages remain an upstream/runtime
integration observation, not a fixed networking defect.

## Transfer decision

Proceed with bounded same-level force/frame experiments. Do not treat that result as a supported
cross-dimension ship handoff. In the inspected upstream API, a SubLevel retains a final parent Level;
its serializer drops small velocities and scales restored velocities. Save/load is insufficient to
preserve exact kinematics, identity, passengers, inventory, connected sections and ownership atomically.
No export-delete-import transfer is implemented.

Before a destructive transfer experiment, choose a supported upstream transfer extension or specify
an explicit transactional reconstruction protocol, including destination preparation, exact state,
constraint handling, rollback and restart recovery. The [feasibility note](orbital-flight-feasibility.md)
records the acceptance scenarios. Visual continuity and multiple simultaneous local frames remain
separate investigations. No Sable API compatibility with 26.1 is assumed.
