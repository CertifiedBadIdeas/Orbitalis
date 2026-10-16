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
| Consumer Propulsion control | Consumer `check` and `runGameTestServer`; actual native engine output, motion and cutoff; manual pilot/command checks remain separate |
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
The standard GameTest run includes Sable and requires nineteen scenarios: ten cubic-player regressions, five native cube/Sable scenarios and four retained
flight/orbit/transfer scenarios. CI retains the resulting `results.xml` report. See [sable-laboratory.md](sable-laboratory.md), [flight gravity](flight-gravity.md) and [ship transfer](ship-transfer.md).
For player movement iterations, select `-PorbitalisGameTestSuite=cube` with the same `runGameTestServer`
task. This runs fifteen required cube scenarios and validates their fresh XML report; it does not establish
flight-space orbit or transfer coverage. The default `all` suite retains all scenarios and required-name checks.
A successful client launch cannot prove visual or interactive correctness. Record manual observations,
initial state, actions and expected outcome; mark unavailable evidence explicitly pending.

Archive checks inspect actual metadata expansion, identity/version, Minecraft range, duplicate entries,
required entry point/core class and laboratory dimension/legal resources, absence of bundled
Minecraft/NeoForge, bundled Sable/Veil implementation or GameTest classes and test-only world resources, Java 21
project bytecode and nested Kotlin stdlib plus its separately packaged upstream/third-party legal files. Sources and upstream legal notices are retained.
When adding targets or libraries, extend packaging checks to their actual contracts. The import scan is
an early architecture guardrail; compilation and review remain necessary for transitive ownership.

Retain real command output in build/agent-logs. Never equate focused checks with complete release readiness.

## Experimental planet neighbour hooks

The opt-in seam GameTest uses all 24 directed adjacent-face pairs, reciprocal native addresses and
centre/corner/interior samples. It verifies ordinary placement/removal powering a native lamp, actual
hopper/dropper transfers, item conservation through native chunk eviction/restoration and native furnace
sides above the reference cube. The consumer additionally exercises native hoppers feeding foreign
Create vault capabilities. Separate native motor and pump scenarios exercise shafts across all 24 directed
face pairs, small cog and gearbox transmission, reversal, break/reconnection, fluid conservation and a
native pipe/tank column's cold restoration. Both graphs include authored blocks continuing inside another
face's geometric sector and connections above the cube away from its edges. The empty test dimension
must be kept awake on every tick for scenarios exceeding Minecraft's idle timeout. These are server checks;
they do not prove cross-chart multiblocks, arbitrary/custom transmissions or client shape/rendering. The seam switch
must remain explicit until those boundaries are separately validated.

Observed on 2026-10-16: `gradle-20261016T162315Z-653065.log` passed `verifyLocalFull` and all fifteen
cube GameTests. The seam scenario reported 24 directed face pairs, 62 additional interior/corner
address checks, lamp notifications, native inventory transfers, cold reconnection and sided furnace
acceptance/rejection. The independent `seam-create-consumer.log` run passed its five face scenarios,
including two native hoppers feeding foreign Create vaults with conserved item counts. These observations
do not establish interactive/client correctness or a general performance budget.

Observed for the Create extension on 2026-10-16: `create-seam-live-init.log` passed consumer `check` and
all seven `faces` GameTests. Earlier bounded iterations exposed an invalid retained capability provider
on pipe removal and a missing native pressure discovery after restoration; the final scenario exercises
both with continuous dimension ticking. Shaft checks include 24 directed transitions plus controls,
five blocks of continuation inside another geometric face, small cogs and a gearbox. Fluid checks use
four real motor-driven circuits; no test-written pump speed or substitute transfer buffer is involved.
Client presentation and large-planet performance remain unmeasured.

Observed for closed networks on 2026-10-16: `network-loops-native-dispatch.log` passed consumer `check`
and all nine `faces` scenarios. The two new 168-cell rings verify reciprocal physical adjacency and
coordinate/axis closure through all six native charts, five-block foreign continuation, branches,
alternate paths, reconnection and native chart-group restoration. The kinetic ring also checks signed
speed, reversal, source cutoff and exact native membership without removed entities. The fluid ring
checks a total of 4000 mB every tick and rejects a sink isolated by two solid plugs. Its long circuit uses
a temporary native Create pump range of 512; default gameplay configuration is unchanged. Fixture setup
explicitly closes unused pipe ports before flow. The reload harness uses native `processChanges` to
consume holding-chunk ready entries; calling the lower-level loader directly left entries scheduled for
a duplicate load on the following tick. The final run has no duplicate-plot loading failures. This is
same-process native save/unload/load evidence, not a full server restart, client placement/rendering or
planet-size performance claim.
