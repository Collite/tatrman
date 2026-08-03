---
schema: ttr-skill/v1
op: op:top-n
triggers:
  - { text: "největší", lang: cs, method: TYPOS(1) }
  - { text: "top", lang: cs|en, method: EXACT }
version: 2
---
Retrieval: order by the measure descending; default N = 10 when unstated.
