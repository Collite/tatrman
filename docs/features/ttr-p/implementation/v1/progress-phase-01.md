# TTR-P Phase 1 — Progress (canonical front-half → `ttrp check`)

> Records the developer's claims for the `/review` cadence (reviews verify against
> runtime; `[x]` = intent, not truth — CLAUDE.md). Companion: [`tasks-overview.md`](./tasks-overview.md).
> **Status: code-complete 2026-07-05** — awaiting review. Branch `feature/ttr-p-v1`.

## What Phase 1 delivers

`ttrp check <file>.ttrp` — parse → resolve → typecheck the canonical hero with full
named diagnostics; graph construction is Phase 2. All in the Kotlin `ttrp-frontend`
module (+ a thin `check` dispatch in `ttrp-cli`).

## Stages (all DONE)

| Stage | Commit | Highlights |
|---|---|---|
| 0.1 Scaffold + hygiene | `e187269` | 6 ttrp-* Gradle modules; `TTRP.g4` seed + ANTLR gen (Kotlin-only, G-b); `@modeler/*`→`@tatrman/*` (S7); `kotlin` CI job; design-doc §Open annotations; PUBLISHING.md rows |
| 1.1 Grammar + parser | `ac76984` | Real `TTRP.g4` (γ-hybrid, containers, control, tagged blocks); Kotlin parser wrapper + typed AST + trivia; `TTRP-<AREA>-<NNN>` diagnostics; golden+negative corpus; AST snapshots; error recovery |
| 1.2 Expressions | `ebe9785` | One PL expression IR (`plan.v1.Expression` twin; `AggregateCall` distinct arm; explicit `Cast`; operators as `op.*` catalogue `FunctionCall`s); typing + coercion + 3VL; S16 `KeywordTable` drift test; builtin catalogue |
| 1.3 Resolution | `b34605d` | `[ttrp]` manifest reader (tomlj); world/model binding via **ttr-metadata** (offline, D-g); position-typed qname/import resolution (D-b); er→db rewrite w/ mandatory provenance (E-d); declared schemas (D-c); `ttrp check` CLI |

## Verification (run against runtime — reproduce these)

- `./gradlew build` → green across all 9 Kotlin modules; **zero ANTLR generation warnings**.
- `./gradlew :packages:kotlin:ttrp-frontend:test` → **121 tests green** (deterministic — AST snapshots stable across `--rerun-tasks`).
- `./gradlew :packages:kotlin:ttrp-cli:test` → **6 tests green**.
- `ttrp check` on the resolution hero → exit 0; on `hero_er` → exit 0; on any negative fixture → exit 1.
- **28** resolution negative fixtures cover all 16 WLD/RES/SCH/CFG/MOV ids, each with a non-blank suggested alternative; plus the Stage 1.1 (7) and 1.2 (7) negatives.
- Diagnostics catalogue asserts no id-string collisions (`TtrpDiagnosticIdSpec`).

## Open item for review (scoped, non-fatal)

**er-hero join arm + `on: relation`→join-condition golden are deferred** — the shared
erp-project fixture (ttr-metadata `src/testFixtures/`, contracts §8) deliberately
under-binds the er tier (only `sales_txn`(entity) + `sales_txn.amount` are er2db-bound;
`customer` / `customer.customerType` / `customer_sales` / `sales_txn.{region,branch,customer}`
are intentionally unbound, with `customer.customerType` doubling as ttr-metadata's own
`RES-005` seed). Per protocol the shared model was **not forked**; a bound-`sales_txn`-arm
er-hero shipped instead (full entity→table + attribute→column rewrite with provenance;
`ttrp check` exit 0). **Upstream ask (a ttr-metadata fixture addition) is recorded in the
stage §Blockers and the overview register** — restoring the join-based er-hero + the
join-condition golden is a small follow-up once those bindings land. Needs a Bora call:
add the upstream bindings now, or accept the reduced er-hero for v1 Phase 1.

## Review 001 — resolutions (2026-07-06)

