# Tasks — Review 001 (TTR-P Phase 0 S0.1 + Phase 1 S1.1–1.3)

Phase 1's DONE bar holds against runtime (121 + 6 tests green, hero/er `check` clean, 28 negatives, zero ANTLR warnings). These tasks close the gaps Review 001 found: one must-fix correctness bug, two overstated claims to correct, and a cluster of test-tightening + deferral-disclosure items.

Work top-down. ⚠ = must-fix before Phase 2 leans on it. Each task has a Verify command. Do NOT batch checkbox updates — check `[x]` only after its Verify passes.

---

## ✅ Resolution status — all addressed 2026-07-06 (build + 129 frontend / 6 cli tests green)

| Task | Status | How |
|---|---|---|
| ⚠ 1.1-A SourceLocation | **FIXED** | `loc(ParserRuleContext)` uses `offsetToLineCol`; snapshots regenerated; `endLine==7` regression assertion added |
| ⚠ 1.3-A join-condition claim | **CORRECTED** | code comment + task/progress wording say "NOT implemented" (was overstated); synthesis remains deferred (upstream joinPairs) |
| ⚠ 1.3-B provenance render | **DOWNGRADED (fallback)** | render() is not provenance-wired; Phase-1 typing is er-named so no db-spelled diagnostic exists; claim + spec relabeled honestly; deferred to Phase 2 |
| 1.2-A predicate-bool | **WIRED** | `filter`/`branch`/`join` predicates enforced through `ttrp check`; wired-pipeline spec added (chose wiring over deferral) |
| 1.2-D + 1.3-D exact-set | **FIXED** | expression + resolution negatives assert exact id set; corpus is the canonical guard |
| 1.2-C nested op-call | **FIXED** | source-position data-op calls skipped; `var-001` now yields exactly EXP-001 |
| 1.2-E integer widening | **FIXED** | restricted to `decimal`/`number`; `integer + double` → TYP-001 (golden + negative added) |
| 1.2-B temporal coercion | **FIXED** | `date → timestamp/datetime` implemented; golden line added |
| 1.2-F AGG gating | **WIRED** | aggregates legal only in `aggregate`/`pivot` config; `sort { sum() }` → AGG-001 |
| 1.3-C package match | **DOCUMENTED + UPSTREAM** | `qname.package` is empty for db/er (verified) → path-match is unavoidable now; centralized `pathInPackage`; wildcard semantics = D-b/Stage-2.1 + upstream ttr-metadata ask |
| 1.3-E symlink | **COVERED** | new spec drives the hero through the default `manifest.modelsRoot()` (the symlink) |
| 1.3-G manifest keys | **FIXED** | `staging`/`assist-provenance` value coverage added |
| 1.3-F folded classes | **DISCLOSED** | noted in progress doc |
| 1.2-H coalesce | **DOCUMENTED** | KDoc explains no-unify is intentional for v1 |
| 1.1-B S10 true/false/else | **PINNED** | grammar-level PRS-001 (not PRS-005) documented + tested as a conscious split |
| 1.2-I / 1.2-G / N0.1 / N0.2 | **NON-ACTIONS** | cosmetic/no live drift; unchanged as recorded below |

Two items are **not** code-"fixed" because the fix is blocked upstream or is a genuine design decision, not a defect: **1.3-A** (join-condition synthesis needs ttr-metadata joinPair bindings) and **1.3-C** (needs ttr-metadata to populate `qname.package`; the wildcard semantics is a D-b Stage-2.1 call). Both are now honestly disclosed rather than silently carried. The original task text below is retained as the audit trail.

**Decision (2026-07-06 — Bora, Option B):** 1.3-A is scheduled as **`tasks-p2-s2.1-graph.md` T2.1.0** — restore the join-based er-hero + implement the join-condition Expression synthesis (against the real `Join` node that first consumes it) + add the currently-uncovered positive `RES-004` happy-path test, done FIRST in Stage 2.1. Rationale: nothing in Phase 1 consumes the condition, and its shape is a Phase-2/3 convention; the metadata model already carries `joinPairs`, so deferral carries no feasibility risk. 1.3-C's precise fix rides the same Stage-2.1 window as an upstream `qname.package` ask + a D-b wildcard-semantics decision.

---

## ⚠ 1.1-A — Fix multi-line `SourceLocation` in `loc(ParserRuleContext)` + regenerate snapshots (MUST-FIX)

