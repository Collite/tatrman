# P0 — CST & trivia foundation (parser)

**Package:** `@modeler/parser` · **Blocks:** all other phases · **Contracts:** §1
**Pre-flight:** working tree green; antlr4ng hidden-channel API confirmed against
`node_modules/antlr4ng`; CLAUDE.md "Grammar regeneration" + `SourceLocation` invariant read.

Goal: comments become first-class `Trivia` attached to AST nodes; the parse round-trips
byte-for-byte. **No grammar parse-tree change** — only the comment token channel moves.

Tick each box the moment the task is done; commit as `Section P0: <task>`.

---

- [ ] **1. (test) Round-trip corpus + identity-printer harness.**
  Create `packages/parser/src/__tests__/cst-roundtrip.test.ts`. Add a small corpus of `.ttr`/`.ttrg`
  source strings that include: leading line comments above defs, trailing line comments on property
  lines, a block comment, blank lines, and a comment at EOF. Write a test-only identity printer that
  walks significant tokens + attached trivia in source order and concatenates their text. Assert
  `identityPrint(parseString(src, uri)) === src` for every corpus entry. **This test must fail to
  compile/run now** (trivia doesn't exist yet) — that's expected; it drives the rest.

- [ ] **2. (test) Trivia span + attachment unit tests.**
  Create `packages/parser/src/__tests__/trivia.test.ts`. For a 3-line fixture (`// lead\n table x {\n  // c\n }`),
  assert: the table node has one `leadingTrivia` line-comment with the exact `SourceLocation`
  (1-indexed line, 0-indexed column, `offsetEnd` exclusive); a trailing comment attaches to the
  right node; block vs line `kind` is correct. Assert spans by **exact range**, never by length
  (CLAUDE.md `endColumn` history).

- [ ] **3. Grammar: route comments to the hidden channel.**
  In `packages/grammar/src/TTR.g4` change lines 598–599: `LINE_COMMENT : '//' ~[\r\n]* -> channel(HIDDEN) ;`
  and `BLOCK_COMMENT : '/*' .*? '*/' -> channel(HIDDEN) ;`. Leave `WS ... -> skip ;` unchanged.
  Then regenerate: `cd packages/parser && pnpm run prebuild` (regenerates `generated/*`), and
  `cd packages/vscode-ext && node scripts/generate-tm-grammar.ts`. Do **not** commit `generated/**`
  (gitignored). Confirm `pnpm --filter @modeler/parser build` still succeeds.

- [ ] **4. Trivia types.**
  Create `packages/parser/src/cst/trivia.ts` exactly per contracts §1.1 (`TriviaKind`, `Trivia`).
  Add optional `leadingTrivia?: Trivia[]` / `trailingTrivia?: Trivia[]` to the AST node interfaces in
  `packages/parser/src/ast.ts` that already carry `source` (definitions, properties, imports, schema
  directive, graph block, refs). Export `Trivia`/`TriviaKind` from `src/index.ts`.

- [ ] **5. Trivia attacher.**
  Create `packages/parser/src/cst/attach.ts` with `attachTrivia(ast, tokenStream)` (contracts §1.4).
  Read hidden-channel comment tokens from the filled `CommonTokenStream`; map token type
  (`TTRLexer.LINE_COMMENT`/`BLOCK_COMMENT`) → `kind`; build `source` via the existing
  `makeSourceLocation` conventions (`offsetEnd = token.stop + 1`). Attachment rule: leading = hidden
  tokens left of a node's start token up to the prior newline; trailing = hidden tokens right of the
  stop token on the same line. Make task-2 tests pass.

- [ ] **6. Wire attacher into the walk.**
  In `packages/parser/src/walker.ts`, after the AST is built and before returning the `ParseResult`,
  call `attachTrivia(ast, tokenStream)` (the stream from `walker.ts:162`; call `.fill()` first).
  Ensure `parseString` and `parseFile` both populate trivia. Make task-1 round-trip test pass.

- [ ] **7. ai-platform sync + verification.**
  Run `packages/grammar/scripts/sync-to-ai-platform.sh ~/Dev/ai-platform`, then
  `packages/grammar/scripts/check-sync.sh ~/Dev/ai-platform` (must report hashes match). In
  ai-platform, regenerate its Kotlin parser and run its parser test on a sample to confirm the
  channel change is parse-equivalent (no behavioural change). Note the coordinated commit in the PR
  description. (If ai-platform is unavailable, record this as a blocking follow-up, do not skip.)

- [ ] **8. Gates + CLAUDE.md note.**
  Run `pnpm -r {build,test,typecheck,lint}` — all green. Update `CLAUDE.md`: the parser "CST view
  with trivia" line now reflects reality — describe the `Trivia` model (contracts §1.2) and that the
  edit synthesizer will preserve trivia during autofix (P4). Confirm no existing parser/semantics
  test changed behaviour.
