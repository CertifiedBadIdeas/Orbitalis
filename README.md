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
The repository contains a development scaffold and an independent finite-planet mathematics experiment;
in-game mechanics and 26.1 support are not implemented yet.

The development workflow and module boundaries are adapted from Compukters.

## Getting started

Prerequisites: JDK 25 for Gradle, JDK 21 for target compilation, Python 3.11+, Git, GNU cp with
reflink support, and flock.

```sh
export JAVA_HOME=/path/to/jdk-25
./worktree-task doctor
./gradlew-sandbox-dev-parallel-summary verifyLocalFast
./gradlew-sandbox-dev-parallel-summary verifyLocalFull
./gradlew-sandbox-dev-parallel :v1_21_1-neoforge:runClient
```

Before the first dedicated development server launch, read and accept the Minecraft EULA in its
separate run/server/eula.txt. The scripts do not accept it on your behalf.

After the first commit, create isolated task checkouts:

```sh
./worktree-task feat/first-space-feature
```

To run the independent finite-planet experiment:

```sh
./gradlew-sandbox-dev-parallel-summary :core:test :core:runPlanetExperiment
```

The task writes a trajectory CSV and measured report under modules/common/core/build/experiments/planet.
See [orbital flight feasibility](docs/contributors/orbital-flight-feasibility.md) for assumptions,
limitations and the remaining Minecraft/Sable experiments.

Contributor documentation: [development](docs/contributors/development.md),
[architecture and migration to 26.1](docs/contributors/architecture.md),
[verification](docs/contributors/verification.md), and [import provenance](docs/contributors/bootstrap-provenance.md).
Agent guidelines are in [AGENTS.md](AGENTS.md). Licensing is covered by [LICENSE.md](LICENSE.md),
[NOTICE](NOTICE), and the [third-party inventory](licenses/README.md).

Shared agent skills are installed separately from [ai-skills](https://github.com/lazyhat/ai-skills);
see the [contributor setup instructions](docs/contributors/development.md#agent-skills).
Only project-specific `orbitalis-*` skills are stored here. Installing shared skills is optional
and is not required to build or contribute.
