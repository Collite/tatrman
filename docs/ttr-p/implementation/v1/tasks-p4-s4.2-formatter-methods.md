# Tasks · P4 · Stage 4.2 — Formatter + custom methods

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

The canonical-text formatter (γ-style rules C3-a: chain linear runs, introduce names at fan-out/reassignment/multi-out; config-block vs named-args width rule C3-a-iii; fragment interiors **byte-untouchable** C2-f) wired to `textDocument/formatting`, plus the five custom methods `ttrp/transpile`, `ttrp/run`, `ttrp/explain`, `ttrp/validate`, `ttrp/authoringContext` (contracts §4) with the **authoringContext v1 JSON schema finalized and documented** (C4-d/S8 — the C4 leftover closes in this stage) and the document-versioning discipline enforced (stale version ⇒ error, client replays).

## Pre-flight (all must pass before T4.2.1)

- [ ] Stage 4.1 DONE: `./gradlew :packages:kotlin:ttrp-lsp:test` green; `TtrpLspHarness` available via `testFixtures`.
- [ ] Phase-2/3 library APIs callable from `ttrp-lsp`: `ttrp explain` internals (`:packages:kotlin:ttrp-graph` normalized-graph + placements), `ttrp build`/`ttrp run` internals (`:packages:kotlin:ttrp-emit`, bundle assembly + `run.sh`). Record the actual entry-point FQNs in §References before starting.
  Verify: `./gradlew :packages:kotlin:ttrp-graph:test :packages:kotlin:ttrp-emit:test`
- [ ] The front-half exposes lossless trivia (comments) and exact fragment `sourceText` spans (Stage 1.1: "trivia attach — fragment interiors verbatim per C2-f") — the formatter is impossible without both. If either is missing, STOP: that is a Phase-1 defect, file it in §Blockers.

## Tasks

### T4.2.1 · Golden before/after fixture corpus + formatter spec harness (TDD anchor)

Formatter tests are **golden pairs**: `<name>.in.ttrp` + `<name>.expected.ttrp` under `packages/kotlin/ttrp-lsp/src/test/resources/format/`. `FormatterGoldenSpec` discovers every pair, formats `.in`, asserts byte-equality with `.expected`, and additionally asserts **idempotency** (`format(format(x)) == format(x)`) on every `.expected`.

- [ ] Create the corpus with at least these six pairs (contents below are the design intent; adjust *mechanically* to the ratified `TTRP.g4` concrete syntax from Stage 1.1 — e.g. exact config-block separators — without changing what each fixture exercises):

  **`chain-linear.in.ttrp`** — needless intermediate names on a linear run (C3-a: chain it):
  ```ttrp
  a = load(erp.db.accounts)
  b = a -> filter(status = "active")
  c = b -> sort(by: created_at)
  c -> store(erp.db.active_accounts)
  ```
  **`chain-linear.expected.ttrp`**:
  ```ttrp
  load(erp.db.accounts)
    -> filter(status = "active")
    -> sort(by: created_at)
    -> store(erp.db.active_accounts)
  ```

  **`fanout-keeps-name.in.ttrp`** — a name consumed twice must survive (C3-a: name at fan-out); already-canonical text is a fixpoint:
  ```ttrp
  prepped = load(erp.db.accounts) -> filter(status = "active")
  prepped -> aggregate(group: region, total: sum(balance)) -> display(by_region)
  prepped -> store(files.active_accounts)
  ```
  **`fanout-keeps-name.expected.ttrp`** — identical to `.in` (fixpoint test).

  **`reassignment-keeps-name.in.ttrp`** — SSA reassignment forces the name (C3-a; Q7-γ):
  ```ttrp
  sales = load(files.sales_csv)
  sales = sales -> filter(amount > 0)
  sales -> store(erp.db.sales_clean)
  ```
  **`reassignment-keeps-name.expected.ttrp`** — reassignment lines keep `sales`; only spacing/indent normalized (the two assignments must NOT collapse into one chain).

  **`width-rule.in.ttrp`** — C3-a-iii: named args canonical for narrow ops; wide ops (Aggregate, Pivot, Switch, multi-key Join) flip to a config block when the one-line named-arg form exceeds the line-width budget (fix the budget as a named constant, default 100 cols):
  ```ttrp
  j -> aggregate(group: region, group: segment, group: channel, total: sum(balance), avg_bal: avg(balance), n: count()) -> sort(by: total)
  ```
  **`width-rule.expected.ttrp`**:
  ```ttrp
  j
    -> aggregate {
         group: region
         group: segment
         group: channel
         total: sum(balance)
         avg_bal: avg(balance)
         n: count()
       }
    -> sort(by: total)
  ```
  (Blocks compose inside chains — C3-a-iii. A narrow `filter(amount > 0)` must NEVER be block-formatted.)

  **`fragment-untouchable.in.ttrp`** — the load-bearing fixture: an embedded `"""sql` block whose interior (ugly spacing, trailing blanks, `-- comment`) must survive **byte-identical** while the canonical text around it reformats (C2-f):
  ```ttrp
  container crunch(in accounts, out sums)   target erp_pg """sql
    SELECT   region,SUM(balance)AS total,--deliberately  ugly
        COUNT( * ) AS n
    FROM accounts
      GROUP BY region
  """
     acc_prep    ->   crunch.accounts
  crunch.sums -> display(by_region)
  ```
  **`fragment-untouchable.expected.ttrp`** — `container` header and the two wiring lines normalized; every byte between the `"""sql` fence line and the closing `"""` identical to `.in` (write the expected file by copy-pasting the interior, then hand-normalizing only the outside).

  **`source-position-chain.in.ttrp`** — chains legal in source position, formatter discourages (C3-a-iv-2): `join(left: load(f) -> filter(status = "active") -> sort(by: id), right: sales, on: relation acct_sales)` → expected hoists the left chain to a named assignment above the join (the "introduce names at multi-in" rule).

