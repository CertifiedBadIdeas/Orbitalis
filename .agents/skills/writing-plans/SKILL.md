---
name: writing-plans
description: Use when accepted design or concrete requirements need coordinated multi-step implementation before code changes begin. Do not use for small localized work.
---

# Writing Plans

Convert settled requirements into an executable implementation plan without reopening decisions or
inflating routine work.

## Establish the Planning Basis

Read applicable repository instructions, the accepted design or requirements, and enough relevant code
to make the plan concrete. If a material product, architecture, interface, or scope decision is still
open, use `brainstorming` before planning.

Do not create a plan for a small localized change that can proceed directly through `production-workflow`
and, when behavior changes, `testing-strategy`.

## Write the Plan

Follow repository instructions for location and publication. Otherwise keep temporary plans under
`.agents/tmp/plans/YYYY-MM-DD-<topic>.md` and do not commit them unless the user requests publication. If
the work is tracked by a roadmap issue, include its number or link.

Make the plan self-contained enough to resume later. Include:

- goal, scope, non-goals, constraints, and accepted decisions;
- ordered stages or tasks, naming exact files or areas and the responsibility of each change;
- dependencies, interfaces, migrations, compatibility, and documentation only where relevant;
- acceptance criteria and proportional verification commands or manual checks.

For changes to production behavior, use `testing-strategy` to plan proportional evidence and decide where
test-first adds value. Describe meaningful, verifiable deliverables rather than minute-by-minute edits.
Follow checkout preparation in [production-workflow](../production-workflow/SKILL.md), subject to explicit
user and repository instructions. For Orbitalis, include the appropriate `worktree-task` invocation and
explicit submodule selection when needed, followed by `worktree-task doctor --path <task-checkout>` before building.
Do not prescribe manual worktree creation or cache copying. Include authorized integration, verification
of the integrated commit, and `worktree-task finish <task-branch>` as completion steps. Name the actual
verification evidence to retain through `build/agent-logs` or `--evidence <path>`; include `--repo` when
finishing a submodule task. Do not require reviews or
delegation unless user or repository instructions do. For a large plan,
make meaningful independently verifiable stages the commit boundaries; do not create commits for mechanical
microsteps.

## Review and Handoff

Check that the plan covers the accepted scope, has a feasible order, exposes material dependencies and
risks, and contains no vague placeholders. Correct routine gaps directly; return to `brainstorming` only
for a genuinely material unresolved decision.

If the request already authorizes implementation and no material decision remains, continue with
`executing-plans` without asking for ceremonial approval. If the user requested only a plan, deliver it
and stop.
