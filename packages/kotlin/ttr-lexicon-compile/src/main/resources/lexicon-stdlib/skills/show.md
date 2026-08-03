---
schema: ttr-skill/v1
op: op:show
triggers:
  - { text: "ukaž", lang: cs, method: TYPOS(1) }
  - { text: "zobraz", lang: cs, method: TYPOS(1) }
  - { text: "vypiš", lang: cs, method: TYPOS(1) }
  - { text: "show", lang: en, method: TYPOS(1) }
  - { text: "display", lang: en, method: TYPOS(1) }
  - { text: "list", lang: en, method: TYPOS(1) }
version: 1
---
Presentation default. Return the result as a table; do not derive series, ratios or
rankings that the question did not ask for.

Retrieval: no shaping of its own. `op:show` states *that* the user wants to see the
subject, not *how* it should be aggregated — the subject and its filters come from the
lattice, and this operator adds nothing to the query.

Formatting: a table, with the subject's default label column first, then measures in the
order the question named them.

Composition: this is the weakest formatting claim in the library. When another operator
co-occurs, defer to it — `op:show` never overrides a chart choice, an ordering or a
delta column that another operator asked for. A question with `show` and `trend` is a
trend.