- [ ] `FormatterGoldenSpec` (`org.tatrman.ttrp.lsp.format`): pair discovery, byte-equality assert with a readable diff on failure, idempotency assert.
- [ ] `FragmentPreservationSpec`: property-style (Kotest `checkAll` over the fixture set + generated whitespace mutations of canonical regions): for every input, the byte span of every tagged-block interior in the output equals the input's span content exactly. This is the C2-f regression tripwire, independent of golden pairs.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.format.*"` — specs run, all fail for the right reason (no formatter yet).

### T4.2.2 · Formatter core — statement reflow per C3-a

- [ ] `TtrpFormatter` (`org.tatrman.ttrp.lsp.format`), pure function `format(source: String, uri: String): String` — operates on the **lossless parse** (AST + trivia), never on regex. Deterministic: same input ⇒ same output (C1-d: "formatter-owned, same edit ⇒ same text" — the Designer depends on this in Stage 5.4).
- [ ] Reflow rules (C3-a / C3-a-iv):
  - Build the def/use graph of variables; a variable with exactly one use, no reassignment, and single-in/single-out consumers is **inlined into a chain**; multi-use (fan-out), reassigned (SSA > 1 generation), or multi-out producers **keep their name**.
  - Chain layout: first segment on the statement line, each `->` segment on its own continuation line indented 2 spaces (single-segment chains stay one line if within width).
  - Comments (trivia) survive attached to their statement/argument; a name elimination that would orphan a comment keeps the name instead (lossless beats pretty).
- [ ] Never reorder statements; never rewrite expressions (S9 `==`→`=` is a *diagnostic*, not a format fix); control-edge statements (`b after a`, `a with b`) and `control {}` blocks pass through with indent normalization only (C3-e).

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.format.FormatterGoldenSpec"` — `chain-linear`, `fanout-keeps-name`, `reassignment-keeps-name`, `source-position-chain` pairs green.

### T4.2.3 · Width rule (C3-a-iii) + fragment byte-preservation (C2-f)

