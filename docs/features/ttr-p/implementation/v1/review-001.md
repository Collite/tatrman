# Review 001 — TTR-P Phase 0 (Stage 0.1) + Phase 1 (Stages 1.1–1.3)

**Scope:** the developer's `progress-phase-01.md` claims S0.1 (scaffold + hygiene) and all of Phase 1 (S1.1 grammar+parser, S1.2 expressions, S1.3 resolution) are code-complete. Reviewed against the four task lists (`tasks-p0-s0.1-scaffold.md`, `tasks-p1-s1.1…1.3-*.md`), the design decisions in `../../design/00-control-room.md`, and the runtime.

> First implementation review for the TTR-P sub-project. Numbering restarts at `001` under `docs/ttr-p/implementation/v1/` (the TTR-M v1 reviews live separately under `docs/v1/implementation/` and are up to 024). `[x]` in the progress/task docs is intent — every claim below was checked against runtime.

## TL;DR

**Phase 1's literal DONE bar holds against runtime.** Forced-rerun (`--rerun-tasks`, cache-defeated) confirms: `./gradlew build` green across all 9 Kotlin modules; **121** `ttrp-frontend` tests + **6** `ttrp-cli` tests, 0 failures, 0 skipped; **zero** ANTLR generation warnings; `ttrp check` exits 0 on `hero.ttrp` and `hero_er.ttrp` and 1 on negatives with correctly-located diagnostics + suggested alternatives; **28** resolution negatives covering all 16 WLD/RES/SCH/CFG/MOV ids, plus 7 parser + 7 expression negatives. S0.1's rename is complete (0 `@modeler/` leftovers in live `.ts`/`package.json`/CI), the 6 modules + PUBLISHING rows + 6 doc annotations are in place, and `publishToMavenLocal` works. **This is solid, disciplined work.**

**But the green bar overstates what the shipped `ttrp check` pipeline actually enforces, and two progress-doc claims are overstated.** The single theme across Phase 1: several checks and behaviors exist only as *API surface exercised by direct-call specs*, not as wiring in the real pipeline, and the negative specs' `shouldContain` assertions let spurious extra diagnostics pass unnoticed. Concretely:

- **One must-fix correctness bug (S1.1):** `loc(ParserRuleContext)` computes `endLine`/`endColumn` wrong for any container terminated by a multi-line `TAGGED_BLOCK`, and the wrong span is frozen into golden snapshots — so the suite *protects* the bug. This violates the load-bearing `SourceLocation` invariant (CLAUDE.md) that the LSP/edit synthesizer depend on.
- **Predicate-bool typing (TYP-001) is never wired into `ttrp check` (S1.2):** `filter(s, amount + 1)` passes clean. The hero test asserts the *API* (`predicateExpected=true`), not the pipeline — false confidence.
- **Two overstated claims (S1.3):** the `on: relation` → join-condition Expression synthesis is a literal placeholder string, not "implemented as far as the model allows"; and the E-d "diagnostic renders the er spelling first" claim is unbacked — `TtrpDiagnostic.render()` never consults provenance.

**Verdict: conditional pass.** No finding breaks the Phase-1 DONE bar as literally defined, and the er-hero reduction is a legitimate upstream-fixture limitation. But fix the SourceLocation bug, correct the two overstated claims in the progress doc/blocker, tighten the negative specs to exact-set assertions, and record the deferred pipeline-enforcement gaps before Phase 2 builds graph semantics on top. Details below, ranked by severity within each stage.

---

## Stage 0.1 — Scaffold + hygiene — ✅ PASS

Verified clean, no findings:

