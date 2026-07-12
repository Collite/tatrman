# A · Model Vocabulary Surface (TTR-M) — Options Catalogue

> **Status: CONVERGED 2026-07-12 — RS-9..12 in the control room §7.** Net shape: a **`lexicon` model** as the canonical vocabulary form (term/pattern/example entries targeting er/db/md), inline `lexicon{}` blocks as sugar (the binding pattern); locale rides the lexicon unit; runtime lemmatization bridges inflection (*declare across derivation and languages, compute across inflection*); `search{}` slims to retrieval config; member vocabulary = columns floor + `valueLabels` + declared `alias_table` semantics. **GI-8** (recorded mid-convergence) relaxed the original ratify-in-place premise: the legacy surface is free to redesign. RS-9 threads T1–T3 + Q-13/Q-15 ride into planning. Fact base = the TTR-M manual (`docs/manual/en/14-reference.md`, `07-er-schema.md`, `15-md-model.md`), the in-flight **search-block** feature (`docs/features/search-block/`) and **semantics-block** feature (`docs/features/semantics-block/`, grammar 4.2), and the recon (`02-recon-live-reference.md`).
> Scope: what the conceptual model declares so that member search (B), grounding (D), and resolution (E) are governed *by the model* — and where in TTR-M it lives. FI-5 is the framing input: specific vocabulary belongs in the conceptual model.

---

## 1. Facts (fixed ground, not options)

**TTR-M already carries three vocabulary-bearing surfaces:**

1. **`search { … }` block** — sub-properties `keywords, patterns, descriptions, examples, aliases, searchable, fuzzy` (search-block feature widens attachment to all data-bearing `er`/`db` kinds: table, column, view, entity, attribute, relation, query, role). This is the *findability* surface — `meta.search` and the fuzzy loader consume it.
2. **`semantics { role | kind | params }` block** — grammar 4.2 (approved 2026-07-06, in flight): free-form object, closed vocabulary validated in `ttr-semantics` (roles `period_start/end/code`, `event_date/due_date/posting_date/document_date`, `geo_lat/lon`, `amount/amount_domestic/currency_code`; kinds `period_table/poi/fx_rate`); attachable to `entity/attribute/table/column`; **new roles need no grammar bump**. This is the *grounding* surface — D's semantic discovery reads it. The hero's fiscal calendar and POIs are already model-declared through it.
3. **Naming & labels** — `aliases` on entity ("alternative names the business uses"); `displayLabel`/`labelPlural`; **`valueLabels`** on attribute (localized meaning of coded values — a small in-model member vocabulary); localized `label` on `cnc` roles (`{ en: …, cs: … }` precedent); `description`/`tags` everywhere.

**The gaps (why A exists):**

- **`md` kinds carry no vocabulary.** Neither `search{}` nor `semantics{}` attaches to `measure`/`dimension`/`cubelet` — the hero's „utržili" (→ revenue measure) has *no declared home*. Today measure binding happens LLM-side (joint-inference) from labels/descriptions only.
- **Aliases/keywords are flat lists** — no locale dimension (unlike `valueLabels` and role `label`, which are localized).
- **A linguistic nuance that shapes A3:** MorphoDiTa gives *inflectional* normalization (`Octavie→octavia`, `pražských→pražský`) but **not derivational** bridging — `utržili` lemmatizes to the verb `utržit`, never to the noun `tržba`. Runtime morphology (C) cannot conjure verb→measure vocabulary; it must be **declared** (or learned). UFAL's DeriNet exists as a possible derivational enrichment, but it is not in the current stack.
- **Grammar-change logistics:** anything here that touches grammar rides the grammar-master process; 4.2 is claimed by semantics-block; search-block is its own editor-tooling change. A's options are constrained to be *additive* over those, not competing edits.

## 2. A1 · The carrier — one surface or three?

**Question.** `search{}` (findability), `semantics{}` (grounding roles), naming properties (aliases/labels/valueLabels) exist separately. Does the understanding layer get its vocabulary from these three as-is, a unified block, or a new dedicated surface?

