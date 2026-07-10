# Tasks · P6 · Stage 6.3 — Bare-fragment programs

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

A pure `.ttr.sql` / `.ttr.py` file is a valid TTR-P program (C0 bare-fragment commitment): dialect markers resolved (double extension; comment override, contracts §1), wrapper container + target + shell + display synthesized from `[ttrp]` defaults (bare-target, bare-shell, display-default; **default-imports as the bare-only implicit prelude, S18**), document-scope resolution completed inside fragments (C2-d α: in-ports > imports > qnames; same-level ambiguity = error), and **THE KEY GATE**: the hero island authored bare, embedded, and canonical produces **byte-identical normalized graphs** (canonical-JSON serialization, byte compare). `ttrp-conform` passes the hero authored three ways; Designer drill-in of fragment containers verified through the P5 pipeline at component-test level. Source text of bare fragments is never rewritten (C0) and never formatted (C2-f).

**Alignment notes:** P0/P1 task lists absent at drafting — same assumptions as Stages 6.1/6.2 (module `ttrp-frontend`, Kotest, reject-table conventions). One rule below is **derived, not explicitly ratified** — flag it for `/review` sign-off: *wrapper synthesis gives the derived container one in-port per distinct external table reference, fed by a synthesized program-level `load(<qname>)` leaf* (rationale: C0 says the wrapper is desugaring from defaults; C4-b's TTR-B analogue desugars Store/Display to program-level leaves; and it is the only P2-deterministic shape under which bare and embedded interiors stay byte-identical — which the plan's graph-identity gate demands). If review lands elsewhere (e.g. Loads inside the container), the identity fixtures must move with it, not be deleted.

## Pre-flight (all must pass before T6.3.1)

- [ ] Stages 6.1 + 6.2 DONE: `./gradlew :packages:kotlin:ttrp-frontend:test` green; hero SQL + pandas islands pass `ttrp check`/`explain` embedded.
- [ ] `[ttrp]` manifest reader (Stage 1.3) reads all §2 contract keys incl. `default-imports` (S18) — `grep -rn "default-imports" packages/kotlin/ttrp-frontend/src/main`. If the key is unread, **STOP → Blocker** (Stage-1.3 gap, fix there).
- [ ] Phase 3 runnable end-to-end (`ttrp build` + `ttrp run` + `ttrp conform` on the canonical hero; dockerized PG available for conform).
- [ ] P5 Designer pipeline available: `ttrp/getGraph` serves derived containers (contracts §4: "full graph incl. derived containers for bare fragments"); Designer frontend fork builds (`pnpm --filter @tatrman/designer test` green — scope name per S7; if the P5 rename landed differently, note the actual filter here: `_______`).
- [ ] Locate Stage-2 normalized-graph model + `ttrp explain` serialization entry point (T6.3.5 builds on it): `_______`.

## Tasks

### T6.3.1 · Fixtures + failing specs (TDD anchor)

- [ ] Fixture home `packages/kotlin/ttrp-frontend/src/test/resources/corpus/bare/`, containing a mini-project (its own `modeler.toml`-equivalent with a `[ttrp]` table) so manifest resolution is exercised for real:

```toml
[ttrp]
world           = "acme.worlds.dev"
bare-target     = "erp_pg"
bare-shell      = "bash"
display-default = "arrow"
default-imports = ["erp.*", "files.*"]
```

