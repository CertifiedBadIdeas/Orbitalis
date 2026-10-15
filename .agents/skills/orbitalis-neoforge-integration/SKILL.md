---
name: orbitalis-neoforge-integration
description: Change Minecraft or NeoForge registration, lifecycle, networking, world persistence, resources or GameTests. Excludes independent domain logic.
---

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
