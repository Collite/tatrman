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

## Notes / minor smells for the reviewer

- The resolution fixture project links the shared models via a committed **symlink**
  (`resolution/project/models` → ttr-metadata testFixtures, git mode 120000) to avoid
  duplication. Works on macOS/Linux CI; worth a glance for portability.
- Runtime shows a harmless ANTLR **4.11.1 vs 4.13.2** version-mismatch notice on the
  `ttrp-cli` classpath (transitive via ttr-parser) — parsing is unaffected; a version
  alignment is a nice-to-have, not a blocker.
