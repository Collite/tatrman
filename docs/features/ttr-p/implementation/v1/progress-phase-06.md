# Progress — Phase 6 (Fragment dialects: TTR-SQL, TTR-pandas)

> **Status:** Stages 6.1 + 6.2 **code-complete** (all tasks, green). Stage 6.3 **core delivered** (dialect markers + the graph-identity KEY GATE for embedded ≡ canonical); three integration items remain (bare-program wrapper synthesis + full C2-d resolution, conform-three-ways, Designer drill-in) — human/CI-gated, see §6.3. Branch `feature/ttr-p-v1-phase6`. `[x]` = intent; the reviewer verifies against runtime (CLAUDE.md cadence).

Deliverable: the hero's SQL island authored as `"""sql` and a TTR-pandas island equivalent, decomposing to **the same graph** as canonical authoring.

## Architecture decision (flagged for /review) — S16 is Kotlin-hosted, not grammar-hosted

The task-list Pre-flight (written before Stage 1.2 existed) assumed the S16 shared keyword/operator table would be an **ANTLR grammar unit** consumed via `import`/`tokenVocab`, and made "no such unit ⇒ STOP → Blocker". **In reality Stage 1.2 deliberately made S16 a Kotlin object** — `KeywordTable` + `CatalogId` + the one `Expression` IR — and its own docstring names this exact P6 scenario:

> "the fragment grammars (`TTRSql.g4`, `TTRPandas.g4`, `TTRB.g4`) skin THIS one expression grammar (T5-e); in P6/P7 they get **sibling drift specs against this SAME object** … That is why the table lives here and not inline in the grammar."

So the sanctioned path (taken here): each dialect is its **own** grammar with its own tokens (SQL case-insensitive; pandas lowercase), each decomposer folds to the **same** `CatalogId`s (pinned by `TtrSqlKeywordDriftSpec` / `TtrPandasKeywordDriftSpec`), and no ANTLR grammar is imported. This dissolves both flagged blockers (no shared-grammar unit; SQL case-sensitivity) **without touching the canonical grammar**. It is the design the code already documented.

## Cross-cutting architecture — decompose to canonical AST

Every fragment lowers to the **same canonical TTR-P AST** (`Chain`/`OpCall`/`Assignment` over the shared `Expression` IR), attached to `FragmentBody.decomposition`. Consequences:

- `TtrpChecker` resolution and `GraphBuilder` node-construction are **reused** (their FlowBody paths) — no parallel graph-builder for fragments.
- **bare ≡ embedded ≡ canonical graphs hold by construction** — all three surfaces funnel through identical node-building code.
- Fragments still **emit verbatim** (`SqlIslandEmitter` returns `container.fragment.sourceText` when `fragment != null`, unchanged) — decomposition is for graph structure / identity / Designer drill-in, **not** re-emission. Interiors stay byte-verbatim (C2-f); the parse is derived.
- A fragment container now carries **both** its raw fragment (verbatim emit + C2-f) **and** its decomposed members (graph structure). The Phase-2 `acc_prep` hero fragment now shows `Load → Filter → Project`; the ttrp-graph/-cli explain golden + Hero/ContainerMapping/RewriteEngine specs were updated accordingly (verbatim emit ⇒ ttrp-emit/-conform goldens unchanged).

## Stage 6.1 — TTR-SQL dialect — **code-complete**

- **`TTRSql.g4`** (own grammar, C2-g α, `caseInsensitive`) — one query expression per fragment: optional `WITH` + final `SELECT` (set-ops), C2-b α clause table. Wired into `ttrp-frontend` `generateGrammarSource` (generated sources gitignored).
- **Reject table** `ttr-sql.rejects.toml` (SQL-001..015, versioned, contracts §8) + `TtrSqlRejectScanner` — curated forms named from the token stream **before** any bare syntax error (C2-g); single-source messages.
- **Decomposition** (`TtrSqlDecomposer` + `TtrSqlExpr`): CTE = SSA label (E-b inverse); `FROM a JOIN b ON` → `join(left/right ports, C2-b-ii)`; `WHERE` → filter; `GROUP BY`/`HAVING` → aggregate + filter; SELECT → project/calc; DISTINCT/set-ops/VALUES/ORDER BY/LIMIT mapped; **`SELECT *`** expands against known schemas (sentinel otherwise); **EXISTS/IN-subquery** → semi/anti Join; qname `FROM` → `load()`; S15 ordered-LIMIT → **`TTRP-SQL-014`**.
- **Specs:** `TtrSqlDecompositionSpec` (hero node-for-node), `TtrSqlRejectTableSpec` (coverage), `TtrSqlRejectSpec`, `TtrSqlParserSpec` (accept corpus), `TtrSqlStarExpansionSpec`, `TtrSqlKeywordDriftSpec` (S16).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.dialect.sql.*"` — green.

## Stage 6.2 — TTR-pandas dialect — **code-complete**

