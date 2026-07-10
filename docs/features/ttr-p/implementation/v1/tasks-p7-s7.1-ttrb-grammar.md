# Tasks · P7 · Stage 7.1 — TTR-B grammar

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

`TTRB.g4` + the Kotlin parse→decompose pipeline for the **third fragment dialect** (C4-a = α): the C2 regime inherited wholesale — each sentence decomposes to node(s) of the standard set, document scope flows in, `err` only, single default-out, formatter never touches interiors, own ANTLR grammar. Concretely: the bare `.ttrb` hero **parses, compiles, and runs** (first half of the plan's P7 DONE); the embedded `"""ttrb` variant is graph-identical; the **reject table** (out-of-roster → repair suggestions, C4-b-iii) and the **verbose-expression synonym table** (C4-c = β) ship as versioned fixtures (contracts §8 regime) ready to become the assist layer's repair vocabulary in Stage 7.2.

## Pre-flight (all must pass before T7.1.1)

- [ ] Phase 6 merged (Stages 6.1–6.3): `TTRSql.g4`/`TTRPandas.g4` decomposers, bare-fragment wrapper synthesis (`[ttrp]` defaults, S18), reject-table pattern, graph-identity test harness. Verify: `./gradlew :packages:kotlin:ttrp-frontend:test :packages:kotlin:ttrp-conform:test` green.
- [ ] **Alignment note:** `tasks-p6-*.md` and `tasks-p4-s4.2-formatter-methods.md` did **not exist** when this list was drafted — this list aligns to `plan.md` Phases 4/6. If those lists exist when you start, reconcile fixture homes, decomposer module placement, and reject-table file format with them first; record any divergence under §Blockers.
- [ ] Canonical tagged-block lexing (P1 Stage 1.1) accepts the `"""ttrb` fence tag (contracts §1, C4-f — Bora: explicit long tag, not `"""b`). If the tag set is closed in `TTRP.g4`, extending it is part of T7.1.3 — confirm which now.
- [ ] Confirm the P0 grammar-generation wiring for TTR-P grammars: `plan.md` Phase 0 mentions an antlr-ng task, but TTR-P is Kotlin-only (G-b) and the shipped precedent is the Gradle ANTLR plugin reading the `.g4` directly (`packages/kotlin/ttr-parser/build.gradle.kts`). `TTRB.g4` must mirror whatever P0/P1 actually wired for `TTRP.g4` — identify that mechanism and note it here before T7.1.3.
- [ ] `ttrp` CLI (`check`/`explain`/`run`, S2) operational from P3: `./gradlew :packages:kotlin:ttrp-cli:run --args="check <any P1 hero fixture>"` exits 0.

## Tasks

### T7.1.1 · TDD: positive fixtures + failing spec skeletons

The v1 sentence roster (C4-b, transcribed from `12-nl-options.md` — this table is the normative statement set; the specs must cover every row):

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
| `Sort [the rows] by a [descending].` | Sort | *(new)* |
| `Keep [only] the first <n> rows.` | Limit | *(new)* |
| `Combine/Append that with <name>.` | Union | *(new)* |
| `Store that/[the] result to file "…" / <model-ref>.` | Store (program-level leaf via desugar) | `output` |
| `Show/Display [me] [the] result [as <name>].` | Display | `browse` |

- [ ] Create fixtures dir `packages/kotlin/ttrp-frontend/src/test/resources/ttrb/`. (If P6 placed dialect fixtures elsewhere, use that home and note it here.)
- [ ] Write `ttrb/hero-sentences.ttrb` with EXACTLY this content (the bare hero — exercises Load×2, Join with names + verbose `is`, implicit anaphora, verbose skin + canonical mixing, Aggregate, Sort with ref-word, Limit, Display + `as`-naming):

  ```
  # hero-sentences.ttrb — the hero island in TTR-B (C4-b roster; # comments per S19)
  # Bare program: wrapper synthesized from [ttrp] bare-target / bare-shell /
  # display-default / default-imports (S18, C4-b-iv).
  Load accounts as accounts.
  Load from file "data/sales.csv" with schema erp.sales_schema as sales.
  Join accounts with sales on account_id is sales.account_id as joined.
  Keep only the rows where amount is more than 0 and customer is not empty.
  Summarize sum of amount as total by region.
  Sort that by total descending.
  Keep only the first 10 rows.
  Show the result as region_totals.
  ```

- [ ] Write `ttrb/hero-embedded.ttrp`: a canonical program with `container region_totals target <hero PG engine> """ttrb … """` whose fence body is **byte-identical** to the island sentences of `hero-sentences.ttrb` (lines 4–11 above, minus the bare-program header comments), wired to a Display leaf. Copy the scaffold (world pin, imports, wiring) from the P6 embedded-SQL fixture verbatim — the load-bearing content is the fence body. TTR-B is engine-agnostic: the container carries the target (C4-f).
- [ ] Write `ttrb/roster-sentences.ttrb`: one sentence per roster row not already in the hero, exactly: `Keep only the columns region, total.` · `Keep all columns except internal_id.` · `Take only the rows where region is one of ('N', 'S').` · `Remove the rows where region is empty.` · `Rename total to region_total.` · `Convert region to string.` · `Compute amount * 0.21 as vat.` · `Combine that with archive_sales.` · `Store that to file "out/totals.csv".` (Preceded by a `Load` so refs resolve.)
- [ ] Create failing Kotest specs (FunSpec, mirroring `packages/kotlin/` conventions; JVM 21, `useJUnitPlatform`) under `packages/kotlin/ttrp-frontend/src/test/kotlin/org/tatrman/ttrp/frontend/ttrb/`:
  - `TtrbParserSpec` — every fixture sentence parses; one test per roster row; synonym variants (`Keep/Take/Select`, C4-b-ii α: full breadth kept) each parse to the same rule.
  - `TtrbDecompositionSpec` — sentence→node expectations (filled in T7.1.5).
  - `TtrbBareProgramSpec` — wrapper synthesis + graph identity (filled in T7.1.7).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.ttrb.*"` — compiles, runs, and the new specs are RED (fail because `TTRB.g4` doesn't exist yet — the TDD baseline).

### T7.1.2 · Versioned tables + negative fixtures

Both tables are **versioned fixtures under the contracts §8 regime** (test fixtures AND the assist repair vocabulary consumed in Stage 7.2 — do not inline them in Kotlin code).

- [ ] Write `packages/kotlin/ttrp-frontend/src/test/resources/ttrb/ttrb-verbose-synonyms.yaml` — the C4-c = β **closed** synonym table over the one PL expression grammar (T5-e): same trees, precedence, catalogue ids, 3VL; canonical spellings also accepted, mixing allowed; **never NLP, never fuzzy** (P2). The four decision-fixed entries come from the C4-c ruling; the remainder is derived from the Byx ancestor (`../../examples/byx/ByxParser.g4` `verbose_eq…verbose_ge`, `verbose_in`) pruned to v1. Canonical spellings MUST be transcribed from the S16 shared keyword/operator table (P1 Stage 1.2) — never invented; the two `CHECK` rows must be resolved against it before merge:

  ```yaml
  # ttrb-verbose-synonyms.yaml — C4-c closed synonym table (versioned fixture, contracts §8 regime)
  version: 1
  entries:
    - verbose: ["is more than", "is bigger than", "is larger than", "is higher than", "comes after"]
      canonical: ">"          # decision-fixed (C4-c)
    - verbose: ["is less than", "is fewer than", "is lower than", "is smaller than", "comes before"]
      canonical: "<"          # decision-fixed ("comes before" named in C4-c)
    - verbose: ["is not more than", "comes not after"]
      canonical: "<="
    - verbose: ["is not less than", "comes not before"]
      canonical: ">="
    - verbose: ["is", "is equal to", "equals", "is the same as"]
      canonical: "="          # S9: the one equality
    - verbose: ["is not", "is not equal to", "does not equal"]
      canonical: "<S16-inequality-spelling>"   # CHECK: transcribe from the S16 shared table
    - verbose: ["is one of"]
      canonical: "IN"         # decision-fixed (C4-c)
    - verbose: ["is empty"]
      canonical: "IS NULL"    # decision-fixed (C4-c)
    - verbose: ["is not empty"]
      canonical: "IS NOT NULL"
    - verbose: ["is between … and …"]
      canonical: "<S16/T5-c BETWEEN form>"     # CHECK: confirm the one-grammar spelling or drop from v1
  ```

- [ ] Write `packages/kotlin/ttrp-frontend/src/test/resources/ttrb/ttrb-reject-table.yaml` — out-of-roster sentences as **named-diagnostic rejects with repair suggestions** (C4-b-iii; contracts §8: every rejected form carries a suggested alternative). IDs are proposals; stable once merged:

  ```yaml
  # ttrb-reject-table.yaml — versioned fixture; doubles as the assist repair vocabulary (C4-b-iii, C4-d)
  version: 1
  area: B                      # TTRP-B-<NNN>
  entries:
    - id: TTRP-B-001
      trigger: "sentence-initial UPDATE"
      message: "TTR-B has no update — data writes are Store."
      suggestion: "Store <name> to <model-ref>."
    - id: TTRP-B-002
      trigger: "sentence-initial INSERT"
      message: "TTR-B has no insert — data writes are Store; row addition is Combine."
      suggestion: "Combine <name> with <name>. / Store <name> to <model-ref>."
    - id: TTRP-B-003
      trigger: "sentence-initial DROP | TRUNCATE | ALTER"
      message: "TTR-B has no DDL."
      suggestion: "Model changes belong in TTR-M; data writes are Store."
    - id: TTRP-B-004
      trigger: "sentence-initial word not in the verb roster (catch-all)"
      message: "Not a TTR-B sentence. The v1 roster: Load, Keep/Take/Select, Remove/Delete, Rename, Convert/Retype, Create/Compute, Summarize, Join, Sort, Combine/Append, Store, Show/Display."
      suggestion: "<nearest roster row is NOT guessed — P2; the full roster is the suggestion>"
    - id: TTRP-B-005
      trigger: "'//' or '/*' comment lexis"
      message: "TTR-B comments use # (S19)."
      suggestion: "Replace // with #."
    - id: TTRP-B-006
      trigger: "non-ASCII letter in sentence-verb position (deterministic check)"
      message: "TTR-B v1 is English-only (S20)."
      suggestion: "Write the sentence with the English v1 roster."
    - id: TTRP-B-007
      trigger: "comparison phrase not in ttrb-verbose-synonyms.yaml"
      message: "Not in the TTR-B verbose comparison table (C4-c — closed, never fuzzy)."
      suggestion: "Use a listed verbose form or the canonical operator."
    - id: TTRP-B-008
      trigger: "sentence-initial PIVOT"
      message: "Pivot authoring is canonical-DSL-only in v1 (C2-b)."
      suggestion: "Author the Pivot node in canonical TTR-P."
  ```

- [ ] Write negative fixtures (one file per diagnostic, exact contents):
  - `ttrb/reject-update.ttrb`: `Load accounts as accounts.` ⏎ `Update accounts set status to 'closed'.` → expect `TTRP-B-001` + its suggestion string.
  - `ttrb/reject-comment-style.ttrb`: `// C-style comment` ⏎ `Load accounts.` → expect `TTRP-B-005` (S19).
  - `ttrb/reject-czech.ttrb`: `# S20 negative` ⏎ `Shrň součet za region.` → expect `TTRP-B-006` (non-ASCII verb position; note: ASCII non-English words fall through to `TTRP-B-004` — deterministic, no language detection, P2).
  - `ttrb/reject-double-eq.ttrb`: `Load accounts.` ⏎ `Keep the rows where status == 'open'.` → expect `TTRP-EQ-001` (S9: `==` rejected everywhere except TTR-pandas; suggestion "use =").
  - `ttrb/reject-unknown-verbose.ttrb`: `Load accounts.` ⏎ `Keep rows where amount is roughly 100.` → expect `TTRP-B-007`.
- [ ] Create failing `TtrbRejectTableSpec`: loads the YAML table, asserts each negative fixture produces exactly its diagnostic id AND the suggestion text from the table (single source — the spec must read expectations from the YAML, not duplicate strings).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.ttrb.TtrbRejectTableSpec"` — RED (parser absent), fixtures + tables load without YAML errors.

### T7.1.3 · `TTRB.g4` + generation wiring

- [ ] Author `packages/grammar/src/TTRB.g4` beside `TTR.g4`/`TTRP.g4` (architecture §6 — canonical sources live together). Requirements:
  - Case-insensitive lexer (`options { caseInsensitive = true; }` — the Byx ancestor `ByxLexer.g4` precedent; sentence-initial capitals are convention, not grammar).
  - `#` line comments → hidden channel (S19); **no** `//`/`/* */` rules (they must reach the reject path, T7.1.6); first-line override marker `# ttr: dialect=b` is host-side (contracts §1), not a grammar rule.
  - English-only keyword set (S20) — the verb/helper rosters from the C4-b table; keep full synonym breadth (C4-b-ii = α: `Keep/Take/Select…`, `where/which/that/with`) and the noise words (`the`, `only`, `me`, `rows/records/lines`) as optional tokens, per the Byx ancestor shapes (`ByxParser.g4`: `select`, `negative_select`, `filter`, `summarize`, `join`, `input`, `output`, `browse`) **pruned to v1**: drop Byx's vendor `db_word` list, `win_path`, `lookup/insert/replace/read/write` verbs; file refs = quoted strings only; model refs = qnames resolved by document scope.
  - Sentence terminator `.`; one statement per sentence; add `sort`, `limit`, `combine` rules (the three C4-b additions).
  - Ref-words: `that/this/it` + implicit subject (C4-b-i = α). **In-stage decision (control-room leftover):** final ref-word roster (does v1 keep Byx's `these/those`?) and the `call it <name>` shape (suffix clause vs standalone sentence) — decide here, record the ruling in a comment header of `TTRB.g4` AND in the roster table of T7.1.1.
  - Expressions: embed the one PL expression grammar the same way `TTRSql.g4` did in P6 (import vs rule duplication — mirror that mechanism exactly), extended with verbose-comparison alternatives generated 1:1 from `ttrb-verbose-synonyms.yaml` rows (closed set — every alternative names its table row; canonical operators remain valid alternatives, C4-c = β).
- [ ] Wire Kotlin generation in `packages/kotlin/ttrp-frontend/build.gradle.kts`, mirroring the mechanism confirmed in Pre-flight (precedent: `packages/kotlin/ttr-parser/build.gradle.kts` — AntlrTask sourced on the canonical `.g4` directly, no copy/sync; `-visitor -package org.tatrman.ttrp.frontend.generated.ttrb`; keep the flat-output note and the `antlr`-configuration-stripped-from-`api` fix).
- [ ] If Pre-flight found the `"""ttrb` fence tag not yet accepted by the canonical lexer, extend `TTRP.g4`'s tagged-block tag set now and regenerate per the P0 wiring.
- [ ] Kotlin wrapper: `TtrbParser` (parseString/parseFile → sentence list + diagnostics), registered in the dialect registry exactly like the P6 `TTRSql`/`TTRPandas` entries (extension `.ttrb`, fence tag `ttrb`, comment lexis `#`).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:generateGrammarSource :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.ttrb.TtrbParserSpec"` — GREEN (all roster/synonym parse tests pass).

### T7.1.4 · Verbose expression skin → PL expression IR

- [ ] Implement the lift: TTR-B expression parse trees → the one PL expression IR (T5), driven by `ttrb-verbose-synonyms.yaml` loaded at build/test time — the table is the single source; no mapping logic outside it (C4-c: "it's grammar, not free text").
- [ ] Canonical spellings and verbose forms may mix in one predicate (C4-c = β ruling); the author's text stays as written — `sourceText` is canonical, the lift is derived (C2-f).
- [ ] `TtrbVerboseExpressionSpec` (new, same package):
  - Per table row: `amount <verbose form> 0` lifts to the identical IR tree as `amount <canonical op> 0`.
  - Mixing: `amount is more than 0 and status = 'open'` — one tree, canonical ops throughout.
  - 3VL: `customer is not empty` → `IS NOT NULL` node, same typing as canonical.
  - Property test (Kotest property module, per T2.3's rewrite-determinism precedent): for every table row × {int, string, date} literal operands, lift is deterministic and total.
- [ ] Resolve the two `CHECK` rows in the YAML against the S16 shared table; update fixture + tests; bump nothing (still `version: 1` pre-merge).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.ttrb.TtrbVerboseExpressionSpec"` — GREEN.

### T7.1.5 · Sentence→node decomposition, anaphora, SSA naming

- [ ] Implement the decomposer beside the P6 dialect decomposers (same module/package layout): each sentence → node(s) per the C4-b "Maps to" column (T7.1.1 table). Full decomposition to the standard node set (C2 regime): `Keep columns` → Project; `except` → Project (negative, static schema expansion); `Remove rows where e` → Filter(NOT e); `Rename` → Project sugar; `Convert` → Calc/Cast sugar; `Compute` → Calc; `Summarize` → Aggregate (AggregateCall arms); `Sort … descending` → Sort (NULLS LAST discipline arrives at emit, Q9-3); `first n rows` → Limit (**ordered-input rule S15 applies** — Limit without a preceding Sort in the island = the same compile error TTR-SQL raises, reuse that diagnostic); `Combine` → Union (list ports `in1..inN`, S11).
- [ ] Anaphora (C4-b-i, deterministic, P2-clean, grammar-resolved): `that/this/it` AND the implicit subject = the previous sentence's default out. First sentence with an implicit subject = error (nothing to refer to — reuse/mint a `TTRP-B-0xx` row in the reject table if no diagnostic exists). Multi-input sentences (`Join`, `Combine`) reference bound names.
- [ ] `as <name>` / `call it <name>` (shape per T7.1.3 ruling) binds an SSA variable (Q7-γ): re-`Load … as sales` after `sales` exists = SSA reassignment; labels survive as graph labels → CTE names (E-b) and ζ canvas keys.
- [ ] Island discipline: statement order = pipeline order; one island per fragment; final sentence's result = single default-out (C4-b-iv, C2-c-i); `err` only. Document scope flows in: in-ports > document imports > qnames (C2-d) — `accounts` in the hero resolves via scope, `erp.sales_schema` via qname.
- [ ] `Store`/`Display` in a **bare** `.ttrb` program desugar to program-level leaves outside the island (C4-b-iv — the C0 wrapper-synthesis case); embedded fragments reject `Store`/`Display`? No — decide per P6 precedent for bare-vs-embedded sink handling and mirror it exactly; record the ruling here.
- [ ] Fill `TtrbDecompositionSpec`: hero fixture → assert exact node-kind sequence `[Load, Load, Join, Filter, Aggregate, Sort, Limit, Display]`, edge shape, SSA labels {`accounts`, `sales`, `joined`, `region_totals`}, anaphora edges (Sort input = Aggregate out; Filter input = Join out).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.ttrb.TtrbDecompositionSpec"` — GREEN.

### T7.1.6 · Reject diagnostics wiring

- [ ] Custom ANTLR error strategy/listener for TTR-B mapping parse failures to `ttrb-reject-table.yaml` rows by trigger (sentence-initial token class, comment lexis, non-ASCII verb position, unknown verbose phrase) — table-driven, catch-all `TTRP-B-004`. Every diagnostic carries the table's suggestion string in the framework's suggested-alternative field (P1 Stage 1.1 diagnostic framework; contracts §8).
- [ ] `==` inside TTR-B expressions → `TTRP-EQ-001` (S9 — the existing canonical diagnostic, not a new B id).
- [ ] Byte-precise spans on all diagnostics (C2-g discipline — provenance/hover).
- [ ] Bare-file marker handling: `.ttrb` extension primary; `# ttr: dialect=b` first-line override honored by the dialect registry (contracts §1).
- [ ] Make `TtrbRejectTableSpec` (T7.1.2) GREEN — all five negative fixtures produce their exact ids + suggestions read from the YAML.

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.ttrb.TtrbRejectTableSpec"` — GREEN.

### T7.1.7 · Bare + embedded integration — the hero runs

- [ ] Register `.ttrb` in the P6 Stage 6.3 wrapper-synthesis machinery (bare program = container + `[ttrp] bare-target` + `bare-shell` + `display-default` sink + `default-imports` prelude, S18) — registration only; no new synthesis logic.
- [ ] Fill `TtrbBareProgramSpec`:
  - Graph identity (the P6 pattern): `hero-sentences.ttrb` (bare, wrapper-derived) and `hero-embedded.ttrp` produce **byte-identical normalized island graphs** (modulo the synthesized wrapper vs authored container shell); assert against a canonical-authored equivalent fixture.
  - Fence-body byte identity between the two fixtures (guards fixture drift).
- [ ] Formatter untouchability (C2-f): format `hero-embedded.ttrp` via the P4 formatter → fragment interior bytes unchanged, fences included; `hero-sentences.ttrb` refuses formatting entirely (bare-fragment guarantee). Add to the P4 formatter spec or here — wherever P6 put the equivalent SQL test, mirror it.
- [ ] CLI end-to-end against the hero world (P3 conform environment, dockerized PG + Polars):
  - `ttrp check hero-sentences.ttrb` → exit 0, no diagnostics.
  - `ttrp explain hero-sentences.ttrb` → shows the derived container, island, `[ttrp]` bare-target placement.
  - `ttrp run hero-sentences.ttrb` → exit 0, `out/region_totals.arrow` present.

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test :packages:kotlin:ttrp-conform:test` GREEN, then the three CLI commands above via `./gradlew :packages:kotlin:ttrp-cli:run --args="…"` (or the installed `ttrp` binary) with exit codes 0/0/0.

## Definition of DONE (stage)

- [ ] All `org.tatrman.ttrp.frontend.ttrb.*` specs GREEN; `./gradlew build` GREEN (ktlint included).
- [ ] Bare `.ttrb` hero **parses, compiles, runs** (plan P7 DONE, first half) — `ttrp run` exit 0 with the Arrow display drop.
- [ ] `ttrb-reject-table.yaml` + `ttrb-verbose-synonyms.yaml` committed as versioned fixtures, consumed by tests via single-source loading (they feed Stage 7.2's diagnostics catalogue + repair vocabulary untouched).
- [ ] The two grammar-prototype leftovers (ref-word roster, `call it` shape) decided and recorded in `TTRB.g4` header + T7.1.1 table (close the `12-nl-options.md` §Leftover items).
- [ ] No per-host or TS-side language logic added (G-b: TTR-P is Kotlin-only; the VS Code `.ttrb` registration already landed in P4 Stage 4.3).

## Blockers

*(record here — STOP on: P6 machinery absent/divergent, S16 table missing the inequality/BETWEEN spellings, fence-tag lexing not extensible, hero world environment unavailable for `ttrp run`)*

## References

- **Primary:** `../../design/12-nl-options.md` (C4 catalogue + Converged block) · `../../design/00-control-room.md` decisions C4-a…C4-f, S19, S20, S9, S15, S18, Q7-γ.
- `../../architecture/architecture.md` §2 (surface tiers), §3 (node set, expressions), §6 (grammar/component homes), §9 (diagnostics tables as fixtures).
- `../../architecture/contracts.md` §1 (file kinds, `"""ttrb`, comment lexis), §3 (fragment regime), §8 (diagnostics convention).
- Byx ancestor: `../../examples/byx/ByxParser.g4`, `ByxLexer.g4`, `VerboseExpressionParser.g4`, `test_some_gram.txt` (sentence style), `test_gram_errors.txt`.
- Build precedent: `packages/kotlin/ttr-parser/build.gradle.kts` (Gradle-ANTLR-reads-canonical-`.g4` pattern, flat-output + `api` notes) · repo `CLAUDE.md` §Grammar regeneration.
- `plan.md` Phase 7 / Stage 7.1; Phase 0 (generation wiring), Phase 6 (dialect task patterns — per-stage lists absent at drafting).
