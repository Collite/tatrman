# Review 064 — ttr-translator extraction A1 + A2 (pre-publish)

> Scope: `docs/ttr-translator/` Stage A1 (`0aed53f`) + Stage A2 (`4665b55`), reviewed before cutting A3 / `kotlin-translator/v0.1.0`. Companion task list: none needed — all findings fixed in-review (see §Resolution). Serial: 064 (project-wide numbering).

## Verdict

**A1 + A2 are sound and the extraction is genuinely parity-proven; ship-ready on the code/build axis.** One real defect in the normative docs (would have broken kantheon Phase B) plus cosmetic nits — **all fixed in this review** (§Resolution). No code changes to the ported translator were needed or made.

## What was verified (runtime, not `[x]` marks)

- **Byte-parity.** All 6 `.proto` files + `SchemaCodes.kt` byte-identical to kantheon `f2e2efb`. Of 73 ported `.kt`: **71 byte-identical modulo the `shared.translator` → `org.tatrman.translator` rename**; the remaining 2 (`SchemaDetectorSpec.kt`, `MapToPhysicalSpec.kt`) differ **only** by ktlint line-wrapping forced by the longer package name — no token/behavior change. TR-7 "no behavioral change" holds.
- **Tests green** (forced `cleanTest`, not cached): `ttr-translator` = **34 specs / 359 tests / 0 failures / 0 errors** — exactly the claimed parity numbers. `ttr-plan-proto` = 4; fresh Python wheel = 3 pytest pass, 5 `_pb2.py` modules.
- **Build/publish plumbing**: `./gradlew build` green for both modules; ktlint clean; root `group = org.tatrman`; artifactIds `ttr-plan-proto` / `ttr-translator`; publication blocks well-formed; CI `./gradlew build` picks up both modules via `settings.gradle.kts`.
- **A2-1 resolution sound**: proteus stub + `SchemaCodes.kt` added byte-identical, FQCNs preserved, `verifyProtosInJar` guards **6**. Python wheel correctly excludes proteus — no wheel proto imports it and **0** kantheon Python consumers need the enums; consistent with contracts §1.
- Rename complete: **0** bare `shared.` / `org.tatrman.query` remnants in the module.

## Findings

### F1 · [HIGH] Wrong rename token in normative docs → contracts §4.2 migration command was a silent no-op

The in-repo lib's real root package is `shared.translator`, not `org.tatrman.query.shared.translator`. Verified at `f2e2efb`: **0** files match the documented token; **89** files use `shared.translator`. The §4.2 command greps/seds the non-existent token, so Phase B run verbatim would rewrite nothing and leave kantheon importing `shared.translator.*` while the artifact exposes `org.tatrman.translator.*` — **uncompilable**. Same wrong token in architecture TR-2, contracts §4.2 prose (line 53) and the §6 DONE-bar (vacuously true). Delivered artifacts (README, `build.gradle.kts`, A2 commit message) were already correct — docs-only defect.

### F2 · [Minor] Two dead in-repo path refs survived T-A2.5

`codec/transdsl/README.md` and `codec/dfdsl/README.md` still pointed at `shared/proto/.../{transdsl,dfdsl}/v1`. T-A2.5's own grep used `shared:proto`/`shared/libs` (colon), so the slash form slipped. Byte-identical carryover; cosmetic.

### F3 · [Trivial] Doc-accuracy drift

Architecture §1 said "73 main files" (actual: 38 main + 35 test `.kt` = 73 total, mislabeled) and "+ resources" (0 test-resource files; golden data is inline). The A2-1 blocker's "Docs to amend … Not yet done" status was stale (the amendments were in fact in the A2 commit). The A2 commit message says "1 file reformatted" — actually 2 (`SchemaDetectorSpec`, `MapToPhysicalSpec`); left as-is (immutable commit message, cosmetic).

## Resolution (all applied in-review)

- **F1** — `contracts.md` §4.2 command + prose (line 53) + §6 DONE-bar, `architecture.md` TR-2, `plan.md` line 23: token corrected to `shared.translator`, with a note on the real package origin.
- **F2** — both codec READMEs re-pointed to `ttr-plan-proto`'s `src/main/proto/org/tatrman/{transdsl,dfdsl}/v1`.
- **F3** — architecture §1 file counts corrected (incl. the `InMemoryModelHandle` → `testFixtures` relocation note); tasks-a2 blocker status de-staled.

## Publish readiness

A1 + A2 clear the Phase A DONE bar (contracts §6): build green, full moved suite green (34/359), ktlint clean, byte-parity proven, wheel builds. **A3 (publish plumbing + `v0.1.0`) may proceed.** The F1 fix must be in place before kantheon executes Phase B §4.2 — now done.