- [ ] Width rule: per-op formatter decision — ops in the wide set {Aggregate, Pivot, Switch, Join with >1 key} render as config block when the named-args one-liner exceeds `MAX_LINE = 100`; everything else always named-args. Block interior: one entry per line, 2-space indent relative to the op keyword, closing brace aligned per the `width-rule.expected.ttrp` fixture.
- [ ] Fragment handling: tagged blocks (`"""sql` / `"""pandas` / `"""ttrb`) are opaque byte ranges — the formatter copies `sourceText` verbatim, fences included; indentation of the fence lines follows the container statement, interior bytes never touched (C2-f). Bare-fragment files (`.ttr.sql`, `.ttr.py`, `.ttrb`) are **rejected wholesale**: `format` on a non-`.ttrp` document returns no edits (C2-f: "bare-fragment files never formatted").
- [ ] Idempotency now holds corpus-wide (T4.2.1's assert flips green).

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.format.*"` — entire format package green, including `fragment-untouchable` and `FragmentPreservationSpec`.

### T4.2.4 · Wire `textDocument/formatting`

- [ ] `initialize` now advertises `documentFormattingProvider = true` (no range formatting in v1 — a range that splits a chain or a fence is a correctness trap; document-only, note in server docs).
- [ ] `TtrpTextDocumentService.formatting(DocumentFormattingParams)`: run `TtrpFormatter` on the stored document text, return a single whole-document `TextEdit` when changed, empty list when already canonical (empty ⇒ hosts skip the write — format-on-save friendly). Ignore `FormattingOptions` tab settings (the TTR-P style is fixed, 2-space — document this in the response to nobody: a code comment).
- [ ] For non-`.ttrp` language ids: empty edit list, plus a `window/logMessage` info once per session ("TTR-P fragments are never formatted — C2-f").
- [ ] `FormattingLspSpec` via `TtrpLspHarness`: open `chain-linear.in.ttrp` content → `formatting` request → applying the returned edit yields the `.expected` bytes; a `.ttr.sql` didOpen + formatting → empty.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.format.FormattingLspSpec"`

### T4.2.5 · authoringContext v1 — design and document the bundle schema (C4-d, S8)

This is a real design-and-document task, not a coding task: contracts §7 lists the content inventory but says "concrete schema = plan work item". It closes **here**.

- [ ] Write `docs/ttr-p/architecture/authoring-context.schema.json` — JSON Schema (draft 2020-12), `$id: "https://tatrman.org/schemas/ttrp/authoring-context/v1"`, top-level `{ "version": 1, ... }`, covering exactly the §7 inventory:
  - `world`: `{qname, fingerprint, engines[], executors[], storages[] (incl. staging + rls flags)}` — mirrors `ttrp/getWorld` shapes where they exist.
  - `capabilities`: per engine, node-level + function-level support tables (T6 manifests, serialized).
  - `modelObjects`: in-scope db + er objects with schemas (S23 types) and er→db provenance links (E-d).
  - `scope`: `{imports[], variables[] (with SSA generation + port schema), portsAtCursor[]}` — cursor-dependent, present only when `position` was given.
  - `grammar`: `{specVersion, statementSummary, dialectRosters: {ttrp, sql, pandas, ttrb}}` — TTR-B roster may be a stub until P7; schema field present, content grows.
  - `diagnostics`: the named-diagnostic catalogue `[{id, message, suggestedAlternative}]` (contracts §8 — the repair vocabulary; TTR-B rows land in P7, schema is final now).
  - Every object closed (`additionalProperties: false`) — hosts prompt LLMs with this; determinism is the product (C4-d-ii).
- [ ] Update `contracts.md` §7: replace "(concrete schema = plan work item, leftover C4)" with a pointer to the schema file + one-paragraph field summary. **Add a changelog entry** at the bottom of contracts.md per its changelog rule ("v1.1 · 2026-MM-DD — authoringContext v1 schema finalized (Stage 4.2); §7 now normative via authoring-context.schema.json").
- [ ] Add 2 example instance documents under `docs/ttr-p/architecture/examples/authoring-context/` (hero at cursor-in-container; bare `.ttr.sql` no-cursor) — these become T4.2.6's test fixtures.

**Verify:** `python3 -c "import json,sys; json.load(open('docs/ttr-p/architecture/authoring-context.schema.json'))"` parses; both examples validate: `npx --yes ajv-cli@5 validate --spec=draft2020 -s docs/ttr-p/architecture/authoring-context.schema.json -d "docs/ttr-p/architecture/examples/authoring-context/*.json"`; contracts.md changelog entry present.

### T4.2.6 · Implement `ttrp/validate` + `ttrp/authoringContext`

- [ ] `ttrp/validate` `{source | uri, dialect?} → {diagnostics[]}` (contracts §4): full front-half check of **candidate text** — when `source` is given, check it against the *current document's* project context (world, imports) without touching the document store; `dialect` selects the fragment grammar for bare candidate snippets. This is the assist repair loop's gate (C4-d-ii) — it must be side-effect-free and re-entrant.
- [ ] `ttrp/authoringContext` `{uri?, position?} → bundle` serializing exactly to the T4.2.5 schema (kotlinx-serialization data classes generated to match; add `libs.kotlinx.ser.json` to ttrp-lsp main deps). No-args call returns the project-level bundle (world + capabilities + grammar + diagnostics catalogue, no `scope`).
- [ ] `CustomMethodsSpec` part 1 (via harness `remote`): validate a broken snippet → `TTRP-EQ-001` with suggested alternative; validate the hero → empty; authoringContext at a cursor inside the hero's container → bundle validates against the schema file (load the schema in-test via a JSON-schema validator test-dep, e.g. `net.pwall.json:json-kotlin-schema` — add as testImplementation only) and `scope.portsAtCursor` names the container's ports.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.CustomMethodsSpec"` (validate + authoringContext cases green)

### T4.2.7 · Implement `ttrp/transpile`, `ttrp/run`, `ttrp/explain` + versioning discipline

- [ ] Versioning discipline first (contracts §4: "stale version ⇒ error, client replays"): a shared guard `requireVersion(uri, version)` — if `version != DocumentStore.get(uri).version`, fail the request with `ResponseError(ErrorCodes.ContentModified /* -32801 */, "stale version <v>, current <cur> — replay", data = {uri, requested, current})`. Applied to transpile/run/explain (and Stage-5's applyGraphEdit later). Document the discipline in a KDoc on `TtrpCustomApi`.
- [ ] `ttrp/explain` `{uri, version, node?}` → normalized graph, placements, rewrites applied, island→payload map — delegate to the Phase-2 explain library entry (S4: same data `ttrp explain` prints; the LSP serializes, never recomputes its own semantics).
- [ ] `ttrp/transpile` `{uri, version}` → `{bundlePath, manifest}` — delegate to Phase-3 bundle assembly (= `ttrp build`); bundle lands beside the program (`<program>.bundle/`, S1); the returned `manifest` is the parsed manifest.json object (contracts §5).
- [ ] `ttrp/run` `{uri, version}` → `{runId, exitCode, out[]}` — transpile-if-stale then shell out to `run.sh` exactly as a terminal would (C1-e: one execution path); blocking request in v1 (the Designer/host shows progress; streaming is v2); `out[]` = paths of `out/<name>.<fmt>` display drops; exit contract 0/1/2 passed through (contracts §5). Missing `TTR_CONN_*` surfaces as exitCode 2, never as an LSP error.
- [ ] `CustomMethodsSpec` part 2: explain on the hero returns the island/wave structure (assert island names + wave count against the P2 golden); transpile produces `hero.bundle/manifest.json` (assert `ttrpVersion == 1` + sha256 map non-empty); stale-version call → error code `-32801` with `data.current` correct. `DocumentVersioningSpec`: open v1 → change to v2 → transpile with v1 fails, with v2 succeeds. Gate the *actual run* test (needs PG + python3) behind an env flag: `ttrpConformEnv` — CI job wires it where the Phase-3 dockerized-PG gate already runs; locally it skips with a visible SKIPPED.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "org.tatrman.ttrp.lsp.CustomMethodsSpec" --tests "org.tatrman.ttrp.lsp.DocumentVersioningSpec"` then full: `./gradlew :packages:kotlin:ttrp-lsp:test`

## Definition of DONE (stage)

- Formatter: whole golden corpus green + idempotent; fragment interiors byte-identical under property spec (C2-f); `textDocument/formatting` wired; already-canonical documents produce zero edits.
- All five `ttrp/*` methods of contracts §4 implemented and harness-tested; stale `{uri, version}` rejected with `ContentModified` and replay data on every versioned method.
- `docs/ttr-p/architecture/authoring-context.schema.json` exists, validates its two examples, contracts.md §7 points at it, changelog entry recorded — the C4 leftover is closed.
- `./gradlew build` green repo-wide.

## Blockers

*(record blockers here; none at authoring time)*

## References

- Plan: [plan.md](./plan.md) Phase 4 · Stage 4.2
- Contracts: [`../../architecture/contracts.md`](../../architecture/contracts.md) §4 (methods + versioning), §5 (bundle), §7 (authoringContext inventory), §8 (diagnostics), changelog rule (header)
- Decisions: C3-a (chain/name style rules), C3-a-iii (named-args vs config block, formatter picks per op width), C3-a-iv (source-position chains discouraged), C3-e (control keywords), C2-f (fragment interiors untouchable; bare files never formatted), C4-d-ii (authoringContext + validate pair, LLM outside toolchain), S8 (method names), S4 (`ttrp/explain`), S9 (`==` diagnostic, not a format fix), C1-d (formatter-owned text placement, determinism), C1-e (run = shell out to bundle)
- Stage 4.1 harness: `packages/kotlin/ttrp-lsp/src/testFixtures/.../TtrpLspHarness.kt`
- Kotlin service patterns: `~/Dev/ai-platform` `EXAMPLES.md`
- *(fill in during pre-flight)* explain/build/run library entry FQNs: ____
