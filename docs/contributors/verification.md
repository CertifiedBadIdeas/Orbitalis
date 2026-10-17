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
production archive inspection. Core tests cover finite rectangular-body potential, the conservative surface layer, numerical low
orbits, spherical/two-body fields and flight/frame math. This is mathematical coverage, not evidence of Minecraft/Sable gameplay. NO-SOURCE is not coverage.
Run `:v1_21_1-neoforge:runGameTestServer` separately. The default `all` suite requires nine surface
border/persistence/preload/void-floor/player-motion scenarios, six finite-orbit/ship-transfer/retirement/travel scenarios and two navigation scenarios, with no
failed, skipped or error results in a fresh XML report. `-PorbitalisGameTestSuite=surface` runs the nine
surface cases alone; `-PorbitalisGameTestSuite=transfer` runs the five native transfer/recovery/retirement/travel cases
without the full-orbit performance cost. See [flat surface](flat-surface.md), [flight gravity](flight-gravity.md) and
[ship transfer](ship-transfer.md). Use `-PorbitalisGameTestSuite=flat` for the inert finite-body native orbit alone. The consumer separately
requires thirteen native flight scenarios; `-PorbitalisConsumerGameTestSuite=flat` runs its low-orbit
Overworld mission and bookmark migration cases; `rocket` runs both rocket round trips and native
steering/hold/RCS cases. Player tests execute native travel with a locally controlled server fixture,
including a vanilla jump comparison and vacuum momentum; they do not prove a real client handoff. See [finite-body flight](flat-gravity.md) for the new field and route.

A successful client launch cannot prove visual or interactive correctness. Record manual observations,
initial state, actions and expected outcome; mark unavailable evidence explicitly pending.

Archive checks inspect actual metadata expansion, identity/version, Minecraft range, duplicate entries,
required entry point/core class and space dimension/legal resources and rejection of retired dimension descriptors, absence of bundled
Minecraft/NeoForge, bundled Sable/Veil implementation or GameTest classes and test-only world resources, Java 21
project bytecode and nested Kotlin stdlib plus its separately packaged upstream/third-party legal files. Sources and upstream legal notices are retained.
When adding targets or libraries, extend packaging checks to their actual contracts. The import scan is
an early architecture guardrail; compilation and review remain necessary for transitive ownership.

Retain real command output in build/agent-logs. Never equate focused checks with complete release readiness.

The native GameTestServer always constructs fresh PrimaryLevelData, even when its run directory has
old chunks and SavedData. Both harnesses archive the disposable surface initialization marker before
each launch; this permits a fresh startup without deleting terrain caches. The persistence scenario
uses a new DimensionDataStorage reader to verify that a saved marker preserves customized settings.
Normal dedicated/integrated servers load their native border settings and do not run this fixture reset.

The `-PorbitalisGameTestSuite=travel` suite requires all three automatic travel scenarios: an
unmanned ship roundtrip plus interrupted committed recovery with no pilot, a seated survival player
in a plot-space minecart travelling both ways plus interrupted recovery and deferred login, and an
independent survival player preserving native velocity and mode in both directions. The unmanned
case includes a chest minecart, checks for dropped-item duplication, and reads its freshly flushed
native entity-region file to establish container persistence. The default suite includes these cases. It does not establish
client rendering or a real logged-in riding handshake.


The `-PorbitalisGameTestSuite=navigation` suite requires `nativecoastforecastandpacket` and
`navigationbookmarkpersistence`: real Sable coast agrees with the numerical forecast, physical/native
frame mapping and bounded packet roundtrips/rejection hold, and a personal surface marker survives a
native SavedData write/read. Both are also required by `all`. See [trajectory navigation](trajectory-navigation.md)
for the separate, still-required manual visual acceptance scenario.
