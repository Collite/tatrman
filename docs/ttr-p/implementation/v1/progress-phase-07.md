# Progress — Phase 7 (TTR-B + assist finalization)

> **Status:** Stage 7.1 (TTR-B grammar) **core code-complete** (T7.1.1–7.1.6 + the embedded
> `"""ttrb` surface of 7.1.7, all green); the **bare-program run** tail of 7.1.7 is deferred
> (needs Phase 6's unbuilt bare-wrapper synthesis + dockerized PG — see §Deferred). Stage 7.2
> (assist finalization) **pending**. Branch `feature/ttr-p-v1-phase7`. `[x]` = intent; the
> reviewer verifies against runtime (CLAUDE.md cadence).

Deliverable: the hero authored as TTR-B sentences parses, folds its verbose comparisons to the
one PL expression IR, and decomposes clause-wise to the standard node set — the embedded
`"""ttrb` island builds the same hero graph (Load×2 → Join → Filter → Aggregate → Sort → Limit
→ Display) through the shared checker/GraphBuilder, exactly as the P6 SQL/pandas dialects do.

## Alignment reconciliations (task-list predates P6; reconciled to shipped P6 pattern)

The `tasks-p7-*.md` lists were drafted before P6 existed and target `plan.md`. Per their
Pre-flight "reconcile with P6" instruction, these divergences are conscious, not drift:

- **Package** `org.tatrman.ttrp.dialect.b` (not the list's `frontend.ttrb`) — matches P6's
  `dialect.sql` / `dialect.pandas`.
- **Generated parser package** = the shared `org.tatrman.ttrp.parser.generated` (TTRB* classes;
  ANTLR prefixes by grammar name) — matches the P6 grammar-gen wiring in `ttrp-frontend/build.gradle.kts`.
- **Tables are TOML in `src/main/resources/`**, not YAML in test resources: `rejects/ttr-b.rejects.toml`
  + `verbose/ttr-b.synonyms.toml`. They are the single source consumed by the reject scanner, the
  verbose lift, and (Stage 7.2) the assist catalogue — so they must be on the main classpath, as
  P6's `ttr-sql.rejects.toml` is. Loaded via the shared `RejectTable`.
- **Fence tag** `"""ttrb` needs no `TTRP.g4` change — the canonical `TAGGED_BLOCK` lexer rule
  already accepts any `[a-zA-Z][a-zA-Z0-9-]*` tag; `FRG_001` already lists `ttrb`.

## Grammar-prototype leftovers decided (close `12-nl-options.md` §Leftover)

Recorded in the `TTRB.g4` header + the T7.1.1 roster:
- **Ref-word roster (C4-b-i):** v1 keeps `that | this | it` only; Byx's plural `these/those`
  dropped (a TTR-B value is one table — a plural anaphor has no distinct referent, P2).
- **Binding form:** `… as <name>` only; Byx's standalone `call it <name>` dropped (one closed
  binding spelling; `as` already carries Load/Join/Compute/Show naming).

## Stage 7.1 — TTR-B grammar — **core code-complete**

- **`TTRB.g4`** (own grammar, C2-g α, `caseInsensitive`) — `sentence+`, the C4-b verb roster with
  full synonym breadth (Keep/Take/Select, Remove/Delete, where/which/that/with, noise words) and
  the three additions (Sort/Limit/Combine). Verbose comparators are closed grammar sub-rules (the
  Byx `verbose_*` pattern) folding 1:1 to the shared operators; `#` comments (S19); a trailing
  `UNMATCHED` catch-all so non-ASCII (S20) reaches the reject path, not a lexer crash. Wired into
  `generateGrammarSource` (generated sources gitignored).
- **Verbose lift** (`TtrbExpr`) — skins the ONE PL expression IR (S16/T5-e): every verbose form
  folds to the SAME `CatalogId` as its canonical operator; canonical + verbose forms may mix in one
  predicate; `is [not] empty` → IsNull (3VL), `is one of` → InList. Table-driven from
  `ttr-b.synonyms.toml`.
- **Decomposition** (`TtrbDecomposer`) — sentence→canonical AST (C2-a-β): Load/Load bind SSA
  sources, Join binds `joined`, the anaphoric tail (`that`/implicit subject = prev out) chains
  filter→aggregate→sort→limit→display, `Show … as region_totals` binds the terminal. Reuses
  `TtrSqlLoc`. Roster 1:1 to the canonical op set (`load/project/filter/calc/aggregate/sort/limit/
  union/store/display`).
- **Reject table** `ttr-b.rejects.toml` (B-001..008 + shared EQ-001) + `TtrbRejectScanner` —
  curated forms named from the token stream before any bare syntax error (C2-g), deterministic
  priority order (P2): `//`/`*` comment lexis → non-ASCII → `==` → sentence-initial DML/DDL/PIVOT →
  unknown-verbose `is <word> <operand>` 3-token window → catch-all. Single-source messages.
- **Entry** (`TtrB.decompose`) registered in `FragmentDecomposer` under the `ttrb` tag (the P6
  dispatch), so `"""ttrb` islands decompose alongside `"""sql`/`"""pandas`.
- **Specs (75, green):** `TtrbParserSpec` (34: hero + roster + synonym breadth),
  `TtrbVerboseExpressionSpec` (25: table-driven fold + mixing + 3VL), `TtrbDecompositionSpec` (6:
  hero node-for-node + SSA labels + derived in-ports), `TtrbRejectSpec` (5 negatives → exact id +
  table suggestion), `TtrbRejectTableSpec` (3: coverage), `TtrbEmbeddedGraphSpec` (2, ttrp-graph:
  the embedded `"""ttrb` hero builds the non-vacuous hero graph deterministically).
- `golden/fragments.ttrp` `frag_b` interior updated to valid TTR-B (the P6 precedent — `frag_pd`
  was updated when pandas landed); AST snapshot regenerated. Verbatim emit ⇒ emit/cli goldens
  unaffected.

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.dialect.b.*"`
and `:packages:kotlin:ttrp-graph:test --tests "*TtrbEmbeddedGraphSpec"` — green;
`{ttrp-frontend,ttrp-graph}:ktlintCheck` — green.

## Deferred (not done — honest gating, mirrors P6's partial 6.3)

- **T7.1.7 bare-program run** — a bare `.ttrb` file → synthesized derived container needs Phase 6's
  **bare-fragment wrapper synthesis** (T6.3.3/6.3.4), which is **not in the merged code** (P6 deferred
  it). `ttrp run hero-sentences.ttrb` also needs the dockerized PG+Polars environment (Phase 3.4
  gate). Registration into that machinery (T7.1.7 step 1) lands with it.
- **Full three-way byte-identity gate** — the embedded surface builds the correct hero graph
  (proven), but byte-identical embedded ≡ canonical hits two **shared-infra deltas**, not TTR-B
  bugs: (1) canonical `load("path")` folds the path to a `Literal` that `refText` drops (source=""),
  whereas the ttrb load carries a `ColumnRef`; (2) the fragment default-out auto-maps to the last
  node while a canonical FlowBody binds the out port by name. Closing these (and adding the bare
  third surface) completes the A4 "hero three ways" for TTR-B.

## Stage 7.2 — Assist finalization — **in progress**

- **Dialect rosters (committed 0bd22b9):** filled the TTR-SQL clause roster + the C4-b TTR-B verb
  roster in `AuthoringContextBuilder.grammar` (closed the P4-left `sql: []`/`ttrb: []` placeholders;
  schema-safe — existing arrays populated).
- **Eval harness (T7.2.4/T7.2.5, `ttrp-conform` + `ttrp eval` CLI, 12 specs green):** the
  **deterministic, engine-free** half of the assist/agent eval (C4-e). `EvalComparator` scores a
  candidate graph vs an expected graph by **shape** — reusing `NormalizedGraphJson` as a label-free
  node-render multiset, so SSA renaming never fails a match (`ssaNames: ignore` is intrinsic); the
  `extraCalcNodes` tolerance drops interposed Calc/Project. `EvalCorpus` loads a versioned `corpus.toml`
  (+ `fixtures/`; TOML per the P6/P7 reconciliation) — a representative seed (program-scope + a
  cursor-`insertionTarget` entry). `EvalRunner` is **compile-agnostic** (the front-half is injected —
  the CLI wires `TtrpPipeline`, specs a deterministic stub), producing `pass | shape-mismatch | invalid`
  with the invalid diagnostics round-tripping the repair vocabulary. `ttrp eval --corpus --candidates
  --report` (top-level, not `conform eval`, to avoid restructuring the existing `conform <file>` leaf;
  the toolchain NEVER generates candidates — P2/C4-d-ii, it only scores).

- **LSP assist bundle (T7.2.1–7.2.3, `ttrp-lsp`, 5 specs green):** `AuthoringContextBuilder` now emits
  **cursor-scoped `insertionTarget`** (C4-d-i γ) — inside a container the bundle carries `{dialect,
  containerName, targetEngine}`, the dialect being the container's (sql/pandas/ttrb from the fragment
  tag, or `ttrp` for a canonical body / program scope); host-declared via the position, no heuristics
  (P2). The **diagnostics catalogue** gains the `area` grouping (`TTRP-<AREA>-NNN` → AREA), 100% id
  coverage intrinsic (the enum registry is the single source). `authoring-context.schema.json` updated
  (both fields optional, so the committed examples still validate; `AuthoringContextSchemaSpec` +
  `CustomMethodsSpec` green). `AuthoringInsertionTargetSpec` asserts sql/ttrp/program-scope placements
  + area + the TTR-B roster.

