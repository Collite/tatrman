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

## Stage 7.2 — Assist finalization — **pending**

authoringContext completeness, diagnostics catalogue, cursor-scoped insertion, eval corpus +
comparator/runner, and the mock-model VS Code demo. The eval **baseline** (T7.2.7) is manual /
off-CI (needs a real model key) and stays deferred.
