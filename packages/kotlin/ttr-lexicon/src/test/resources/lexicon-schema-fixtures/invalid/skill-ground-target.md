---
# expected: RG-LEX-004 — grounding triggers are DATA entries in a `.lex.yaml` file, not
# markdown skills. A `ground:` ref in a skill's `op` is the wrong file kind: there is no
# behaviour body to load for a grounding kernel (RV-42 — the kernel stays generative).
schema: ttr-skill/v1
op: ground:chrono
triggers:
  - { text: "rok", lang: cs }
version: 1
---
Retrieval: …
