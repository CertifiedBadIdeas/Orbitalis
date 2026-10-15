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
Create, Sable, Create Aeronautics and Create Propulsion: Simulated. It has no gameplay sources,
mod identity or distributable umbrella JAR. The ordinary Orbitalis build does not include this build,
resolve its external mod stack, or depend on it. Existing Sable laboratory runs remain available separately.

## Launch

Use JDK 25 for Gradle and a Java 21 toolchain. Verify from the repository root, then launch through
the consumer's own wrapper:

```sh
./gradlew -p consumers/flight check
cd consumers/flight
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

Only this consumer owns Create/Aeronautics/Propulsion runtime composition. It adds no mandatory
Orbitalis dependency, custom ship controller, flight automation, celestial gravity in the Overworld or
cross-dimension transfer implementation. Other integrations and renderer mods are outside this stand.
The ordinary laboratory's Sable-only GameTests remain in the main build; this consumer does not claim
that those tests cover the combined external stack.

## Verification

`check` runs `verifyConsumer`: it builds and inspects the current development archive, checks the pinned
physics mod set for missing/duplicate IDs, and verifies original physics release checksums. Both game
launches depend on that check. Root `verifyLocalFull` independently checks Orbitalis, including production
archive rejection of consumer mod dependencies and bundled external implementation classes.

Agent-operated builds use the repository's managed wrappers with the same `-p consumers/flight` argument;
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

Copy `consumers/flight/fixtures/terrain_ship` into the selected world's `datapacks` directory, using a
folder name such as `orbitalis_terrain_ship`. Run `/reload` in that world. Nothing executes on load or
every tick. In a disposable Overworld or after `/orbitalis flight enter`, stand on the intended launch
ground, away from existing ships, and run:

```mcfunction
/function orbitalis_flight_test:setup
```

**This clears a 21×21×11 volume at and above the caller and replaces a 21×21 ground layer beneath it.**
Use it once per clear launch site; do not invoke it on an existing ship. It creates a seven-block-square
oak deck, corner legs, five creative thrusters, native redstone receivers, four chests containing sixteen
cobblestone each and a centre Physics Assembler, with Super Glue covering the construction. It gives
the player a configured Create Linked Controller and wrench and places the player on deck. Only setup
moves the player; there is no flight automation. The internal `build` function is not the entry point.
The aligned launch position must be between Y=-63 and Y=309, so the entire fixture fits inside the
supported dimensions' build height. Outside that range the function reports an error before placing
blocks, spawning glue, giving items or moving the player. Flying Sable ships can exceed the build
height; invoking `setup` there cannot create a new static ship beneath the player.

Select an empty hand, aim at the Physics Assembler, hold right-click and drag upward until its indicator
reaches the end, then release. Check that Sable reports one sub-level and the whole craft moves together.
The assembler stands on solid deck; the lift engine receivers are on its sides beneath the deck.

Select the Linked Controller and right-click while aiming into air to activate it. Clicking a receiver
instead enters frequency binding mode. W/S fire the north/south translation engines, A/D the west/east
engines, and Space the lift engine; these are fixed ship axes, not camera-relative movement. Shift is
unused. Opposite translation thrust provides braking. Releasing Space cuts lift; use short pulses to
test descent/braking. There is no hover or attitude controller. Keep creative flight disabled when
checking native deck carrying. Toggle the controller off before walking normally on the deck.

The lift engine starts at 1000 kN (`ScrollValue:9` with the pinned default maximum); translation engines
start at 100 kN each (`ScrollValue:0`). Set thrust with the engine's native Create value control. These
are diagnostic settings, not a balanced survival vehicle. The Overworld trial initially used 500 kN;
the user reported needing twice that lift setting. Do not change global upstream configs to tune one ship.

During the 2026-10-15 interactive Overworld trial, the user reported ascent to approximately y = 5000,
with engines, legs and all cargo chests remaining together. After a full client restart the user reported
that the construction and sixteen cobblestone per chest remained. These are user-observed results;
controlled landing and chunk unload/reload were not established. A suspected split was not confirmed as
a reproducible defect. The later flight test dimension exercises a different gravitational environment;
the Overworld observations alone do not validate that integration.

See [central gravity](flight-gravity.md) for the Orbitalis force adapter and its independent checks.
