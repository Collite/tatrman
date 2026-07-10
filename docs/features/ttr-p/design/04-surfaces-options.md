# C — Surface Languages: Option Catalogue (DIVERGENCE)

> **Mode: divergence.** Enumerate alternatives + trade-offs. **No decisions here** — those go to the control-room decision log.
> Control surface: [`00-control-room.md`](./00-control-room.md). Branch context: [`01-design-space-map.md`](./01-design-space-map.md) §C. Internal model (all converged): [`02-internal-model-options.md`](./02-internal-model-options.md). Tooling: [`03-tooling-delivery-options.md`](./03-tooling-delivery-options.md).
> Opened 2026-07-02, after **workstream B converged 🟢**.

**The question C0 must answer.** How do the five surfaces relate to the one graph and to *each other* — isomorphic peers or tiers? What is the canonical text? Can one program mix surfaces? Which surface is prototyped first? And the pinned **A1-bis revisit**: design 5, ship how many?

**What B hands us (constraints, not options):**
- Text is canonical (G-e); layout/ports serialize into PL text; the graphical surface is a *view* issuing structured edits (modeler invariant).
- One PL expression grammar, surface-independent; strings lift to trees; never pass-through dialect SQL (T5-e).
- The program = one document = one graph; **containers** group ops, act as functions, bear targets (T9/T4); variables are textual edge-sugar with SSA reassignment (Q7=γ).
- Surfaces must be able to express containers + targets (v1 placement is author-assigned).

---

## C0-a · Coverage architecture — the load-bearing fork

