---
name: orbitalis-contract-change
description: Change persisted world data, network schemas, stable identifiers or public integration interfaces. Excludes internal refactors behind unchanged contracts.
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

# Orbitalis contracts

Identify the source of truth, producer and every reader before editing. Decide compatibility,
versioning and migration explicitly. Keep resource/dimension IDs stable unless intentionally migrating.
Put loader-independent models in core; Minecraft/NBT/network adapters belong to the integration layers.
For persistence cover valid round trips, missing/old versions, malformed bounds and migration outcomes.
For networking cover receiver validation, wrong versions, side/thread authority and request lifecycle.
Do not introduce VM ABIs or native runtime ownership from Compukters: this repository has none.
Update architecture/reference documentation with the changed contract and verify both sides.
Use the configured roadmap only when this repository has one; absent GitHub setup is not a reason to
create issues in Compukters or stop authorized local work.
