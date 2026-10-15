---
name: production-workflow
description: Use when starting or resuming changes in a software repository, including implementation, bug fixes, refactors, plans, documentation, configuration, or when the user reports making a change and asks to continue.
---

# Production Workflow

Route repository work through the relevant specialist skills while preserving momentum, user changes,
and repository policy.

## Establish State

Read applicable repository instructions and inspect enough current state to identify the next incomplete
step. Preserve user changes and do not redo completed work. A direct request to implement or fix something
authorizes ordinary in-scope changes; ask only when a material choice, missing authority, or conflicting
state prevents safe progress.

## Isolate Implementation Work

For repository changes, use a separate Git worktree and task branch by default. Read-only investigation
can stay in the current checkout. Reuse an existing worktree for the same task rather than creating
another one. Record the starting commit, preserve the original checkout and its user changes, and keep
all task edits, checks, and commits in the task worktree. Initialize required submodules at the revisions
pinned by that checkout; do not copy another agent's uncommitted state. Follow repository conventions
for worktree locations. Merge only with current or standing user authorization; do not push or remove
the worktree without authorization.

Explicit user instructions and repository checkout rules override this default. A repository instruction
to stay in the current checkout applies unless the user explicitly requests a worktree for this task.

## Orbitalis Checkout Preparation

Follow AGENTS.md and docs/contributors/development.md. After the first commit use
`./worktree-task <branch>` and run doctor in the resulting task checkout. During the unborn
bootstrap preserve the original index and work in the current checkout. Use managed sandbox
wrappers and keep independent reflink caches. Finish only after authorized integration and checks.
No standing Compukters merge authorization applies to Orbitalis.

## Verify Git Identity

Before the first task commit, and again after creating or switching a worktree or checkout, inspect
`git config --show-origin --get user.name`, `git config --show-origin --get user.email`,
`git var GIT_AUTHOR_IDENT`, and `git var GIT_COMMITTER_IDENT` in the actual working checkout. Environment
variables can override configured identity. Use the user's established repository identity for both author
and committer; do not invent an agent identity or rely on `--author` alone. Resolve unexpected differences
before committing, without changing global Git configuration. Repeat this check for separately committed
submodules and verify the recorded author/committer after the commit.

## Preserve Architectural Integrity

Do not silently trade architectural correctness for speed. A workaround is not an acceptable default when it knowingly
preserves incorrect ownership or component boundaries, duplicates a source of truth, bypasses an intended abstraction
or invariant, or special-cases a symptom while leaving its structural cause in place.

When a task presents a real choice between a durable architectural correction and a quicker compromise of that kind,
stop before implementation. Explain the durable option, the expedient option, and their concrete scope and
consequences, then ask the user which direction to take. Do not implement the compromise without explicit user
approval, even when it is smaller or reversible. This gate does not apply merely because a correct localized fix is
small; use it when speed and architectural integrity actually conflict.

## User-Reported Changes

When the user says they changed, fixed, installed, merged, configured, or verified something and asks to
continue:

1. Treat the report as a state transition to verify, not as work to repeat.
2. Inspect the narrowest relevant evidence: file content or diff, effective configuration, dependency
   version and lockfile, focused reproduction or test, build artifact, or remote state through the
   repository-approved tool.
3. If evidence matches the expected state, continue from the next incomplete step.
4. If the only defect is formatting with no semantic effect, repair it and continue without asking.
5. If the change is missing, functionally incorrect, semantically different from the agreed model, or
   invalidates the current plan, stop dependent work. Report expected versus observed evidence and ask
   the user how to proceed.
6. If objective verification is unavailable, state what remains unverified. Continue only when the
   assumption is low-risk; otherwise request the necessary evidence or access.

Do not second-guess subjective manual results without contrary evidence. Verify any related machine state
that is accessible.

## Route the Work

- For a bug, failing test, build failure, performance regression, or unexpected behavior, use
  `debugging-strategy` before proposing or implementing a fix.
- For explicit ideation or unresolved product, architecture, public-interface, or scope decisions, use
  `brainstorming`. For architecturally significant work in a roadmap-tracked repository, first use
  `using-github-roadmap` as required by repository policy, then continue with `brainstorming`.
- For an accepted design or requirements that need coordinated implementation stages, use
  `writing-plans`. When resuming an existing plan, use `executing-plans`.
- Before changing production behavior, use `testing-strategy` to choose proportional evidence and whether
  test-first is valuable. Documentation, formatting, and non-executable configuration normally use direct
  validation rather than automated tests unless repository policy says otherwise.
- When acting on code-review feedback, use `review-feedback`.

Before claiming completion, committing, or handing work back, use
`verification-workflow`. For a localized task, run proportional checks, create one
focused commit, and follow AGENTS.md for completion. For a multi-stage plan, run focused
verification and create a coherent commit
after each completed stage; after every stage is complete, run the repository's full verification and
apply any authorized completion policy below. Otherwise stop after verification. Do not push or open a
pull request unless the user explicitly requests it; a local merge does not authorize either action.

Repository and user instructions override workflow preferences, including artifact locations, issue
tracking, worktrees, delegation, commits, remote operations, and verification commands.
