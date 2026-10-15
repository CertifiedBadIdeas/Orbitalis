---
name: brainstorming
description: Use when the user explicitly wants to explore or design an idea, or when implementation has unresolved product, architecture, interface, or scope decisions. Do not use for clearly specified localized changes.
---

# Brainstorming

Turn meaningful uncertainty into a clear design while preserving the user's authorization and momentum.

## Boundary

Use this skill when the user asks to brainstorm, compare approaches, or design something, or when a
requested implementation contains choices that could materially change architecture, public interfaces,
product behavior, or scope.

Skip it for clearly specified localized fixes, routine refactors, documentation or configuration edits,
and implementation whose important decisions are already settled. If hidden complexity introduces a
material design choice, pause the affected work and use this skill for that choice.

## Authorization

A direct request to add, implement, change, or fix something already authorizes implementation within
the stated scope. Do not require a second approval merely because you summarized the approach.

Ask for a decision before implementation only when:

- multiple plausible choices would produce materially different outcomes;
- the recommended design expands or redirects the requested scope; or
- applicable repository or user instructions require design review.

Treat a choice between a durable architectural correction and an expedient workaround as material when the workaround
would preserve incorrect ownership or boundaries, duplicate a source of truth, bypass an intended abstraction or
invariant, or special-case a symptom. Explain both options and ask the user; do not select the workaround by default.
A small but architecturally sound fix does not require this extra decision.

When the user explicitly asks only to explore, discuss, or design, deliver the design without assuming
authorization to implement it. A user's selection of a proposed option counts as approval of that option.

## Workflow

1. Read applicable repository instructions and only enough relevant context to understand the decision.
2. Ask questions whose answers could materially change the design. For architecture work, do not silently
   assume missing boundaries, constraints, scale, compatibility, operating model, or migration needs.
   When the solution space is broad, narrow it iteratively with one question or a small related group at
   a time. Infer only routine details and state consequential assumptions explicitly.
3. Offer multiple approaches only when they are genuinely distinct. Lead with the recommendation after
   enough context is known, and explain the consequential trade-offs. A preliminary recommendation may
   accompany questions when it helps the user answer, but label its assumptions.
4. Present a design proportional to the uncertainty. Cover boundaries, interfaces, data flow, failure
   behavior, and verification only where relevant.
5. Resolve remaining material decisions. If the original request authorized implementation, continue
   without a ceremonial confirmation. If it requested discussion only, stop with the design and offer
   implementation as a separate next step.

For architecturally significant work in a roadmap-tracked repository, use `using-github-roadmap` first
when repository policy requires it. For architecture-scale design, read
[references/production-design.md](references/production-design.md) and apply only the relevant concerns.
Write a durable specification only for architecture-scale work or when the user requests one; follow
repository instructions for its location and publication. Use `writing-plans` after an accepted design
when the implementation is sufficiently multi-step to benefit from a plan.
