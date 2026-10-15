---
name: debugging-strategy
description: Use when investigating a bug, failing test or build, performance regression, or other unexpected technical behavior before implementing a fix.
---

# Debugging Strategy

Establish a defensible cause before changing behavior, with investigation proportional to the problem.

## Build the Evidence

State expected versus observed behavior and obtain the narrowest reliable reproduction. Read the complete
relevant error, inspect recent or local changes, and trace inputs and state across the boundaries that can
produce the symptom. Compare with a nearby working path when useful.

For a simple local defect, stop investigating once evidence identifies the cause and excludes plausible
alternatives. For intermittent, performance, concurrency, persistence, or multi-component failures,
gather enough logs, measurements, state, or boundary probes to locate the failure before editing
production code. Do not expose secrets or leave diagnostic instrumentation behind unintentionally.

When evidence is unavailable, say what is missing. Ask the user when obtaining it requires their manual
reproduction, environment, credentials, or a materially different investigation scope.

## Test a Hypothesis

Form a specific hypothesis that explains the evidence. Test the smallest useful consequence and vary one
material factor at a time. If it fails, discard or revise the hypothesis instead of accumulating speculative
fixes.

Repeated failed attempts, contradictory evidence, or fixes that move the symptom across boundaries are a
signal to reassess assumptions and architecture. Stop and involve the user when that reassessment would
change the accepted design or scope; do not use an arbitrary attempt count as proof of an architecture
failure.

## Fix and Verify

Fix the identified cause at the owning boundary and avoid unrelated cleanup. Use `testing-strategy` to
decide whether a failing regression test, integration reproduction, benchmark, build, or other evidence is
most valuable. A test is not mandatory when it would not protect meaningful behavior.

Re-run the original reproduction, focused affected checks, and proportional regression checks. Report
the causal evidence, the change, and any uncertainty that remains.
