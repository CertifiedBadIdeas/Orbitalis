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

# Flight consumer development stand

`consumers/flight` is an independent Gradle build for running the current Orbitalis checkout with
Create, Sable, Create Aeronautics and Create Propulsion: Simulated. It owns a development-only control adapter with a separate mod identity,
`orbitalis_flight_consumer`, and produces no distributable umbrella JAR. The ordinary Orbitalis build does not include this build,
resolve its external mod stack, or depend on it. Existing Sable laboratory runs remain available separately.

The adapter, guidance, unit tests and GameTests are written in Kotlin. Keep new consumer code in
`src/main/kotlin`, `src/test/kotlin` or `src/gameTest/kotlin`. Kotlin and kotlinter versions come from
the root version catalog; `check` includes Kotlin lint and the Java 21 compilation target.

## Launch

Use JDK 25 for Gradle and a Java 21 toolchain. Verify and launch through the consumer's own wrapper:

```sh
cd consumers/flight
./gradlew check
./gradlew runClient
./gradlew runServer
```

Before running the dedicated server, read and accept the Minecraft EULA yourself in
`consumers/flight/run/server/eula.txt`. The build does not accept it automatically.
Client and server worlds are separate under `consumers/flight/run/client` and `run/server`.
Use disposable worlds for experiments; do not copy existing player worlds into the stand.
The stand also contains the same checksummed Gradle 9.8.1 wrapper as the root:

```sh
cd consumers/flight
./gradlew check
./gradlew runClient
```

In IntelliJ, choose **Gradle distribution: Wrapper** and **Gradle JVM: JDK 25** for the consumer.
Reload it after changing these settings. Selecting an older local Gradle distribution can cause Kotlin
metadata errors while compiling the included build scripts; the settings now reject a mismatched
Gradle version before configuring the included Orbitalis build. `verifyConsumer` checks that all four
wrapper files remain identical to the root copies, so update both wrappers together.

In IntelliJ, link `consumers/flight/settings.gradle.kts` as a separate Gradle build and use its
`runClient` / `runServer` tasks or generated run configurations.

The consumer includes the repository root as a composite build and builds
`:v1_21_1-neoforge:developmentModJar` before launch. This named development archive contains the same
core, Minecraft-common and NeoForge outputs as Orbitalis, with its metadata and legal notices.
It is a development input, not the remapped distribution. Kotlin is supplied separately by the consumer.
No Maven Local publication or manually copied Orbitalis archive is needed after source edits.

## Runtime pins and ownership

| Component | Pin |
| --- | --- |
| Minecraft / NeoForge / Loom / Kotlin | Root `gradle/libs.versions.toml` |
| Sable | 2.0.6, Modrinth `fg9dTRz9` |
| Aeronautics bundle (Aeronautics, Simulated, Offroad) | 1.3.2, Modrinth `44pLdPGg` |
| Create Propulsion: Simulated | 1.1.5, Modrinth `H13U56dc` |
| Create | 6.0.10-280, slim development artifact |
| Ponder / Flywheel / Registrate | 1.0.82+mc1.21.1 / 1.0.6 / MC1.21-1.3.0+67 |

Sable's exact embedded Companion, Rapier and Veil mods, Veil's embedded libraries and the three
Aeronautics mods are extracted by artifact transforms before Loom prepares the development classpath.
They are not replaced with independently selected versions. Embedded legal notices stay with those
artifacts. Do not also install the pinned mods manually into the run directories: duplicate IDs fail loading.

Only this consumer compiles its adapter against Create/Propulsion and composes their runtime.
Those dependencies and adapter classes never enter Orbitalis production outputs. The adapter runs
only on the logical server; all real thrust and integration remain native Propulsion/Sable behavior.
The ordinary laboratory's Sable-only GameTests remain in the main build. Separate consumer GameTests
exercise the combined external stack.

## Verification

