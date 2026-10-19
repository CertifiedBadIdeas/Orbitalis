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

# Preparing Orbitalis 0.1.0

Status: local release preparation; not published or declared ready. No release date is assigned.
The selected CurseForge channel is **Alpha**, with mod version **0.1.0**.
The development branch stays at `0.1.0-SNAPSHOT` until final acceptance. Build a candidate using the
existing `-PmodVersion=0.1.0` override; no release-only branch of gameplay is maintained.

## Release contents

The player-facing scope is [the changelog](../CHANGELOG.md), with setup in
[installation](../installation.md). The initial target is Minecraft 1.21.1 / NeoForge 21.1.252+
within 21.1, on Java 21. Kotlin is nested. Sable 2.0.6 and Propulsion 1.1.5 are optional,
separately installed integrations. The Voxy preview is optional and is not the minimum install.
Development fixture functions and the consumer entry point are excluded from distribution.

## Candidate checks

Use an isolated task checkout and run `./worktree-task doctor` first:

```sh
./gradlew-sandbox-dev-parallel-summary -PmodVersion=0.1.0 verifyLocalFull :v1_21_1-neoforge:buildProductionJar
./gradlew-sandbox-dev-parallel-summary -PmodVersion=0.1.0 :v1_21_1-neoforge:runGameTestServer
./gradlew-sandbox-dev-parallel-summary -PmodVersion=0.1.0 -p consumers/flight check runGameTestServer
```

Inspect the real remapped archive under `modules/minecraft/v1_21_1/v1_21_1-neoforge/build/libs`,
not `developmentModJar`, a plain common JAR or a sources JAR. Preserve its exact filename, SHA-256,
source commit and command logs beside the candidate outside the temporary checkout. Archive verification
checks metadata, bytecode, bundled Kotlin and legal notices; it does not prove a normal installed game.
The full native suites exercise development classpaths, not a clean installed production archive.

## Acceptance still required before publication

- Install the exact hashed production JAR in a clean normal NeoForge client with optional mods absent;
  create a new world, open the map, visit space as an independent player, save, exit and reopen.
- Repeat in the pinned construction/engine stack: assemble a ship, launch, enter space, coast, return,
  verify cargo and a seated passenger, then save/reopen. Check control cutoff and camera continuity.
- Visit the Moon and inspect Earth/Moon relief, clouds and atmospheric edges at near and far scales.
  Check both the 1,000-block preview and 10,000-block default settings. Existing screenshots support
  visual review but do not establish acceptance of a newly hashed candidate.
- Run a dedicated server with the production JAR and join with real clients; verify optional-mod
  absence and the supported installed stack, player/mount synchronization, reconnect and transfer.
- Keep optional Voxy compatibility evidence separate from the base install. Record exact external
  versions, graphics hardware and any driver requirements observed during manual review.
- Use the selected **Alpha** channel on CurseForge and complete project-page links, description,
  screenshots, dependency relationships and known limitations. Use original game screenshots.
- Freeze the final version/changelog only after acceptance. Any later code change needs a rebuilt
  candidate and checks appropriate to its scope. Tagging, pushing and uploading need explicit authorization.

Do not label the candidate release-ready while these checks remain pending. Record concrete failures
and missing evidence, rather than representing build success as completed player acceptance.

## CurseForge description draft

Orbitalis brings orbital flight to finite Minecraft worlds. Choose the size of your Earth, build on
ordinary Minecraft terrain, then fly into space and explore a cratered Moon. Open the 3D orbital map
to view planets and constructions, forecast a coast trajectory, and plan maneuvers.

With Sable installed, constructions can travel between the surface and space with their passengers
and cargo. The optional Propulsion adapter provides flight guidance for supported Creative engine
layouts. Neither external mod is bundled.

This is an **early Alpha** for **Minecraft 1.21.1 / NeoForge**, requiring Java 21. Start a new world
and back up saves. Ship transfers have size and passenger limits; autopilot is currently a Creative
operator tool. Physical solar lighting, survival fuel progression, terrain avoidance and lunar landing
autopilot are not implemented. World changes can pause briefly, and unexplored terrain uses a visual
fallback in the distant planet model.

The minimum install does not require Voxy. Advanced Voxy/WorldGenV2 compatibility is a separately
installed, version-pinned preview stack. See the installation guide for details.
