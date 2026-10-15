---
name: orbitalis-visual-assets
description: Iterate on Orbitalis textures, block models, sounds or other subjective game assets with provenance and an isolated client preview.
---

# Visual assets and provenance

Read licenses/README.md before importing material; code licensing does not establish texture/font rights.
Use native JSON/vector tools for code/model geometry, imagegen only when raster generation is useful.
Keep edits in the task worktree and use the owning target's runClient in a disposable preview world.
The Compukters addon visual-preview helper is not installed here and is not an Orbitalis command.
Build resources first, compare relevant states/orientations, lighting, item view and scale, and retain
screenshots plus the reviewed revision. Resource-only F3+T reload can support iteration; removed files
and code changes require rebuilding with the client stopped. Stop managed clients before another build.
Present subjective appearance for user review before integration unless the user waives review; do all
build/preview work before asking. Preserve worlds and never invent approval. Record unavailable visual
evidence as pending. Add source, license and attribution for each imported asset.
