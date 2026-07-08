# S0-B — grammar change, regeneration, version cut

Goal: land the syntax carrier (MDS4): `NUMBER`→`INT` + parser-composed floats + `mdPath` +
`DOTDOT` + set braces in `TTRP.g4`; `publish: members` in `TTR.g4`. Turn S0-A's red specs green
with zero behavior change for existing programs. Cut the grammar version.

Prereq: S0-A complete (inventory table filled, fixtures red).

## Tasks

- [ ] **S0-B1 — TTRP.g4 token changes.** Replace `NUMBER : [0-9]+ ('.' [0-9]+)? ;` with
  `INT : [0-9]+ ;`. Add `DOTDOT : '..' ;` **above** `DOT`, `ASSIGN_MAT : ':=' ;` **above**
  `COLON`, and `PLUS_ASSIGN : '+=' ;` / `MINUS_ASSIGN : '-=' ;` above `PLUS`/`MINUS` (ANTLR
  maximal munch then prefers the two-char tokens). Confirm `LBRACE`/`RBRACE`/`STAR`/`STRING`
  tokens exist or add per contracts §1.1.
- [ ] **S0-B2 — TTRP.g4 parser rules.** Per contracts §1.2 exactly: `numericLiteral`,
  `floatLiteral` (three alternatives), `mdPath`, `pathComponent`, `pathAtom`, plus
  `cubeletStmt` (all four operators; LHS = `mdPath | IDENTIFIER`) and `withClause : 'with'
  object_ ;` (free-form body — key checking is semantic, "parser stays mechanical"). Re-point
  every S0-A1 inventory row: `typeName` arity args → `INT`; `literal` → `numericLiteral`;
  expression alternatives ordered `floatLiteral` **before** `mdPath` (R1). `mdPath` joins the
  expression primary alternatives; `STAR`-as-multiplication remains the operator rule —
  path-internal `*` only via `pathComponent`. `cubeletStmt` joins the statement alternatives
  beside the existing assignment (plain `x = expr` variable assignment must parse **unchanged**
  — dispatch between variable and cubelet statement is semantic, R24).
- [ ] **S0-B3 — TTR.g4 publish clause.** `publishClause : 'publish' ':' 'members' ;` inside the
  domain body (beside the existing domain properties; keyword soft — follow how other
  property keywords are declared). AST: add `publishMembers: boolean` to the domain node in
  `packages/parser/src/ast.ts` **and** the Kotlin AST twin — kind strings and field names
  identical cross-target (AST-NAMING.md).
- [ ] **S0-B4 — regenerate all targets.** `cd packages/parser && pnpm run prebuild` (zero ANTLR
  warnings); `./gradlew :packages:kotlin:ttr-parser:generateGrammarSource` (flat-output caveat in
  `ttr-parser/build.gradle.kts` — do NOT nest `outputDirectory`); Python target per the
  reference-jar script. Then `cd packages/vscode-ext && node scripts/generate-tm-grammar.ts`.
- [ ] **S0-B5 — writer/renderer + walker.** Kotlin `TtrRenderer` renders `publish: members`
  (parse order preserved); TS walker attaches source locations on `mdPath`/`floatLiteral` nodes —
  remember the `endColumn = stopToken.column + stopTokenLength` rule (CLAUDE.md invariant; the
  relaxed-test bug must not recur).
- [ ] **S0-B6 — green.** S0-A5/A6/A3/A4 specs and fixtures green; guard fixture 40 **byte-identical
  goldens**; full conformance harness green on all three targets (`conformance.yml` locally).
- [ ] **S0-B7 — version cut.** Follow `docs/grammar-master/new-grammar-version-process.md` end to
  end (version bump, changelog, tags, publish as the process prescribes). Record the version id in
  Coder notes and in `../implementation-plan.md`'s changelog.
- [ ] **S0-B8 — gates & commit.** Both domains' gates green. Commit
  `md-sugar S0B: grammar vN — INT/floatLiteral/mdPath/DOTDOT + publish members`.

## Coder notes

_(empty)_

## References

- Contracts §1 (normative rules R1–R4) · design note D14/D15.
- ANTLR: token order = declaration order for equal-length matches; maximal munch otherwise —
  `DOTDOT` before `DOT` is the only ordering that matters here.
- Grammar is the single source for three parsers — never edit generated output
  (`packages/*/src/generated/` is gitignored).
