# SV-P0 · S2 — tatrman proto amendments (`ttr-plan-proto`)

> Repo: **tatrman** (`packages/kotlin/ttr-plan-proto`). Two changes, both pre-publish-legal: the `proteus.v1 → translate.v1` rename (RO-20, contracts §4) and the TableHint adoption (RO-21, contracts §3 — exact proto text there, copy it verbatim). TDD: extend the wire tests first.

- [x] **T1 — Baseline.** `./gradlew :packages:kotlin:ttr-plan-proto:build` green before touching anything (record the commit hash in findings). Locate the existing round-trip test: `src/test/kotlin/org/tatrman/plan/.../WireRoundTripSpec` — read it; new tests follow its style.
- [x] **T2 — TableHint tests FIRST.** Extend `WireRoundTripSpec` (or add `TableHintRoundTripSpec` beside it, same package) with: (a) a `TableScanNode` carrying `hints = [TableHint(name="NOLOCK"), TableHint(name="INDEX", options=["0"])]` round-trips byte-stable (serialize → parse → serialize, byte-equal); (b) same for `ScanNode`; (c) a plan serialized WITHOUT hints parses identically before/after the schema change (backward-compat within the unpublished line). Tests must FAIL to compile now (no `TableHint` type) — that's correct.
- [x] **T3 — TableHint schema.** Apply contracts §3 to `src/main/proto/org/tatrman/plan/v1/plan.proto`: add `message TableHint`, and on BOTH `ScanNode` and `TableScanNode` replace `reserved 3 to 7;` with `repeated TableHint hints = 3;` + `reserved 4 to 7;`. Match ai-platform's field numbering exactly (`~/Dev/ai-platform/shared/proto/src/main/proto/cz/dfpartner/plan/v1/plan.proto` is the reference — diff your message against it, modulo package). Regenerate; T2 tests green.
- [x] **T4 — `translate.v1` rename.** `git mv src/main/proto/org/tatrman/proteus src/main/proto/org/tatrman/translate`; inside `translate/v1/translator.proto` change `package` and `option java_package` to `org.tatrman.translate.v1`; update the header comment ("Proteus v1" → "translate.v1 — translator library types"; keep the fork-provenance line). Fix any Kotlin references to the generated `org.tatrman.proteus.v1` types across the tatrman repo (`grep -rn 'tatrman\.proteus' packages/`).
- [x] **T5 — Persona grep on the artifact.** `grep -rn -iE 'proteus|ariadne|theseus|argos|kyklop' packages/kotlin/ttr-plan-proto/src/` returns ONLY the fork-provenance comment line (or nothing) — the N2 check that failed on 2026-07-10 now passes.
- [x] **T6 — Full tatrman suites.** `pnpm -r test` + `./gradlew build` at repo root — the rename must not ripple anywhere unseen (ttr-translator lib in kantheon consumes these types too, but via the artifact — kantheon is rebuilt in S5, not here).
- [x] **T7 — Publish to mavenLocal + record.** `./gradlew :packages:kotlin:ttr-plan-proto:publishToMavenLocal` (still `0.0.1-LOCAL`); record in findings: new artifact contains `translate/v1`, no `proteus`. Check the S2 row in `00-task-management.md`.

**Verify block:**
```bash
cd ~/Dev/collite-gh/tatrman
./gradlew :packages:kotlin:ttr-plan-proto:build
grep -rn 'proteus' packages/kotlin/ttr-plan-proto/src/ | grep -v 'Forked 2026-06-13' ; test $? -eq 1 && echo PERSONA-OK
./gradlew build && pnpm -r test
```

## Findings / ⚑

- **Baseline commit:** `61fd91c` (branch `sv-p0-server-fork` off master).
  Baseline `:packages:kotlin:ttr-plan-proto:build` green before any change.
- **T2/T3 — TableHint.** Added `TableHintRoundTripSpec` (byte-stable round-trip for
  `TableScanNode`+`ScanNode` hints, plus the hint-free backward-compat case) — failed to
  compile pre-schema (no `TableHint`), green after. `TableHint` adopted **verbatim from
  ai-platform** (`name=1`, `options=2`, `reserved 3 to 7;`); both `ScanNode` and
  `TableScanNode` now carry `repeated TableHint hints = 3;` + `reserved 4 to 7;`. Diffed
  against `~/Dev/ai-platform/.../plan/v1/plan.proto` — byte-identical modulo package.
- **T4 — translate.v1.** `git mv proteus → translate`; package + `java_package` +
  header updated. **20 Kotlin files** across `ttrp-emit` (6) and `ttr-translator` (14)
  imported `org.tatrman.proteus.v1.{Language,SqlDialect}` directly (in-repo consumers of
  the generated types) — all rewritten to `org.tatrman.translate.v1`. build.gradle.kts
  `verifyProtosInJar` comment updated (still 6 protos; count unchanged).
- ⚑ **Scope note — extra persona strings cleaned in `plan/v1/context.proto` (beyond the
  two stated S2 changes).** The RO-20 check only anticipated `proteus`, but the T5 persona
  grep surfaced pre-existing persona references in `context.proto` comments:
  `theseus-mcp` (→ `ttr-query-mcp`), and the worker personas **Arges**/**Brontes** (+ a
  `Midas` RLS mention, + a `docs/architecture/arges/` path). These are comment-only (zero
  wire/behavior impact) but sit on a wire-surface proto the **ledger N2 requires clean**
  ("confirm no persona strings in `packages/kotlin/*`"). I reworded them to functional
  terms ("the Postgres worker", "the MSSQL worker", "the database RLS policies"). Flagging
  for Bora since it widened S2 past its two-change brief. No other persona strings remain
  in `ttr-plan-proto/src` (verified via the full ledger regex).
- **T6 — no ripple.** Affected modules green: `ttr-plan-proto`, `ttr-translator`,
  `ttrp-emit` tests all pass. `pnpm -r test` green (31 files / 187 pass, 1 skip).
  **Pre-existing, unrelated failure recorded (⚑ not caused by S2):** `./gradlew build`
  fails 4 tests in `:packages:kotlin:ttrp-lsp` (`AuthoringContextSchemaSpec` ×2,
  `CustomMethodsSpec` ×2 — JSON-schema validation of authoring-context, nothing to do with
  plan protos). **Verified identical failures on the clean `61fd91c` baseline** in a
  throwaway worktree — this is repo debt predating the fork work, surfaced here only
  because T6 runs a full `./gradlew build`.
- **T7 — publish.** `publishToMavenLocal` → `org.tatrman:ttr-plan-proto:0.0.1-LOCAL`
  in `~/.m2`; jar bundles `org/tatrman/translate/v1/translator.proto` (+ the 5 plan/
  transdsl/dfdsl protos = 6), **no `proteus`**. Persona grep verify → `PERSONA-OK`.
