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

This bounded experiment targets Minecraft 1.21.1 / NeoForge 21.1.252 with optional Sable 2.0.6.
It creates two real single-block constructions, exercises Sable's native Rapier backend, and
translates both constructions and an unmounted creative observer within one level. It does not
implement orbital ships, surface-to-orbit travel or transfer between dimensions. Use a disposable world.

## Launch and commands

```sh
./gradlew :v1_21_1-neoforge:runClient -PorbitalisSableLab=true
```

The profile verifies the pinned release SHA-512 and stages the original Sable JAR under
`run/client-sable/mods` in the NeoForge target. Server and GameTest profiles use separate
`run/server-sable` and `run/gameTestServer-sable` directories. Normal runs do not install Sable.
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
only marked Orbitalis diagnostic constructions. After one initialization physics step, a 0.5 second
pulse applies impulse `mass * 2 m/s² * actual timestep`, producing delta-v 1 m/s along positive X.
The second probe also receives angular velocity. Afterwards the bodies coast. Mass is reported in
Sable's units; it is not claimed to be kilograms.

The laboratory dimension alone has zero Sable gravity, pressure and universal drag. Ordinary player
and Minecraft entity gravity remain unchanged. This vacuum scene has no celestial gravity or orbital
integrator attached to its constructions. Do not infer a physical orbit from the particle demonstration.

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

## Automated evidence

```sh
./gradlew verifyLocalFull :v1_21_1-neoforge:runGameTestServer
./gradlew :v1_21_1-neoforge:runGameTestServer -PorbitalisSableLab=true
```

The base profile runs seven planet lab scenarios without Sable. The optional profile adds two real
native-physics scenarios, checking impulse across one/four substeps, coasting in vacuum, a frame shift
of two bodies with nonzero linear/angular velocity, relative/global positions and observer orientation.
Separate `results.xml` and `sable-results.xml` reports live under the target's
`build/test-results/gameTestServer`. Both are required in CI; a missing fresh report or missing required
Sable scenario fails the run. Test-only players use an inert channel and packet sink without a login;
these tests establish server state, not a real client's mod negotiation or rendering.

Observed headless results on 2026-10-15: gold-block mass 4.0 Sable units; X velocity
1.000000119 m/s at one substep and 0.999999583 m/s at four (0.0125 s callbacks).
The frame test's relative body-position discrepancy was 0.002473 m within its 0.01 m bound.
These measurements apply to this simple diagnostic scene, not arbitrary ships or docking constraints.

The pinned build loses an impulse submitted before its first completed physics step. The scene waits
for that boundary and then applies the entire measured pulse; it does not compensate by guessing a force.
Sable also logs an unknown `create:flywheel` block from its bundled physics data when Create is absent.
This upstream resource warning is nonfatal in the tested configuration.

## Observed client scenario

On 2026-10-15, the isolated Linux development client created a fresh creative world, logged into its
integrated server with Sable/Companion/Veil, entered the laboratory, and rendered both probes.
`sample` reported speed 1.000000 m/s after the force pulse and after one 32 m shift; the client
remained connected. `clear` removed the probes and `leave` returned to the Overworld. The observer
fell after being translated off the stationary platform, consistent with unchanged vanilla gravity.
This confirms bounded command/rendering behavior, not cockpit continuity, multiplayer synchronization,
passenger transport, or a seamless camera transition. Those remain unverified.

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
