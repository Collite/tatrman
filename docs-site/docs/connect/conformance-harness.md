<!-- SPDX-License-Identifier: Apache-2.0 -->
# The conformance suite as your test harness

*How-to. Use the same conversation fixtures the platform gates on to prove your own agent consumes
Tatrman correctly.*

The MCP surface ships with an executable test: a suite of declarative **conversation fixtures** that
assert the observable path — which tools were called, what the governed result was, and what
provenance came back — against a reference model. The platform's own reference agent must pass it;
so can yours.

!!! note "Availability"
    The suite and its reference runner ship with the standard (the tatrman repo, beside the surface
    contract). Fixture-schema finalization and the hand-authored core tier land in **SV-P4·S5**;
    until then this page describes the shape you will run against.

## What a fixture asserts

A fixture pins one conversation turn and asserts what is *observable*, never the LLM's wording:

```yaml
id: core/rls-denied-column
model: fixtures/models/pilot-mini      # a reference model the suite ships
identity: { user: analyst_a, roles: [sales_read] }
turn: "Average salary by department last quarter"
expect:
  calls:                               # expected tool-call set, order-free, with argument matchers
    - tool: meta.search
    - tool: query.run
  result:
    envelope: { ok: false }
    error_code: column_denied
  provenance:
    pipelineWarnings_contains: [{ code: column_denied_mask, sourceService: validator }]
```

The assertion vocabulary covers: the expected tool-call set (with argument matchers), the governed
result shape (envelope fields, column sets, row-count bounds — never exact floats where engines may
differ), provenance codes in `pipelineWarnings`, and rejection assertions (the identity gate,
permission denials). Because each fixture is a real request/response pair the platform already
asserts on, the fixtures **cannot rot into fiction** — which is exactly why they double as the
worked examples throughout this track.

## The two tiers

- **Core tier** — hand-authored (~25–40 fixtures); **100% must pass to claim conformance.** Its
  coverage floor exercises every tool of every door, a row-level-security filter, a column
  deny/mask, all three identity-gate rejections, truncation and row-limit behavior, parameter
  binding via `compile`, fuzzy matching with diacritics, and **refusal-over-guess** — a
  low-confidence turn must end in a clarifying question or a typed error, never a fabricated answer.
- **Extended tier** — a larger, real-world-derived corpus reported as a **score**, non-gating. It
  grows without threatening anyone's conformance claim.

## Pointing it at your agent

The runner is a thin harness: it feeds a fixture, observes the MCP traffic your agent produces, and
diffs it against the assertions. To conform, you run the core tier against your agent and reach
100%. A failure is a real defect — a missing bearer forward, a swallowed refusal, a guessed binding
— to fix in your agent, not a flake to retry.

Treat green on the core tier as the definition of "my agent consumes Tatrman correctly." Everything
else in this track is how you get there; this is how you know you did.
