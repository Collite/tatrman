---
schema: ttr-skill/v1
op: op:trend
triggers:
  - { text: "vývoj", lang: cs, method: TYPOS(1) }
  - { text: "trend", lang: cs, method: EXACT }
  - { text: "evolution", lang: en, method: TYPOS(1) }
  - { text: "trend", lang: en, method: TYPOS(1) }
  - { text: "over time", lang: en, method: TOKENS }
requires: [ time-grain ]
version: 1
---
Change over time.

Retrieval: group by the finest time grain the question requests, and no finer — a monthly
question answered daily is a different question. Order chronologically. Require at least
two periods: one point is not a trend, and saying so is better than drawing a line
through a single value.

Formatting: line chart by default, with the period column first. Keep the period labels
in the grain's own vocabulary (months as months, quarters as quarters) rather than
normalizing them to dates.

Applicability: `time-grain` — without a grain, either resolved from the question or
carried by the conversation, this operator has nothing to group by and should surface a
gap rather than guess one.
