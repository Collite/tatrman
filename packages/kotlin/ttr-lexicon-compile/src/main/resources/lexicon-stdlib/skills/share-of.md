---
schema: ttr-skill/v1
op: op:share-of
triggers:
  - { text: "podíl", lang: cs, method: TYPOS(1) }
  - { text: "procento z", lang: cs, method: TOKENS }
  - { text: "share", lang: en, method: TYPOS(1) }
  - { text: "share of", lang: en, method: TOKENS }
  - { text: "percentage of", lang: en, method: TOKENS }
version: 1
---
Part against whole.

Retrieval: fetch the numerator and the denominator in one result, and compute the ratio
after both are in hand — a percentage divided across two queries can divide numbers that
were filtered differently.

Formatting: a percentage column beside the absolute value, never instead of it. **State
the denominator in the answer.** "Marketplace is 23%" is not an answer until the reader
knows 23% of what — of total revenue, of the channel's own total, of the filtered subset.
This is the operator most able to mislead by omission, and the denominator sentence is
what prevents it.