`packages/kotlin/ttrp-frontend/src/main/kotlin/org/tatrman/ttrp/parser/TtrpWalker.kt:516-529`. For any container terminated by a multi-line `TAGGED_BLOCK`, `endLine`/`endColumn` point at the *opening* fence. The correct logic already lives in the sibling `loc(Token)` (`:531`).

- [ ] In `loc(ParserRuleContext)`, replace `endLine = stop.line` / `endColumn = stop.charPositionInLine + stopText.length` with the offset-derived pair:
  ```kotlin
  val (eLine, eCol) = offsetToLineCol(stop.stopIndex + 1)
  // … endLine = eLine, endColumn = eCol …
  ```
  (Mirror `loc(Token)` exactly; keep `offsetStart`/`offsetEnd` as-is — they're already correct.)
- [ ] Add a regression assertion so the suite catches this class directly, not just via snapshots: in `TtrpParserGoldenSpec` assert the hero's `acc_prep` ContainerDecl has `endLine == 7` (the closing-fence line), not 3. This is the guard that was missing.
- [ ] Regenerate golden snapshots: `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpParserGoldenSpec*' -DupdateSnapshots=true` — then **eyeball the diff**: only the multi-line-container `loc` fields (hero `acc_prep`, all of `fragments.json`) may change; no other node moves.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --console=plain` green twice in a row (determinism); `grep '"3:0-3:136"' packages/kotlin/ttrp-frontend/src/test/resources/golden/snapshots/hero.json` → no hits; `git diff` on snapshots shows only container-span line/col changes.

## ⚠ 1.3-A — Correct the join-condition-synthesis claim (docs) (MUST-DISCLOSE)

`TtrpChecker.kt:522-528` emits `dbSpelling = "join-condition($name)"` — a placeholder, not a synthesized `Expression` tree. The deferral is acceptable for Phase 1; the *claim* that it's "implemented as far as the model allows" is not.

- [ ] In `tasks-p1-s1.3-resolution.md` §Blockers (line ~149) and `progress-phase-01.md` "Open item for review", change the wording to: "RES-004 endpoint validation implemented + tested; `on: relation` → port-qualified join-condition `Expression` synthesis **not implemented** — deferred together with the upstream ttr-metadata `customer_sales` joinPair bindings."
- [ ] Add a `// DEFERRED (review-001 1.3-A): returns a placeholder; real Expression synthesis lands with the bound joinPairs` comment at `TtrpChecker.kt:525` so the next reader isn't misled.
- [ ] Confirm the upstream ask (customer/customerType/customer_sales/remaining sales_txn attr bindings + a fresh unbound RES-005 seed) is still tracked in `tasks-overview.md` §Blockers register (it is — verify it names the joinPair need explicitly; add if not).
  - **Verify:** `grep -n "not implemented" docs/ttr-p/implementation/v1/tasks-p1-s1.3-resolution.md` hits the §Blockers line; `grep -n "1.3-A" packages/kotlin/ttrp-frontend/src/main/kotlin/org/tatrman/ttrp/resolve/TtrpChecker.kt` hits.

## ⚠ 1.3-B — Back or downgrade the E-d provenance-render claim (MUST-FIX one way)

`TtrpDiagnostic.render()` (`Diagnostics.kt:143`) never consults provenance; `ErRewrite.renderErFirst()` (`Provenance.kt:27`) exists but is unwired. T1.3.5 promised a render path that renders the er spelling first.

Pick one:
- [ ] **(preferred, small)** Give `TtrpChecker` a way to look up whether a diagnostic's location falls on a rewritten node, and when it does, have the CLI/diagnostic formatter route through `renderErFirst()`. Add a spec that raises a real diagnostic on an er-rewritten ref (e.g. an unbound *attribute* of an otherwise-bound entity) and asserts the message shows `amount (bound to AMOUNT)` er-first.
- [ ] **(fallback)** If no rewritten-node diagnostic can arise until Phase 2, downgrade the DONE-bar line in `tasks-p1-s1.3` (line ~140) to "provenance captured on every rewrite; er-first *rendering* deferred until a rewritten-node diagnostic exists (review-001 1.3-B)" and delete the isolated `renderErFirst()` unit assertion's implication that it's wired.
  - **Verify:** whichever path — either a new spec asserts an er-first *diagnostic message* (not the helper in isolation), or the claim text no longer says "proven by spec." `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*ErRewrite*' --console=plain` green.

## 1.2-A — Disclose (or wire) predicate-bool enforcement

`TtrpFrontend.checkOpCall` (`TtrpFrontend.kt:157-166`) never passes `predicateExpected=true`, so `filter(s, amount + 1)` passes `ttrp check` clean. The `predicateExpected` path is exercised only by `TtrpHeroExpressionsSpec` direct calls.

- [ ] Add a `ttrp check`-level fixture `expr/negative/typ-003-nonbool-filter.ttrp` = `s = load(files.x, schema: {amount: decimal})\nf = filter(s, amount + 1)` and a spec that runs it through `TtrpFrontend.check`.
- [ ] If op-position metadata is genuinely a Phase-2 prerequisite (the frontend can't yet distinguish `filter`'s predicate from `calc`'s formula): mark the fixture `.config(enabled = false)` **with a comment** citing "deferred to Stage 2.1 op-position metadata (review-001 1.2-A)", and record the deferral in `progress-phase-01.md`. Otherwise, thread `predicateExpected` through for the known predicate positions and make it pass.
  - **Verify:** either the new spec is green (wired) or it is explicitly `enabled=false` with the cited reason and the deferral appears in the progress doc. Document which in the PR.

## 1.2-D + 1.3-D — Tighten all negative specs to exact-set assertions

`shouldContain` lets a spurious extra diagnostic pass. The S1.1 parser negative spec already does this right (`shouldHaveSize 1` + exact id) — make the others match.

- [ ] `TtrpExpressionNegativeSpec.kt:52`, `TtrpResolutionNegativeSpec.kt:57`, and the negative cases in `TtrpQnameResolutionSpec`/`TtrpErRewriteSpec`: change `map{…} shouldContain expected` to assert the **exact** set of ERROR ids equals the expected set for that fixture.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --console=plain` — expect `var-001` (and any other) to now **fail** if it leaks a second id, exposing 1.2-C. Fix the underlying leak (next task) so it goes green.

## 1.2-C — Stop op-call-in-source-position from being typechecked as a scalar function

`isBareSource` (`TtrpFrontend.kt:219`) whitelists only `ColumnRef`/`Literal`, so `load(files.b)` as a source folds to `FunctionCall("load")` → unknown → spurious `TTRP-FN-001` on top of the intended `EXP-001`.

- [ ] Treat an `OpCall`/op-call-shaped `FunctionCall` in source position as a *source*, not a scalar expression — do not run it through the scalar-function catalogue check. (This is the same op-vs-expression seam Stage 2.1 must draw; a minimal frontend guard is fine for now.)
  - **Verify:** with 1.2-D's exact-set assertion in place, `expr/negative/var-001.ttrp` produces **exactly** `TTRP-EXP-001` (no FN-001). `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpExpression*' --console=plain` green.

## 1.2-E — Restrict `integer` implicit widening to `decimal` (Q9-4 consistency)

`unifyNumeric` (`ExpressionTypechecker.kt:330-331`) widens `integer` to *any* numeric, so `integer + double` silently becomes double — the precision-loss case the same code forbids for `decimal → double`.

- [ ] Restrict the integer-widen target to `decimal` (and `number` if intended); `integer + double`/`integer + float` → `TTRP-TYP-001` requiring an explicit cast.
- [ ] Add golden/negative lines: `int_col + decimal_col :: decimal` (ok) and an `integer + double` negative expecting TYP-001.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpExpression*' --console=plain` green with the new lines.

## 1.2-B — Implement `date → timestamp/datetime` implicit coercion

Spec T1.2.6 lists it; `unifyResult` only widens numerics (`ExpressionTypechecker.kt:336-344`).

- [ ] Extend result-unification so two `TEMPORAL` types widen `date → timestamp`/`datetime` (per the spec's direction), everything else explicit.
- [ ] Add a golden line, e.g. `case when amount > 0 then some_date else some_ts end :: timestamp` against a schema with a date and a timestamp column.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpExpressionTyping*' --console=plain` green.

## 1.2-F — Make AGG-001 gating op-name-aware (or disclose the deferral)

`TtrpFrontend` allows aggregates in **any** op's config block (`:169-190`), not only `aggregate{…}`.

- [ ] If op-position metadata is available now: gate `aggregatesAllowed = true` on the op name being `aggregate`. Add a `sort { k = sum(x) }` negative expecting AGG-001. Otherwise: record as a Stage 2.1 deferral in `progress-phase-01.md` alongside 1.2-A (same root cause).
  - **Verify:** new negative green, or deferral disclosed.

## 1.3-C — Match packages by exact segment, not substring containment

`ModelIndex.kt:39-84` uses `sourceFile.contains("/erp/")`, so `import erp.*` over-matches `erp.er`.

- [ ] Resolve/store each object's actual package (from ttr-metadata's qname/package, not a path-substring guess) and compare package equality (and prefix-with-boundary for wildcard tiers) instead of `contains`.
- [ ] Confirm `res-002-ambiguous.ttrp` still fires RES-002 for the *right* reason (genuine same-name clash across tiers under an all-tier import), not the accidental over-match.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*QnameResolution*' --console=plain` green; add a fixture proving `import erp.*` (db tier) does NOT pull an `erp.er` entity when it shouldn't.

## 1.3-E — Resolve the unused `models` symlink (cover it or cut it)

`resolution/project/models` (symlink) is never driven by a test; coverage comes from injected `MetadataFixtures` roots.

- [ ] Either add a test that resolves through the default `manifest.modelsRoot()` (exercising the symlink, closing the implied coverage gap) **or** delete the symlink and rely on the Gradle `testFixtures` dependency, removing the Windows-CI liability.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test :packages:kotlin:ttrp-cli:test --console=plain` green after the choice; if kept, the new test fails when the symlink is broken.

## 1.3-G / 1.2-H / 1.3-F / 1.1-B / 1.1-C — Minor cleanups (batch)

- [ ] **1.3-G** — Add positive-value coverage for `staging` and `assist-provenance` in `TtrpManifestSpec` (set them in a fixture `modeler.toml`, assert parsed values).
- [ ] **1.2-H** — Make `coalesce` unify its arg types (reject mixed-kind `coalesce(decimal, 'str')` with TYP-001, consistent with case-branch unify) — or KDoc-document why it's `SameAsArg(0)` intentionally.
- [ ] **1.3-F** — Add a one-line note in `progress-phase-01.md` that `NameResolver`/`ErRewriter`/`SchemaResolver` are folded into `TtrpChecker.kt` (structural divergence from the task plan); consider splitting before Stage 2.1 grows the file.
- [ ] **1.1-B** — Decide S10's `true/false/else`-as-assignment-target behavior: either add a fixture pinning the current generic-PRS-001 outcome (conscious) or route it to PRS-005 for a consistent message. Document the choice.
- [ ] **1.1-C** — Fix the "7 statements" → "8" typo in `tasks-p1-s1.1` T1.1.7 (code is correct).
  - **Verify (batch):** `./gradlew :packages:kotlin:ttrp-frontend:test --console=plain` green; the doc greps confirm the notes landed.

## Non-actions (recorded, no change needed)

- 1.2-I (S16 drift regex scans only uppercase single-literal lexer rules) — no live drift; grammar is disciplined. Soften the KDoc's "can never diverge" to "cannot diverge for named single-literal lexer tokens" if touching the file, else leave.
- 1.2-G (3VL/Kleene unobservable) — conscious all-nullable v1 stance per spec; only the "canonical SQL 3VL under typing" phrasing overstates. Soften if convenient.
- N0.1 (ANTLR 4.11.1→4.13.2 force-resolution) / N0.2 (Kotlin-plugin-loaded-twice) — cosmetic Gradle notices, pre-existing/transitive; a catalog align + config consolidation is a nice-to-have, not gated here.

---

## Sweep before marking review-001 addressed

- [ ] `./gradlew build --rerun-tasks --console=plain` → BUILD SUCCESSFUL, all 9 modules, zero ANTLR warnings.
- [ ] `./gradlew :packages:kotlin:ttrp-frontend:test :packages:kotlin:ttrp-cli:test --rerun-tasks --console=plain` → green; frontend test count ≥ 121 (rises with the new fixtures).
- [ ] `ttrp check` still exit 0 on hero + hero_er (absolute paths), exit 1 on negatives.
- [ ] Every deferral introduced above is written in `progress-phase-01.md` — no silent Phase-2 hand-offs.
