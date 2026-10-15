---
name: testing-strategy
description: Use when changing production behavior or deciding what evidence should protect code. Select proportional behavioral tests, preferring test-first where it adds confidence and avoiding tests of incidental text or structure.
---

# Testing Strategy

Choose evidence by behavior and risk. Test-first is a useful technique, not a universal ceremony.

## Decide What Deserves a Test

Before implementation, identify the observable behavior, likely failure modes, and cost of regression.
Add an automated test when it can catch a meaningful regression and targets a sufficiently stable
contract.

Do not add tests solely to prove that a file changed or to freeze documentation wording, formatting,
private implementation details, mock interactions, or generated content already verified at its source.
Exact text or file structure deserves a test only when it is itself a contract, such as a parser format,
serialized output, generated configuration consumed elsewhere, or user-visible text with strict
requirements. For ordinary documentation and configuration edits, prefer lint, schema validation,
builds, focused commands, or manual inspection.

## Choose the Development Mode

- **Bug fix:** reproduce the defect with a failing test before the fix when practical. The failure must
  demonstrate the reported behavior. If automation is disproportionately expensive or unreliable, use a
  focused reproduction and add a regression test only when it will provide durable value.
- **New domain logic or stable contract:** prefer a small behavior-first test, then implement and
  refactor.
- **Integration, adapter, UI, or infrastructure work:** choose the narrowest useful integration,
  contract, end-to-end, or smoke check. Test-first is optional when the harness does not provide useful
  feedback before a working slice exists.
- **Refactor:** establish confidence with existing tests. Add characterization tests only around unclear
  or risky behavior that must remain stable.
- **Prototype, generated code, documentation, formatting, or mechanical change:** do not create tests by
  default; verify the actual artifact or workflow instead.

If production code already exists, do not delete or rewrite it merely to recreate a test-first sequence.
Inspect it, identify uncovered risk, and add only valuable tests.

## Test and Verify Behavior

Prefer observable outcomes over internal calls, real collaborators over excessive mocking, and the
smallest test level that gives credible evidence. Avoid duplicating coverage across levels without a
specific risk justification.

When using test-first, observe the expected failure, implement the behavior, observe the pass, and keep
the relevant suite green. Otherwise record the chosen evidence and why an automated regression test was
not valuable. Run focused checks during implementation and proportional broader checks before
completion. Repository and user testing requirements take precedence.
