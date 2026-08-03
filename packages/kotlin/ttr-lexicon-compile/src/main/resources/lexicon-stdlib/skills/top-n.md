---
schema: ttr-skill/v1
op: op:top-n
triggers:
  - { text: "prvních", lang: cs, method: TYPOS(1) }
  - { text: "top", lang: cs, method: EXACT }
  - { text: "nejlepších", lang: cs, method: TYPOS(1) }
  - { text: "největších", lang: cs, method: TYPOS(1) }
  - { text: "top", lang: en, method: EXACT }
  - { text: "first", lang: en, method: TYPOS(1) }
  - { text: "largest", lang: en, method: TYPOS(1) }
requires: [ order-measure ]
version: 1
---
The leading n.

Retrieval: order descending by the ordering measure and limit to the numeral in the
question. The numeral is grounding's output, not this operator's — if the question names
no number, that is a gap, not a default of 10.

Formatting: show a rank column, and state the ordering measure in the answer. "Top 5
stores" is only meaningful once the reader knows top by what.

Applicability: `order-measure` — a ranking needs something to rank by. When the question
names exactly one measure, that is it; when it names several, ask rather than choose.