`check` runs `verifyConsumer`: it builds and inspects the current development archive, checks the pinned
physics mod set for missing/duplicate IDs, and verifies original physics release checksums. Both ordinary game
launches depend on that check. `check` also compiles isolated consumer GameTests and verifies that
their outputs are absent from ordinary client/server runs. Run `./gradlew runGameTestServer` from
`consumers/flight` for the real combined-stack scenarios; a fresh XML report must contain all required
cases without failures, errors or skipped results. Root `verifyLocalFull` independently checks Orbitalis, including production
archive rejection of consumer mod dependencies and bundled external implementation classes.

Agent-operated builds and launches use the consumer wrapper with the repository's managed build slot;
see [development](development.md). A live consumer client holds the same workspace build slot.
A successful launch establishes loading compatibility only. Ship thrust, control, landing and persistence
must be observed in a disposable ordinary-terrain world before being described as verified gameplay.

See [consumer dependency provenance](../../licenses/flight-consumer/PROVENANCE.md) for licensing and
[architecture](architecture.md) for production module boundaries.

## Observed loading smoke test

On 2026-10-15 the consumer reached the title screen with 14 mods, including Orbitalis and the pinned
flight stack. An ordinary-terrain world was opened during the interactive session; the integrated
server loaded successfully, and the Orbitalis laboratory dimension was also entered. Save and Quit
saved all dimensions, and the client exited normally. No Propulsion-powered ship flight or restoration
scenario was performed. Upstream missing-model, optional-integration and absent optional fuel warnings
remain visible in the log; this run does not establish visual completeness of those external mods.
Local evidence is retained under `build/agent-logs/flight-consumer-20261015/` and
`build/agent-logs/flight-consumer-client-20261015.log` (ignored development artifacts).

## Repeatable terrain ship fixture

The consumer includes the fixture functions and predicates as ordinary mod data resources. Running
`./gradlew runClient` from `consumers/flight` makes them available in new and existing worlds without
installing a datapack or running `/reload`. The external flight stack, diagnostic commands and test
autopilot are also enabled by default. Keep future stand tools in this consumer and wire them into its
normal launch; they must not require manual installation or enter the production mod.

If you previously copied `orbitalis_terrain_ship` into a world's `datapacks` directory, remove that old
copy while the world is closed so it cannot override the built-in functions with an outdated version.
Nothing executes on load or every tick. In a disposable Overworld or after `/orbitalis flight enter`,
stand on the intended launch ground, away from existing ships, and run:

```mcfunction
/function orbitalis_flight_test:setup
```

**This clears a 21×21×11 volume at and above the caller and replaces a 21×21 ground layer beneath it.**
Use it once per clear launch site; do not invoke it on an existing ship. It creates an eleven-block-wide,
eleven-block-long white/light-blue shuttle, pointing north: glazed cockpit, walkable cabin, open aft
hatch, swept wings, four landing legs and four separate cargo chests containing sixteen cobblestone each.
The cabin's centre Physics Assembler sits on the floor. One Create Super Glue volume covers the craft
and excludes the launch ground. No new textures or external assets are imported.

Twenty-four separated creative Propulsion engines provide four actuators per thrust direction. Lift
engines sit below the wing pods, downward engines above them, and side/fore/aft engines are separated
vertically and laterally to produce differential torque. All start at 1000 kN (`ScrollValue:9` with the
pinned default maximum). The autopilot can throttle them independently; normal linked receivers group
them by direction for manual operation. These are diagnostic settings, not survival balancing.
Do not change global upstream configs to tune one ship.

Setup gives the player a configured Create Linked Controller and wrench and places the player inside
the cabin. Only setup moves the player; the blueprint does not start a flight. The internal `build`
and `shuttle` functions are not player entry points. The aligned launch position must be between Y=-63
and Y=309, so the entire cleared volume fits inside the supported dimensions' build height. Outside
that range the function reports an error before placing blocks, spawning glue, giving items or moving
the player. Flying Sable ships can exceed build height; invoking `setup` there cannot create a new
static ship beneath the player.

Select an empty hand, aim at the cabin Physics Assembler, hold right-click and drag upward until its
indicator reaches the end, then release. Check that Sable reports one sub-level and the entire craft
moves together. Keep creative flight disabled when checking native deck carrying.

