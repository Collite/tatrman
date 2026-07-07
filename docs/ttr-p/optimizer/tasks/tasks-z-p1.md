# Z-P1 — Cost model (the W3-verifiable core)

> Pre-flight: Z-P0 done; grammar-master window for S1. DoD: [`../plan.md`](../plan.md) §Z-P1. The W3 numbers below are the **contract of this phase** — everything exists to reproduce them.

## S1 · TTR-M grammar: `schema manifest`, world cost blocks, `def stats` {#s1}

TTR-M-side change (grammar + Kotlin parser). Follow `CLAUDE.md` §Grammar regeneration to the letter (antlr-ng prebuild + TextMate regen + commit rules). Verify: `pnpm --filter @tatrman/parser test` and `./gradlew :packages:kotlin:ttr-parser:test` green.

- [ ] **T1 (tests first, Kotlin).** In `ttr-parser`'s Kotest suite add `ManifestSchemaParseTest`: parse the **contracts §3 example verbatim** (postgres engine-type with `capabilities` + `cost-shapes`); assert node kinds, `Load.csv` variant selector, unit terms (`0.5/row`, `1/byte`, bare fixed) land as structured attributes, not strings.
- [ ] **T2 (tests first, Kotlin).** `WorldCostBlocksParseTest`: parse the contracts §4 world (calibration, prices — empty allowed, `transfer-rates` with `default:` and one pair override, inline `stats {}` in a storage def). `StatsDocParseTest`: parse the sibling `def stats … for <world>` doc; assert `asOf` and `table <storage> / <qname> { rows, bytes }` entries.
- [ ] **T3.** Extend `packages/grammar/src/TTR.g4`: `schema manifest` document kind + `engine-type` def with `capabilities`/`cost-shapes` blocks; cost-term rule `NUMBER ('/' UNIT)?` with `UNIT : 'row' | 'byte'` **only** (reserved units are a *semantic* reject, not lexical — parse `/[a-z-]+`, validate later, so TTRP-OPT-030 can carry a suggestion). Extend `schema world`: `calibration`, `prices`, `transfer-rates` (pair syntax `(a, b): rate`), inline `stats`; new `def stats <id> for <worldRef>` document body.
- [ ] **T4.** Run the regeneration procedure (parser prebuild; TextMate grammar; TS parser tests updated for the new defs with one happy-path fixture each).
- [ ] **T5.** Cut the TTR-M spec-version increment via the grammar-master process (`docs/grammar-master/new-grammar-version-process.md`) — this stage is exactly the process's intended client.
- [ ] **T6.** Update `docs/ttr-p/architecture/contracts.md` changelog (world/file-kinds tables gain manifest + stats kinds, pointer to optimizer contracts §3–4); commit `Z-P1.S1: TTR-M grammar — manifest cost-shapes, world calibration, def stats`.

## S2 · Cost-shape & world model (in `ttr-optimizer`) {#s2}

Verify: `./gradlew :packages:kotlin:ttr-optimizer:test`.

