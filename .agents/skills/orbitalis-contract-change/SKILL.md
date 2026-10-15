---
name: orbitalis-contract-change
description: Change persisted world data, network schemas, stable identifiers or public integration interfaces. Excludes internal refactors behind unchanged contracts.
---

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