Select the Linked Controller and right-click while aiming into air to activate it. Clicking a receiver
instead enters frequency binding mode. W/S fire the north/south translation engines, A/D the west/east
engines, Space fires lift, and Shift fires downward thrust. These are fixed ship axes, not camera-relative
movement. Opposite translation thrust provides braking. Manual inputs do not stabilize attitude; use
short pulses and toggle the controller off before engaging the test autopilot or walking normally.

The following historical trial used the earlier five-engine wooden platform, not this shuttle.
During the 2026-10-15 interactive Overworld trial, the user reported ascent to approximately y = 5000,
with engines, legs and all cargo chests remaining together. After a full client restart the user reported
that the construction and sixteen cobblestone per chest remained. These are user-observed results;
controlled landing and chunk unload/reload were not established. A suspected split was not confirmed as
a reproducible defect. The later flight test dimension exercises a different gravitational environment;
the Overworld observations alone do not validate that integration.

See [central gravity](flight-gravity.md) for the Orbitalis force adapter and its independent checks.


## Test autopilot (consumer only)

Assemble a ship with creative Propulsion engines in a diagnostic flight/orbit dimension. Stay on deck
in creative mode with flying disabled, and toggle the Linked Controller off. Wait for native assembly to settle. With operator permission, use:

```mcfunction
/orbitalis flight autopilot hold
/orbitalis flight autopilot altitude 400
/orbitalis flight autopilot status
/orbitalis flight autopilot stop
```

`hold` captures the current centre of mass and attitude and brakes toward that point. `altitude`
replaces the target centre-of-mass height above the cube's top face, preserving the current global
X/Z and attitude. Height is a cube-shell height, not Minecraft Y or distance from the centre. A target
of 400 corresponds to surface-frame Y=464, or orbit-frame Y=208. Commands retarget the same pilot's
session; only one session or mission is allowed per server. These two hold commands do not plan travel;
use the orbital mission commands below for insertion and return.

Consumer guidance requests gravity compensation, bounded translation and attitude correction. A bounded
nonnegative force/torque allocator uses each native engine's direction, force point and configured
capacity. The adapter writes fractional peripheral throttle; it never changes engine power settings,
injects impulses, sets velocity, moves the ship or creates a virtual stabilizing torque. Native engine
startup, fade, atmospheric multipliers and collision behavior still apply. Pinned creative engines
bypass exhaust obstruction checks in Propulsion; the adapter preserves that native behavior. Measured native output
updates the capacity estimate once an engine produces thrust.

For `hold` and `altitude`, start airborne with clearance for startup and braking. The target-speed cap is 12 m/s and requested
net acceleration is limited to 4 m/s²; these are guidance requests, not guaranteed physical bounds.
The adapter rejects active manual inputs, unsupported non-creative thrusters and
layouts that cannot approximately balance gravity. It supports up to 64 creative engine controllers
across at most four plot chunks. Surface/legacy-orbit control uses the existing ±2048 X/Z diagnostic
patch and global Y=64..16000; full-space control uses a planet-centred radius envelope of 6500..24000 m.
Targets from `altitude` are limited to shell heights 32..15000. Current and target COM must also
remain above local Y=64; in the orbit dimension this requires a shell height of at least 256. A single lift engine cannot provide every
attitude correction: add spaced lift engines and opposing translation engines for more control.
`status` reports position error and predicted unachievable force/torque. A large residual identifies
missing authority; the controller cannot make an unsuitable layout stable.

Sessions are ephemeral. Leaving the deck/dimension, logging out, losing an engine, paused physics,
exceeding the patch or the speed/rotation limits disengages control. `flight transfer` and `flight leave`
disengage before their snapshots/travel run; engage again after arrival. Stop clears peripheral and
redstone inputs and restores NORMAL control, allowing the native thrust envelope to fade. Keep manual
controllers off: normal redstone can command engines again afterwards. The pinned Propulsion server
load resets peripheral mode to NORMAL, so a saved session cannot resume itself after reconstruction
or restart. Hold sessions do not persist controller state. Orbital missions save a separate launch
bookmark and use the shared version-2 transfer journal described below.

