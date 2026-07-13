# CEP-P3 — Publish 0.9.6 + consumer re-point

> DoD: [`../plan.md`](../plan.md) §CEP-P3. Contracts: [`../../contracts.md`](../../contracts.md) §7.
> Policy: repo-root [`PUBLISHING.md`](../../../../../PUBLISHING.md) (tag-driven, lockstep
> ttr-translator + ttr-plan-proto). Pre-flight: **CEP-P0..P2 DONE**, full ttr-translator suite green.
>
> **Verify (phase):** `0.9.6` resolves from Maven Central + GH Packages; tatrman-server builds and its
> grounding + veles suites are green against the bumped pin.

- [ ] **T1 — Sources-jar dry-run (architecture R1).** Run `./gradlew
  :packages:kotlin:ttr-translator:publishToMavenCentral --dry-run` (or a `publishToMavenLocal` with a
  version override) and confirm the **sources jar builds with the generated `CalciteExtParserImpl.java`
  included** and the codegen runs before it. If the sources-jar task lacks a `dependsOn(generateParser)`,
  add it. This is the one publish-surface risk the port introduces.

- [ ] **T2 — Local cross-repo smoke.** `./gradlew -Pversion=0.0.1-LOCAL
  :packages:kotlin:ttr-translator:publishToMavenLocal :packages:kotlin:ttr-plan-proto:publishToMavenLocal`,
  point tatrman-server at Maven Local, and confirm veles + grounding compile and test against the
  local build **before** cutting the real tag.

- [ ] **T3 — Cut the release.** Per `PUBLISHING.md`, push `kotlin-translator/v0.9.6` (ttr-translator +
  ttr-plan-proto lockstep). Confirm CI publishes both to Maven Central + GH Packages.

- [ ] **T4 — Bump the consumer pin.** In `tatrman-server/gradle/libs.versions.toml`, bump
  `ttr-translator` (and `ttr-plan-proto` if pinned separately) → `0.9.6`. `./gradlew
  --refresh-dependencies :services:veles:test :services:chrono:test :services:money:test
  :services:geo:test :services:ttr-grounding-mcp:test` — all green.

- [ ] **T5 — Re-check the RG-audit case.** With `0.9.6`, the veles `QueryParseWorker` T-SQL
  `CONCAT('%', {p}, '%')` form (the case adapted to `||` during the RG audit) should now **parse**.
  Optionally restore the original `CONCAT(...)` form in `QueryParseWorkerSpec` and confirm green (or
  leave the `||` form and note the CONCAT path is now available). Confirm the grounding calendar
  round-trips that motivated the RG-P3 translator work still pass.

- [ ] **T6 — Wrap.** Tick CEP-P3 in `00-task-management.md`; update
  `docs/features/ttr-translator/calcite-ext-parser/STATUS.md` (state → done) and the
  `ttr-translator-extraction-gaps` memory (ext-parser gap **closed**; only the wider `functions/`
  operator-surface parity remains, out of scope). Commit `CEP-P3: publish ttr-translator 0.9.6 +
  tatrman-server pin bump`; run the phase-exit `/review`.
