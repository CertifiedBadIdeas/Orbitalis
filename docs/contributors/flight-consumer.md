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
resolve its external mod stack, or depend on it. Sable-only development runs remain available separately.

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
Nothing executes on load or every tick. In a disposable Overworld,
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

Assemble a ship with creative Propulsion engines in Overworld or `orbitalis:space`. Stay on deck
in creative mode with flying disabled, and toggle the Linked Controller off. Wait for native assembly to settle. With operator permission, use:

```mcfunction
/orbitalis flight autopilot hold
/orbitalis flight autopilot altitude 400
/orbitalis flight autopilot status
/orbitalis flight autopilot stop
```

`hold` captures the current centre of mass and attitude and brakes toward that point. `altitude`
sets target centre-of-mass Y relative to sea level Y=64, preserving physical X/Z and attitude.
A target of 400 corresponds to physical Y=464, or space-frame Y=4560. Commands retarget the same pilot's
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
across at most four plot chunks. Control is limited to the Overworld/space pair, positions within
10000 m of the physical origin and represented COM above local Y=64. `altitude` accepts heights
32..15000, subject to the same position bounds. A single lift engine cannot provide every
attitude correction: add spaced lift engines and opposing translation engines for more control.
`status` reports position error and predicted unachievable force/torque. A large residual identifies
missing authority; the controller cannot make an unsuitable layout stable.

Sessions are ephemeral. Leaving the deck/dimension, logging out, losing an engine, paused physics,
exceeding the patch or the speed/rotation limits disengages control. `flight transfer`
disengage before its snapshot/travel runs; engage again after arrival. Stop clears peripheral and
redstone inputs and restores NORMAL control, allowing the native thrust envelope to fade. Keep manual
controllers off: normal redstone can command engines again afterwards. The pinned Propulsion server
load resets peripheral mode to NORMAL, so a saved session cannot resume itself after reconstruction
or restart. Hold sessions do not persist controller state. Orbital missions save a separate launch
bookmark and use the shared version-4 transfer journal described below.

The native consumer tests retain a symmetric engine rig for controller isolation and also execute the
actual built-in shuttle blueprint, including glue, through Simulated's native Physics Assembler. The
shuttle test requires one connected body, 24 independent engine controllers and all four cargo sentinels,
then verifies powered ascent and settled translation/attitude after an initial disturbance. Additional
checks cover native output cutoff, manual-input/missing-lift rejection and native reload behavior.
The user reported that both hold and target-height commands worked in the earlier interactive trial;
that predates the shuttle replacement and does not establish its appearance, boarding or landing.

## Orbital insertion and return (consumer only)

`/orbitalis flight autopilot orbit` starts the [finite-body mission](flat-gravity.md) from Overworld.
`/orbitalis flight autopilot land` returns to the saved launch site. Assemble on a clear Overworld pad inside the
first 2000-block footprint, leave manual inputs off and stay on deck with creative flight disabled.
The target is about 1000 m above sea level; the trajectory circles the finite body and coasts with
engines fully off. Surface gravity remains native through the build ceiling; a conservative
compatibility layer connects it to the exterior field. The diagnostic 18 km mission and its dimensions have been removed.

The new mission uses pure numerical prediction on a worker, real engine capture, ascent terrain
preloading from 400 m, an upward handoff at 600 m and downward handoff at 400 m. Its separate space
frame uses a 4096-m Y translation for native float precision. Journal staging still pauses physics.
The launch bookmark codec writes version 2 with an explicit Overworld surface ID. Retired diagnostic
bookmarks (all version 1, or version 2 naming `flight_test`) are skipped rather than converted; valid
Overworld bookmarks in the same file remain available. Saved bookmarks never resume thrust automatically.
After a restart on the same ship, issue `land` explicitly. A new successful `orbit` preflight replaces
that pilot's bookmark. The consumer stores at most 64 pilot/ship bookmarks.

