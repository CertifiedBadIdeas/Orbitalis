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

# Changelog

## 0.1.0-SNAPSHOT — In development

- Add an independent flight consumer build for the current Orbitalis checkout with pinned Create, Sable, Aeronautics and Propulsion; isolate its dependencies/worlds and reject consumer implementations or dependencies in the production JAR.

- Add a native 3×3 rider platform, one-time boarding, short/orbital launches, support/drift telemetry and bounded airborne frame translation including its owned landing pad; distinguish declared-contact server fixtures from real client evidence.
- Add a bounded short-hop itinerary with hover and physical touchdown to the independent flight diagnostic.

- Add a real single-level Sable takeoff/orbit/return diagnostic, temporary terrain with safe cleanup, flight-phase measurements and native physical-landing tests at one/four substeps.
- Add a bounded, independent diagnostic flight controller for takeoff, orbital insertion, a measured unpowered revolution and return to a launch pad.

- Install pinned Sable automatically for standard development client/server/GameTest runs; remove the separate Gradle opt-in flag and duplicate CI profile.

- Add bounded prograde/retrograde Sable diagnostic manoeuvres, per-probe orbital apsides, burn-aware reference errors and native post-burn flight checks.
- Derive instantaneous bound-orbit periapsis and apoapsis in the independent two-body core, including escape and radial-fall handling.

- Add a physical Sable orbit diagnostic: central gravitational impulses evaluated at each construction’s centre of mass, independent trajectory/error measurements and complete-revolution tests across physics timesteps and frame shifts.

- Add optional Sable 2.0.6 diagnostics with isolated runtime installation, native impulse/coasting and same-level frame-shift scenarios, zero-drag laboratory settings and upstream license provenance; keep Sable binaries outside the Orbitalis JAR.

- Keep all production layers in Loom's single `main` development mod group; prevent client world-creation crashes caused by shared classes being assigned to a separate group.
- Add an opt-in Minecraft planet laboratory with six bounded chart pads, operator enter/sample/orbit/leave commands, transient return points and a server-ticked particle orbit.
- Add isolated headless laboratory GameTests, fresh XML result validation, development Kotlin runtime wiring and production checks against leaked test resources.
- Translate the remaining Russian README into English and document standard Gradle commands and the planet experiment entry point; keep agent-specific checkout/build helpers in contributor guides.
- Add an independent finite-planet coordinate and two-body trajectory experiment, behavioral tests and a repeatable CSV/report task; no Minecraft/Sable flight implementation yet.
- Record the exploration-first orbital flight direction, Sable source findings and surface/orbit feasibility experiments.
- Initialize the Minecraft 1.21.1 / NeoForge development scaffold with independent Kotlin core and shared Minecraft layers.
- Add isolated task worktrees, reflink Gradle caches, managed build scheduling, complete build logs and tooling verification.
- Establish project-specific local agent workflows, optional shared ai-skills setup, version migration guidance and full Apache-2.0 source headers, attribution and packaging rules.
- Add a GitHub verification workflow with retained build logs and artifacts.
- Complete Apache-2.0 copyright headers in project documentation, local skills and editor/ignore configuration.
- Identify Vsevolod Petrov (lazyhat) as the copyright holder in original Orbitalis files and NOTICE.