The native consumer tests retain a symmetric engine rig for controller isolation and also execute the
actual built-in shuttle blueprint, including glue, through Simulated's native Physics Assembler. The
shuttle test requires one connected body, 24 independent engine controllers and all four cargo sentinels,
then verifies powered ascent and settled translation/attitude after an initial disturbance. Additional
checks cover native output cutoff, manual-input/missing-lift rejection and native reload behavior.
The user reported that both hold and target-height commands worked in the earlier interactive trial;
that predates the shuttle replacement and does not establish its appearance, boarding or landing.

## Orbital insertion and return (consumer only)

Launch the stand from `consumers/flight` with `./gradlew runClient`. Assemble the built-in shuttle on
a flat clear pad in `orbitalis:flight_test`, turn manual inputs off, disable creative flight and remain
on deck. Let the ship settle before running:

```mcfunction
/orbitalis flight autopilot orbit
/orbitalis flight autopilot status
```

`orbit` records this pilot's launch COM, ship UUID and attitude, climbs to a physical Y=1000 handoff,
waits for engine fade and transfers through the existing journal/client-readiness barrier to
`orbitalis:space_test`. It climbs above the launch X/Z toward a planet-centred radius of 18000 m,
begins insertion within 100 m at less than 10 m/s, accelerates east along the launch orbital plane
and cuts engine inputs after insertion settles. `COAST` means
free flight under gravity with engine inputs off; the reference speed is about 140.07 m/s and the
period about 807 s. The far region is central around the cube, including the diagonal parts of this
circle. Phase changes and `status` expose radius, actual speed/radial speed, far-field specific energy,
eccentricity and distance from the remembered launch COM. Energy/eccentricity are Kepler diagnostics
for the central far field, not conserved quantities for the near-surface blended field.

Once the mission reports `COAST`, use:

```mcfunction
/orbitalis flight autopilot land
```

The return controller follows the orbital plane, brakes along the arc to the point above the launch
site, descends vertically above its saved X/Z, transfers to the surface near Y=1000 and approaches
the saved COM/attitude slowly. It cuts engine inputs near touchdown and requires the native terrain
to hold the craft for 60 ticks before reporting `LANDED`. It never corrects the ship by teleport,
velocity assignment or virtual impulse. This conservative test path spends thrust to hold the orbital
radius during braking; it is not an efficient ballistic reentry planner. A return can take most of a
revolution. There is no obstacle avoidance, arbitrary landing-site selection, terrain-clearing operation
or automatic safe path around another construction. Keep the launch pad and vertical approach clear.

The launch bookmark is consumer-owned version-1 SavedData in the Overworld's
`data/orbitalis_flight_launch_sites.dat`, bounded to 64 pilot/ship entries. It contains the saved pose,
not a running mission or engine inputs. After restarting in space, stay on the same ship and issue
`land` explicitly; automatic thrust never resumes. Return requires that ship near the 18 km launch
orbital plane. A stop leaves the bookmark available. Starting a new `orbit` replaces that pilot's
bookmark only after surface/ship preflight. Missing/unknown versions, malformed poses and another
pilot/ship identity are rejected.

Mission guidance requests up to 3 m/s² of corrective acceleration, with up to 80 m/s for long vertical
legs and 220 m/s as the full-space safety limit. Preflight requires balanced additional acceleration
authority in all six directions alongside gravity compensation. Actual native engine limits and fade
still govern the flight. All propulsion, guidance, phase scheduling and launch bookmarks remain in this
consumer; the production mod only supplies frames, gravity, rendering and journaled handoff. Manual
`flight transfer`/`leave`, loss of deck contact or shutdown disengages a mission as it does hold.

