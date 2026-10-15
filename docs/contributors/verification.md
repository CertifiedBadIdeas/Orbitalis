<!--
The Orbitalis Developers

Copyright 2026 The Compukters Developers
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

# Verification scope

| Changed boundary | Evidence |
| --- | --- |
| Gradle wrappers/cache/worktrees | python3 tools/verify.py tooling; real subprocess, locks, reflink and local Git fixtures |
| Module/source ownership | python3 tools/verify.py architecture; compile affected modules |
| Independent domain logic | :core:test with meaningful scenarios once behavior exists |
| Minecraft/NeoForge adapters | Target compile/check plus focused tests; real GameTests for world lifecycle and ticks once a harness exists |
| UI/assets | Focused build plus an observed bounded client scenario in a disposable world |
| Metadata/archive/license | :v1_21_1-neoforge:verifyProductionJar |
| Coordinated bootstrap/build change | verifyLocalFull |
| Release candidate | verifyLocalFull, relevant real gameplay/server checks, exact archive hash and revision |

Use ./gradlew-sandbox-dev-parallel-summary for noninteractive Gradle commands. verifyLocalFast runs
verifyTooling, verifyArchitecture and :core:check. verifyLocalFull adds common/check, loader/check and
production archive inspection. Core tests cover the experimental finite-planet coordinates and two-body
trajectories; `:core:runPlanetExperiment` writes a measured reference flight CSV/report. This is mathematical
coverage, not evidence of a Minecraft/Sable flight. NO-SOURCE is not domain coverage.
Run `:v1_21_1-neoforge:runGameTestServer` separately for real laboratory GameTests. `verifyLocalFull`
compiles the isolated tests and checks run visibility, but does not start the server. The GameTest run
requires a fresh XML report containing all laboratory scenarios without failed, skipped or error results;
missing/empty reports fail even when the launcher exits successfully. See [planet-laboratory.md](planet-laboratory.md).
The standard GameTest run includes Sable and requires all fifteen scenarios, including eight real Rapier
scenarios. CI retains the resulting `results.xml` report. See [sable-laboratory.md](sable-laboratory.md), [flight gravity](flight-gravity.md) and [ship transfer](ship-transfer.md).
A successful client launch cannot prove visual or interactive correctness. Record manual observations,
initial state, actions and expected outcome; mark unavailable evidence explicitly pending.

Archive checks inspect actual metadata expansion, identity/version, Minecraft range, duplicate entries,
required entry point/core class and laboratory dimension/legal resources, absence of bundled
Minecraft/NeoForge, bundled Sable/Veil implementation or GameTest classes and test-only world resources, Java 21
project bytecode and nested Kotlin stdlib plus its separately packaged upstream/third-party legal files. Sources and upstream legal notices are retained.
When adding targets or libraries, extend packaging checks to their actual contracts. The import scan is
an early architecture guardrail; compilation and review remain necessary for transitive ownership.

Retain real command output in build/agent-logs. Never equate focused checks with complete release readiness.
