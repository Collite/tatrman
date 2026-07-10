# SV-P0 · S2 — tatrman proto amendments (`ttr-plan-proto`)

> Repo: **tatrman** (`packages/kotlin/ttr-plan-proto`). Two changes, both pre-publish-legal: the `proteus.v1 → translate.v1` rename (RO-20, contracts §4) and the TableHint adoption (RO-21, contracts §3 — exact proto text there, copy it verbatim). TDD: extend the wire tests first.

- [ ] **T1 — Baseline.** `./gradlew :packages:kotlin:ttr-plan-proto:build` green before touching anything (record the commit hash in findings). Locate the existing round-trip test: `src/test/kotlin/org/tatrman/plan/.../WireRoundTripSpec` — read it; new tests follow its style.
- [ ] **T2 — TableHint tests FIRST.** Extend `WireRoundTripSpec` (or add `TableHintRoundTripSpec` beside it, same package) with: (a) a `TableScanNode` carrying `hints = [TableHint(name="NOLOCK"), TableHint(name="INDEX", options=["0"])]` round-trips byte-stable (serialize → parse → serialize, byte-equal); (b) same for `ScanNode`; (c) a plan serialized WITHOUT hints parses identically before/after the schema change (backward-compat within the unpublished line). Tests must FAIL to compile now (no `TableHint` type) — that's correct.
- [ ] **T3 — TableHint schema.** Apply contracts §3 to `src/main/proto/org/tatrman/plan/v1/plan.proto`: add `message TableHint`, and on BOTH `ScanNode` and `TableScanNode` replace `reserved 3 to 7;` with `repeated TableHint hints = 3;` + `reserved 4 to 7;`. Match ai-platform's field numbering exactly (`~/Dev/ai-platform/shared/proto/src/main/proto/cz/dfpartner/plan/v1/plan.proto` is the reference — diff your message against it, modulo package). Regenerate; T2 tests green.
- [ ] **T4 — `translate.v1` rename.** `git mv src/main/proto/org/tatrman/proteus src/main/proto/org/tatrman/translate`; inside `translate/v1/translator.proto` change `package` and `option java_package` to `org.tatrman.translate.v1`; update the header comment ("Proteus v1" → "translate.v1 — translator library types"; keep the fork-provenance line). Fix any Kotlin references to the generated `org.tatrman.proteus.v1` types across the tatrman repo (`grep -rn 'tatrman\.proteus' packages/`).
- [ ] **T5 — Persona grep on the artifact.** `grep -rn -iE 'proteus|ariadne|theseus|argos|kyklop' packages/kotlin/ttr-plan-proto/src/` returns ONLY the fork-provenance comment line (or nothing) — the N2 check that failed on 2026-07-10 now passes.
- [ ] **T6 — Full tatrman suites.** `pnpm -r test` + `./gradlew build` at repo root — the rename must not ripple anywhere unseen (ttr-translator lib in kantheon consumes these types too, but via the artifact — kantheon is rebuilt in S5, not here).
- [ ] **T7 — Publish to mavenLocal + record.** `./gradlew :packages:kotlin:ttr-plan-proto:publishToMavenLocal` (still `0.0.1-LOCAL`); record in findings: new artifact contains `translate/v1`, no `proteus`. Check the S2 row in `00-task-management.md`.

**Verify block:**
```bash
cd ~/Dev/collite-gh/tatrman
./gradlew :packages:kotlin:ttr-plan-proto:build
grep -rn 'proteus' packages/kotlin/ttr-plan-proto/src/ | grep -v 'Forked 2026-06-13' ; test $? -eq 1 && echo PERSONA-OK
./gradlew build && pnpm -r test
```

## Findings / ⚑
_(baseline commit: … · anything unexpected)_