- **`TTRPandas.g4`** (own grammar, method-chain, lowercase) — S17 full-word roster `select calc filter join aggregate sort union limit load store display`; **`==`** as the ONE closed dialect synonym for `=` (S9). Wired into generation.
- **Reject table** `ttr-pandas.rejects.toml` (PD-001..010) + `TtrPandasRejectScanner` — abbreviations, `.apply`/lambdas, IO, boolean-mask indexing, control flow, index ops, engine-API ceremony, `.branch` all named (S17); S15 mirror = **`TTRP-PD-009`**.
- **Decomposition** (`TtrPandasDecomposer` + `TtrPandasExpr`): the chain **receiver** carries the join left port (the C2-b-ii analogue for chains); assignment = SSA rebind (Q7-γ); last value = default out (C2-c-ii); `==`/`=` fold to the same `op.eq`. The pandas hero decomposes to the **same node shape as the SQL hero** (the 6.3 identity precondition).
- **Specs:** `TtrPandasDecompositionSpec`, `TtrPandasRejectTableSpec`, `TtrPandasRejectSpec`, `TtrPandasParserSpec`, `TtrPandasEqSynonymSpec` (`==` accepted in pandas only; still `TTRP-EQ-001` in canonical + rejected in SQL), `TtrPandasKeywordDriftSpec`.
- `golden/fragments.ttrp` `frag_pd` interior updated to valid TTR-pandas (the dict literal moved into a `#` comment — still byte-preserved for the C2-f spec); AST snapshot regenerated.

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.dialect.pandas.*"` — green.

## Stage 6.3 — Bare-fragment programs — **core delivered; 3 items remain**

**Delivered + tested:**
- **T6.3.2 · Dialect markers** — `DialectMarker` (double extension + first-line comment override; comment wins, C3-g-ii; zero sniffing, P2). `TTRP-FRG-002`/`003`. `DialectMarkerSpec`.
- **T6.3.5 · THE KEY GATE (embedded ≡ canonical)** — `NormalizedGraphJson`, the canonical byte-stable serializer (excludes ids/locations/fragment `sourceText`; includes kinds/params/SSA labels/ports/edges/expression trees). `FragmentGraphIdentitySpec` proves the hero island authored as `"""sql`, as `"""pandas`, and in **canonical TTR-P** compile to **byte-identical normalized graphs** (Join INNER + Aggregate-by-region + agg.sum + Sort + Limit present — non-vacuous). Byte compare, not structural (P2).
- **contracts §8** — `FRG` area + v1.4 changelog entry.

**⏸ Remaining P6 work (NOT done — human/CI-gated, mirrors Phase 5's honest gating):**
- **T6.3.3 + T6.3.4 · Bare-program wrapper synthesis + full C2-d resolution** — a `.ttr.sql`/`.ttr.py` file → a synthesized derived container (name from filename, in-ports + program-level `load`s from external refs resolved via `default-imports` S18, display, err) + the full `FragmentScope` (in-ports > imports > qnames, ambiguity = error, er provenance through fragments). This adds the **third (bare) surface** to the identity gate. It needs careful name alignment across surfaces (the interior's bare `sales` must resolve to the same storage the embedded/canonical loads use) — a bounded but non-trivial resolution task, deferred to keep the delivered surfaces correct.
- **T6.3.6 · `ttrp conform` — hero three ways** — needs a **dockerized Postgres + local Polars**; the seven-point comparison across canonical/embedded/bare. Same environment gate as Phase 3.4 / the Phase-5 acceptance.
- **T6.3.7 · Designer fragment drill-in** — `ttrp/getGraph` on a fragment container + the `@tatrman/ttrp-designer` component test (auto-only, read-only drill-in). Needs the P5 designer stack; the getGraph path already serves derived containers.

## Verification (run by the coder; reviewer re-runs)

- `./gradlew :packages:kotlin:ttrp-frontend:test` — green (incl. all `dialect.sql.*`, `dialect.pandas.*`, `dialect.bare.*`).
- `./gradlew :packages:kotlin:ttrp-graph:test` — green (incl. `FragmentGraphIdentitySpec`; hero explain golden + Hero/ContainerMapping/RewriteEngine specs updated for the decomposed `acc_prep`).
- `./gradlew :packages:kotlin:ttrp-cli:test :packages:kotlin:ttrp-emit:test` — green (verbatim emit ⇒ unaffected).
- `./gradlew :packages:kotlin:{ttrp-frontend,ttrp-graph}:ktlintCheck` — green.
- No generated parser sources committed — only `TTRSql.g4` / `TTRPandas.g4`, build wiring, Kotlin, fixtures, reject tables.

## Notes / deferrals (read before review)

- **S16 resolution is the one design call to sign off** (§ above): Kotlin-hosted shared table + dialect-grammar skins + drift specs, per the `KeywordTable` docstring — not the grammar-import shape the task-list Pre-flight assumed.
- The **bare identity surface** is the main Stage-6.3 gap; the embedded↔canonical byte-identity (2 of 3 surfaces) is proven, and the decomposers already emit the `derivedInPorts` the bare wrapper will consume.
- **A4 exit for fragments** ("hero authored three ways → identical results") is **met in code for two surfaces** (embedded ≡ canonical, byte-identical graphs) and **sealed** only by the bare surface + `ttrp conform` three-ways (the remaining items).