The full-space native coast test is server evidence for a complete unpowered revolution. The consumer
mission scenario exercises real Propulsion insertion, coast, restored-bookmark return, both journaled
handoffs, preserved cargo/UUID and touchdown supported by native terrain with thrust off. On
2026-10-16 all eight consumer GameTests and eight unit tests passed after the Kotlin migration. The mission landed at tick 27200
with a displayed launch error of 0.2 m (asserted below 0.5 m), speed 0.05 m/s and no engine output;
the native terrain supported it for 60 ticks before success. The 200-tick coast measurement starts
only after native engine power and actual thrust both reach zero. Evidence is retained in
`build/agent-logs/consumer-kotlin-native-2.log`. Its inert pilot
models client acknowledgments/contact; real cockpit continuity and interaction still require a client
trial. Sable also requests ordinary world chunks along a full orbit, which can make initial world
saving expensive. This fixed-origin stand does not establish the eventual space chunk-loading policy,
finite terrain topology or seamless rendering. At local Y around 44000 the pinned native integrator
can report small velocity while the Y pose remains quantized; the climb therefore transitions into
insertion while moving rather than demanding a precise high-altitude hover. After orbital braking,
the descent starts within 10 m of the launch radial at less than 2 m/s. The return handoff
accepts a 5 m corridor at less than 2 m/s and preserves the measured motion.

## Repeatable gravity trials

The consumer gravity GameTest uses native-assembled shuttles with engines off. It arranges independent
initial positions, waits for the native pose to settle, and measures the velocity delta over twenty
server ticks. No position/velocity correction occurs during each measurement. Cases cover the top-face
centre and x=1600 near the surface, x=1600 at shell heights 2000 and 6000, and the equivalent high point
in the orbital frame. The test compares actual acceleration against the field, checks negligible
near-surface sideways acceleration and gravitational torque, a centreward component in the transition,
weaker high gravity, and agreement across the translated frames. Initial test teleports do not establish
powered travel, cross-dimension transfer or orbital insertion.

On 2026-10-16 all six consumer GameTests passed. The shuttle retained 24 independent engines and four
cargo sentinels after powered ascent and three-axis disturbance recovery; final position error was
about 0.01 m. Representative free-fall results (m/s², twenty-tick velocity delta) were:

| Frame | Global X | Approximate shell height | Measured X acceleration | Measured Y acceleration |
| --- | ---: | ---: | ---: | ---: |
| Surface | 0 | 175 | 0.000 | -9.271 |
| Surface | 1600 | 175 | 0.000 | -9.271 |
| Surface | 1600 | 2000 | -0.136 | -5.666 |
| Surface | 1600 | 6000 | -0.319 | -2.389 |
| Orbit | 1600 | 6000 | -0.319 | -2.389 |

The largest field-versus-measured vector difference in this run was about 0.011 m/s², below the
0.05 m/s² acceptance threshold. Native positions drift slightly during the initial settling interval
and during each fall. Local evidence: `build/agent-logs/flight-shuttle-07.log` and
`consumers/flight/build/test-results/gameTestServer/results.xml` (ignored development artifacts).

For an interactive trial, start a disposable creative world from the consumer's own `./gradlew runClient`:

1. Enter with `/orbitalis flight enter`, stand on clear terrain and run
   `/function orbitalis_flight_test:setup`. Assemble once and disable creative flight.
2. With the Linked Controller off, use `/orbitalis flight autopilot altitude 400`. Check that the ship
   lifts, stabilizes and retains all four cargo chests. `status` should settle to small position and
   force/torque residuals. A ceiling/floor collision invalidates a free-flight observation.
3. Repeat with target heights 2000 and 6000. Use `/orbitalis flight sample` at each settled height to
   inspect the field. Sampling a hovering ship reports gravity, not measured total acceleration;
   the engine compensation is intentional. The 12 m/s guidance cap makes high ascents take minutes.
4. For a short unpowered fall, run `autopilot stop`, wait for native engine fade and sample again.
   Re-engage `hold` before speed reaches the controller's 120 m/s limit and well clear of terrain.
   Engine fade and braking make this a qualitative check; the automated velocity-delta trial provides
   the controlled acceleration measurement.
