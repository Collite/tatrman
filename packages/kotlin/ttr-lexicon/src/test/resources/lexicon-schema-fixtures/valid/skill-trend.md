---
schema: ttr-skill/v1
op: op:trend
triggers:
  - { text: "vývoj", lang: cs, method: TYPOS(1) }
  - { text: "trend", lang: cs|en, method: EXACT }
  - { text: "evolution", lang: en, method: TYPOS(1) }
requires: [ time-grain ]
version: 1
---
Retrieval: group by the finest requested time grain; order chronologically; require ≥2
periods before answering — a single period is a value, not a trend.

Formatting: line chart by default; period column first; absolute values with the
period-over-period delta beside them.