- [ ] **Bare hero, TTR-SQL** — `corpus/bare/crunch.ttr.sql` (filename `crunch` on purpose: derived container name is filename-derived (S12 analogue), so it matches the embedded fixture's `container crunch`); interior **byte-identical** to the `"""sql` interior of Stage 6.1's `accept/hero-crunch.ttrp` — `accounts`/`sales` now resolve via the default-imports prelude (`erp.accounts`, `files.sales`) instead of in-ports:

```sql
WITH joined AS (
    SELECT accounts.account_id, accounts.customer_id, sales.amount
    FROM accounts
    JOIN sales ON accounts.account_id = sales.account_id
),
sums AS (
    SELECT customer_id, SUM(amount) AS total_amount, COUNT(*) AS sale_count
    FROM joined
    GROUP BY customer_id
)
SELECT customer_id, total_amount, sale_count
FROM sums
ORDER BY total_amount DESC NULLS LAST
LIMIT 100
```

- [ ] **Bare hero, TTR-pandas** — `corpus/bare/crunch.ttr.py`, interior byte-identical to Stage 6.2's `"""pandas` hero interior. **Canonical hero equivalent** — `corpus/bare/crunch-canonical.ttrp`: the same island written in canonical text (`container crunch(in accounts, in sales, out result) target erp_pg { joined = join(left: accounts, right: sales, type: inner, on: left.account_id = right.account_id) -> select(…) … }` — spelling per the Stage-1.1/1.2 grammar), plus the same `load`/wiring/`display` statements as the embedded fixtures. These three + the two embedded heroes (6.1/6.2) are the identity-test inputs.
- [ ] **Marker fixtures:** `corpus/bare/report.sql` with first line `-- ttr: dialect=sql` (comment override serving a generic extension) · `corpus/bare/prep.py` with `# ttr: dialect=pandas` · `corpus/bare/mismatch.ttr.sql` whose first line says `-- ttr: dialect=pandas` (comment overrides extension per C3-g-ii — expect pandas parse, hence rejects; assert the override direction explicitly) · `corpus/bare/unmarked.sql` (no override, generic extension → named diagnostic, not a sniff — P2).
- [ ] **Negative fixtures:** `corpus/bare-no-defaults/` (a second mini-project whose `[ttrp]` lacks `bare-target`) + `crunch.ttr.sql` → expect a named diagnostic (T6.3.3 assigns `TTRP-FRG-002`); `corpus/bare/ambig.ttr.sql` — a name resolvable via two same-level imports (`erp.*` + a second package both exporting `accounts`) → same-level ambiguity error (C2-d-i); `corpus/bare/qname.ttr.sql` — `FROM erp.accounts` full qname (no prelude needed) accepted.
- [ ] **S18 boundary fixture:** `corpus/bare/prelude-leak.ttrp` — a *canonical* document in the same project using a bare `accounts` name with no import statement → must **fail** to resolve (default-imports is the bare-fragment implicit prelude **only**, S18 — it never leaks into `.ttrp` documents).
- [ ] Failing Kotest specs (package `org.tatrman.ttrp.frontend.bare`): `DialectMarkerSpec`, `WrapperSynthesisSpec`, `FragmentScopeResolutionSpec`, `FragmentGraphIdentitySpec` (T6.3.5 names the method roster), `BareUntouchedSpec` (bare files never rewritten/formatted — C0/C2-f: run the formatter entry point over the bare files, assert byte-identity of file content).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.bare.*"` — compiles, red.

### T6.3.2 · Dialect marker resolution (contracts §1)

- [ ] `org.tatrman.ttrp.frontend.bare.DialectMarker` — resolution order (all explicit, zero sniffing, P2): (1) first-line comment override `-- ttr: dialect=sql` / `# ttr: dialect=pandas` (exact forms per contracts §1; tolerate leading whitespace only), (2) double extension `.ttr.sql` → sql, `.ttr.py` → pandas, (3) neither → named diagnostic. Comment **overrides** extension when both present (C3-g-ii).
- [ ] Diagnostics — new area `FRG` (fragment/bare-program): `TTRP-FRG-001` "no dialect marker: use the .ttr.sql/.ttr.py extension or a first-line `-- ttr: dialect=…` comment". **`FRG` is a new area code** → append it to contracts §8's area list and add a contracts changelog entry (contracts header rule) in the same commit.
- [ ] Wire into file-kind routing: `.ttrp` → canonical parse; marked bare file → dialect parse via the 6.1/6.2 parsers (whole file = `sourceText`, `hostOffset = 0`); `.ttrb` untouched (Phase 7).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.bare.DialectMarkerSpec"` green (incl. override-beats-extension and unmarked-generic cases); contracts changelog entry present (`git diff docs/ttr-p/architecture/contracts.md` shows §8 + changelog lines).

### T6.3.3 · Wrapper synthesis from `[ttrp]` defaults

- [ ] `org.tatrman.ttrp.frontend.bare.WrapperSynthesizer` — a marked bare file desugars to (never rewriting source text — the wrapper is derived AST/graph, C0):
  - **Container** named from the filename (`crunch.ttr.sql` → `crunch`; S12 analogue), `target` = `[ttrp] bare-target`, executor = `bare-shell`; missing key → `TTRP-FRG-002` "bare-fragment program needs [ttrp] bare-target (and bare-shell)" — no fallback guessing (P2). Add both to the FRG rows of the contracts §8 note from T6.3.2.
  - **In-ports + Loads (the flagged derived rule — see Alignment notes):** one derived in-port per distinct external table reference resolved by T6.3.4 (name = the referenced short name as written), each fed by a synthesized program-level `load(<resolved qname>)` leaf. Fragment interior decomposition is thereby **identical** to the embedded case (interior sees ports-as-tables).
  - **Display:** the fragment's final result (single default-out, C2-c-i) wires to a synthesized anonymous `Display` (Q11 bare-program default; sink format = `display-default`).
  - **err:** the derived container exposes `err` only (C2-e α); unconnected ⇒ fail-fast (F-d).
- [ ] Derived elements are marked `derived` in the graph model (contracts §4 `ttrp/getGraph` exposes the flag; T6.3.7 renders them).
- [ ] `ttrp explain` on `crunch.ttr.sql` shows: derived container `crunch` @ `erp_pg`, two synthesized Loads, Display, and the island interior with SSA labels `joined`/`sums`.

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.bare.WrapperSynthesisSpec"` green; `ttrp explain corpus/bare/crunch.ttr.sql` output pasted into the progress note; missing-defaults project produces `TTRP-FRG-002`.

### T6.3.4 · Document-scope resolution inside fragments (C2-d α, complete)

- [ ] Implement the full `FragmentScope` (the seam left in T6.1.5/T6.2.5) — resolution order for table-position names inside any fragment, embedded or bare: **(1) container in-ports, (2) document imports** (for bare programs: the `default-imports` implicit prelude, S18, expanded in declared list order), **(3) full qnames**. Same-level ambiguity = error (C2-d-i; position-typed per D-b — `FROM`/`load` position expects storage/model object). Shadowing across levels is silent and deterministic (ports win).
- [ ] Ambiguity diagnostic: reuse the Stage-1.3 resolution-area id if one exists for same-level import ambiguity (check `grep -rn "ambig" packages/kotlin/ttrp-frontend/src/main`); only mint a new id (next free in that area, or `TTRP-FRG-0xx` if resolution has no area) when nothing fits — record the choice in the reject/diagnostic tables.
- [ ] **er refs through fragments (C2-d-ii):** enable/complete the 6.2 `accept/er-refs.ttrp` fixture and add `corpus/bare/er-variant.ttr.sql` (`FROM customer JOIN … ` with `erp.er.*` in `default-imports`): early rewrite + provenance — the decomposed nodes reference db columns (`CUST_TYPE`) while diagnostics/hover name the er attribute (`customerType`) (E-d). Assert both sides on one deliberately-broken er fixture (type error inside the fragment must cite the er name).
- [ ] **Column scope stays closed (C2-d-iii):** fixture where a document *variable* name is used as a column inside a fragment expression → resolution error (variables never reach fragment expressions; C3-a-iv-3).
- [ ] S18 boundary test green (`prelude-leak.ttrp` fails resolution).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "org.tatrman.ttrp.frontend.bare.FragmentScopeResolutionSpec"` green — order, ambiguity error, er provenance, S18 boundary, column-scope closure all asserted.

### T6.3.5 · THE KEY GATE — graph-identity harness

- [ ] **Define the comparison** (this definition is the deliverable — write it as KDoc on the serializer):
  - Input = the **normalized graph** (post-Stage-2.3 normalization, the same artifact `ttrp explain` reports — sugar expanded, er rewritten, placements assigned).
  - Serializer `org.tatrman.ttrp.graph.NormalizedGraphJson.write(graph): String` (lives beside the Stage-2 graph model): canonical JSON — object keys sorted lexicographically; nodes in deterministic order (topological, ties broken by SSA label then port path); edges sorted by (from, port, to, port); expression trees serialized structurally with catalogue ids; UTF-8, `\n` line endings, 2-space indent, no trailing whitespace.
  - **Excluded from serialization:** source locations/ranges, file paths/URIs, fragment `sourceText`, trivia, `derived` flags, and provenance *spans* (er provenance *origins* — `CUST_TYPE ← erp.er.customer.customerType` — are semantic and stay **in**).
  - **Included:** node kinds + params, SSA labels, ports (names, kinds, schemas), edges, container names + targets, expression trees, world/engine placements.
  - Identity = `assertEquals` on the raw strings — **byte compare**, no structural diffing (a structural comparator would hide serializer nondeterminism, which is itself a bug under P2).
- [ ] **The named test:** `FragmentGraphIdentitySpec` (package `org.tatrman.ttrp.frontend.bare`; if the full pipeline isn't visible from `ttrp-frontend`, host it where the Phase-2 `ttrp explain` tests live and note the move). Cases:
  - `"bare .ttr.sql ≡ embedded \"\"\"sql ≡ canonical — hero island, byte-identical normalized graphs"` — compiles `corpus/bare/crunch.ttr.sql`, 6.1's `accept/hero-crunch.ttrp`, and `crunch-canonical.ttrp`; asserts the three serializations are pairwise byte-equal.
  - `"bare .ttr.py ≡ embedded \"\"\"pandas ≡ canonical — hero island"` — same for the pandas trio (canonical comparand: `crunch-canonical.ttrp` retargeted fixture or a `target polars_local` variant — the *interior* must be identical; targets must match within each trio, so keep a `crunch-canonical-polars.ttrp`).
  - `"serializer is deterministic"` — serialize the same compiled graph twice + after a graph-model round-trip; byte-equal.
- [ ] Failure output: on mismatch print a unified diff of the two JSON strings (the diff *is* the debugging artifact — decomposition drift shows up as a named node/param line).
- [ ] Wire the two hero-trio cases into CI as a required check for the `ttrp-frontend` job (this is the phase's regression gate for decomposition drift).

**Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests "*FragmentGraphIdentitySpec"` green. Then mutate one fixture (add a column to the bare SQL SELECT), confirm the spec fails with a readable diff, revert.

### T6.3.6 · `ttrp conform` — the hero three ways

- [ ] Add a conform scenario in `packages/kotlin/ttrp-conform` (follow the Phase-3.4 scenario layout): build + run the hero authored (a) canonical, (b) embedded fragments, (c) bare — `ttrp build` each, `ttrp run` each against the dockerized PG + local Polars world, compare all `out/` + staged Arrow under the seven-point procedure (Q9). Spec: `HeroThreeWaysConformSpec`.
- [ ] Since T6.3.5 already proves graph identity, this test's marginal value is the **bundle/emit path for derived wrappers** (bare programs must produce a complete `<program>.bundle/` — manifest program name = filename, synthesized Loads/Display present in `islands/`/`displays`). Assert the bundle manifest of the bare build lists the same islands/waves as the canonical build.
- [ ] Wire into the CI conform job (same gate as Phase 3.4's).

**Verify:** `./gradlew :packages:kotlin:ttrp-conform:test --tests "*HeroThreeWaysConformSpec"` green locally (with PG container up); CI job includes it.

### T6.3.7 · Designer drill-in of fragment containers (P5 pipeline, component level)

Component-test level only — **not** browser E2E (plan: drill-in "works in the Designer" is verified through the P5 pipeline's existing test seams).

- [ ] **Server side** (Kotlin, `ttrp-lsp` tests, paired-connection harness pattern per the P4/P5 suites): `ttrp/getGraph` on (a) a document with an embedded `"""sql` container and (b) the bare `crunch.ttr.sql` returns the fragment container with its decomposed interior sub-graph, nodes carrying host-document source ranges + er provenance, and `derived: true` on the bare wrapper + synthesized Loads/Display (contracts §4). Spec: `FragmentGetGraphSpec`.
- [ ] **View-state side:** fragment drill-in canvases are **auto-only, read-only** (C1-b-iv) — `ttrp/setLayout` targeting a fragment-derived canvas key is rejected/no-ops with a diagnostic; `.ttrl` never gains manual entries for fragment-interior nodes (their ζ identity is unstable by design). Extend the P5 layout spec with one fragment case.
- [ ] **Frontend side** (Vitest component test in the Designer package, `pnpm --filter @tatrman/designer test`): render the graph payload from (a)/(b) fixtures — fragment container renders as a container node at orchestration level; drill-in shows the derived sub-graph (auto layout, no drag affordance, read-only badge); structured-edit affordances absent inside fragment canvases (C1-d: structured edits never target fragment interiors). Test file: `src/__tests__/fragment-drillin.test.tsx` (adjust to the P5 fork's test layout).
- [ ] Fixture payloads for the frontend test are **generated** from the Kotlin side (dump `ttrp/getGraph` results for the two corpus files into the Designer test fixtures dir via a small conformance-dump-style task — the `ConformanceDump.kt` pattern from `ttr-parser`), so frontend fixtures can't drift from the real protocol.

**Verify:** `./gradlew :packages:kotlin:ttrp-lsp:test --tests "*FragmentGetGraphSpec"` green **and** `pnpm --filter @tatrman/designer test -- fragment-drillin` green.

## Definition of DONE (stage)

- [ ] All T6.3.1–T6.3.7 checkboxes checked; `./gradlew :packages:kotlin:ttrp-frontend:test :packages:kotlin:ttrp-lsp:test :packages:kotlin:ttrp-conform:test` + Designer component tests green.
- [ ] Markers: double extension + comment override behave per contracts §1; no content sniffing anywhere.
- [ ] Wrapper synthesis complete (target/shell/display/default-imports, S18 bare-only proven); bare source text never rewritten or formatted (C0/C2-f specs).
- [ ] C2-d resolution order + same-level-ambiguity error + er provenance + S18 boundary all fixture-proven.
- [ ] **KEY GATE:** `FragmentGraphIdentitySpec` green — bare ≡ embedded ≡ canonical, byte-identical canonical-JSON normalized graphs, wired as a CI gate.
- [ ] `ttrp conform` passes the hero three ways (phase DONE criterion); Designer drill-in component tests green (the other phase DONE criterion).
- [ ] Contracts §8 changelog entry for the `FRG` area committed; the derived in-port/Load synthesis rule flagged for `/review`.

## Blockers

*(record here and STOP: `default-imports` unread by the Stage-1.3 manifest reader · P5 `getGraph` lacks the `derived` flag · normalized-graph model not reachable from a test module · identity gate fails due to a genuine decomposition divergence — that is a 6.1/6.2 bug, fix there, never special-case the serializer)*

## References

- Primary: `docs/ttr-p/design/11-fragments-options.md` (C2-d α + i/ii/iii, C2-e α, C2-f) + control-room C0 bare-fragment entry (wrapper commitments a/b/c)
- Decisions: `docs/ttr-p/design/00-control-room.md` — **S18** (default-imports bare-only), C3-g-ii (marker override), S12 (filename identity), Q11 (bare display default), C1-b-iv (fragment canvases auto-only/read-only), E-d (provenance), F-d (err/fail-fast), Q9 (seven-point conform)
- Contracts: `docs/ttr-p/architecture/contracts.md` §1 (file kinds & markers), §2 (`[ttrp]` keys), §3, §4 (`ttrp/getGraph` derived containers), §8 (+ changelog rule), §9 (conform)
- Architecture: `docs/ttr-p/architecture/architecture.md` §2, §4, §7
- Sibling lists: `tasks-p6-s6.1-ttr-sql.md`, `tasks-p6-s6.2-ttr-pandas.md` (fixture reuse — hero interiors must stay byte-aligned across all three lists)
- Plan: `docs/ttr-p/implementation/v1/plan.md` Phase 6, Stage 6.3 + phase DONE line