- [ ] **T1 (tests first).** `CostShapeModelTest`: `"parses /row and /byte and fixed terms"` · `"reserved unit /row-in rejected with TTRP-OPT-030 incl. suggestion 'use /row'"` · `"missing shape for capable node kind yields default 1/row + TTRP-OPT-031 info"` · `"variant Load.csv wins over Load for csv-flagged load"`.
- [ ] **T2 (tests first).** `ResolvedEngineTest`: type × instance merge — calibration `cpu-factor` scales cpu terms; `io-rate`/`transfer default` parse `"400MB/s"` → bytes/ms; `memory: "32GB"` → bytes; missing calibration key → named error (no silent defaults, P2). Fixture = the contracts §4 dev world.
- [ ] **T3 (tests first).** `StatsPrecedenceTest`: served snapshot > sibling stats doc > inline `stats{}` > per-kind default — four fixtures, one table per level, assert the winning `rowCount` at each level; `"absence falls through, never errors"`.
- [ ] **T4.** Implement `CostShapes` (per engine-type: kind[+variant] → `ResourceVector(cpuPerRow, cpuFixed, ioPerByte, memPerRow, …)`), reading the S1 parse via `ttr-metadata`'s resolved-world API — **if the resolved-world API lacks manifest content**, add the reader here against the parser's AST and file a ttr-metadata ticket (marker comment `// Z: move into ttr-metadata resolution`).
- [ ] **T5.** Implement `ResolvedEngine` (calibrated rates, memoryBytes, concurrency, prices map) and `TransferRates` (pair lookup with default).
- [ ] **T6.** Implement the stats chain: `StatsProvider(snapshot: StatsSnapshot?, statsDoc, inlineStats, defaults)` exposing `tableStats(ref): TableStats` (never null — the chain ends at defaults) + `provenance(ref)` for explain.
- [ ] **T7.** Green; commit `Z-P1.S2: cost shapes, calibrated engines, stats chain`.

## S3 · Size estimator & makespan evaluator — the W3 golden {#s3}

Verify: `./gradlew :packages:kotlin:ttr-optimizer:test`; the golden test is the phase gate.

- [ ] **T1 (build the fixture).** `HeroFixture.kt` (test sources, reused by every later phase): the 9-node hero graph on `FixtureGraph` (`load_accounts, filter_active, load_sales, filter_amount, join, aggregate, branch, store, display`), dev world per contracts §4, stats: accounts 200 000 rows / 24 000 000 B; sales 10 000 000 rows / 800 000 000 B; default selectivity 0.5 for Filter; join out = min-side-driven 5 000 000 rows @150 B; aggregate out 1 000 rows. Transfer 10 ms/MB default. Cost shapes = contracts §3 postgres + polars tables **verbatim**.
- [ ] **T2 (tests first — THE golden).** `W3GoldenTest` (FunSpec): evaluate the five hand-run assignments and assert **exact** cost-sums in ms — `A(all-PG) = 20 110` · `B(all-Polars) = 6 850` · `C(cut after filter_active) = 6 702` · `D(C + eager-agg) = 5 252` · `E(pre-agg, join in PG) = 5 300`. (These are the precise fixture values; W3's table rounds to ≈. Recompute by hand before ever editing them — tracker rule 7.)
- [ ] **T3 (tests first).** `MakespanGoldenTest`: same fixture, makespan objective — `C = 6 530` (PG prep ∥ CSV read; critical path through Polars chain) · `D = 5 080` · `E = 5 250`; assert `criticalPath` node sequence for C = `[load_sales, filter_amount, join, aggregate, …]`.
- [ ] **T4 (tests first).** `SizeEstimatorTest`: per-edge rows/bytes for the fixture (accounts_f = 100 000 rows / 12 000 000 B; sales_f = 5 000 000 / 400 000 000); stats-provenance surfaces (declared vs default) for explain.
- [ ] **T5.** Implement `SizeEstimator` (topological pass: base tables from `StatsProvider`; per-kind selectivity constants in one visible table `DefaultSelectivities`).
- [ ] **T6.** Implement `DurationModel` (node: vector fold over `ResolvedEngine`; edge: bytes / pair rate) and `MakespanEvaluator` (longest path over data deps + FS/SS precedence with per-engine concurrency from calibration; also returns per-island sums + critical path for explain) and `CostSumEvaluator`.
- [ ] **T7.** Implement `MemEstimator` (per-island working set = Σ mem-vector folds of its nodes; used by Z-P2 capacity filter) with `MemEstimatorTest` (hero all-Polars island vs `memory: 32GB` → fits; shrink fixture memory to 1 GB → doesn't).
- [ ] **T8.** All green including goldens; check the tracker; commit `Z-P1.S3: estimator + evaluators; W3 golden locked`.
