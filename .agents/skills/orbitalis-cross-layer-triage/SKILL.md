---
name: orbitalis-cross-layer-triage
description: Investigate failures whose owner is unclear across domain logic, Minecraft, loader lifecycle, resources or packaging. Stop using it once ownership is known.
---

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

# Locate the owning boundary

Collect a focused reproduction plus exact target, revision, Java runtime, command and full log.
When installed, debugging-strategy from ai-skills can guide the investigation; otherwise follow
AGENTS.md and the verification guide directly.
Trace domain state -> Minecraft adapter -> loader/event -> resource/archive.
Compare dev execution with the packaged artifact when symptoms differ by launch mode.
Determine whether the failed invariant belongs to simulation, serialization, lifecycle, client/server
side, version compatibility or packaging. Fix it at that owner; do not duplicate a workaround in leaves.
Select the corresponding local integration, contract, UI or release skill only after localization.
Never infer gameplay correctness from compilation or replace missing observations with assumptions.
