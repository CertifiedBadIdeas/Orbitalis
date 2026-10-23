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
Create, Sable, Create Aeronautics and Create Propulsion: Simulated. It owns disposable fixtures and integration tests with a separate mod identity,
`orbitalis_flight_consumer`, and produces no distributable umbrella JAR. The ordinary Orbitalis build does not include this build,
resolve its external mod stack, or depend on it. Sable-only development runs remain available separately.

Flight controllers belong to external integrations; consumer fixtures and GameTests are written in Kotlin. See [flight-control ownership](flight-control.md). Keep new consumer code in
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

## Client terrain LODs

`runClient` automatically runs `prepareFlightClientMods`, which downloads eight pinned original JARs,
checks their SHA-512 digests and installs the prepared runtime only in `run/client/mods`. Native
NeoForge dependencies and their nested modules are prepared through Loom in an isolated `clientVisual`
source set; Voxy, Roxy and Connector remain original JARs for loader discovery. Generated IDE launches need
that preparation task once before launch. Prepared native JARs are compared by entry names and
contents, ignoring ZIP timestamps, compression and entry order that may differ between IDE and managed Loom caches. Converted access-transformer
rules may also appear in a different order; the complete rule list must match. Original release JARs still
require identical bytes. A different existing file with a managed name is preserved
and reported instead of overwritten. Server and GameTest run directories/classpaths do not receive
these mods, and they are never packaged in Orbitalis.

| Client dependency | Version / Modrinth release |
| --- | --- |
| Voxy | 0.2.16-beta for 1.21.11 / `H3w2nVdU` |
| Roxy | 0.4.0 for NeoForge 1.21.1 / `cDmwpuYr` |
| Sinytra Connector | 2.0.0-beta.17+1.21.1 / `IITF0PRC` |
| Forgified Fabric API | 0.116.14+2.3.0+1.21.1 / `dAxle9F7` |
| Sodium NeoForge | 0.8.12+mc1.21.1 / `S3DUMfBo` |
| Voxy World Gen V2 NeoForge | 2.4.2 / `PzNUZlWw` |
| Cloth Config NeoForge | 15.0.140 / `izKINKFg` |
| Better ModList | 21.1.4 / `ZID4kkto` |

The Voxy Minecraft version is intentional: [Roxy's supported original release](https://modrinth.com/mod/roxy)
is the 1.21.11 JAR, adapted at runtime to NeoForge 1.21.1. Connector alone does not perform that version
port. Use Forgified Fabric API rather than adding the Fabric-loader API distribution. Sodium and
Forgified versions follow Roxy's release dependency pins. Keep the Voxy JAR unmodified and outside
Loom's remapped mod configurations so Roxy can discover it. Connector's development clean-Minecraft
input comes from Loom's unpatched intermediate JAR. Native API modules need development remapping
because their production lambda names differ. Select Forgified API's newer/equal copies of Sodium's
four embedded Fabric API modules; retain Sodium's service library and renderer module separately.
Verification checks that no mod IDs are lost/duplicated and that prepared files stay out of ordinary
compile, server and test classpaths.

Voxy World Gen V2 fills the terrain cache through background generation in single-player; its
settings live in `config/voxyworldgenv2.json`. Cloth Config and Better ModList follow the generator
release's published dependency list. Installing this stack does not replace Orbitalis' space-body renderer.

Observed client smoke check (2026-10-17): a new disposable Overworld loaded with the full module set,
Sodium/Voxy rendered terrain, WorldGen initialized its Voxy ingester and reported 244 completed chunks
in F3. Saving to title and quitting completed normally. The preview used a 12-chunk generation radius
and two active tasks; these bounded preview settings are not installed in the ordinary consumer.

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

`check` compiles the fixtures, unit tests and native GameTests. `runGameTestServer` verifies finite
fuel supply, native coal-powered pumps, exact fuel/cargo snapshots and occupied chunk limits. The
`expedition` suite omits the function-registration check. Built-in autopilot suites are retired;
external control tests belong to their owning integration stand.

## Repeatable terrain ship fixture

The consumer includes the fixture functions and predicates as ordinary mod data resources. Running
`./gradlew runClient` from `consumers/flight` makes them available in new and existing worlds without
installing a datapack or running `/reload`. The external flight stack and fixture commands are enabled by default. Keep future stand tools in this consumer and wire them into its
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
pinned default maximum). External controllers can throttle them independently; normal linked receivers group
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
short pulses and toggle the controller off before enabling an external controller or walking normally.

The following historical trial used the earlier five-engine wooden platform, not this shuttle.
During the 2026-10-15 interactive Overworld trial, the user reported ascent to approximately y = 5000,
with engines, legs and all cargo chests remaining together. After a full client restart the user reported
that the construction and sixteen cobblestone per chest remained. These are user-observed results;
controlled landing and chunk unload/reload were not established. A suspected split was not confirmed as
a reproducible defect. The later flight test dimension exercises a different gravitational environment;
the Overworld observations alone do not validate that integration.

See [central gravity](flight-gravity.md) for the Orbitalis force adapter and its independent checks.


## Fuelled expedition fixture

The additive development command `/orbitalis_test expedition` places the long north-facing expedition
ship in an empty, loaded 13 x 31 x 10 space. Its origin is 24 blocks south and one block above the
caller's feet. Existing blocks prevent placement; this command does not clear terrain or build a pad.
Use a disposable Overworld launch site with level ground under all four landing feet. The fixture
belongs to the flight consumer; programmable control belongs to external integrations.

The white industrial hull has a glazed bow, walkable cabin, aft boarding stairs, four cargo chests,
four landing legs and a cabin Physics Assembler. It uses 40 independent standard liquid Propulsion
thrusters: twenty for lift and four for each other translation direction. Thin native Create copycat
plates carry the concrete exterior materials; this avoids a solid concrete pressure hull.
One native 2 x 2 x 6 Create reservoir contains a finite 192 buckets of turpentine. Four native
coal-powered Simulated portable engines, each supplied with eight coal blocks, drive mechanical pumps
at 64 RPM through native 2:1 cogwheel gearing. Separate intakes draw from the same native reservoir.
The engines start empty and receive fuel through pipes. Wait for priming before assembly or powered flight. No runtime code refills tanks,
creates rotation, supplies FE, changes native capacity or replaces native fuel consumption.

Placement also gives the pilot a wrench and paired Create Linked Controller. Board from the
south; with an empty hand, hold right-click on the cabin Physics Assembler, drag upward and release
to assemble. Use the linked controller for translation with WASD, Space and Shift. The fixture uses fixed development radio frequencies; use one expedition fixture at a time
within radio range to avoid controlling another fixture. Switch manual input off before an external
program claims the engines. Fuel, startup and force application remain native Propulsion behavior.
An unpowered ship coasts under Orbitalis physics. Orbitalis contains no return/landing controller
or maneuver executor; the navigation map stores plans only.

The `expedition` suite verifies the finite onboard supply and exact native ship snapshots, including
coal, pump state, fuel and occupied/read-created chunk separation. The `all` suite also verifies
fixture function registration. Powered flight under Guest control is tested in the separate
Orbitalis–Compukters stand.
