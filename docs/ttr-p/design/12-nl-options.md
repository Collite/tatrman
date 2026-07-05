# C4 · The NL surface (TTR-B + the assist layer) — Option Catalogue

> **Naming note (consolidation sweep, 2026-07-04):** `ttrp/assistContext` was renamed **`ttrp/authoringContext`** (S8 — it serves agents, not just assist). TTR-B comments = `#`, English-only v1 (S19/S20). Standing contracts live in `../architecture/contracts.md`.

> Workstream C4 divergence doc, opened 2026-07-04. Companion to [`00-control-room.md`](./00-control-room.md), [`04-surfaces-options.md`](./04-surfaces-options.md) (C0-d), [`11-fragments-options.md`](./11-fragments-options.md) (C2 — much inherits).
>
> **The question C4 must answer:** what exactly is **TTR-B** (the strict controlled grammar, Byx evolved) — its place in the tier architecture, its statement set, its expression posture — and what exactly is the **LLM-assist layer** (what it emits, where it lives, what the review flow is), giving **Q1** (agent as author) its concrete shape. Confirms `.ttrb`.

**What's already constrained (not on the table):**

- **C0-d = BOTH:** Byx stays as a strict controlled-grammar surface (deterministic, LLM-free — "bypassing LLMs for a simple instruction set has value"), AND a 2026-grade LLM-assist layer is additional on top (world + manifests → generated canonical TTR-P, user reviews).
- The tier architecture is settled (C0-γ): canonical flow-DSL owns program structure; fragments are container content; bare-fragment documents are valid programs via document-level sugar. C2 fixed the fragment regime: full decomposition to the node set, document scope flows in, err-only, single default-out, formatter-untouchable interiors, own ANTLR grammars.
- T5-e: one PL expression grammar; strings lift to trees; catalogue functions; canonical NULL.
- H-2: `.ttrb` reserved for TTR-B. No LLM in the compiler — determinism is P2's floor; the toolchain never calls a model.
- This repo never talks to runtime services (kantheon consumes published artifacts) — constrains where the assist layer's LLM call can live.

**Prior art:** the Byx PoC (`../examples/byx/` — ANTLR grammar: verb-synonym statements `Load/Keep/Filter/Summarize/Join/Create/Rename/Retype/Output/Browse`, verbose comparisons `is more than`/`comes before`, anaphora `that/this/it`, sentence terminator `.`) · Alteryx expression grammar (same PoC) · dbt/LookML's "the model provides the vocabulary" · 2026 LLM structured-output patterns (constrained decoding against a grammar; validate-and-repair loops).

---

## C4-a · TTR-B's place in the tier architecture

Byx predates C0-γ. Where does the strict language slot now?

- **C4-a-α · Third fragment dialect.** TTR-B is container content + a bare-program kind, exactly like TTR-SQL/TTR-pandas: sentences express a data-flow island; the C2 regime inherits wholesale (full decomposition — each sentence → node(s); document scope flows in; err-only; single default-out; formatter never touches interiors; own ANTLR grammar).
  - *Buys:* zero new architecture — C2 already built TTR-B's house; the Byx PoC *is* island-shaped (its statement set has no containers/control/movement beyond input/output); "NL person writes the NL part" = the same mixing story as SQL people; bare `.ttrb` file = a valid program via the same document-level sugar (the analyst's five-sentence script just runs).
  - *Costs:* TTR-B can't express program structure (containers, targets, control edges) — the graduation boundary again (accepted for SQL/pandas; is NL different?).
- **C4-a-β · Full surface.** TTR-B covers the whole graph: "run crunch on polars after acc_prep finishes…" — NL for containers, control, movement.
  - *Buys:* whole programs in prose.
  - *Costs:* C0-a-α's rejected cost returns for the one surface *least* suited to structural precision; controlled-NL for port wiring is a stealth programming language with worse syntax errors; nobody asked for it (A1 personas are engineer+analyst; the NL persona wants islands, not topology).