- **VS Code assist demo (T7.2.6, `ttrp-vscode-ext`, 3 specs green):** the reference host —
  `ttrp.assist.generate` + `ttrp.assist.setApiKey`. The model-agnostic **generate → validate →
  repair loop** (`assistLoop.ts` + `prompt.ts`) is pure/injected and unit-tested (mock model: an
  invalid `==` first draft repaired once the EQ-001 diagnostic is fed back; exhaustion ⇒ `ok=false`
  and **no edit** — the C4-d-iii exit gate is structural, the loop never applies). The vscode glue
  (`command.ts`, typechecked) wires it: cursor→`ttrp/authoringContext` (host-declared insertion
  target), NL request, model at the HOST only (endpoint/model from settings, **key from
  SecretStorage**, never the LSP), `ttrp/validate` per candidate, then a `vscode.diff` + modal
  **Apply/Discard** — only Apply mutates the doc. A `mock:` endpoint drives the whole loop offline for
  the Extension Dev Host demo (no network, no key). Manual F5 acceptance + a real-key run are the
  operator's step.

**Remaining 7.2 (deferred by the agreed scope):**
- **T7.2.7 baseline** — manual / off-CI (real model key + the T7.2.6 host's candidate-dump → `ttrp
  eval` scorer → committed `baselines/001/`).
- The eval seed is representative (2 entries), not the full A5 ten — the rest land with the baseline.

## Verification (Stage 7.2)

- `./gradlew :packages:kotlin:{ttrp-lsp,ttrp-conform,ttrp-cli}:test` + `ktlintCheck` — green (incl.
  `EvalComparatorSpec`/`EvalCorpusSpec`/`EvalRunnerSpec`, `AuthoringInsertionTargetSpec`,
  `AuthoringContextSchemaSpec`/`CustomMethodsSpec` under the extended schema).
- `ttrp-vscode-ext`: `tsc --noEmit` + `eslint src` clean; `vitest run` green (assist-loop specs).
