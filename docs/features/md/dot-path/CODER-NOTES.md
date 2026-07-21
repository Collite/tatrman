# MD dot-path — coder notes (branch `md-dot-path`)

Running log of implementation surprises / plan↔repo reconciliations, per the INDEX rule
"Record surprises in the list's Coder notes section — reviews read them." The canonical task
lists live in `collite-gh/project/tatrman/features/md/dot-path/plan/tasks/`; this file mirrors the
notes so they travel with the code branch under review.

---

## S1 — Kotlin MD semantics port

### Discovered dependency (bigger than the plan's S1-A implied)

S1-A's prereq says "S0-B: parser exposes MD defs in Kotlin" — but it does **not**. The Kotlin
`ttr-parser` walker (`TtrWalker.visitDefinition`) has **no branch for `DOMAIN`/`DIMENSION`/`MAP`/
`HIERARCHY`/`MEASURE`/`CUBELET`**; MD defs fall through `else -> null` and are **silently dropped**
(`Definition.kt` has zero MD def types). So before `MdModel` can be built from parse results, the
MD-def AST + walker must be **ported to `ttr-parser`** first — the faithful twin of the TS
`ast.ts`/`walker.ts` MD defs. Scope kept to MDS2's "subset the resolver needs": domains, dimensions
+attributes, maps, measures, cubelets, hierarchies. The `md2db_*` **binding** defs are NOT ported
here (they gate S4 lowering, not the lattice/defaults) — deferred to S4. Kind strings + field names
mirror the TS twin exactly (AST-NAMING parity for future conformance). Recorded as S1-A step 0.

### S1 as-built (S1-0 + S1-A + S1-B) and deferrals

- **S1-0** (pushed): ttr-parser MD-def port (above).
- **S1-A** (pushed): `MdModel` + `GrainLattice` in `ttr-semantics.md`, shared `sales-model`
  testFixture. Fixture gotcha: `date` is a reserved data-type keyword, so the Time grain attribute
  is named **`day`** (domain stays `Date`) — mirrors the TS `MD_LOGICAL` fixture.
- **S1-B**: `MdCalcCatalog` (11 time entries hand-vendored from `@tatrman/md-catalog`,
  `MD_CATALOG_VERSION = 0.1.0` with a drift-guard test) + defaults (`AggKind`, `defaultMeasure`,
  `defaultAgg`, `aggKindOf` with `latestValid ⇒ MAX`). CalcCatalogSpec (5) + DefaultsSpec (5) green.

**Deferred within S1 (flag at review):**
1. **S1-B4 generation script** — the calc catalog is hand-vendored, not generated. The
   `scripts/generate-md-catalog.main.kts` reading `packages/md-catalog/src/` is a named seat; until
   it lands, the 11 entries + version are kept in lock-step by hand (drift-guard test catches version
   skew, not entry-content skew).
2. **S1-B5 TS↔Kotlin parity byte-export** — the Kotlin MD semantics are locked by
   MdModelLoadSpec/GrainLatticeSpec/DefaultsSpec/CalcCatalogSpec, but the *cross-language* golden
   (TS dumps lattice/leaves/defaults for the same model → Kotlin byte-compares) is bundled into the
   **S8 cross-target parity sweep** alongside the conformance-fixture work, rather than standing up a
   second TS export harness mid-S1.
3. **Default-agg rule** — `aggKindOf`'s fallback (`→ SUM`) and `latestValid ⇒ MAX` mirror the
   design-note intent. **Confirmed at review against MD feature contracts §6.5** (Additivity
   consistency: `additive` ⇒ single fn, default `sum`); `latestValid ⇒ MAX` is blessed by S1-B1.
   `Defaults.kt` KDoc now records two forward caveats: `defaultAgg` returns SUM *unconditionally*
   (no `class` gate, unknown token ⇒ SUM) so the S2/S3 resolver must gate on `measureClass` /
   validate the token before consuming it (nonAdditive must not be blind-summed, §6.5); and S4
   must derive real `latestValid` from `valid_from`/`valid_to` (R31/D26), not from `AggKind.MAX`.
   `aggKindOf` trimmed to the closed R5 set (dropped the `average`/`mean` synonyms — TS-parity).

