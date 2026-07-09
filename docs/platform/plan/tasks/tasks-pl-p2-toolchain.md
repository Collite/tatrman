# PL-P2 (③) — Toolchain half: params + on-failure vocabulary (stage S1)

> Pre-flight: PL-P1 review done; the PL-P0 grammar-master work items for F-4-i/F-4-iv frozen. DoD: [`../plan.md`](../plan.md) §PL-P2. Check each box the moment its task is done. This stage is `tatrman` (MIT) — it closes the "schema permits, toolchain doesn't emit yet" gap left by PL-P1.S3, and it must land before S4 (the executor consumes what this emits).

## S1 · Runtime params + on-failure islands (grammar → manifest v2 emission) {#s1}

Verify: grammar regeneration procedure run (CLAUDE.md); `pnpm -r test` AND `./gradlew build` green; `ManifestV2EmitTest` params/on-failure cases green; conformance corpus round-trips the new surface in TS + Kotlin parsers; `ttrp conform mode-drift` still byte-identical.

- [ ] **T1 (tests first).** Conformance-corpus entries for the F-4-i surface per the PL-P0 spec: program-level `param run_date: date = @run-date` declarations (typed scalar; required-when-no-default) parse in both parsers; negative fixtures (unknown type, duplicate param, `@run-date` on a non-date) error with `TTRP-`-area diagnostics named in the spec. Watch both parsers fail.
- [ ] **T2 (tests first).** Corpus entries for F-4-iv: `on failure of <island>` container surface parses; negative fixtures (`absorbs` → reserved-vocabulary error; on-failure of unknown island; cyclic on-failure) error. FF remains a compile error (`TTRP-CTL-001` untouched — add a regression case).
- [ ] **T3 (tests first).** Extend `ManifestV2EmitTest.kt` (closes the PL-P1.S3.T3 "params absent" state): hero-with-params fixture emits top-level `params[]` (name/type/required/default per contracts §6), per-island `params` consumption lists, `onFailureOf` on the error-consuming island, and per-island `retries` from the program's declared counts. Schema (PL-P0) validates the result.
- [ ] **T4 (test first).** `ExecutorManifestGateTest.kt`: compiling a params/on-failure program against a **bash world** (executor manifest lacks the vocabulary — contracts §7) fails with the ordinary T6 compile error naming the missing capability; against a **tatrman-executor world** it compiles. This is P3 made executable.
- [ ] **T5.** Amend `TTR.g4` with both surfaces per the frozen specs; run the full regeneration procedure; commit grammar + corpus together (separate commit per CLAUDE.md convention).
- [ ] **T6.** Implement resolution + typing of params (`@run-date` builtin; param refs legal in island payloads via the existing expression pipeline) and on-failure edges in the graph builder (an on-failure island is excluded from waves and attached as an error edge — it must NOT extend the happy-path wave levelling).
- [ ] **T7.** Implement manifest v2 emission for `params[]`/`onFailureOf`/`retries` in `ttrp-emit`; regenerate the `manifest-v2-hero.json` designer fixture (PL-P1.S9 program-graph test gains the error-edge case — update it in the same change).
- [ ] **T8.** Run Verify, check tracker boxes, commit `PL-P2.S1: F-4-i params + F-4-iv on-failure (grammar → manifest v2)`.
