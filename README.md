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
New worlds use an ordinary flat Overworld bounded by a 2000×2000-block square centred at (0,0).
Minecraft and other mods keep native block coordinates and physics. Cube-planet experiments have
been retired. Separate Sable flight/space and journaled ship-transfer diagnostics remain available;
seamless Overworld-to-space travel and 26.1 support are future work.

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

Start a **new world** for the [finite flat surface](docs/contributors/flat-surface.md). The border is
initialized once; later `/worldborder` changes remain authoritative. Old cube save files are preserved,
but their runtime and commands have been removed. See [flight diagnostics](docs/contributors/flight-gravity.md)
and [ship transfers](docs/contributors/ship-transfer.md) for retained experiments.
The standard `./gradlew :v1_21_1-neoforge:runClient` command automatically installs the pinned
external Sable release in the client development run directory.

For an isolated flight development stand with Create, Aeronautics and Propulsion:

```sh
cd consumers/flight
./gradlew runClient
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
