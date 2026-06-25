# Stage 4A — Cross-target conformance (Kotlin + Python)

Goal: extend the conformance harness so the Kotlin (and Python) parsers read the 3.1 `TTR.g4` and
produce ASTs that match the TS parser on the MD corpus. This is the parity gate.

Prereq: Phases 1–3 DONE; Phase 0 Stage D shipped 3.0. TDD: 4A1 (fixtures) before 4A2–4A4.

References (verified):
- Harness: `tests/conformance/` — `dump.ts`/`dump.py` (AST dump per target), `diff.ts` (AST diff),
  `dump-sem.ts`/`dump_sem.py` + `diff-sem.ts` (semantic dump/diff), numbered fixtures in
  `tests/conformance/fixtures/` (`01-model.ttrm` … `10-attribute.ttrm` …).
- CI: `.github/workflows/conformance.yml`.
- Kotlin parser: `packages/kotlin/ttr-parser` (reads `TTR.g4` directly via the ANTLR Gradle
  plugin); semantics: `packages/kotlin/ttr-semantics`. Build: `./gradlew :packages:kotlin:...:test`.
- Kotlin AST naming parity: `docs/grammar-master/AST-NAMING.md`.

---

- [ ] **4A1 — Conformance fixtures first.** Add numbered `.ttrm` fixtures under
  `tests/conformance/fixtures/` (next free numbers) covering each MD construct: a domain set, a
  dimension with inline attributes, the three map forms, a hierarchy, measures, a cubelet, and the
  four binding kinds. Keep each fixture minimal and deterministic (the dump is byte-compared).

- [ ] **4A2 — TS dump baseline.** Run `tests/conformance` TS dump (`dump.ts` / `run-ts.ts`) over the
  new fixtures to produce `out-ts/`; eyeball the AST JSON for the MD nodes. This is the reference
  the other targets must match.

- [ ] **4A3 — Kotlin parser parity.** Ensure `packages/kotlin/ttr-parser` builds the 3.1 grammar
  (the ANTLR Gradle plugin regenerates from the same `TTR.g4`) and walks the new constructs into the
  matching AST shape (follow `AST-NAMING.md`). `./gradlew :packages:kotlin:ttr-parser:test`. Make
  `diff.ts` (TS↔Kotlin) green on the MD fixtures.

- [ ] **4A4 — Python parity (if in the harness).** Update `dump.py` so the Python reference parser
  walks the MD constructs; make `diff` (TS↔Py) green. (Scope per the existing harness — match
  whatever targets `conformance.yml` already runs.)

- [ ] **4A5 — Semantic conformance (if applicable).** If the semantic dump (`dump-sem`) covers
  cross-target semantics, extend it for the MD diagnostics that are shared contract (not editor-only
  niceties); keep editor-only `md/*` codes out of the cross-target semantic diff if ai-platform
  doesn't emit them.

- [ ] **4A6 — Verify.**
  - `cd tests/conformance && <the harness's run command>` green for AST (+ semantic) diffs on MD
    fixtures.
  - `./gradlew :packages:kotlin:ttr-parser:test :packages:kotlin:ttr-semantics:test`
  - `conformance.yml` passes locally/CI.

- [ ] **4A7 — Commit.** `Section MD-4A: cross-target conformance for MD constructs`.
