---
schema: ttr-skill/v1
op: op:trend
triggers:
  - { text: "vývoj", lang: cs, method: TYPOS(1) }
  - { text: "trend", lang: cs|en, method: EXACT }
requires: [ time-grain ]
version: 1
---
Retrieval: group by the finest requested time grain; order chronologically.
