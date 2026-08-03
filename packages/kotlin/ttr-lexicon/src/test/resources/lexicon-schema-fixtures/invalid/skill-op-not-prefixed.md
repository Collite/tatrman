---
# expected: RG-LEX-004 — `op` must be an `op:` ref. A bare word is a term, not an operator id.
schema: ttr-skill/v1
op: trend
triggers:
  - { text: "vývoj", lang: cs, method: TYPOS(1) }
version: 1
---
Retrieval: …