- `@modeler/*` → `@tatrman/*` rename complete: `grep -rn "@modeler/" --include="*.ts" --include="package.json" packages tests` → 0; no `@modeler` in live `.yml`/`.json`/`.mjs` (historical `docs/v1*` untouched, correct); CLAUDE.md updated with the rename note; the `vscode-smoke` CI job filters `ttr-modeler-vsc` (the fix the task called for).
- Six `ttrp-*` modules present, wired into `settings.gradle.kts`, with the one-way dep graph exactly as specified (`ttrp-graph→frontend`, `ttrp-emit→graph`, `ttrp-lsp/cli→frontend implementation`, `ttrp-conform` standalone; `ttrp-cli` adds `application` + `ttr-metadata`).
- `TTRP.g4` seed grew into the real grammar (S1.1); generation is ANTLR-Gradle-plugin-only (G-b), zero warnings.
- CI `kotlin` job runs `./gradlew build` (build+test all modules) on PR; `PUBLISHING.md` carries the 6 module rows + 1 tag row; the 6 design docs carry the stale-`§Open` annotation.
- `./gradlew -Pversion=0.0.1-LOCAL :packages:kotlin:ttrp-frontend:publishToMavenLocal` succeeds.

**Two cosmetic notes (not defects, dev already flagged the first):**
- N0.1 — A harmless `org.antlr:antlr4-runtime:4.11.1 -> 4.13.2` force-resolution appears on the classpath (transitive via `ttr-parser`); Gradle unifies it to 4.13.2 at build time, so it is a runtime *notice* only. A version-align in the catalog would silence it. Nice-to-have.
- N0.2 — `./gradlew` prints "The Kotlin Gradle plugin was loaded multiple times in different subprojects." This is a pre-existing repo-wide Gradle-config smell (not introduced by the `ttrp-*` modules), but the new modules add to the count. Worth a consolidation pass someday; out of scope here.

---

## Stage 1.1 — Grammar + parser

### 1.1-A ⚠ MAJOR — `SourceLocation` wrong for multi-line `TAGGED_BLOCK` containers (snapshot-locked)

`packages/kotlin/ttrp-frontend/src/main/kotlin/org/tatrman/ttrp/parser/TtrpWalker.kt:516-529` — `loc(ParserRuleContext)` sets `endLine = stop.line` and `endColumn = stop.charPositionInLine + stopText.length`. ANTLR reports `token.line` as a token's **first** line, so when the stop token is a multi-line `TAGGED_BLOCK`, both fields point at the *opening* fence line and a column that doesn't exist there.

Confirmed against the committed source + snapshot (not hypothetical):
- `golden/hero.ttrp`: the `acc_prep` container spans source **lines 3–7**, but `golden/snapshots/hero.json:15` records `"loc": "3:0-3:136"` (endLine 3, endColumn 136 — line 3 is only ~35 chars long).
- `golden/snapshots/fragments.json`: all three tagged-block containers similarly collapse to their opening line (`"1:0-1:90"` for a block ending on line 4, etc.).

