---
schema: ttr-skill/v1
op: op:compare
triggers:
  - { text: "porovnej", lang: cs, method: TYPOS(1) }
  - { text: "srovnej", lang: cs, method: TYPOS(1) }
  - { text: "srovnání", lang: cs, method: TYPOS(1) }
  - { text: "compare", lang: en, method: TYPOS(1) }
  - { text: "versus", lang: en, method: EXACT }
  - { text: "vs", lang: en, method: EXACT }
requires: [ two-series ]
version: 1
---
Two things, side by side.

Retrieval: fetch both comparanda in ONE result set, not two queries stitched afterwards —
a comparison assembled from two round trips can silently compare different filter states.

Formatting: side-by-side columns, then an absolute delta column, then a percentage delta
column. State the direction explicitly (which one is the baseline); a bare "+12%" is
ambiguous about what moved against what.

Applicability: `two-series` — exactly two comparanda. Three or more is a breakdown, not a
comparison, and should be answered as one.
