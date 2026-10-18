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
Run `:v1_21_1-neoforge:runGameTestServer` separately. The focused `terrain` suite checks four native partially intersecting corners, exterior empty/full/light
completion, void biomes and heightmaps, feature/entity rejection and ordinary runtime writes.
The default `all` suite also includes these two scenarios and requires four surface-atlas sampling/persistence/backfill/packet scenarios, nine surface
border/persistence/preload/void-floor/player-motion scenarios, six finite-orbit/ship-transfer/retirement/travel scenarios, two navigation scenarios, five sparse-vacuum scenarios and two unloaded-orbital-object scenarios, with no
failed, skipped or error results in a fresh XML report. `-PorbitalisGameTestSuite=surface` runs the nine
surface cases alone; `-PorbitalisGameTestSuite=transfer` runs the five native transfer/recovery/retirement/travel cases
without the full-orbit performance cost. See [flat surface](flat-surface.md), [flight gravity](flight-gravity.md) and
[ship transfer](ship-transfer.md). Use `-PorbitalisGameTestSuite=flat` for the inert finite-body native orbit alone. The consumer separately
requires fifteen native flight scenarios, including remote multi-ship commands without added loading tickets; `-PorbitalisConsumerGameTestSuite=flat` runs its low-orbit
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

Both Gradle GameTest harnesses use a unique owned directory under the launching project's
`build/agent-logs/gametest-runs/<run-id>`. Each invocation starts with fresh PrimaryLevelData,
chunks and SavedData; persistence/reload scenarios still run within that invocation. The finalizer
records region bytes and chunk counts in `metrics.json`, and retains logs and a copy of the XML.
It deletes that run's `world` and regenerable `mods`/`.sable` runtime copies only after normal native
process exit and successful report validation; logs, traces and configuration remain available.
Failed/interrupted worlds remain for diagnosis; dependency failures never adopt existing directories.
Legacy `run/gameTestServer/world` directories and interactive saves are untouched. A forcibly terminated
Gradle process may not run its finalizer; its owned directory remains intact. Retained failed runs can
be removed explicitly after diagnosis, while no server is using them. Do not run the GameTest main
class directly from an IDE: use the Gradle task so ownership and finalization execute.

The flight consumer's GameTest-only event subscriber sets server view and simulation distances to
two chunks. The inert pilot already requests a two-chunk client view; that alone does not constrain
server simulation tickets. Native chunk/entity ticking and saves remain enabled. It samples each
level's peak loaded chunk count and records loaded chunks, remaining players, active sublevels and
force-loaded sublevels at shutdown in
`server-stopping.json`; the finalizer includes these plus shutdown wall time in `metrics.json`.
Ordinary consumer clients and production servers keep their own distance settings. Sable's collision
manager also loads terrain along force-loaded ships in the Overworld and legacy terrain dimensions.
New objects-mode orbital worlds disable those terrain tickets; see [sparse orbital world](sparse-orbital-world.md).
The occupied-plot fixture releases its reservation body and force-load ticket immediately after the
outbound relocation assertion; it must not continue falling or trigger unrelated automatic travel
while the real rocket completes its mission.

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

The `-PorbitalisGameTestSuite=sparse` suite checks native terrain-ticket control versus vacuum
flight, object/object collision, actual region-worker persistence and exclusion, construction ownership,
and conservative SavedData compatibility. All five cases are required by `all`. See
[sparse orbital world](sparse-orbital-world.md) for the exact storage boundary and legacy policy.

Automatic travel fixtures pump a bounded number of native chunk-completion tasks per tick and
yield to IO workers. GameTestServer omits the ordinary server's inter-tick wait; its tick count is
not a wall-clock IO budget. The transfer scenarios retain a bounded timeout while allowing native
readiness and checkpoint futures to finish without relying on incidental Sable terrain loads.

Consumer gravity and RCS fixtures populate an already allocated native Sable plot in objects-mode
space. They do not place static terrain blocks into vacuum. Surface assembly cases still execute the
actual player blueprint, Create glue and native physics assembler; full missions cover dimension
handoffs, modded block entities, cargo and seated riders. The reconnected return mission is checked
for engine-off coast, attitude and energy preservation throughout its background planning phase.

The `-PorbitalisGameTestSuite=atlas` suite requires `nativesurfaceatlaspersistence`, `surfaceatlaspacketbounds`,
`savedsurfaceatlasbackfill` and `surfaceatlaslifecycleburst`: real native height/map colours and terrain updates, disk SavedData
reload, unknown-version preservation, malformed records and bounded packet roundtrips/rejection.
Both are included in `all`. Client framebuffer composition and relief appearance require the separate
manual scenario in [space presentation](space-visuals.md).

The `-PorbitalisGameTestSuite=objects` suite requires `nativeunloadedstation` and
`orbitalmetadatacompatibility`, also included by `all`. It checks native unloading without a terrain
trail, numerical motion, fresh disk reads of both motion index and native payload, exact velocity and
cargo restoration, plus conservative schema handling. See [unloaded stations](unloaded-orbital-objects.md)
for the distinction between storage-boundary coverage and an observed second-process restart.
### Finite terrain runtime evidence (2026-10-18)

An isolated flight consumer with the pinned Voxy/WorldGenV2 stack created a new ordinary noise-based
Overworld. A server-thread probe checked two opposite partially intersecting corner chunks and four
entirely exterior chunks: all 147,456 inspected outside block positions were air, with interior terrain
preserved. A spectator preview showed the terrain ending at both border planes. Runtime probes against
the transformed WorldGenV2 graph checked exterior pruning, interior/edge work and unaffected unscoped
search. Its transformed manager filtered a 16-chunk edge batch to one intersecting chunk, rejected a
wholly exterior batch and retained unscoped batch identity. Probe sources, results, screenshot and the
normal save/quit client log are retained in task verification evidence; they are not production hooks.
This observation covers the pinned stack and inspected locations, not arbitrary generators that bypass
native chunk stages or direct-write outside their generation region.
