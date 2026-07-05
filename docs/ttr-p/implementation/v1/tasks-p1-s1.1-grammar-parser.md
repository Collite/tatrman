# Tasks · P1 · Stage 1.1 — Grammar + parser

> Part of [tasks-overview.md](./tasks-overview.md) · Plan: [plan.md](./plan.md) · Decision IDs → `../../design/00-control-room.md`
> **Coder rules:** Work top-to-bottom. Check `[x]` each checkbox IMMEDIATELY after its task's verification passes — never batch checkbox updates. If blocked, STOP and record the blocker under §Blockers; do not improvise around it.

## Stage deliverable

The real `TTRP.g4` (replacing the P0 seed) covering the C3-converged canonical surface — γ-hybrid statements (chains + SSA assignment, C3-a), `->` (C3-b), named args + config blocks (C3-a-iii), closed containers with program-level wiring (C3-d), keyword control `after`/`with` with `finishes with` reserved (C3-e/F-b), two error ports (C3-f), tagged blocks lexed opaque (C3-g), reserved port names (S10), no program header (S12) — plus the Kotlin parser wrapper (`parseString`/`parseFile`, trivia attach, fragment interiors verbatim per C2-f), a golden parse corpus including the hero, an error-recovery baseline, and the `TTRP-<AREA>-<NNN>` diagnostic framework with suggested-alternative field (contracts §8), including the `=`/`==` rule (S9 → `TTRP-EQ-001`).
**DONE bar:** golden corpus (hero included) parses to committed AST snapshots; every negative fixture produces exactly its named diagnostic; multi-error documents recover and report all errors.

## Pre-flight (all must pass before T1.1.1)

- [ ] `./gradlew build` green (Stage 0.1 DONE bar holds).
- [ ] `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpSeedGrammarSpec*'` green (seed grammar generation wired).
- [ ] Read `docs/ttr-p/design/05-canonical-dsl-options.md` §RESOLVED + "The converged hero rendering", and contracts §3 — the grammar below implements exactly that text.

## Tasks

### T1.1.1 · Golden + negative corpus and spec skeletons (TEST-FIRST)

- [ ] Create fixture tree `packages/kotlin/ttrp-frontend/src/test/resources/`:
  - `golden/hero.ttrp` — the C3 converged hero, **verbatim** from `05-canonical-dsl-options.md` §"The converged hero rendering" (starts `uses world "acme-prod"`, containers `acc_prep` (tagged `"""sql` body) + `crunch` (flow body with `load`/`filter`/`join`/`aggregate {}` block/`branch`/port bindings), program wiring `acc_prep -> crunch.accounts`, `display(main_result)`, two `store(...)` sinks, `//` comments).
  - `golden/chains.ttrp` — `a = load(files.x, schema: s1)\nb = a -> filter(amount > 0) -> sort(by: amount) -> limit(10)\nb -> display`
  - `golden/ssa.ttrp` — `sales = load(files.sales)\nsales = filter(sales, amount > 0)\nsales = filter(sales, region = 'EU')\nsales -> display`
  - `golden/containers.ttrp` — two containers, one flow-bodied with full port signature `(in a, out r, err e)`, one tagged-block-bodied; wiring + `x.err`/`x.rejects` references at program level.
  - `golden/control.ttrp` — `b after a`, `a with b`, and the same two inside a `control { }` block.
  - `golden/union-display.ttrp` — `u = union(a, b, c)` (S11 list form) + named and anonymous `display`.
  - `golden/fragments.ttrp` — all three tags `"""sql`, `"""pandas`, `"""ttrb` as container bodies; interiors contain tricky bytes (`--` comments, Python dict literal `{ 'k': 1 }`, blank lines, trailing spaces) that MUST survive byte-for-byte (C2-f).
  - `negative/eq-001.ttrp` → expects `TTRP-EQ-001`: `x = filter(s, amount == 0)`
  - `negative/ctl-001.ttrp` → expects `TTRP-CTL-001`: `a finishes with b`
  - `negative/prs-002-program-header.ttrp` → expects `TTRP-PRS-002`: first line `program sales_summary` (S12).
  - `negative/prs-003-positional-join.ttrp` → expects `TTRP-PRS-003`: `j = join(accounts, sales, on: left.id = right.id)` (C3-c named-only multi-in).
  - `negative/prs-004-named-union.ttrp` → expects `TTRP-PRS-004`: `u = union(in1: a, in2: b)` (S11 list-form only).
  - `negative/prs-005-reserved-port.ttrp` → expects `TTRP-PRS-005`: `err = load(files.x)` (S10 reserved name bound as variable).
  - `negative/frg-001-unknown-tag.ttrp` → expects `TTRP-FRG-001`: `container c target erp_pg """scala\nval x = 1\n"""`
