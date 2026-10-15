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

A space exploration mod for Minecraft, written in Kotlin. The current target is **1.21.1 / NeoForge**.
The repository contains an independent finite-planet mathematics experiment and an opt-in Minecraft
planet laboratory with Sable physics diagnostics. Seamless flight, cross-dimension ship
transfer and 26.1 support are not implemented yet.

The development workflow and module boundaries are adapted from Compukters.

## Getting started

Prerequisites: JDK 25 for Gradle, JDK 21 for target compilation, Python 3.11+, and Git.
On Windows, use `gradlew.bat` in place of `./gradlew`.

```sh
export JAVA_HOME=/path/to/jdk-25
./gradlew verifyLocalFast
./gradlew verifyLocalFull
./gradlew :v1_21_1-neoforge:runClient
```

Before the first dedicated development server launch, read and accept the Minecraft EULA in its
separate run/server/eula.txt. The scripts do not accept it on your behalf.

To run the independent finite-planet experiment:

```sh
./gradlew :core:test :core:runPlanetExperiment
```

The task writes a trajectory CSV and measured report under modules/common/core/build/experiments/planet.
See [orbital flight feasibility](docs/contributors/orbital-flight-feasibility.md) for assumptions,
limitations and the remaining Minecraft/Sable experiments.

In a disposable world with cheats enabled, use `/orbitalis lab enter`, `/orbitalis lab sample`,
`/orbitalis lab orbit`, and `/orbitalis lab leave` to try the laboratory. It contains six separate
surface charts and a particle orbit demonstration. See the [laboratory guide](docs/contributors/planet-laboratory.md)
for commands, limitations and server tests. The [Sable laboratory](docs/contributors/sable-laboratory.md)
adds real rigid-body probes, impulse/coasting measurements, central-gravity orbits, bounded prograde/retrograde manoeuvres
and a same-level frame shift. `/orbitalis lab sable flight` runs a one-construction takeoff,
unpowered orbital revolution and physical landing back on a small temporary pad.
The standard `./gradlew :v1_21_1-neoforge:runClient` command automatically installs the pinned
external Sable release in the client development run directory.

For an isolated flight development stand with Create, Aeronautics and Propulsion:

```sh
./gradlew -p consumers/flight runClient
```

This independent consumer builds the current Orbitalis checkout and owns its external mod stack and
separate worlds. See the [flight consumer guide](docs/contributors/flight-consumer.md).

Contributor documentation: [development](docs/contributors/development.md),
[architecture and migration to 26.1](docs/contributors/architecture.md),
[verification](docs/contributors/verification.md), and [import provenance](docs/contributors/bootstrap-provenance.md).
Agent guidelines are in [AGENTS.md](AGENTS.md). Licensing is covered by [LICENSE.md](LICENSE.md),
[NOTICE](NOTICE), and the [third-party inventory](licenses/README.md).

Shared agent skills are installed separately from [ai-skills](https://github.com/lazyhat/ai-skills);
see the [contributor setup instructions](docs/contributors/development.md#agent-skills).
Only project-specific `orbitalis-*` skills are stored here. Installing shared skills is optional
and is not required to build or contribute.