5. Stop and leave the old ship in place. For the offset trial, use creative flight to reach clear
   terrain near x=1600, z=0 in `orbitalis:flight_test`, then build a fresh shuttle at ground height.
   Repeat the same heights. Near the surface gravity should be vertical; higher up its vector should
   gain a component toward x=0. Hold deliberately counters that sideways acceleration.

`setup` replaces only the launch area; it does not delete old native ships. Keep each trial away from
previous constructions. Test surface/orbit transfer separately and engage the autopilot again after
arrival. Shuttle appearance, manual handling, landing and long powered high/offset ascents remain
interactive acceptance checks.


## Native planet chart acceptance

The `faces` GameTest suite exercises the same six-chart cube used by `/orbitalis lab cube enter`:
`runGameTestServer -PorbitalisConsumerGameTestSuite=faces`. It checks native Create vault formation,
shared inventories and both placement axes on every face, all 24 directed edge crossings with native
player contact, and native chart unload/reload with inventory, excavation and anchor retention.
The ordinary default `all` consumer suite also includes these scenarios. Create fixture blocks and
acceptance tests belong only to this stand; canonical shared/common planet code does not depend on Create.
For a manual trial, use the standard cube commands in a disposable consumer world, take vaults from
the ordinary creative inventory and place them along both local horizontal directions on each face.
Do not expect a vault or inventory network to continue automatically across a chart seam.


The opt-in seam scenario adds two native hoppers feeding actual Create vault block capabilities across
the UP/EAST chart boundary in both directions. This proves endpoint inventory interaction only; it does
not join vault controllers. The `faces` suite now requires ten scenarios, adding actual motor/shaft
and motor-driven pump/pipe/tank networks. Typed optional NeoForge adapters translate graph addresses
and destination-local ports while native Create owns rotation, network membership, pressure and fluids.
The tests include same-chart controls, all 24 directed shaft transitions, sign changes, motor reversal,
removal/reconnection, fluid conservation and reversed/blocked/reconnected pipes. They also connect an
UP-authored shaft five blocks inside the EAST geometric sector and connect authored networks above
both faces, away from the cube edge. One native pipe/tank column is also evicted and restored while the source continues ticking, checking
conservation and resumed flow. Large/diagonal cog, encased chain and custom network contracts remain
outside the validated set.

Two closed-network scenarios add a 168-cell nonplanar ring visiting all six native charts, a branch and
five cells retained in the UP chart inside the EAST geometric sector. Reciprocal neighbour checks and
the composed address/direction transform must return to the original point and axes after the loop.
The kinetic graph checks signed shaft speeds, one native network without removed members, reversal,
an alternate route after a cut, source removal and reconnection. The ordinary-pipe graph uses a real
motor-driven pump and two native tanks, checks 4000 mB conservation on every tick, transfers through
each separately isolated route, rejects an isolated sink and resumes after reconnection. Native Create
pressure discovery chooses endpoint paths; the two alternate routes together must cover all six faces.
The test temporarily raises Create's own pump range from its configured value to 512 for this long
circuit, then restores it. Production pump range is unchanged.

Both rings invoke native Sable save/unload/load of the complete chart group without rebuilding their
blocks, then check topology and resumed native network operation. The reload helpers use Sable's native
ready-queue dispatch so restored entries cannot be loaded twice. This is same-process native restoration,
not a two-process server restart test. Pipe connection states are authored by the fixture and checked before flow; interactive
placement, cross-chart shape updates and client connection rendering remain unvalidated. The scenarios
are small-cube correctness evidence, not a large-planet performance measurement.

The Physics Assembler scenario uses the native Aeronautics/Simulated assembler to build a new ship
on each face. Fixed planet charts are terrain for the assembly/disassembly mode decision; native Sable
assembly still receives the real parent chart and preserves the new ship's physical pose and cargo.
The new ship remains dynamic and all six planet identities remain unchanged. This does not validate
ship-to-chart disassembly or client lever animation/hover text.

The small cube dimension enables the server-side hooks by default. For manual investigation,
`/orbitalis planet seams false` disables them for the current server session; `true` restores them. Toggling does not rescan existing machines or
synchronize client connection shapes. This default does not establish world lifecycle or client presentation correctness.