- [ ] Create spec skeletons (Kotest `StringSpec`, package `org.tatrman.ttrp.parser`) — all red at this point:
  - `TtrpParserGoldenSpec.kt` — one test per `golden/*.ttrp`: `parseString(fixture).diagnostics.filter { it.severity == ERROR } shouldBe emptyList()` + AST-snapshot compare (snapshot mechanism lands in T1.1.7; until then assert zero errors only).
  - `TtrpParserNegativeSpec.kt` — table-driven: fixture name → expected diagnostic id; asserts exactly-one ERROR with that id AND a non-blank `suggestedAlternative`.
  - `TtrpTriviaSpec.kt`, `TtrpErrorRecoverySpec.kt`, `TtrpTaggedBlockSpec.kt` — empty shells with one `"TODO".config(enabled = false)` placeholder each.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpParser*Spec*'` runs and FAILS (red) — fixtures load, assertions fail because the grammar/wrapper don't exist yet.

### T1.1.2 · TTRP.g4 — statements, chains, containers, control (the tricky productions)

- [ ] Replace the seed body of `packages/grammar/src/TTRP.g4`. Load-bearing productions (write these exactly; fill the routine remainder — literals, `qname`, lists — in the same style):
  ```antlr
  document        : statement* EOF ;

  statement
      : usesWorld | importDecl | containerDecl | controlBlock | controlStmt
      | programHeader              // parses ONLY to name the S12 rejection (walker → TTRP-PRS-002)
      | bindingOrChain
      ;

  usesWorld       : 'uses' 'world' STRING ;
  importDecl      : 'import' qname ('.' '*')? ;
  programHeader   : 'program' identifier ;

  // C3-a-iv precedence  =  <  ->  <  call  — encoded by rule nesting, not token precedence.
  bindingOrChain
      : identifier '=' chain       # assignment          // SSA reassignment legal (Q7-γ)
      | chain                      # chainStmt           // incl. program-level wiring
      ;

  chain           : chainElem ('->' chainElem)* ;        // one token for chains AND wiring (C3-b)
  chainElem       : opCall | dottedRef ;                 // dottedRef = variable | node.port | qname
                                                          // (which one is a RESOLUTION question — D-b
                                                          //  position typing; grammar keeps it opaque)

  opCall
      : identifier '(' argList? ')' configBlock?
      | identifier configBlock                            // wide-op block form: aggregate { … }
      ;
  argList         : arg (',' arg)* ;
  arg             : (identifier ':')? argValue ;          // named canonical; bare positional allowed
                                                          // ONLY as a single-in op's source or a
                                                          // union list element — enforced in the
                                                          // walker (TTRP-PRS-003/004), NOT the grammar
  argValue        : chain | expr | 'relation' qname | schemaLiteral ;

  configBlock     : '{' configEntry* '}' ;
  configEntry
      : 'group' 'by' identifier (',' identifier)*
      | identifier '=' expr
      ;

  controlStmt     : identifier 'after' identifier                  # afterFs      // FS
                  | identifier 'with' identifier                   # withSs       // SS
                  | identifier 'finishes' 'with' identifier        # finishesFf   // reserved → TTRP-CTL-001
                  ;
  controlBlock    : 'control' '{' controlStmt* '}' ;

  containerDecl   : 'container' identifier portSig? 'target' qname
                    ( '{' statement* '}' | TAGGED_BLOCK ) ;         // closed containers (C3-d-iii)
  portSig         : '(' portDecl (',' portDecl)* ')' ;
  portDecl        : ('in' | 'out' | 'err') identifier ;             // hero writes `err rejects`; see References note on the C3-f signature ambiguity
  ```
