---
schema: ttr-skill/v1
op: op:drilldown
triggers:
  - { text: "rozpad", lang: cs, method: TYPOS(1) }
  - { text: "rozpad podle", lang: cs, method: TOKENS }
  - { text: "detail podle", lang: cs, method: TOKENS }
  - { text: "drill down", lang: en, method: TOKENS }
  - { text: "breakdown", lang: en, method: TYPOS(1) }
  - { text: "break down", lang: en, method: TOKENS }
requires: [ parent-context ]
version: 1
---
One level finer.

Retrieval: group one hierarchy or grouping level below the conversation's current grain.
The parent context comes from the conversation machinery, never from the question text —
positional references ("that one", "the second row") are resolved before the core sees
anything (RV-34), so this operator receives a concrete parent or it receives nothing.

Formatting: keep the parent's total as a row, so the parts visibly sum to the whole the
user was just looking at. A breakdown that does not reconcile with the number above it
reads as an error even when both are correct.

Applicability: `parent-context` — with no parent, there is no "finer". Surface the gap
rather than picking the model's top level and hoping.