Use `-PorbitalisConsumerGameTestSuite=flat` with `check runGameTestServer` for the focused native
Overworld/space/free-revolution/return and bookmark migration scenarios. The default suite includes it alongside all retained
native Propulsion scenarios. This headless pilot does not verify actual client input, camera or deck contact.

## Gravity trials

Native shuttle trials compare initial unpowered acceleration from rest at surface, transition and exterior positions,
check torque and translated-frame agreement, and retain the real assembled blueprint with cargo.
Overworld assembly/hold, engine cutoff, unsafe-input/missing-lift rejection and the complete
Overworld/space mission remain separate scenarios. Historical spherical and cube test results are
not acceptance data for the finite-body route. Interactive cockpit continuity still needs client evidence.

## Vector rocket prototype

In a disposable Overworld, stand on a clear flat launch site and run:

```mcfunction
/function orbitalis_flight_test:setup_rocket
```

This replaces a bounded 13×13×18 area with a pad and an upright rocket: glazed cabin, four cargo
chests, landing legs, four bottom-mounted creative vector engines and twelve small native RCS engines.
Feet must be between Y=-63 and Y=303; leave 40 metres inside the finite surface boundary. Assemble
with the cabin lever, turn off creative flight and all manual engine inputs, and let the legs settle.
Then use:

```mcfunction
/orbitalis flight autopilot orbit
/orbitalis flight autopilot status
/orbitalis flight autopilot land
/orbitalis flight autopilot stop
```

`orbit` detects the vector-engine layout and selects the consumer rocket controller. After a short
vertical clearance leg it pitches over and accelerates horizontally during ascent, joining a numerically
predicted finite-body orbit with a 900-metre crown altitude above sea level. The reference crown speed
is about 72 m/s and the period about 65 seconds; speed and height vary around this nonspherical body.
Square-body symmetries select the orbital plane from the launch site. No spherical Kepler formula or
arbitrary rotation of the near-field gravity model is used.

After capture, RCS aligns the hull, all engines fade completely, and a full unpowered revolution is
measured before the return window. Return then turns the rocket retrograde with RCS while the main
engines stay off, brakes with the real vector engines, and joins a vertical approach to the remembered
pad while still descending. The braking and approach references share position, velocity and
acceleration; they do not form a polyline or stop at an intermediate waypoint. Engine slew, gimbal
interpolation and physical torque determine the actual attitude. The controller never sets the live
ship's pose, velocity or applied force directly.

The rocket route returns automatically after the full revolution. `land` during free coast also queues
that return, preserving the full-revolution requirement; after a controller restart it uses the persisted
launch bookmark. `stop` releases all inputs and native engine fade still applies. The current stand
requires one unmounted creative pilot, a supported upright rocket, a clear pad and an unobstructed
approach. It does not plan around arbitrary terrain or buildings. The shuttle fixture and its hold
controller remain separate regression tools.

All guidance, engine allocation, transient gimbal override and fixture resources belong to this removable
consumer. Production only admits the pinned creative vector engine's saved block entity through the
existing journaled transfer boundary; all serialized fields remain subject to exact reconstruction checks.
The native creative engine variants supply unlimited development power without fuel or FE. No external
textures or models are imported. Steering overrides preserve manual signals and clear on load/stop.

The focused `rocket` GameTest suite checks assembly, actual gimbal basis/interpolation, powered hold,
RCS-only orientation and complete native centre/off-axis missions with cargo and both dimension handoffs.
Pure tests check force/torque constraints and continuity of the moving ascent and landing references.
Headless pilot acknowledgments are test fixtures; they do not establish real cockpit continuity.

### Manual rocket flight evidence (2026-10-17)

The user completed the full rocket route in the flight consumer and reported a successful flight.
The client log records liftoff, ascent, both dimension handoffs, capture, a full engine-off revolution,
retrograde turn, braking and supported landing at the saved pad. The controller disengaged after
landing. Cabin visibility was insufficient for watching the flight; an external ship camera is a
separate follow-up. This observation does not establish seated Create passenger support.