- [ ] Keyword strategy: only structural words are lexer keywords (`uses, world, import, program, container, target, control, after, with, finishes, group, by, relation, schema, in, out, err, cast, as, is, not, and, or, null, in?`). **Reserved port names `in,out,err,rejects,true,false,else` are NOT all keywords** — `true/false/else/rejects` stay lexable as `identifier` parts so `b.true`, `j.rejects` parse naturally; reservation is enforced by the walker (S10). Add an `identifier : ID | soft-keyword alts ;` rule mirroring TTR.g4's `idPart` pattern so keywords remain usable in dotted positions.
- [ ] Provisional expression rule for this stage (Stage 1.2 replaces it — mark with a `// STAGE-1.2 REPLACES` comment): precedence ladder `or < and < not < comparison < additive < multiplicative < unary < primary`; comparison ops `= <> < <= > >=`, `is [not] null`, and `==` (parsed on purpose, rejected in T1.1.5); primaries = literal, `dottedRef`, function call, parenthesized.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:generateGrammarSource compileKotlin` succeeds with **zero ANTLR warnings** (`-long-messages` shows ambiguity warnings — fix, don't ignore); `grep -c 'STAGE-1.2 REPLACES' packages/grammar/src/TTRP.g4` → 1.

### T1.1.3 · Tagged-block lexis + verbatim fragment capture (C2-f)

- [ ] Lexer rules — reuse TTR's proven single-token approach (TTR.g4 line ~846; non-greedy `.*?` — **no lexer modes needed**), byte-identical pattern:
  ```antlr
  TAGGED_BLOCK  : '"""' [a-zA-Z] [a-zA-Z0-9-]* [ \t]* '\r'? '\n' .*? '"""' ;
  STRING        : '"' (~["\r\n])* '"' ;
  LINE_COMMENT  : '//' ~[\r\n]* -> channel(HIDDEN) ;
  BLOCK_COMMENT : '/*' .*? '*/' -> channel(HIDDEN) ;
  WS            : [ \t\r\n]+ -> skip ;
  ```
  Declaration order matters: `TAGGED_BLOCK` before `STRING` (ANTLR breaks equal-length ties by declaration order — same note as TTR.g4).
- [ ] In the walker (T1.1.4): peel the tag (`sql` | `pandas` | `ttrb`); any other tag → `TTRP-FRG-001` with suggested alternative "supported fragment dialects: sql, pandas, ttrb (C3-g/C4-f)". Store the interior **verbatim** — raw bytes between the tag line's `\n` and the closing `"""`, no dedent, no trim (dedent/format is never applied to fragment interiors; TTR-M's dedent logic is explicitly NOT reused here — C2-f).
- [ ] Fill `TtrpTaggedBlockSpec.kt`: per `golden/fragments.ttrp` container, `fragment.sourceText` equals the exact byte slice of the input file (compare against substring of the raw fixture, not a re-derived string).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpTaggedBlockSpec*'` green, including the dict-literal/trailing-space bytes.

### T1.1.4 · Parser wrapper, AST + SourceLocation + trivia attach

- [ ] `packages/kotlin/ttrp-frontend/src/main/kotlin/org/tatrman/ttrp/parser/TtrpParser.kt`:
  ```kotlin
  object TtrpParser {
      fun parseString(source: String, fileName: String = "<memory>"): TtrpParseResult
      fun parseFile(path: java.nio.file.Path): TtrpParseResult
  }
  data class TtrpParseResult(val document: TtrpDocument, val diagnostics: List<TtrpDiagnostic>, val source: String)
  ```
- [ ] AST under `org.tatrman.ttrp.ast` (package `…/ast/`): `TtrpDocument`, `UsesWorld`, `ImportDecl`, `Assignment`, `ChainStmt`, `OpCall`, `Arg`, `ConfigBlock`, `ControlDep(kind: FS|SS|FF)`, `ContainerDecl`, `PortDecl(kind: IN|OUT|ERR, name)`, `FragmentBody(tag, sourceText, interiorLocation)`, `DottedRef(parts)` (opaque until Stage 1.3), provisional `Expr` arms. **Every node carries a `SourceLocation`** with the repo's ANTLR-style convention (CLAUDE.md invariant): `line`/`endLine` 1-indexed, `column`/`endColumn` 0-indexed, offsets 0-indexed end-exclusive, and for multi-token spans `endColumn = stopToken.charPositionInLine + stopTokenText.length` — port the tested logic from `packages/kotlin/ttr-parser/.../walker/` (see `SourceLocationSpec.kt`), do not re-derive it.
- [ ] Trivia: comments ride the HIDDEN channel; attach leading/trailing trivia to statements the way `@modeler/parser`'s `attachTrivia` does on the TS side (nearest preceding hidden tokens = leading; same-line following = trailing). Fragment interiors are verbatim `sourceText` and never trivia-scanned.
- [ ] Fill `TtrpTriviaSpec.kt`: leading `//` comment on a statement round-trips; comment between two statements attaches to the second; trailing same-line comment attaches to its statement.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpTriviaSpec*'` green; `TtrpParserGoldenSpec` zero-error assertions green for all `golden/*.ttrp`.

### T1.1.5 · Diagnostic framework `TTRP-<AREA>-<NNN>` + the named rejects

- [ ] `org.tatrman.ttrp.diagnostics`:
  ```kotlin
  enum class TtrpDiagnosticId(val id: String, val suggestedAlternative: String?) {
      EQ_001("TTRP-EQ-001", "use `=` — it is the one equality operator; `==` is only a TTR-pandas synonym (S9)"),
      CTL_001("TTRP-CTL-001", "`finishes with` (FF) is reserved and not available in v1; use `after` (FS) or `with` (SS) (F-b)"),
      PRS_001("TTRP-PRS-001", null),   // generic syntax error (ANTLR-reported)
      PRS_002("TTRP-PRS-002", "TTR-P has no `program` header — identity is the filename; delete this line (S12)"),
      PRS_003("TTRP-PRS-003", "multi-input ops take named inputs only: join(left: …, right: …) (C3-c)"),
      PRS_004("TTRP-PRS-004", "union takes the list form: union(a, b, c) (S11)"),
      PRS_005("TTRP-PRS-005", "this name is a reserved port name (in, out, err, rejects, true, false, else) — choose another (S10)"),
      FRG_001("TTRP-FRG-001", "supported fragment dialects: sql, pandas, ttrb (C3-g/C4-f)"),
  }
  data class TtrpDiagnostic(val id: TtrpDiagnosticId, val severity: Severity, val message: String,
                            val location: SourceLocation, val suggestedAlternative: String? = id.suggestedAlternative)
  ```
  Convention per contracts §8: ids stable, every rejected form carries a suggested alternative. The enum is the single catalogue (it later feeds `ttrp/authoringContext`'s diagnostics table — keep messages self-contained).
- [ ] Post-parse walker checks (in `TtrpParser`, after tree walk): `==` token anywhere in expression context → EQ_001; `finishesFf` alt → CTL_001; `programHeader` alt → PRS_002; `join`/multi-in op (`join`, `intersect`, `except`) with a bare positional arg beyond arg-position 0 → PRS_003; `union` with any named `inN:` arg → PRS_004; assignment target or port declaration named in the S10 reserved set → PRS_005.
- [ ] ANTLR syntax errors surface as PRS_001 with ANTLR's message and a correct `SourceLocation` (custom `BaseErrorListener` collecting into the result — never print to stderr).
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpParserNegativeSpec*'` — all 7 negative fixtures produce exactly their expected id, each with non-blank `suggestedAlternative` (except PRS_001 cases).

### T1.1.6 · Error-recovery baseline

- [ ] Keep ANTLR's `DefaultErrorStrategy` (recovery on), verify statement-level resynchronization is acceptable: a bad statement must not swallow the rest of the document. If the default sync set eats following statements, add a conservative `sync`-friendly reshaping of `statement` alternatives — do NOT write a custom error strategy in v1.
- [ ] Fill `TtrpErrorRecoverySpec.kt`:
  - "two independent syntax errors → two PRS-001 diagnostics" — fixture with a broken chain on line 2 and a broken container header on line 6; assert `diagnostics.size >= 2` and both lines represented.
  - "statements after an error still parse" — broken line 1, valid `a = load(files.x)` on line 3; assert the assignment node exists in the AST.
  - "unterminated tagged block reports at the fence" — `"""sql` with no closing fence; assert one ERROR whose location points at the opening fence line.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test --tests '*TtrpErrorRecoverySpec*'` green.

### T1.1.7 · AST snapshot dumps + golden corpus locked

- [ ] Test-only JSON dumper (mirror the `ttr-parser` conformance pattern — `ConformanceDump.kt` uses kotlinx-serialization on the **test** classpath only; `testImplementation(libs.kotlinx.ser.json)` in `ttrp-frontend/build.gradle.kts`): deterministic, field-ordered serialization of the AST (node kind, children, source spans, fragment `sourceText`).
- [ ] Commit snapshots under `src/test/resources/golden/snapshots/<fixture>.json`; `TtrpParserGoldenSpec` upgraded: parse → dump → byte-compare against the committed snapshot; regeneration via `-DupdateSnapshots=true` system property (document in a comment at the top of the spec).
- [ ] Add the hero's statement inventory as explicit assertions in `TtrpParserGoldenSpec` (guards against a "snapshot matches wrong tree" blind spot): hero parses to 1 `usesWorld`, 2 `ContainerDecl` (one `FragmentBody(tag="sql")`, one flow body with 7 statements), 4 top-level wiring/`ChainStmt`s.
  - **Verify:** `./gradlew :packages:kotlin:ttrp-frontend:test` — entire module green twice in a row (proves dump determinism); `git status` shows committed snapshots only (no churn on re-run).

## Definition of DONE (stage)

- [ ] All `golden/*.ttrp` fixtures (hero included) parse with zero ERROR diagnostics and match committed AST snapshots.
- [ ] All 7 `negative/*.ttrp` fixtures produce exactly their named `TTRP-…` diagnostic with a suggested alternative.
- [ ] Fragment interiors byte-identical through parse (`TtrpTaggedBlockSpec` green).
- [ ] Multi-error recovery baseline green (`TtrpErrorRecoverySpec`).
- [ ] `./gradlew build` green; zero ANTLR generation warnings.

## Blockers

_(empty — coder records here)_

## References

- **C3-a (γ)** hybrid statements · **C3-b** `->` one token · **C3-a-iii** named args + config blocks for wide ops · **C3-a-iv** precedence `=` < `->` < call, chains legal in source position · **C3-c** named-only multi-in · **C3-d-iii** closed containers, program-level wiring · **C3-e** keyword control · **C3-f** two error ports `err`+`rejects` · **C3-g** `"""tag` tagged blocks, tag = dialect.
- **S9** `=` one equality (`TTRP-EQ-001`) · **S10** reserved ports `in,out,err,rejects,true,false,else`, lowercase, column lexical rules · **S11** union list form · **S12** no program header · **F-b** FF dropped from v1, keyword reserved (`TTRP-CTL-001`).
- **C2-f** formatter/parser never touch fragment interiors — verbatim bytes.
- Contracts §3 (surface summary), §8 (diagnostics convention — id format + suggested alternative).
- Hero text: `docs/ttr-p/design/05-canonical-dsl-options.md` §"The converged hero rendering".
- **Flagged ambiguity (do not resolve silently):** the hero declares the rows-error port as `err rejects` (an err-kind port *named* `rejects`) while C3-f speaks of two distinct reserved ports `err`/`rejects` with different shapes (signal vs rows). This task list follows the hero verbatim (`portDecl : ('in'|'out'|'err') identifier`). If Stage 2.1 port typing needs a distinct `rejects` declaration keyword, record it in §Blockers and raise — grammar change is cheap now, silent divergence is not.
- TAGGED_BLOCK precedent: `packages/grammar/src/TTR.g4` line ~846 (comment explains the tie-break-by-declaration-order trick).
- SourceLocation convention + tested span logic: CLAUDE.md §Key invariants; `packages/kotlin/ttr-parser/src/test/kotlin/org/tatrman/ttr/parser/model/SourceLocationSpec.kt`.
- Snapshot-dump precedent: `packages/kotlin/ttr-parser/src/test/kotlin/org/tatrman/ttr/parser/conformance/ConformanceDump.kt`.
