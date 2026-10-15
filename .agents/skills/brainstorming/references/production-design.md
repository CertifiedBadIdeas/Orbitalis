# Production Design Concerns

Use this reference for architecture-scale work. Select concerns that can change the design; do not add
empty sections or boilerplate for irrelevant topics.

## Intent and Boundaries

- State the problem, desired outcome, scope, and meaningful non-goals.
- Record constraints, compatibility requirements, and assumptions that affect the solution.
- Define ownership and boundaries between components, services, modules, or teams.

## Behavior and Interfaces

- Describe the important control and data flows, including data ownership and lifecycle.
- Specify public interfaces, contracts, invariants, and compatibility behavior.
- Cover failure modes, timeouts, retries, partial completion, recovery, and idempotency where applicable.

## Production Qualities

Evaluate these only when they can materially affect the design:

- security, privacy, trust boundaries, and sensitive-data handling;
- capacity, latency, resource limits, concurrency, and backpressure;
- observability needed to detect, diagnose, and measure the behavior;
- operational ownership, configuration, dependencies, and support burden.

## Delivery and Evidence

- Explain migration, rollout, compatibility windows, and rollback when existing state or consumers are
  affected.
- Identify consequential risks and unresolved decisions. Resolve decisions that block implementation.
- Define acceptance criteria and evidence: focused tests, integration checks, production signals, or
  manual validation.

Keep the result proportional. A design is ready when another engineer can plan and implement it without
inventing material product or architecture decisions.