**A1-α — ratify the trio, extend in place.** Keep the three surfaces with their distinct jobs; A's gaps are filled by widening *attachment* (search→md kinds) and *shape* (locale-keyed lists) where needed.
Buys: zero new concepts; both in-flight features land untouched; each surface keeps a crisp consumer (search→B/F, semantics→D, naming→E's registry & prompts).
Costs: vocabulary authorship is spread across three places on one definition (an entity can carry `aliases`, `search{aliases}`, and `semantics{}` — the first two overlap confusingly *today*).
Prior art: the live metadata already merges them into `EntityTypeSpec` (entity label/aliases + field labels + fuzzy namespaces) — proof the trio composes.

**A1-β — one `vocabulary{}` (or `language{}`) block to rule them all.** Fold aliases, keywords, verb forms, locale variants into a single new block; `search{}` shrinks to retrieval config (`searchable`, `fuzzy`, `patterns`), `semantics{}` stays grounding.
Buys: one place to author "what humans call this thing"; the locale dimension designed once.
Costs: a third block landing while two are in flight (grammar churn, migration of `aliases`/`search{aliases}`); the search/vocabulary boundary is genuinely blurry (keywords *are* retrieval config); re-opens freshly-decided surfaces.

**A1-γ — a `lexicon` document kind.** Vocabulary as its own `.ttrm` content (`def lexicon` / glossary packages): term → target (entity/measure/query) + language + forms; models import lexicon packages.
Buys: domain glossaries versioned/reviewed independently (a BA can own the lexicon without touching structure); per-estate/per-language packs; scales to rich vocabularies without bloating entity definitions.
Costs: referential integrity burden (lexicon → model references need validation); two places to look ("is 'obrat' on the measure or in the lexicon?"); heavier than the bar needs for v1.

**A1-δ — vocabulary outside TTR-M (the weird one).** Service config / Veles-side registry / UI-managed tables.
Buys: nothing the thesis wants. Costs: un-governed, un-versioned, un-reviewed — breaks "resolution is governed *because* vocabulary is model-declared" (resolver-rewrite §1). Maps why FI-5 is the frame.

*~~Lean: α~~ **DECIDED 2026-07-12: γ-canonical + α-as-sugar — RS-9** (control room §7). Bora's fork, sharpened by **GI-8** (the current surface is legacy YAML carry-over, explicitly free to redesign; `search{}` unused except `fuzzy`): follow the **binding pattern** — TTR's existing inline-`binding:`-sugar / standalone-`er2db_*`-canonical dual, applied to language. See §2a for the ratified shape. Q-14 dissolves (both alias forms become sugar).*

## 2a. The ratified carrier shape (RS-9) — and its open threads

```ttr
// canonical form — a lexicon model, like binding (sketch, not final syntax)
model lexicon                      // locale placement = open A3 thread (file header? entry-level?)

def term trzba    { for: md.measure.net, forms: ["tržba", "tržby", "obrat", "utržit"] }
def pattern nazev { for: db.query.by_name, match: "název .*" }
def example q1    { for: md.cubelet.sales, text: "Kolik jsme utržili za Octavie…" }

// sugar form — inline, desugars to canonical entries
def measure net {
  domain: md.Money, class: additive, aggregation: sum,
  lexicon { terms: ["tržba", "obrat", "utržit"] }
}
```

- **Boundary sentence:** *lexicon = what things are called; `search{}` = how retrieval treats them.* The search block slims to retrieval config (`searchable`, `fuzzy`) — the in-flight search-block feature proceeds unchanged (its flag relocation is the end state); its vocabulary sub-properties migrate to lexicon in the 4.3 arc (Q-13).
- **Legacy dispositions (proposed — ratify at the A sweep):** entity `aliases` + `search.{aliases,keywords}` → lexicon **terms** · `search.patterns` → **pattern** entries (load-bearing for pattern queries) · `search.examples` → **example** entries (feed agent prompts + conformance fixtures) · `search.descriptions` → fold into `description` (single prose home), pending a consumer check.
- **Open threads:** **A1-T1** desugar conflict rules (inline + canonical entry for the same target — copy binding's discipline) · **A1-T2** entry schema detail (terms/forms/pos/weight — grow additively, start minimal per RS-10's rejection of β-now) · **A1-T3** consumer schema propagation (snapshot, `EntityTypeSpec`, `meta` doors read canonical form only).

## 3. A2 · Measure & intent vocabulary — the „utržili" gap

**Question.** What does the model declare so that a verb/phrase can reach a measure (or named query) — and *who* consumes it (deterministic matcher, LLM context, or both)?

**A2-α — widen `search{}` attachment to `md` kinds.** `measure`/`dimension`/`cubelet` (and `md` domains?) accept the same search block: `def measure net { …, search { keywords: ["tržby", "obrat", "utržit", "revenue"], aliases: […] } }`.
Buys: one existing mechanism, additive grammar change; the vocabulary flows everywhere the search block already flows (meta.search, get_model search hints, fuzzy loader if marked, **and** the agent's prompt context — consumer-agnostic by construction); the two-call thesis is respected — the model *declares*, call #2's LLM (or a future deterministic tier) *binds*.
Costs: needs its own small grammar/feature arc (attachment widening — 4.3-class, rides grammar-master); flat lists until A3 adds the locale dimension.

**A2-β — lexicon entries with typed mappings.** Per A1-γ: `term "utržit" { maps_to: md.net, language: cs, pos: verb }` — an explicit term→target table.
Buys: precision (POS, language, weight per mapping); auditable "why did 'utržili' pick net?".
Costs: carries A1-γ's whole machinery for one gap; overkill before evidence that declared keywords underperform.

**A2-γ — declare nothing; intent is the LLM's job (the boundary-mapping one).** Measures already ship labels + descriptions in `get_model`; call #2 reads them; per-estate terms live in agent prompt config.
Buys: zero model change; honestly describes *today*.
Costs: per-estate vocabulary becomes prompt folklore — untracked, unreviewed, non-portable across agents (a third-party MCP agent gets nothing); contradicts FI-5 directly. Recorded to sharpen the line: **the model declares vocabulary so that *any* consumer can bind — the LLM is one consumer, not the owner.**

**A2-δ — learned vocabulary (the weird one).** Mine conversation logs for verb→measure co-occurrence; propose vocabulary via PR (robots write through git).
Buys: vocabulary that matches real usage; the teach-in pattern (import-schema F3-δ precedent — writes config/model via PR, never silently).
Costs: needs conversation volume + the commercial harvest boundary question; an *enrichment loop*, not a foundation. Park with a revisit trigger (post-debut, once DFP corpus access lands).

*~~Lean: α~~ **DECIDED 2026-07-12: α, realized through RS-9's lexicon — RS-10** (control room §7): md kinds become legal lexicon targets (+ inline sugar on `measure`/`dimension`/`cubelet`); consumer-agnostic (retrieval, agent context, future deterministic binder). Bora: intent/measure vocabulary should **one day be covered by the conceptual model itself** — recorded as a named future arc; RS-10 is the declared-surface step. β's precision fields = additive schema growth if parity evidence demands; γ recorded as the boundary-sharpener; δ parked (teach-in loop, post-debut).*

## 4. A3 · The language dimension — how does declared vocabulary speak Czech?

**Question.** Locale-keyed forms, flat lists + runtime morphology, or lemma-canonical declarations?

*(RS-9 adds a new candidate here — **A3-ε**, see below.)*

**A3-α — locale-keyed vocabulary.** Extend list-bearing sub-properties to the `valueLabels` precedent: `aliases: { cs: ["zákazník", "odběratel"], en: ["customer", "client"] }` (flat list stays legal = "all locales").
Buys: mirrors an existing localized-property pattern; per-locale retrieval/labeling everywhere; the capability matrix (RS-7) can report vocabulary coverage per language.
Costs: authorship burden (though only for multi-locale estates); schema change in every consumer (snapshot, EntityTypeSpec, meta.search) — additive but broad.

**A3-β — flat surface lists + runtime morphology.** Declare a few surface forms; C's backends lemmatize both vocabulary (at index/snapshot build) and query spans, so inflection is bridged mechanically.
Buys: least authoring; leverages RS-3..8 (the lemma axis is finally real in-cluster).
Costs: **inflection-only** — lemmatization never bridges `utržili→tržba` (derivation) nor `cs↔en`; language mixing in one flat list pollutes per-locale display; silently depends on the NLP backend being up at index-build time.

**A3-γ — lemma-canonical declaration.** Authors declare lemmas (validated by tooling against MorphoDiTa); runtime matches lemma-to-lemma.
Buys: smallest possible vocabulary (one lemma covers the paradigm); deterministic given pinned models.
Costs: authors must *know* lemmas (BA-hostile: is it `tržba` or `tržby`?); validation couples model authoring to an NLP backend (the toolchain gains a service dependency — P-shaped smell); still doesn't bridge derivation.

**A3-δ — derivational expansion (the weird one).** Enrich declared vocabulary via DeriNet (UFAL's derivational network): declare `tržba`, derive `tržit/utržit/tržní` mechanically.
Buys: attacks the actual hero gap (verb↔noun) with a real UFAL resource.
Costs: DeriNet is not in the stack (new engine, new model file, licensing sibling of FI-4); derivational neighborhoods overgenerate (`tržní` ≠ revenue-intent) — precision needs curation anyway, which is… declaration again. Maps the boundary: **derivation is declared (A2's keywords list the verb forms), inflection is computed (C's lemmas).**

**A3-ε — locale on the lexicon unit (new, from RS-9).** The lexicon model carries the locale at file/model-header level (`model lexicon` + locale parameter — the `db … schema` precedent): per-locale lexicon files, same targets, no locale keys inside entries.
Buys: clean separation per language (a Czech lexicon file a Czech BA owns); no nested locale maps; adding a language = adding files, zero shape change; composes naturally with packages/worlds.
Costs: cross-locale view of one target is spread across files (IDE/Designer can aggregate); tiny estates with two words in each language pay a file of ceremony (inline sugar mitigates — sugar entries could default to the deployment/base locale, an A1-T1-adjacent rule).

*~~Lean~~ **DECIDED 2026-07-12: ε + β — RS-11** (control room §7): per-locale lexicon units + runtime lemmatization; inline sugar defaults to the deployment/base locale (rule detailed with A1-T1); γ = optional IDE assist; δ parked. **Declare across derivation and languages; compute across inflection.***

## 5. A4 · Member vocabulary — what does the model say about *values*?

**Question.** Members (dimension values) live in the estate's data. What may the model declare about them, between "nothing" and "everything"?

**A4-α — columns only (the live shape, ratified).** The model declares *which columns* are member vocabularies (`search{fuzzy:true}` → loader) plus `nameAttribute`/`codeAttribute`; the values themselves stay data.
Buys: members-are-data stays clean; zero authoring; scale-proof (100k products never enter git).
Costs: no per-member synonyms ("Škodovka" → brand Škoda lives nowhere); cross-language member names invisible unless the estate stores them.

**A4-β — `valueLabels` ratified + widened as the small-set vocabulary.** For coded/enum-like attributes, `valueLabels` (already localized) is the member vocabulary; optionally widened with per-value aliases (`1: { label: {cs: "Aktivní", en: "Active"}, aliases: ["živý"] }`).
Buys: exists; perfect for status/type/category dimensions; localized already.
Costs: only viable for small sets; widening `valueLabels` shape is a grammar/loader touch.

**A4-γ — synonym data declared via `semantics`.** Estates that *have* member-synonym tables (alias tables, multilingual product names) declare them: `semantics { kind: alias_table, role: alias_of → <entity> }`-class vocabulary; the fuzzy loader ingests declared alias tables alongside the primary name column.
Buys: per-member synonyms at scale, governed as data (the estate owns the table, the model declares its meaning — exactly the fx_rate/period_table pattern D already set); no git bloat.
Costs: extends the semantics vocabulary (fine — closed-vocab evolution is designed for this, no grammar bump); loader work in B; estates without such tables get nothing (honest).

**A4-δ — members mirrored into model text (the weird one).** Reference-data packages: small dimensions authored as model content (country lists, chart-of-accounts) with full vocabulary.
Buys: total governance for reference dims; offline-testable.
Costs: members-are-data violated at scale; drift vs the estate's own tables becomes a new sync problem (import-schema F4's territory). Maps the boundary; may return as a curated "reference package" idea for the standard library, not for estates.

*~~Lean~~ **DECIDED 2026-07-12: α + β + γ — RS-12** (control room §7): columns floor · `valueLabels` small-set vocabulary (per-value alias widening rides the lexicon arc) · declared `alias_table`-class semantics for estates with synonym data (fuzzy loader ingests them — B inherits the loader work). δ rejected (members-are-data at scale; possible future curated standard-library reference packages only).*

## 6. Cross-links

- **→ B:** whatever A declares, the fuzzy loader/index consumes: declared keywords/aliases join member values in the index (B's fork on index architecture must budget for vocabulary-from-model, not just values-from-DB); A4-γ adds alias-table ingestion to the loader.
- **→ C:** A3's division of labor leans on RS-3..8 (in-cluster lemmatization at index-build and query time); vocabulary lemmatization becomes part of B's index build, *not* model authoring.
- **→ D:** `semantics{}` is ratified as-is (grammar 4.2 feature untouched); A4-γ extends its closed vocabulary through the designed evolution path.
- **→ E:** the resolver registry (`EntityTypeSpec`) is the merge point of all three surfaces — its schema inherits the locale dimension (A3-α) and md vocabulary (A2-α).
- **→ F:** `get_model(include_search_hints, locale?)` already has a `locale` parameter — A3-α gives it real content; conformance fixtures should assert locale-filtered vocabulary.
- **Grammar logistics (Q-13):** A2-α (search on md kinds) and A3-α (locale-keyed lists) are grammar touches — one consolidated "vocabulary 4.3" arc through grammar-master, sequenced after 4.2 merges; A4-γ needs none.

## 7. Open questions raised here

- **Q-13 *(re-scoped by RS-9)* — grammar sequencing:** the lexicon model arc (new model code + def kinds + inline sugar + search-block slimming + A4-β widening) as one grammar 4.3-class feature through grammar-master, after 4.2 merges; search-block feature proceeds independently. (Planning-shaped once A converges.)
- ~~**Q-14 — entity `aliases` vs `search{aliases}` overlap**~~ **Dissolved by RS-9:** both become sugar for lexicon terms; standalone forms deprecate with the lexicon arc.
- **Q-15 — vocabulary coverage reporting:** should the capability matrix / conformance suite assert "hero-class vocabulary declared for locale X" (a model-lint, `@modeler/semantics`-style), so estates learn their vocabulary gaps before their users do?
