---
name: review-feedback
description: Use when evaluating and acting on code-review feedback, especially when comments may be ambiguous, incorrect, coupled, or inconsistent with repository decisions.
---

# Review Feedback

Turn review comments into technically justified changes without blind compliance or unnecessary ceremony.

## Evaluate

Read the complete relevant feedback and inspect the referenced code. Determine the requested outcome, its
technical premise, and whether it fits the repository's behavior, compatibility requirements, and accepted
design. External suggestions are evidence to evaluate, not authority to expand scope.

Ask the user when a comment is materially ambiguous, conflicts with their decisions, changes architecture
or scope, or cannot be validated with available evidence. Continue independent clear items while waiting
only when they do not depend on the unresolved decision. Repair formatting-only defects directly.

## Apply

Group related comments into coherent changes. Prioritize correctness, security, data loss, and broken
behavior before maintainability or style. Push back with concrete code or test evidence when a suggestion
is incorrect, unnecessary, or incompatible; do not add speculative infrastructure for unused behavior.

Use `testing-strategy` for behavior changes and `verification-workflow` for focused evidence before each
meaningful commit. Check that the result satisfies the substance of the comment without introducing
unrelated refactoring.

Reply on external review systems only when the user requested that external action. Keep responses factual:
state what changed, what evidence supports it, or why the suggestion should not be applied.
