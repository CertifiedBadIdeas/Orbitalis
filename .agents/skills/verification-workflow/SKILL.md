---
name: verification-workflow
description: Use before committing, claiming a change is complete or fixed, or reporting checks as passing. Require fresh evidence with scope proportional to the current stage and full repository verification at the end of a multi-stage plan.
---

# Verification Workflow

Match every completion or correctness claim to fresh observable evidence. Do not substitute confidence,
an earlier run, or the existence of code for verification.

## Choose the Evidence Scope

- **Meaningful implementation stage:** inspect the scoped diff and run the narrowest checks that exercise
  the changed behavior, contract, or artifact and its immediate dependencies. Do not require the full
  repository suite for every intermediate commit unless repository policy or risk does.
- **Localized completed task:** run proportional focused checks, including lint, build, schema validation,
  artifact inspection, tests, or manual checks as appropriate.
- **Bug fix:** re-run the original reproduction and relevant regression checks. Use `testing-strategy` to
  decide whether an automated regression test provides value.
- **Completed multi-stage plan:** after all stage commits, run the repository-defined full verification.
  If none is defined, use the broadest practical checks covering the changed system boundaries.

Do not require red-green history, delete working code, or temporarily revert a fix merely to validate a
test. Test timing and value belong to `testing-strategy`. A passing test proves the current behavior; make
stronger claims about regression sensitivity only when the available evidence supports them.

## Verify That the Evidence Ran

An aggregate command proves only the checks in its actual task graph, not the checks suggested by its name.
Before relying on a new or changed aggregate, inspect its dependencies or execution report and confirm that
every required scenario ran against the intended inputs. A missing artifact, filtered or zero-test run,
silent early return, or skipped mandatory scenario is failed evidence even when the command exits successfully.
Cached or up-to-date results are acceptable only when their declared inputs cover the current change.

## Apply the Gate

1. State internally what is about to be claimed or committed and which fresh evidence would prove it.
2. Run that evidence after the last relevant change. Read the exit status and the meaningful output; do
   not infer success from partial or truncated results.
3. Inspect the diff and repository status so the commit contains only intended files and no user changes.
   Before committing, verify effective author and committer with `git var GIT_AUTHOR_IDENT` and
   `git var GIT_COMMITTER_IDENT` in this checkout, matching the user's established repository identity.
   Check the resulting commit's recorded author and committer; setting `--author` alone is insufficient.
4. If verification fails or contradicts the intended behavior, do not claim success or commit the broken
   stage. Use `debugging-strategy` when investigation is required.
5. If a required check cannot run, report what remains unverified and why. Do not describe the broader
   result as passing.

Repository and user instructions override verification scope and completion defaults. After final
verification succeeds, create any necessary focused fix commit and apply the authorized completion policy
in [production-workflow](../production-workflow/SKILL.md), subject to AGENTS.md.
There is no automatic merge authorization. After authorized integration, retain evidence with
worktree-task finish. Never bypass its preservation checks or push/open PRs without authorization.