`/review` of S0.1 + S1.1–1.3 → `review-001.md` + `tasks-review-001.md`. Verdict: conditional
pass; DONE bar held at runtime. Fixes applied on this branch (build + 129 frontend / 6 cli
tests green):

- **1.1-A (must-fix, done):** `loc(ParserRuleContext)` computed wrong line/col for multi-line
  `TAGGED_BLOCK` containers (span collapsed onto the opening fence, was snapshot-locked). Now
  uses `offsetToLineCol` like the sibling `loc(Token)`; golden snapshots regenerated (hero
  `acc_prep` 3:0-3:136 → 3:0-7:3); added an explicit `endLine == 7` regression assertion.
- **1.2-A / 1.2-C / 1.2-F (done):** predicate-bool (TYP-001) is now enforced through the wired
  `ttrp check` pipeline for `filter`/`branch`/`join`; nested op-calls in source position
  (`filter(load(x), …)`) no longer emit a spurious FN-001; aggregates in a non-aggregating op's
  config (`sort { … }`) now raise AGG-001. All backed by wired-pipeline specs. **Deferral noted:**
  the op→predicate-position and op→aggregate-config tables in `TtrpFrontend` are a conservative
  front-half mirror; **Stage 2.1's T10 node set is the authoritative source and supersedes them.**
- **1.2-B / 1.2-E (done):** `date → timestamp/datetime` implicit widening implemented; `integer`
  implicit widening restricted to `decimal`/`number` (no `integer → double/float`, Q9-4). Golden +
  negative coverage added.
- **1.2-D / 1.3-D (done):** expression and resolution negative specs now assert the EXACT diagnostic-id
  set (was `shouldContain`), catching spurious extras. The curated 28-fixture resolution corpus is the
  canonical exact-set guard.
- **1.3-A (claim corrected):** the `on: relation` → join-condition **Expression synthesis is NOT
  implemented** — `dbSpelling` is a placeholder string. RES-004 endpoint validation IS real/tested.
  Deferred with the upstream ttr-metadata `customer_sales` joinPair bindings. Code comment + this note
  replace the earlier "implemented as far as the model allows" wording.
- **1.3-B (claim corrected):** `TtrpDiagnostic.render()` does not consult provenance; in Phase 1 typing
  stays er-named, so diagnostics already show er spellings and there is no db-spelled diagnostic to
  re-render. `ErRewrite.renderErFirst()` is the E-d rendering primitive (tested as such); wiring it into
  the diagnostic pipeline is deferred to Phase 2.
- **1.3-C (documented + upstream-blocked):** package matching uses the source-file path because
  ttr-metadata leaves `qname.package` EMPTY for db/er objects (only world objects populate it — verified).
  Centralized into `pathInPackage` (boundary-safe); the `import erp.*`-reaches-`erp.er` wildcard question
  is a D-b Stage-2.1 decision needing an upstream metadata change. See tasks-overview §Blockers.
- **1.3-E / 1.3-G / 1.1-B (done):** added a spec that drives the hero through the committed `models`
  symlink (default `manifest.modelsRoot()` path — was uncovered); added `staging`/`assist-provenance`
  manifest value coverage; pinned that reserved literals `true/false/else` as assignment targets are a
  grammar-level PRS-001 syntax error (not PRS-005) — a conscious split, now documented + tested.
- **1.3-F (disclosed):** the task-named `NameResolver`/`ErRewriter`/`SchemaResolver` are folded into
  `TtrpChecker.kt` (~600 lines). Functional, but a structural divergence from the task plan; splitting
  before Stage 2.1 grows the file is advisable.

## Notes / minor smells for the reviewer

- The resolution fixture project links the shared models via a committed **symlink**
  (`resolution/project/models` → ttr-metadata testFixtures, git mode 120000) to avoid
  duplication. Works on macOS/Linux CI; worth a glance for portability.
- Runtime shows a harmless ANTLR **4.11.1 vs 4.13.2** version-mismatch notice on the
  `ttrp-cli` classpath (transitive via ttr-parser) — parsing is unaffected; a version
  alignment is a nice-to-have, not a blocker.