The fix already exists in the sibling overload: `loc(Token)` (`TtrpWalker.kt:531`) and `fragmentBody` (`:218`) use `offsetToLineCol(...)` and produce correct multi-line spans (the hero's `FragmentBody` child is correctly `"3:33-7:3"`). `loc(ParserRuleContext)` was simply never given the same treatment. `offsetStart`/`offsetEnd` are correct, so offset-based patching survives — but any line/column consumer (LSP `Range`, the edit synthesizer the invariant exists to protect) gets a container node whose end is the opening fence. Flow-body (`}`-terminated) containers like `crunch` are unaffected (`"9:0-30:1"` is correct).

**Why it matters now:** the wrong values are locked into golden snapshots, so the test suite actively defends the bug. Fixing the code requires regenerating snapshots — do both together. This is the one finding I'd call must-fix before Phase 2, because Phase 2/4 build graph nodes and LSP ranges off these spans.

### 1.1-B — MINOR — S10 reservation enforced by two mechanisms with inconsistent diagnostics

`TtrpChecks.kt:28` defines `RESERVED_PORTS = {in,out,err,rejects,true,false,else}`, but the grammar's `identifier : IDENT | IN | OUT | ERR | BY | SCHEMA` (`TTRP.g4:142`) means `true`/`false`/`else` are keyword tokens that can never parse as an `Assignment` target. So `err = …` and `rejects = …` reach the PRS-005 walker check (good), but `true = …`/`false = …`/`else = …` fall through to a generic `TTRP-PRS-001` syntax error with no S10 suggested-alternative. Only `err` is exercised (`negative/prs-005-reserved-port.ttrp`). The `true/false/else` entries in `RESERVED_PORTS` are effectively dead on the assignment path. Not wrong output, but inconsistent UX and undisclosed. Consider either documenting the split or adding a fixture that pins the `true = …` behavior so it's a conscious choice.

### 1.1-C — NIT — hero statement-inventory assertion says 8, task text said 7 (code is right)

`TtrpParserGoldenSpec.kt:74` asserts `crunch`'s flow body has **8** statements; task T1.1.7 wrote "7". The verbatim hero genuinely has 8 (load, filter, join, aggregate, branch, result, low, rejects) — code/test correct, task text under-counted. Noted only because the divergence is recorded nowhere.

### Verified solid (1.1)
- **R4 `err rejects` ambiguity handled correctly:** implemented as `portDecl : (IN|OUT|ERR) identifier` per the task; the signal-vs-rows question is *recorded* (grammar comment `TTRP.g4:87-89` "typing revisited in Stage 2.1" + task References R4), not silently resolved. §Blockers correctly empty (the instruction was to record there only *if* Stage 2.1 needs a distinct keyword).
- **Negative spec is strict** (`TtrpParserNegativeSpec.kt:31-33`): `shouldHaveSize 1` + exact id + non-blank `suggestedAlternative` — a genuine exact-one check (contrast the resolution/expression negatives below).
- **Fragment byte-identity is genuine** (`TtrpTaggedBlockSpec.kt`): compares `FragmentBody.sourceText` against a raw slice of the fixture file, including the pandas dict-literal + trailing-space bytes. No dedent/trim.
- Error recovery real (≥2 distinct error lines, post-error survival, unterminated-fence at the fence). No disabled tests remain (`enabled = false` grep clean).

---

## Stage 1.2 — Expression grammar / IR / typing

### 1.2-A ⚠ MAJOR — predicate-bool enforcement (TYP-001) is never wired into `ttrp check`

`ExpressionTypechecker.check(..., predicateExpected: Boolean = false)` (`ExpressionTypechecker.kt:65`) has the parameter, but `TtrpFrontend.checkOpCall` calls `check(...)` **without** it (`TtrpFrontend.kt:157-166`), so it always defaults to `false`. `predicateExpected=true` appears **only** in `TtrpHeroExpressionsSpec` direct typechecker calls (`:56,66,86`) — never in the shipped pipeline. Failure scenario: `filter(s, amount + 1)` (a non-bool "predicate") passes real `ttrp check` with zero diagnostics, though T1.2.6 + the DONE bar require `TTRP-TYP-001` for `filter`/`branch`/`join on:` positions. The hero test's "predicate types bool" line validates the API, not the wiring — false confidence.

This is arguably *legitimately* blocked on Phase-2 op-position metadata (the frontend can't yet tell "this arg is `filter`'s predicate, that one is `calc`'s formula" — it treats all non-bare args uniformly). If so, that's a fine deferral — but it is undisclosed in the progress doc, and the hero assertion papers over it. Record the deferral and keep the API-level test, but don't let it read as "enforced."

### 1.2-B — MINOR/major — `date → timestamp/datetime` implicit coercion not implemented

Spec T1.2.6 explicitly lists `date → timestamp/datetime` as an allowed implicit widening. `unifyResult`/`unifyNumeric` (`ExpressionTypechecker.kt:323-344`) only widen `Kind.NUMERIC`; two `TEMPORAL` types with different canonicals never unify. Failure scenario: `case when p then some_date else some_timestamp end` (or a mixed-temporal `coalesce`) yields a spurious `TTRP-TYP-001`. No fixture exercises temporal mixing, so the omission is silent. Add the temporal-widen rule + a golden line.

### 1.2-C — MINOR/major — nested op-call as a source arg emits a spurious `TTRP-FN-001`

`isBareSource` whitelists only `ColumnRef`/`Literal` (`TtrpFrontend.kt:219`); every other non-bare `ExprArg` is typechecked as a scalar function (`:157-166`). In `expr/negative/var-001.ttrp` (`filter(load(files.b), x > 0)`), `load(files.b)` folds to `FunctionCall(CatalogId("load"))` (`TtrpWalker.kt:411-424`); `load` isn't in the catalog → unknown → `TTRP-FN-001` fires **in addition to** the intended `TTRP-EXP-001`. The hero dodges this (pure-SSA bare-ref sources). Masked entirely by finding 1.2-D. This is really the frontend conflating "op-call in source position" with "scalar function in expression position" — a seam that Stage 2.1 graph construction will have to draw anyway; flag it.

### 1.2-D — MINOR — expression negative specs assert `shouldContain`, not exact-set

`TtrpExpressionNegativeSpec.kt:52` — `errors.map { it.id.id } shouldContain expectedId`. Any spurious extra diagnostic (e.g. the FN-001 in 1.2-C) passes silently. The DONE bars across Phase 1 say "produce exactly their named diagnostic." Tighten to an exact-set assertion. (Same weakness in the S1.3 negatives — see 1.3-D. The S1.1 parser negative spec got this right; make the others match it.)

### 1.2-E — MINOR — `integer` widens implicitly to *any* numeric, not just `decimal`

`unifyNumeric` (`ExpressionTypechecker.kt:330-331`): `if (a.canonical == INTEGER) return b`. So `integer + double → double`, `integer + float → float` — implicitly, no cast. Spec names only `integer → decimal` as implicit; `integer → double/float` can lose precision (>2^53), which is the *exact* Q9-4 rationale used to forbid implicit `decimal → double`. Internally inconsistent. Restrict the integer-widen target to `decimal` (and `number`, if that's intended) and require an explicit `cast` otherwise.

### 1.2-F — MINOR — `AGG-001` gating is not op-name-aware

`TtrpFrontend` passes `aggregatesAllowed = true` for **any** op's config block (`:169-190`), not only `aggregate { … }`. So `sort { k = sum(x) }` would not raise `TTRP-AGG-001`, contrary to "aggregates only inside `aggregate(…)`/`aggregate{…}`." Same Phase-2 op-semantics deferral as 1.2-A; disclose it.

### 1.2-G — MINOR — 3VL/Kleene is unobservable this stage (dead `NullRule` metadata)

`CatalogEntry.nullPropagation` (STRICT/CUSTOM, `FunctionCatalog.kt`) is never read by the typechecker, and `TtrpType` carries no nullability flag, so `and/or` Kleene, STRICT NULL-propagation, and "`IsNull` returns non-null bool" are all indistinguishable — every bool op just returns `Bool`. This looks like a conscious "all types nullable in v1" stance (which the spec does state), but the KDoc/DONE-bar phrase "canonical SQL 3VL under typing" is cosmetic at this stage. Either wire a nullability bit or soften the claim. (Also 1.2-H: `coalesce`'s `SameAsArg(0)` does zero arg-type unification, so `coalesce(decimal_col, 'str')` types as decimal with no diagnostic — inconsistent with the case-branch unify.)

### 1.2-I — NIT — S16 drift regex only scans single-literal UPPERCASE lexer rules

`TtrpKeywordTableSpec.kt:32-33`'s regex matches `NAME : 'literal' ;` lexer rules only. Inline string literals in *parser* rules, multi-alternative keyword tokens, or a lexer rule with a `-> channel(...)` suffix would evade the tripwire. The grammar is currently disciplined (all tokens named, comments/WS have `-> command` suffixes and are correctly excluded), so there's no live drift — but the KDoc's "can never silently diverge" overstates it. The tripwire *does* correctly trip for a bogus `XOR : 'xor';`.

### Verified solid (1.2)
- **IR shape correct:** `AggregateCall` is a distinct sealed arm (`Expression.kt:98`); a scalar reaches it only via `distinct`, which then deterministically fails FN-002; operators uniformly fold to `op.*` FunctionCalls; no lambda/subquery arms.
- **Catalogue is a closed deterministic table** (P2): the 6-entry alias map is a literal (`BuiltinCatalog.kt:69-77`), no fuzzy matching; unknown→FN-001, arity/kind→FN-002.
- `Cast` explicit-only; TYP-002 legality table correct for tested pairs; parse snapshot is structurally detailed (would catch a wrong IR tree); S23 vocabulary matches `TTR.g4` `typeValue` verbatim including `object`/`list` (confirmed against `TTR.g4:558-564` — advertising them in the SCH-003 message is therefore correct, not a divergence); `==`→EQ-001 from the expression walker.

---

## Stage 1.3 — Resolution (= Phase 1 DONE bar)

### 1.3-A ⚠ MAJOR (overstated claim) — `on: relation` → join-condition Expression synthesis is a placeholder string

`TtrpChecker.resolveRelation` (`TtrpChecker.kt:522-528`) emits, on a matched relation, `ErRewrite(dbSpelling = "join-condition($name)")` — a literal placeholder. There is **no** code that reads bound key columns / joinPairs and builds the port-qualified equality `Expression` tree that T1.3.5 lists as delivered `[x]`. The §Blockers text (`tasks-p1-s1.3` line 149, mirrored in the progress doc) says the synthesis is "implemented as far as the model allows … provenance stub on a matched relation." That mischaracterizes it: even with bound endpoints, this method still returns the string stub — the Expression machinery is *absent*, not merely unexercised.

**Judgment: the deferral itself is acceptable for v1 Phase 1.** The DONE bar requires hero + er-hero to `check` clean plus 25 negatives; the er-hero was legitimately reduced to the bound `sales_txn` arm (a genuine end-to-end of entity→table + attr→column rewrite *with* provenance, `TtrpErRewriteSpec.kt:23-30`); and RES-004 endpoint validation is real and tested (`res-004-wrong-endpoints.ttrp`). The *claim* is what's wrong. Correct the blocker/progress wording to "endpoint validation (RES-004) implemented; join-condition Expression synthesis not implemented — deferred with the upstream fixture bindings," so Phase 2 doesn't assume it exists.

### 1.3-B ⚠ MAJOR (overstated claim) — E-d "diagnostic renders er spelling first" is unbacked

T1.3.5 requires "a `TtrpDiagnostic.render()` path that consults provenance." `TtrpDiagnostic.render()` (`Diagnostics.kt:143`) does **not** consult provenance — it is `file:line:col id message`. The only er-first rendering is `ErRewrite.renderErFirst()` (`Provenance.kt:27`), a standalone helper never wired into any diagnostic; the sole test (`TtrpErRewriteSpec.kt:32-35`) asserts that helper's string in isolation. So the DONE-bar line "er-spelled diagnostics proven by spec" (`tasks-p1-s1.3` line 140) is not backed: no diagnostic is raised on a rewritten node and rendered through provenance. Low runtime impact this phase (no such diagnostic exists yet), but the claim is untrue. Either wire `render()` to consult provenance when the located node was rewritten, or downgrade the claim to "provenance captured; er-first rendering deferred until a rewritten-node diagnostic exists."

### 1.3-C — MINOR — package/import scoping over-matches via substring, not path segment

`ModelIndex.inScope`/`packageExists`/`findByPackage` (`ModelIndex.kt:39-84`) match with `sourceFile.contains("/" + pkg.replace('.','/') + "/")`. Because `.../models/erp/er/…` contains `/erp/`, `import erp.*` (tier=null) pulls in `erp.er` entities, and `load(erp.sales_txn)` (pkg=`erp`) resolves to the er entity that actually lives in package `erp.er`. The `res-002-ambiguous.ttrp` fixture *depends* on this over-match to fire RES-002. Latent tier/package-boundary bug (D-a sub-1 says the qname path distinguishes tiers; the impl doesn't enforce the exact containing package). Not exercised to a wrong result today, but surprising mis-resolution is possible once more packages share a prefix. Match on exact package equality, not path containment.

### 1.3-D — MINOR — resolution negatives assert `shouldContain`, not exact-set

`TtrpResolutionNegativeSpec.kt:57` — `ids shouldContain expect`; same in `TtrpQnameResolutionSpec`/`TtrpErRewriteSpec` negatives. DONE bar (`tasks-p1-s1.3` line 138) says "produce **exactly** their named diagnostic." A fixture leaking an extra ERROR still passes. No leak found by inspection, but the guard is weaker than the bar. Fold into the same fix as 1.2-D.

### 1.3-E — MINOR — the committed `models` symlink is unused by the suite yet carries the portability risk

`resolution/project/models` is a git symlink (mode 120000) → ttr-metadata testFixtures; the target resolves in-repo and it's portable to Linux CI (the dev's flag is accurate). But **no test drives it**: `ResolutionFixtures.modelsRoot()` and `TtrpCheckCliSpec` both inject `MetadataFixtures.erpModelsRoot()` / a `modelsRootOverride`. So the symlink is simultaneously (a) a Windows-CI liability and (b) dead weight — the default `manifest.modelsRoot()` path it appears to cover is never exercised by a test. Either add a test that resolves through it (closing the coverage gap it implies) or drop it and rely solely on the Gradle `testFixtures` dependency.

### 1.3-F — MINOR — task-named resolver classes don't exist; logic folded into `TtrpChecker`, undisclosed

The tasks name `NameResolver` (T1.3.4), `ErRewriter` (T1.3.5), `SchemaResolver` (T1.3.6) as deliverables; none exist — all ~600 lines of resolution/rewrite/schema logic are inlined into `TtrpChecker.kt`. Functionally fine (and arguably simpler), but the structural divergence from the plan isn't noted. Either note it in the progress doc or split for readability before Phase 2 grows this file further.

### 1.3-G — NIT — two manifest keys parsed but never value-tested

`staging` and `assist-provenance` are in the data class and `knownKeys` (all 9 present — good), but the fixture `modeler.toml` omits both; `TtrpManifestSpec`'s "every key parsed" checks 7. `assist-provenance` has a CFG-001 enum fixture; `staging` has no positive-value coverage.

### Verified solid (1.3)
- **default-imports / S18:** `TtrpChecker` reads only document `ImportDecl`s, never `manifest.defaultImports` — enforced by construction and spec-cased (`TtrpManifestSpec.kt:63-80`: canonical doc with `default-imports` + no `import` → exactly RES-001).
- **Position typing miss paths** name both expected + found kind (RES-003 "is a storage", MOV-001 "is an engine"); **RES-002 no-first-wins** is real (both the case-insensitive-clash and exact-name-multi-import paths return `null`).
- **ttr-metadata boundary (D-g) clean:** no `.ttrm`/`TtrLoader`/`ttr-parser` usage in `ttrp-frontend`/`ttrp-cli` main; all model access via `MetadataLoader`/`MetadataQuery`/`WorldResolver`.
- **Manifest reader:** all 9 keys; walk-up returns all-defaults + `found=false` (not error) when no `modeler.toml`; CFG-001 enum violation; CFG-002 with a *closed*-table suggestion (no fuzzy); foreign tables ignored.
- **Schema precedence** inline > program > world with same-level conflict = error (SCH-001); SCH-002 schema-on-read ban; SCH-003 closed S23 list. All spec-cased.
- Diagnostics catalogue has no id collisions (`TtrpDiagnosticIdSpec` invokes `assertNoDuplicateIds()`), 28 negatives cover all 16 ids, tree is clean (no committed generated churn).

---

## Cross-cutting theme (read this if nothing else)

The recurring pattern is **"tested at the API, not at the pipeline; asserted with `contains`, not `equals`."** 1.2-A (predicate-bool), 1.2-C/1.2-F (op-position-unaware checks), and 1.3-B (provenance render) are all cases where a capability exists as a function but isn't wired into `ttrp check`, while the specs exercise the function directly and the negatives use `shouldContain` — so the green suite reads as "enforced" when it's "enforceable." Two concrete guards close most of the gap: (1) make every negative spec assert the **exact** diagnostic-id set; (2) add a handful of **`ttrp check`-level** (not direct-typechecker) fixtures for the predicate-bool / aggregate-placement / nested-source cases and either make them pass or record them as explicit Phase-2 deferrals. Several of these checks genuinely *need* Phase-2 op-position metadata — that's fine, but it should be a written deferral, not a silent one.

See `tasks-review-001.md` for the actionable, verification-gated task list.
