---
name: orbitalis-neoforge-integration
description: Change Minecraft or NeoForge registration, lifecycle, networking, world persistence, resources or GameTests. Excludes independent domain logic.
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

# Orbitalis Minecraft integration

Read AGENTS.md, docs/contributors/architecture.md and the matching verification row.
Canonical Minecraft behavior belongs in shared/common, loader events and registration in shared/neoforge,
and version compatibility/packaging in the target leaves. Independent simulation belongs in core.
Determine physical side, logical authority, tick/thread and save/load lifecycle before editing.
Client classes must not load on a dedicated server. Keep blocking work off the server thread.
Validate packet bounds and IDs at receipt. Keep a single authoritative server model; do not duplicate
it in menus or networking adapters. Apply orbitalis-contract-change for durable data or public protocols.
Verify version-sensitive APIs against the pinned sources or official documentation.
Use unit tests for independent transforms/codecs; real GameTests for world/tick/persistence behavior
once the harness exists. A launch alone is not a passed scenario. Build and verify the actual JAR for
metadata/resources/packaging. Apply UI or visual skills only when those surfaces change.
