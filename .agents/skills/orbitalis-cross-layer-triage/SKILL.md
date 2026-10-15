---
name: orbitalis-cross-layer-triage
description: Investigate failures whose owner is unclear across domain logic, Minecraft, loader lifecycle, resources or packaging. Stop using it once ownership is known.
---

# Locate the owning boundary

Use debugging-strategy and collect a focused reproduction plus exact target, revision, Java runtime,
command and full log. Trace domain state -> Minecraft adapter -> loader/event -> resource/archive.
Compare dev execution with the packaged artifact when symptoms differ by launch mode.
Determine whether the failed invariant belongs to simulation, serialization, lifecycle, client/server
side, version compatibility or packaging. Fix it at that owner; do not duplicate a workaround in leaves.
Select the corresponding local integration, contract, UI or release skill only after localization.
Never infer gameplay correctness from compilation or replace missing observations with assumptions.