- **C0-a-α · Isomorphic peers.** All five surfaces are full file formats; any program saves/round-trips in any surface. Five grammars × full coverage (containers, control edges FS/SS/FF, error ports, movement, layout).
  - *Buys:* maximal "many skins" promise; any program readable in any style.
  - *Costs:* five full grammars to design + maintain; SQL-like and NL must express control/error/containers (unnatural — the brief's own fork #4 doubt); round-trip fidelity ×5; the conformance burden G just shed comes back ×5.
- **C0-a-β · One canonical language + projections.** ONE textual language (the canonical PL text) is the file format and full-coverage. The graphical surface is a *view* of it (already decided, G-e). The other textual surfaces are **input modes / projections**: you can author in them, but what's saved is canonical text.
  - *Buys:* one full grammar; everything else is a translator *into* it; round-trip = only canonical↔graph.
  - *Costs:* "5 languages" becomes "1 language + 4 authoring modes" — an honest reframe but a reframe; projections that can't express the whole graph are lossy views (fine for input, odd for display).
- **C0-a-γ · Tiered by the CONTAINER boundary (the structural candidate).** The **canonical language** expresses the full program: containers, control, movement, layout. **SQL-like and pandas-like are *container-content dialects*** — they express exactly what a container holds (a pure data-flow island: the thing they're naturally good at) and are embedded per-container in the canonical document (`container sales_prep [target: polars] as pandas { … }`). NL is an **authoring assist** (emits canonical or container-content), not a storage format. Graphical views the whole.
  - *Buys:* each surface covers precisely what it's naturally good at — SQL never has to say "finish-to-start"; the tier boundary isn't arbitrary, it **is** the container (the model's own function/target unit); **mixed authoring falls out for free** (C0-c); SQL/pandas surfaces shrink from full languages to *fragment grammars* (dramatically cheaper — reopens A1-bis economics); matches how real users think (SQL people write the SQL part).
  - *Costs:* SQL/pandas dialects can't express a *whole* program (deliberate, but must be communicated); canonical grammar must host embedded fragments (parser complexity, but TTR already embeds `sourceText` blocks); per-fragment expression syntax must still lift to the one PL expression grammar (T5-e pin holds — fragments are PL-expression flavored, not raw dialect).
- **C0-a-δ (weird one) · Graph-primary, all text projections.** Rejected by G-e already; listed for completeness.

*Lean: γ, with β as its degenerate reading (γ = β + fragments-at-containers).* The container tier boundary is the first option that makes "tiered" precise instead of hand-wavy.

## C0-b · Which language is the canonical text?
Candidates: the **flow-DSL** (Kyx-lineage, dot/`+`-linked ops — most graph-shaped, already full-coverage in spirit) vs a **new declarative block syntax** (TTR-family `def`-style: `node`/`edge`/`container` blocks) vs canonical = **serialized graph** (bare, machine-oriented; humans always use a projection).
- Flow-DSL-as-canonical: *buys* human-writable canonical files, one surface is free; *costs* the canonical grammar carries ergonomic sugar (variables, chaining) — canonical parse must handle full sugar.
- TTR-family blocks: *buys* family consistency (`def world`, `def program`?), trivially serializable, layout block precedent; *costs* verbose to hand-write; the flow-DSL then becomes a projection anyway.
- *Lean:* **flow-DSL IS the canonical language** — one grammar serves both the "nice DSL" persona and the file format; TTR-family block syntax remains available *inside* it where declarative bits live (world refs, container headers, layout block).

## C0-c · Mixed authoring
Under γ: **the container is the mixing unit.** One document: canonical flow-DSL for program structure + movement + control; any container's body optionally in a content dialect (`as sql`, `as pandas`). NL assist can *generate* either level.
- Open: fragment nesting (container in container — allowed, same rule applies); fragment expression syntax (must parse to PL expressions — the SQL fragment is a *PL-SQL-like*, not T-SQL passthrough; T5-e pin).
- Alternative (if γ falls): no mixing in v1 — one surface per document.

## C0-d · The NL surface — what is it in 2026?
Byx (strict controlled-NL grammar) predates the LLM era. Options:
- **C0-d-α · Strict controlled grammar** (Byx evolved): deterministic parse, no model dependency. *Costs:* users must learn the "controlled" part (it's a stealth programming language); weakest persona pull (A1 personas are engineer+analyst).
- **C0-d-β · LLM-assisted authoring**: NL → LLM (+ world/model context via `ttr-metadata`, + capability manifests) → **canonical PL text**, user reviews the generated program (diffable, checkable — everything B built makes the output verifiable). The "surface" is an authoring *experience*, not a grammar. *Buys:* matches 2026 reality (and Q1's agent-as-author); zero grammar to maintain; the compiler's static checking is the safety net. *Costs:* nondeterministic; needs LLM infra in the toolchain (Designer server extension?); "language" claim softens.
- **C0-d-γ · Hybrid**: a small controlled command set for the common 80% (deterministic), LLM fallback for the rest.
- *Lean:* **β** — and note it *re-scopes* rather than kills the NL deliverable: v1 ships "NL authoring assist" not "NL language". Rename accordingly (H).

## C0-e · Expression syntax across surfaces (inherited, pin)
Settled by T5-e: **one PL expression grammar**; each surface's expression *concrete syntax* is that grammar (fragments included); strings lift to trees; never raw dialect SQL. C0 only confirms the pin holds for fragments.

## C0-f · Prototype order (Q3)
- *Lean:* **canonical flow-DSL first** — it pressure-tests the entire model (containers, control, movement, variables, expressions), it's the file format everything else round-trips through, and it needs no Designer server. Then **graphical** (Designer server exists as G's component roster; validates ports/layout serialization). Fragments (SQL-like, pandas-like) third — they slot into an existing canonical parser. NL assist last (needs world + manifests live to be useful).

## C0-g · A1-bis revisit (pinned from review 260702)
Under γ the economics change: SQL-like + pandas-like are **fragment grammars** (weeks, not months); NL is an **assist** (no grammar). So "all 5 in v1" may survive *in re-scoped form*: canonical flow-DSL + graphical (full), SQL/pandas fragments (container content), NL assist (LLM). That is both honest to A1-bis's intent (5 authoring experiences pressure-testing one core) and compatible with A4's ≥2-surfaces bar.
- Options: (α) keep all-5 as re-scoped above; (β) v1 = canonical + graphical only, fragments fast-follow; (γ) original all-5-as-full-languages (rejected by economics unless C0-a-α wins).

## RESOLVED (2026-07-02) — C0 converged
- **C0-a = γ** (tiered at the container boundary) · **C0-b = flow-DSL canonical** · **C0-c = container is the mixing unit** · **C0-f order confirmed** (canonical → graphical → fragments → NL) · **C0-g = all-5 re-scoped** (2 full + 2 fragments + NL). → decision log.
- **C0-d = BOTH:** Byx stays as a strict controlled grammar (deterministic, LLM-free value) **and** LLM-assisted authoring is an additional layer. → decision log.
- **Bare-fragment programs:** a pure SQL/pandas document is a valid program via document-level sugar (compiler synthesizes container+shell from project/world defaults); dialect via explicit marker; fragments are PL-dialects (T5-e pin), not raw passthrough; source text never rewritten. → decision log.

## Open questions
- Canonical grammar: how do **fragments** embed lexically (heredoc-style blocks? indentation? `{ }` with mode switch)?
- Does the **graphical surface display fragments** as sub-graphs (parsed) or as opaque "code containers"?
- Layout block: per-document or per-container?
- Naming (H): the canonical language needs a name; Byx/Kyx renames; "fragment dialects" terminology.

## Cross-links
C0 → A1-bis/A4 (ship-scope), C0 → T9/T4 (container = tier + mixing boundary), C0 → T5-e (one expression grammar pin), C0 → G-e (text canonical; layout serialization), C0 → G Designer server (graphical + NL assist host), C0 → D (fragments reference model objects), C0 → Q1 (agent authors via NL-assist path or canonical directly), C0 → H (names).
