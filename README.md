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

# Orbitalis

Orbitalis is a space exploration mod for **Minecraft 1.21.1 / NeoForge**. Fly from a finite
Overworld into orbit, visit the Moon, and build orbital constructions with optional Sable support.
The first **Alpha**, version **0.1.0**, is frozen for publication.

## Features

- A finite Earth with ordinary Minecraft terrain, blocks and fluids. Choose its width when creating
  a world: 1,000, 2,000, 5,000, 10,000 (default), or 20,000 blocks. Fly around its edges and underneath.
- A scaled Sun–Earth–Moon system, rotating surface frames, altitude-dependent gravity and a native
  lunar surface with craters. The Sun is currently a catalogue/map body; physical solar lighting is
  not implemented. Vanilla daylight remains independent of physical rotation.
- Automatic surface/space travel for individual players and supported Sable ships, including
  uncrewed ships, seated passengers and cargo. A held-frame transition masks loading; a short pause
  can remain.
- An orbital map with planet relief, object selection, coast forecasts, personal markers and timed
  maneuver plans. Ship instruments include a navball and an external camera.
- Distant planet relief from saved terrain, clouds, layered cliffs and an atmospheric halo.
  Unknown terrain uses an illustrative fallback; the overview does not generate missing chunks.
- Optional Propulsion flight control: hold, ascent, orbit, return and maneuver execution for the
  supported creative-engine layouts.

## Playing

Use **Java 21**, Minecraft **1.21.1**, and NeoForge **21.1.252 or later in the 21.1 series**.
Install the production Orbitalis JAR on the client and server. Kotlin is included.
Sable **2.0.6** is optional and enables construction physics and ship travel. Propulsion **1.1.5**
is optional and enables engine control with its separately installed dependencies.

Start with a **new world**. In **Create World → More → Earth size**, select the planet width;
1,000 blocks is convenient for visual testing. This setting also scales the Moon and system physics.
The terrain footprint is fixed for that world; `/worldborder` controls movement, not planet size.

Open the orbital map with **M**, switch ship camera with **F6**, and switch navball nose axes with
**F7**. All three bindings can be changed in Controls. Near the surface, ordinary Minecraft physics
remain; gravity and atmosphere transition during ascent. Space travel requires crossing the body's
approach envelope, rather than using a universal fixed altitude.

Read [installation and first-flight notes](docs/installation.md),
[the current changelog](docs/CHANGELOG.md), and [flight-control limits](docs/contributors/flight-control.md).
Back up saves before installing or updating this early version. Retired cube experiments are unsupported.

## Development

Build prerequisites: **JDK 25** for Gradle, **JDK 21** for target compilation, Python 3.11+, and Git.
On Windows, use `gradlew.bat` in place of `./gradlew`.

```sh
./gradlew verifyLocalFast
./gradlew verifyLocalFull
./gradlew :v1_21_1-neoforge:runClient
```

Standard development game runs install the pinned external Sable release separately. For the isolated
Create/Aeronautics/Propulsion flight stand, run `./gradlew runClient` from `consumers/flight`.
These stands and their fixture ships are development tools, not part of the published mod.
Read and accept Minecraft's EULA yourself before launching a dedicated server.

Contributor documentation: [development](docs/contributors/development.md),
[architecture](docs/contributors/architecture.md), [verification](docs/contributors/verification.md),
[flight stand](docs/contributors/flight-consumer.md), and [release record](docs/contributors/release-0.1.0.md).
Agent guidelines are in [AGENTS.md](AGENTS.md).

Original code is licensed under [Apache-2.0](LICENSE.md). See [NOTICE](NOTICE) and
[third-party attribution](licenses/README.md). Shared agent skills are optional contributor tooling;
see [setup](docs/contributors/development.md#agent-skills).