- **C4-a-γ (weird) · Command language, not a file format.** TTR-B sentences are *interactive edit commands* (REPL/chat/voice driving `ttrp/applyGraphEdit`), never stored text.
  - *Buys:* matches 2026 chat ergonomics; no file-format obligations.
  - *Costs:* kills the deterministic-authoring value C0-d explicitly kept (a stored, reviewable, LLM-free program text); Byx's PoC value was files; interactive command routing is Designer/LSP UX, buildable *later* on top of α's grammar anyway.

*Lean: α — with γ noted as a v2 layer over the same grammar (a sentence parser is a sentence parser; batch vs interactive is hosting).*

## C4-b · The statement set (Byx evolved)

Proposed v1 sentence set — the Byx roster, renamed to the settled vocabulary and pruned to the island regime (C4-a-α):

| Sentence (synonyms abbreviated) | Maps to | Byx ancestor |
|---|---|---|
| `Load [from] file "…" [with schema <s>] [as <name>].` / `Load [from] <model-ref> [as <name>].` | Load / TableScan | `input` |
| `Keep/Take/Select only [the] columns a, b [as c].` | Project | `select` |
| `Keep all columns except a, b.` | Project (negative) | `negative_select` |
| `Keep/Filter [only] [the] rows where <expr>.` | Filter | `filter` |
| `Remove/Delete [the] rows where <expr>.` | Filter (negated) | `negative_filter` |
| `Rename a to b.` / `Rename the columns a as b, c as d.` | Project sugar | `rename` |
| `Convert/Retype a to <type>.` | Calc/Cast sugar | `retype` |
| `Create/Compute [new column] <expr> as <name>.` | Calc | `formula` |
| `Summarize sum of amount as total [, …] by/grouped by region.` | Aggregate | `summarize` |
| `Join that/it/<name> with <name> on <expr> [as <name>].` | Join | `join` |
| `Sort [the rows] by a [descending].` | Sort | *(new — obvious gap)* |
| `Keep [only] the first <n> rows.` | Limit | *(new)* |
| `Combine/Append that with <name>.` | Union | *(new)* |
| `Store that/[the] result to file "…" / <model-ref>.` | Store (program-level leaf via desugar) | `output` |
| `Show/Display [me] [the] result [as <name>].` | Display | `browse` |

Sub-forks:

- **C4-b-i · Anaphora + naming.** Byx's `that/this/it` = the previous sentence's result (deterministic — grammar-resolved, P2-clean); the *implicit* subject (no ref at all) = same. Naming: `as <name>` / `call it <name>` binds a variable (SSA per Q7-γ; re-`Load … as sales` after `sales` exists = reassignment). Multi-input sentences (`Join`, `Combine`) reference names or ports. **α keep all three ref-words + implicit** (Byx's charm) vs **β implicit + names only** (drop `that/it` — less NL, fewer parse ambiguities). *Lean: α — the ref-words are free in a grammar this closed.*
- **C4-b-ii · Synonym breadth.** Byx carries wide verb synonymy (`Keep/Take/Select/Return`, `where/which/that/with`). **α keep the roster** (the grammar's whole point is forgiving-but-deterministic; ANTLR eats it) vs **β one canonical verb per op + formatter normalizes** (smaller docs, but "controlled NL with one verb" is just a stiff DSL — kills the charm). *Lean: α; the formatter (which never touches fragment interiors — C2-f) has no normalization role anyway.*
- **C4-b-iii · Out-of-roster sentences are parse errors with named diagnostics** (the C2 discipline): `Update the table …` → "TTR-B has no update — data writes are Store" etc. A curated diagnostic table doubles as the assist layer's repair vocabulary (C4-d).
- **C4-b-iv · Statement order = pipeline order;** one island per fragment; final result = default out (single default-out inherits from C2-c-i). `Store`/`Display` sentences in a *bare* `.ttrb` program are the C0 wrapper-synthesis case (they desugar to program-level leaves outside the island — same as bare TTR-SQL's implicit display).

## C4-c · Expressions inside sentences — the verbose layer

T5-e says one expression grammar. Byx's expressions are *verbose*: `amount is more than 0`, `customer is not empty`, `region is one of ('N', 'S')`. Fork:

- **C4-c-α · Strict pin: PL expression grammar verbatim.** Sentences embed `amount > 0 and customer is not null` exactly as canonical text writes it.
  - *Buys:* literally one grammar; zero lift ambiguity.
  - *Costs:* guts the surface — `Keep rows where amount > 0` is half NL, half code; the verbose comparisons are most of Byx's identity and they already lift deterministically.
- **C4-c-β · Verbose skin over the same trees.** The PL expression grammar gains a TTR-B-only *concrete-syntax layer*: verbose comparisons (`is more than` → `>`, `comes before` → `<`, `is one of` → `IN`, `is empty` → `IS NULL`…), word operators (`and/or/not` — already canonical), same precedence, same catalogue functions, same 3VL. One abstract grammar, two spellings; the lift table is closed and documented (each verbose form names its canonical operator — the T5-e spirit: it's *grammar*, not free text).
  - *Buys:* keeps the surface's reason to exist; deterministic by construction; the formatter/`pl explain` can render the canonical spelling for review.
  - *Costs:* a second concrete syntax to maintain (bounded: a fixed synonym table, no user extension); two ways to write `>` *inside TTR-B only* (canonical spelling also accepted — or not? sub-fork below).
- **C4-c-γ · Verbose-only (canonical spellings rejected in TTR-B).** Purity of register; but rejecting `>` in an expression surface is user-hostile and buys nothing.

*Lean: β, with canonical operators also accepted (mixing allowed; the author's text stays as written per C2-f). Pin restated: the verbose layer is a closed synonym table in the grammar — never NLP, never fuzzy (P2).*

## C4-d · The LLM-assist layer

C0-d's second half: world + manifests → generated canonical TTR-P, review flow. Three axes.

**C4-d-i · What the assist emits.**

- **α · Canonical TTR-P** (with fragments where natural). Full program coverage — containers, targets, wiring; the review artifact is the file format itself; compile+typecheck is the gate.
- **β · TTR-B strict text.** The LLM targets the controlled grammar; its output is itself NL-readable (audit trail for non-programmers) and the strict parser rejects hallucinated forms.
  - *Costs:* caps the assist at island expressiveness (no containers/targets — exactly what a whole-program assist must produce); two-step lossy chain (NL → TTR-B → graph) when the LLM could emit the real thing; the strict parser's rejection catches *syntax* hallucinations, not semantic ones — the compiler does that regardless of target.
- **γ · Both, by scope:** island-scoped requests ("filter out the nulls here") *may* emit TTR-B/fragment content in place; program-scoped requests emit canonical TTR-P. The scope is the user's cursor context, not a heuristic (P2: the *host* declares the insertion target).

*Lean: α primary, γ's cursor-scoped refinement recorded as a natural editor behavior (assist inserts in the dialect of the container it's pointed at — including TTR-SQL/TTR-pandas, not just TTR-B).*

**C4-d-ii · Where the LLM call lives.** The compiler never calls a model (P2). Candidates for who does:

- **α · Editor-side feature.** VS Code ext / Designer UI calls the user's LLM (their key, their model) with a context bundle it requests from the toolchain; the toolchain's contribution is deterministic: context assembly + validation.
- **β · Designer-server/LSP method** (`ttrp/assist`): the server holds LLM credentials and proxies the call.
  - *Costs:* puts a model dependency and secrets in the repo-attached editing backend (G-b called it editor infrastructure — an LSP with an API key is a smell); every host re-plumbs auth.
- **γ · Out of the toolchain entirely:** Tatrman ships only the two deterministic halves as contracts — a **context bundle** (`ttrp/assistContext`: world + engine manifests + model schemas + in-scope names + grammar/diagnostic spec, serialized for a prompt) and a **validation loop** (`ttrp/validate`: parse + typecheck + capability-check a candidate program, returning the same named diagnostics). Any LLM host — VS Code ext, Designer, Claude-in-IDE, a kantheon agent — implements the loop: request context → generate → validate → repair on diagnostics → present for review.

*Lean: γ (with α as its first consumer): the boundary matches the repo invariant (deterministic tooling here, model calls at the host), and one contract serves human-assist and agent-authoring identically — which is exactly Q1's shape (C4-e).*

**C4-d-iii · The review flow.** Non-negotiables under P2: generated text is **never applied silently** — it arrives as a proposed document/WorkspaceEdit, pre-validated (the loop's exit gate: parses + typechecks + capabilities pass, or it isn't presented); the human sees canonical text (plus the graphical view for free — same graph); provenance is recorded. Fork on provenance form: **α** a comment header (`// generated: <model>, <date>, prompt-hash`) vs **β** nothing persistent (review = the git diff; generated text is just text once accepted). *Lean: β with α optional — the repo's diff *is* the review artifact; a mandated comment is churn the author will delete. Record as project preference (`[pl] assist-provenance = none|comment`).*

## C4-e · Q1 — the agent as author (concrete shape)

Q1 asked: is the AI agent (Pythia/Golem) a first-class user of a surface, or only a consumer of compiled graphs? With C4-d-ii-γ the answer is structural:

- **The agent is a first-class *author of canonical TTR-P*, via the same two contracts** (`assistContext` + `validate`) — no agent-special surface, no TTR-B detour (an LLM gains nothing from targeting the controlled grammar; it reads/writes the real language). The strict TTR-B surface is for *humans* avoiding LLMs; the assist loop is for *LLMs* serving humans; both compile through the identical front-half.
- Kantheon-side consequence (recorded, not designed here): an agent (Pythia investigation step, Golem area question) generates a program, runs the validate loop against the *project's* world, and executes the compiled bundle through its own runtime — G-g's "kantheon consumes compiled plans only" softens at exactly one seam: agents may also *author* source, using the published toolchain artifacts (`ttr-parser`+front-half), never a running Tatrman service. Fits §7.3 (Maven consumption, not runtime coupling).
- The eval story rides the same rails: an assist/agent corpus (NL request → expected graph shape) becomes a `pl-conform`-adjacent test suite — a consolidation work item.

## C4-f · Extensions & embedding — confirming `.ttrb`

- **Bare files: `.ttrb`** (H-2 reservation confirmed). Single extension, not double (`.ttr.b` buys no foreign-editor highlighting — no editor highlights controlled English); first-line comment override (`# ttr: dialect=b`? `-- ttr:`?) — comment *lexer* for TTR-B: `#` (Byx had none; `--` collides with prose dashes less than `//`… sub-point for the grammar prototype).
- **Embedded fence: `"""b` vs `"""ttrb` vs no embedding.** Is TTR-B embeddable as container content in canonical documents (`container prep target pg """b …`)? α yes, symmetric with sql/pandas (a fragment dialect is a fragment dialect; tag = `b`); β bare-programs-only (TTR-B's persona doesn't write canonical documents — but *mixed* documents where an analyst hands a colleague's `.ttrp` a sentence-island are plausible and cost nothing). *Lean: α, tag `b` (short, matches `sql`).* — wait: does an NL island *inside* a pg-targeted container make sense? Yes: TTR-B is engine-agnostic (it decomposes to the node set; the *container* carries the target — same as canonical text, unlike TTR-SQL whose idiom is SQL-shaped but equally retargetable).

---

## Converged (2026-07-04) — C4 is 🟢

- **C4-a = α · TTR-B is the THIRD FRAGMENT DIALECT** — container content + bare-program kind; the C2 regime inherits wholesale (each sentence → node(s); document scope flows in; err-only; single default-out; formatter never touches interiors; own ANTLR grammar). γ (interactive command mode) noted as a v2 layer over the same grammar.
- **C4-b · Full roster + anaphora.** Verb synonymy kept; ref-words `that/this/it` + implicit = previous result (grammar-resolved, P2-clean); `as <name>` / `call it <name>` binds SSA variables (Q7-γ); Sort/Limit/Combine added to the Byx set; out-of-roster sentences = named-diagnostic rejects (the table doubles as the assist layer's repair vocabulary).
- **C4-c = β · VERBOSE SKIN over the one expression grammar, canonical spellings also accepted.** Closed synonym table (`is more than` → `>`, `is empty` → `IS NULL`, `is one of` → `IN`, …), same trees/precedence/catalogue/3VL; mixing allowed; never NLP, never fuzzy — it's grammar (T5-e holds).
- **C4-d-i = α + γ · Assist emits CANONICAL TTR-P; cursor-scoped insertion emits the pointed-at container's dialect** (any of TTR-SQL/TTR-pandas/TTR-B); the host declares the insertion target (P2), never a heuristic.
- **C4-d-ii = γ · CONTRACTS ONLY: `ttrp/assistContext` + `ttrp/validate`.** Tatrman ships the two deterministic halves (context bundle: world + manifests + schemas + in-scope names + grammar/diagnostic spec, prompt-ready; validation: parse/typecheck/capability-check → named diagnostics). The LLM call lives at the host (VS Code ext, Designer, Claude, kantheon agent): generate → validate → repair → review. No model dependency or secrets in the toolchain.
- **C4-d-iii = β · No mandatory provenance; the git diff is the review artifact.** Generated text never applied silently — arrives as a pre-validated proposed edit. Optional `[pl] assist-provenance = none|comment` knob.
- **C4-e · Q1 RESOLVED: the agent is a first-class AUTHOR of canonical TTR-P** via the same two contracts — no agent-special surface, no TTR-B detour. Kantheon agents consume published toolchain artifacts (§7.3 Maven pattern), never a running Tatrman service; G-g softens at exactly this one seam. Eval corpus = `pl-conform`-adjacent → consolidation.
- **C4-f · `.ttrb` CONFIRMED (bare files, single extension); embeddable with fence tag `"""ttrb`** (Bora: the explicit long tag over `"""b` — the short-tag pattern is not a rule). TTR-B is engine-agnostic: a sentence-island is legal in any container; the container carries the target.

## Leftover sub-points (→ grammar prototyping / consolidation)

- TTR-B comment syntax + the ref-word list final roster — grammar prototype.
- Localization: is the verb/synonym table English-only v1 (lean: yes; the Czech question is real for the personas but a second synonym table is v1.x at earliest — and the *expression* verbose layer must localize with it or not at all)?
- Assist-context bundle format (prompt-shaped text vs structured JSON the host renders) — consolidation/contracts.
- The eval corpus home and format (`pl-conform`-adjacent) — consolidation.
- Does `ttrp/assist*` naming stay, or does the method pair get a neutral name (it serves agents, not just "assist") — H-flavored, consolidation.

## Cross-links

C4 → C2 (the fragment regime inherits wholesale under C4-a-α) · C4 → T5-e (verbose layer = closed synonym table over the one grammar) · C4 → Q1 (resolved here) · C4 → G-b/G-g (assist contracts live at the LSP boundary; agents consume published artifacts) · C4 → H-2 (`.ttrb` confirmed; fence tag) · C4 → D/T6 (context bundle = world + manifests + models) · C4 → consolidation (diagnostic tables as repair vocabulary; eval corpus; contract schemas).
