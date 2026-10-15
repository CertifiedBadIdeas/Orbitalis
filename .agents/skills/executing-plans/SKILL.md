---
name: executing-plans
description: Use when an accepted written implementation plan is ready to execute or resume in the current repository.
---

# Executing Plans

Complete an accepted plan while adapting it to verified repository state.

## Resume from Evidence

Read applicable repository instructions, the plan, linked design or issue, and enough current state to
identify completed and remaining work. Reuse the worktree only for the same active task. For new
implementation, follow checkout preparation in [production-workflow](../production-workflow/SKILL.md),
including its `worktree-task` default for Orbitalis. Before the first build in a new or resumed checkout,
run `worktree-task doctor --path <task-checkout>` through the main workspace entrypoint. Address prerequisite
errors; a cold cache is informational. Update an older plan's manual worktree or cache-copy steps to the
current workflow. Preserve user changes and do not redo completed tasks.

Review the plan against reality before relying on it. Correct small stale paths, ordering details, and
other mechanical gaps in place. Stop dependent work and ask the user only when evidence reveals a
material design conflict, scope change, missing authority, or blocker. When the user reports making a
change and asks to continue, follow the verification and stopping rules in `production-workflow`.

## Execute to Completion

Implement all remaining tasks in dependency order. Keep plan status current when it helps later
resumption, but do not create arbitrary batch checkpoints or pause for routine deviations.

- For production behavior, use `testing-strategy`.
- For failures or unexpected behavior, use `debugging-strategy`.
- After each meaningful completed stage, use `verification-workflow` with focused checks and create a
  coherent commit before continuing.

After every planned stage is complete, run the repository's full verification. Resolve failures through
`debugging-strategy`; do not claim completion based only on the focused stage checks. Record checks that
cannot be run. Repository and user instructions override verification scope and completion defaults.
After the final verified commit, apply the authorized completion policy in
[production-workflow](../production-workflow/SKILL.md). When integration is authorized, completion includes local integration,
retaining verification evidence and removing the completed clean worktree through `worktree-task finish
<task-branch>` from the main workspace. Supply `--repo <submodule-path>` for submodule tasks and retain
real check output in `build/agent-logs` or supply `--evidence <path>`. Resolve a helper refusal while
preserving the checkout; do not replace it with manual force-removal.
Do not push or open a pull request without their own authorization.
