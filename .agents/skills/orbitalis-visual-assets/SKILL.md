---
name: orbitalis-visual-assets
description: Iterate on Orbitalis textures, block models, sounds or other subjective game assets with provenance and an isolated client preview.
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

# Visual assets and provenance

Read licenses/README.md before importing material; code licensing does not establish texture/font rights.
Use native JSON/vector tools for code/model geometry, imagegen only when raster generation is useful.
Keep edits in the task worktree and use the owning target's runClient in a disposable preview world.
The Compukters addon visual-preview helper is not installed here and is not an Orbitalis command.
Build resources first, compare relevant states/orientations, lighting, item view and scale, and retain
screenshots plus the reviewed revision. Resource-only F3+T reload can support iteration; removed files
and code changes require rebuilding with the client stopped. Stop managed clients before another build.
Present subjective appearance for user review before integration unless the user waives review.
For Minecraft, prepare a live client handoff: launch the owning task's client, open the preview world,
navigate to the affected location and set a useful camera angle. Use the consumer module's own wrapper
when the scenario needs that stand. Verify the requested scene is visible, then leave the client open
for the user and briefly identify the window, what to inspect and any controls needed for comparison.
Screenshots supplement this handoff; they replace it only when the user explicitly chooses that format.
Complete build checks before the final preview launch so the prepared client can remain open while
awaiting review. Once handed over, coordinate further input or closure with the user; do not close the
preview merely to finish the task. If a live preview cannot be prepared, explain the concrete blocker
and keep visual acceptance pending. Preserve worlds and never infer approval from a successful launch,
screenshots or silence. Add source, license and attribution for each imported asset.