### Review fixes applied on-branch (post-S1 review, 2026-07-18)

- **Calc-catalog content drift fixed + guard hardened.** Five vendored `semantics` strings had
  drifted from `md-catalog/src/catalog.ts` (`week-of-year`→`week number`, `month-of-date`→`month
  number`, `quarter-of-date`→`quarter number`, `year-of-date`→`calendar year`, `quarter containing
  the month`→`⌈m/3⌉`); now byte-matched. `CalcCatalogSpec`'s drift guard no longer compares the
  version to a hardcoded literal — it reads `MD_CATALOG_VERSION` straight out of
  `packages/md-catalog/src/index.ts` (both packages are in this monorepo) so a TS bump fails loudly.
  (Entry-content skew is still only fully caught by the deferred S1-B5 parity export — item 2.)
- **`TtrRenderer` no longer throws on MD defs.** S1-0 made the six MD logical defs reach every
  `r.definitions` consumer; `ttr-writer`'s `renderDef` had an `else -> error(...)`, so rendering any
  model containing MD defs (ttr-metadata's export pipeline is a live consumer) would crash where it
  previously round-tripped with the MD defs silently absent. Now `renderDefinitions` filters MD
  logical defs and `renderDef` returns `""` for them — preserving the pre-arc effective behaviour
  until a real MD renderer lands. All ttr-writer / ttr-metadata suites stay green.
- **TextMate grammar regenerated.** `TTR.g4` gained `PUBLISH` but the tmLanguage was not regen'd, so
  `publish` did not highlight; added the `PUBLISH` case to `generate-tm-grammar.ts` and re-ran
  `pnpm --filter ttr-modeler-vsc regen-tmgrammar`.
- **`CubeletStmtParseSpec` counts pinned exact** (6 CubeletStmt / 2 Assignment out of 8) — the ≥5/≥2
  bounds left the `C = sales.2025.net` classification (the not-chain-viable boundary that is the
  point of the statement ordering) unproven.
- **Stale doc comments corrected** (`PublishMembersParseSpec` claimed Kotlin models no typed MD defs
  — false since S1-0; `MdModel.underlyingDomain` KDoc now lists its resolution hazards).

### GrainLattice — deltas from the TS twin (`md-graph.ts` lowering) — RESOLVED review-071 T-P2

`md-lattice.ts` itself (leaves / union-find co-leaves / N:1 closure / `inferStep`) is ported
line-for-line. The **model→edge lowering** (`GrainLattice.of`) once differed from TS `buildMdMapGraph`
in three ways. **Ruling (Bora, 2026-07-21): Kotlin is canonical** (it is the runtime path).

1. **Unresolved refs — FIXED.** TS used to fall back to the raw ref string and keep the edge
   (`?? fromRef`); Kotlin drops it. This one changed leaves/reachability for a model with a dangling
   ref, so `buildMdMapGraph` now **drops** the edge too (review-071 T-P2, `md-graph.test.ts` +
   `GrainLatticeSpec` dangling-edge cases pin both sides). Divergence closed.
2. **Node set — accepted, benign.** TS nodes = edge endpoints only; Kotlin unions in every declared
   domain (so `Money`/`ProductCode` surface as leaves). An isolated domain *is* trivially its own
   grain, so this changes neither reachability nor the co-leaf partition — left as-is, documented.
3. **Naming — accepted, benign.** TS keys nodes by symbol-table qname; Kotlin by the domain's simple
   name (unambiguous within the single `model md` namespace). No semantic effect.

The full S1-B5 byte-parity export stays deferred (it needs a TS Defaults port to be meaningful); the
one behavioural divergence is now closed, so it is no longer a correctness risk in the meantime.

## S0 — grammar version

### Reconciliations vs. the 2026-07-08 plan (the repo moved under it)

1. **TTRP.g4 is Kotlin-only.** Its header states "there is no antlr-ng/TS target and no TextMate
   grammar." So the TTRP-side S0 changes (`INT`/`floatLiteral`/`mdPath`/`DOTDOT`/`cubeletStmt`)
   regenerate **only** the Kotlin target (ANTLR Gradle plugin in `ttrp-frontend`). The plan's
   S0-A6 "TS red spec" and the `packages/parser` prebuild / TextMate steps do **not** apply to the
   TTRP side. They *do* apply to the TTR-M change (`publish: members` on TTR.g4 — 3 targets +
   TextMate).

2. **TTRP parser lives in `ttrp-frontend`, not `ttr-parser`.** Generated classes `TTRPParser`/
   `TTRPLexer` in `org.tatrman.ttrp.parser.generated` (flat output — do not nest `outputDirectory`).
   So the plan's `ttr-parser/.../ttr/parser/md/FloatPathLexSpec.kt` path is wrong; the float/path/
   cubelet specs belong in `ttrp-frontend`. `ttr-parser` is the TTR-M parser and is the right home
   only for the `publish: members` spec.

3. **`tests/conformance/fixtures/` is the TTR-M (.ttrm) harness** (dump/diff across TS+Kotlin+
   Python). There is no `.ttrp` conformance path there; the plan's `40-*.ttrp` fixtures don't fit.
   To keep S0-A's *general* gates green (only the new dedicated specs go red), the new fixtures are
   placed as **module-local test resources / inline sources**, not added to the auto-globbed
   conformance dir. They fold into `tests/conformance/fixtures/` (with baseline regen) at S0-B
   *green* for the TTR-M `publish` clause.

4. **Fixture numbering:** highest existing is `64-*`, not `39` — new TTR-M conformance fixtures (at
   S0-B) start at `65`.

5. **Dialect grammars are standalone.** `TTRSql.g4` / `TTRPandas.g4` / `TTRB.g4` each declare their
   **own** `NUMBER` token (no `import TTRP;`). So `NUMBER→INT` in TTRP.g4 is **isolated to the
   TTRP walker** — the dialect walkers (`TtrSqlExpr`/`TtrPandasExpr`/`TtrbExpr`) that the plan's
   S0-A1 grep flagged are **not** affected and are **not** re-pointed. (Corrects the plan's "every
   former NUMBER site is re-pointed" — that assumed one shared token.)

6. **No typed MD-def AST on the Kotlin side.** `ttr-parser`'s `Definition.kt` has no
   `MdDomainDef`/`CubeletDef`/etc.; the Kotlin MD model is the **S1** `MdModel` port. So the
   `publishMembers` *typed flag* surfaces on the **TS** `MdDomainDef` (ast.ts) now; the Kotlin side
   only needs the grammar to **accept** `publish: members` at S0-B (parse-level), and reads the
   flag at S1. (Refines plan S0-B3 "add publishMembers to the Kotlin AST twin.")

7. **Two grammars, two version processes.** `docs/features/grammar-master/new-grammar-version-
   process.md` governs **TTR.g4** (3 targets + conformance + publish tags). TTRP.g4 carries its own
   integer `@grammar-version` and is Kotlin-only. S0's "version cut" therefore spans two distinct
   processes. **Publish/tag steps (S0-B7) are deferred pending arc review** — pushing grammar
   artifact tags from an unreviewed feature branch would ship to downstream consumers (ai-platform);
   that is an outward-facing action to confirm at S8, not to do mid-arc.

### S0-A1 — `NUMBER` inventory (TTRP.g4 → Kotlin `ttrp-frontend`, the only re-point set)

Grammar sites (`packages/grammar/src/TTRP.g4`):
| line | rule | use of NUMBER | S0-B action |
|---|---|---|---|
| 133 | `typeName` | `LPAREN NUMBER (COMMA NUMBER)? RPAREN` (decimal(12,2) arity) | → `INT` |
| 134 | `literal` | `… | NUMBER | …` | → `numericLiteral` (`floatLiteral | INT`) |
| 223 | lexer | `NUMBER : [0-9]+ ('.' [0-9]+)? ;` | replace with `INT : [0-9]+ ;` |

Kotlin consumers to re-point (`ttrp-frontend/src/main`):
| file:line | what | S0-B action |
|---|---|---|
| `parser/TtrpWalker.kt:390-397` | `typeName(ctx)` reads `ctx.NUMBER()` list → precision/scale | → `ctx.INT()` |
| `parser/TtrpWalker.kt:439` | `literal()` `ctx.NUMBER().text` → `LiteralValue.Num` | → handle `numericLiteral`/`floatLiteral`, reconstruct raw text (zero behaviour change: `12.5`→`Num("12.5")`) |
| `expr/ExpressionTypechecker.kt:110` | `Num.raw.contains('.')` → Decimal else Integer | unchanged if raw text preserved (float still has `.`) |

Not affected (separate grammars/tokens): `dialect/sql/*`, `dialect/pandas/*`, `dialect/b/*`,
`TtrbRejectScanner.kt` (`TTRBLexer.NUMBER`). See reconciliation #5.

Zero-behaviour-change guard: keeping `LiteralValue.Num(rawText)` for both `INT` and `floatLiteral`
means the committed `expr/snapshots/golden-exprs.json` stays byte-identical (INT literals + `12.5`
unchanged) — that snapshot suite is the guard; no separate guard fixture needed.

### S0-A red artifacts authored (all fail until S0-B)

- `ttrp-frontend/src/test/resources/md/float-path.cases` + `.../parser/md/MdFloatPathParseSpec.kt`
  — the §1.3 normative table (float vs path vs binop), incl. R2 whitespace + quoted member.
- `ttrp-frontend/src/test/resources/md/cubelet-stmts.ttrp` + `.../parser/md/CubeletStmtParseSpec.kt`
  — the four operators, `with` clause, mdPath-LHS slice, interleaving; plain `x = a` stays an
  Assignment.
- `ttr-parser/.../parser/md/PublishMembersParseSpec.kt` — Kotlin grammar-level `publish: members`.
- `parser/src/__tests__/md-publish-members.test.ts` + `publishMembers?: boolean` on `MdDomainDef`
  — TS typed-flag coverage.

### Grammar design decisions taken for S0-B (recorded now; implemented next)

- **`primary` alternative order:** `literal`(→`numericLiteral`→`floatLiteral` first) … `dottedRef`
  … `mdPath` **last**. Rationale: R1 needs float tried before path (`2025.06`→float); `dottedRef`
  before `mdPath` keeps a pure-identifier chain (`sales.net`) parsing as today's `dottedRef`
  (column-first, R23) — `mdPath` only catches chains `dottedRef` can't (numeric/quoted/set/range/
  star components). ANTLR ALL(*) full-context prediction picks `mdPath` for `sales.2025.06` because
  `dottedRef` can't consume `.2025` (`2025` ∉ `idPart`).
- **`DOTDOT` before `DOT`** in the lexer (maximal-munch: `2024..2026` → INT DOTDOT INT).
- **`cubeletStmt`** is a new `statement` alternative listed **after** `bindingOrChain`, so
  `x = a` (chain-viable) stays an Assignment and only chains that can't parse as a chain
  (`C = sales.2025.net`) or have an mdPath/`:=`/`+=`/`-=` LHS fall to `cubeletStmt`. Dispatch
  (variable vs cubelet vs slice) stays **semantic** (R24), decided in S5C.

### S0-B — as-built (all S0-A red specs now green)

**TTRP.g4** (Kotlin target): `NUMBER→INT`; added `DOTDOT`/`ASSIGN_MAT`/`PLUS_ASSIGN`/`MINUS_ASSIGN`
tokens; `numericLiteral`/`floatLiteral`/`mdPath`/`pathComponent`/`pathAtom` rules; `mdPath` as the
last `primary` alternative; `cubeletStmt`/`cubeletLhs`/`withClause`/`mdObject` rules + `cubeletStmt`
in `statement`. **TTR.g4**: `PUBLISH` token, `publishProperty : PUBLISH propSep? MEMBERS` in
`mdDomainProperty`, `PUBLISH` added to the keyword-as-id list.

**AST + walker (ttrp-frontend):** new `MdPath` + `MdPathComponent`/`MdPathAtom` (expr) and
`CubeletStmt`/`CubeletLhs`/`CubeletOp`/`MdWithClause`/`MdWithEntry` (ast); walker builds them;
`literal()` re-pointed to `numericLiteral().text` (raw reconstruction → `12.5` byte-identical);
`typeName()` → `INT`. **TS (parser):** `walker.ts` fills `publishMembers` from `publishProperty`.

**Downstream exhaustive-`when` sites updated for the new nodes** (compiler-guided): ttrp-frontend
(`ExpressionTypechecker` → null type, `TtrpChecker.exprColumnRefs` → none, `TtrpAstDump` +
2 test helpers); ttrp-graph (`CapabilityChecker` ×2, `NormalizedGraphJson`, `Rules.swapLeftRightPorts`);
ttrp-emit (`PolarsExprRenderer`, SQL `PlanNodeBuilder` → both throw `UNSUPPORTED_NODE` "MD lowering
is S4"); ttrp-lsp (`ExprRenderer` renders the path back to source text). All treat MdPath as an
opaque leaf pending S3/S4 — nothing lowers it yet.

**Gate status at S0-B:** ttrp-frontend 329 · ttr-parser 172 · ttrp-graph 84 · ttrp-emit 50 ·
ttrp-lsp 75 Kotlin tests green; TS parser 248 + grammar green; all touched modules pass ktlint.
Zero-behaviour-change proven: the committed `expr/snapshots/golden-exprs.json` stayed byte-identical.

**Two pre-existing baseline issues (NOT this arc — do not "fix" on this branch):**
1. `./gradlew build` fails `:ttr-translator:ktlint*` on `wire/PlanNodeDecoder.kt`/`PlanNodeEncoder.kt`
   — those files are unchanged by this arc; last touched by master commit `b283cba` (NX-A.S4, the
   parallel dev). Compilation and all tests are green; only that module's lint is red at baseline.
2. Fresh-worktree build-order: `@tatrman/grammar` must be built (`pnpm --filter @tatrman/grammar
   build`, which also runs `extract-property-map`) before `packages/parser` typecheck/tests — else
   `src/generated/{version,property-map}.ts` are missing and `grammar-version.test.ts` fails. Not a
   regression; an environment setup step for a fresh checkout.

**Deferred to S8 (bundled with the version cut — decision #4, outward-facing):**
- TTRP.g4 integer `@grammar-version` bump + TTR.g4 `0.9→0.10` bump + the tag-driven publish.
- Python `ttr-parser` regen + a Python `publish: members` test.
- Adding the §1.3 float/path + `publish` fixtures to the auto-globbed `tests/conformance/fixtures/`
  and refreshing the TS/Kotlin/Python baselines (grammar-master §3). The syntax itself is proven on
  TS+Kotlin here; the cross-target conformance-baseline sweep rides the reviewed version cut.
- **TS conformance `KIND_KEYWORD` MD entries.** `tests/conformance/dump.ts`'s `KIND_KEYWORD` map has
  no MD rows, so once MD defs enter the conformance corpus the TS dump would emit the raw
  discriminators (`mdDomain`/`mdMap`) while the Kotlin `ConformanceDump` already emits the TTR
  keywords (`domain`/`map`, per the "kind = TTR keyword" convention — Kotlin is correct). Add
  `mdDomain→domain`, `mdMap→map`, `dimension`, `hierarchy`, `measure`, `cubelet` to the TS map when
  the MD fixtures fold in.
- **GrainLattice TS↔Kotlin lowering deltas** — resolve the node-set / unresolved-ref / naming
  divergence (recorded above) as part of standing up the S1-B5 parity export.
