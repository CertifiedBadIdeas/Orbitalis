---
name: using-github-roadmap
description: Use when beginning architecturally significant work or capturing a durable idea in a repository configured with a GitHub Projects v2 roadmap.
---

# Using a GitHub Roadmap

Bind architecture-scale work to a precise GitHub issue without adding ceremony to small changes.

## Issue boundary

Require a roadmap issue when the work does one or more of the following:

- introduces a subsystem or a new cross-module capability;
- materially changes component boundaries, architecture, or a public API/ABI;
- needs a design specification or coordinated implementation stages;
- captures a durable idea the user wants tracked.

Do not require an issue for localized bug fixes, documentation changes, test-only changes,
mechanical refactors, routine chores, or read-only investigation. The user may explicitly require
or waive issue tracking for any task. If scope is initially unclear, inspect enough context to
classify it; do not stop small work merely to ask about tracking.

## Workflow

1. Read the applicable repository instructions and the repo's fenced `yaml roadmap` configuration.
   In Orbitalis this configuration lives in `.github/copilot-instructions.md`.
2. For qualifying work, list active roadmap issues and reuse one only when it matches the current
   deliverable. Otherwise create a focused issue and add it to the configured project.
3. Ask the user only when multiple plausible issues or materially different scopes require a real
   choice. Use the available user-input mechanism; do not depend on a named UI-specific tool.
4. Move the issue to `Now` before implementation. Keep its scope and acceptance criteria current
   when the design materially changes.
5. Put the issue number in architecture spec and plan filenames and add a clickable issue line below
   their title. Follow repository instructions for artifact location and whether they are committed.
6. Close the issue as `completed` and move it to `Done` only after the work is integrated and its
   acceptance criteria are verified. If integration or manual verification remains, leave it open
   and report the remaining step.

Use `gh` for GitHub and Projects v2 operations. Follow repository sandbox and authentication rules.
Do not substitute GitHub MCP or app tools when the repository requires the CLI.

## References

- Read [references/roadmap-operations.md](references/roadmap-operations.md) when selecting, creating,
  updating, relating, or closing issues.
- Read [references/issue-template.md](references/issue-template.md) only when creating or materially
  rewriting an issue.
