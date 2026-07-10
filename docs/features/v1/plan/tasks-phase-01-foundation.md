# Phase 1 — Foundation tier complete

**Status:** v0 draft, ready for Bora review then handoff to Claude Code.
**Branch:** `feat/phase-01-foundation`
**Time budget:** 1.5–2 weeks (revised from the original 1-week estimate to absorb the review-001 P1/P2 carryover and the parser error-recovery work needed for `parse-recovery-info` diagnostics).
**Dependencies:** Phase 0 review-001 P0 tasks all green (i.e. the **Definition of done** at the bottom of `tasks-review-001.md` satisfied — `pnpm -r build && test && lint` pass on a fresh clone, demo path verified by hand).
**Blocks:** Phase 2 (Core tier — semantics, resolver, navigation).

## Goal

Take Phase 0's vertical thin slice from "the wire works" to "every file under `samples/` looks and behaves like a real language file in any LSP host." After Phase 1, opening a `.ttr` file in VS Code shows accurate, full-coverage syntax highlighting (including TextMate + semantic tokens for the cases TextMate can't disambiguate); brackets, comments, and indentation behave correctly; deliberately-broken variants of each sample produce diagnostics with stable codes and useful messages; the `.ttrl` layout sidecar is a recognized read-only language; and the grammar-sync check between this repo and ai-platform runs in CI.

Phase 1 is still pre-semantics. No symbol table, no cross-reference resolution, no go-to-definition. Those land in Phase 2.

## Pre-flight

- [ ] Confirm `tasks-review-001.md` "Definition of done" is satisfied: P0 tasks green, `pnpm -r build && test && lint && --filter @modeler/integration-tests test` exits 0, demo path works
- [ ] Create branch `feat/phase-01-foundation` from the merged Phase 0 PR
- [ ] Create `docs/plan/progress-phase-01.md` and copy the section headers below
- [ ] Re-read `docs/design/architecture.md` §4.5 (LSP server features), §4.6 (VS Code extension contributions), §6 (`.ttrl` schema), and §8.4 (testing strategy)
- [ ] Re-read `tasks-review-001.md` P1 (Tasks 14–19) and P2 (Tasks 20–22) — all of these are folded into the sections below

## Section A — Carryover from review-001 P1/P2

These were P1/P2 items from review-001. Most are already on disk — the list below is for verification only. Fix any line that is not true on disk before proceeding to §B.

- [ ] **Confirm review-001 P1/P2 items are reflected on disk:**
  - `Definition` is a discriminated union in `packages/parser/src/ast.ts` (17 per-kind interfaces with `kind: '<kind>'` discriminant)
  - `.gitignore` uses `/*.ttrl` (root-only layout files)
  - No duplicate `version` keys in `packages/vscode-ext/package.json`
  - `packages/parser/tsconfig.json` has no `exclude` for `src/generated/**`
  - `SourceLocation` carries a JSDoc indexing-convention block
  - `eslint.config.js` ignores `**/generated/**` and `**/dist/**`
  - `packages/grammar/src/index.ts` uses `fileURLToPath(new URL(...))`
  - `progress-phase-00.md` "Carried into Phase 1" lists only outstanding items (VS Code smoke test, TextMate structural rebuild)
  - Parser test fixtures path: tracked as Known Issue 3 in progress-phase-00; address as part of §K (broken-fixture infrastructure) by introducing a shared `_fixtures.ts` helper
  - `endColumn` uses last-token's length (not span length) — verified by `node -e "..."` asserting `endColumn === 20` on `def entity foobar {}`

**Acceptance**: `pnpm -r build && test && lint` green; on-disk state matches every verification line above.

## Section B — TextMate grammar full coverage

The Phase 0 generator covered keywords, strings, numbers, and comments only — and the keyword list contained tokens that aren't in the grammar (review §4.1). Phase 1 rebuilds the generator so the produced `ttr.tmLanguage.json` covers every meaningful syntactic category and is generated mechanically from the actual lexer rules.

- [ ] **Audit pass (Task 19 from review).** For every entry in `packages/vscode-ext/scripts/generate-tm-grammar.ts`'s token arrays, grep `packages/grammar/src/TTR.g4` for the matching lexer rule. Remove anything not present (the review flagged `PRIMARY`, `SECONDARY`, `BTREE`, `FULLTEXT`, etc. as suspect). Add anything present in the grammar but missing from the generator.
- [ ] **Restructure the generator** so the input is the grammar's lexer rules (parsed via a tiny ANTLR-free regex pass over `TTR.g4`) and the output groups tokens by scope category. Categories to cover:
  - `keyword.control.def.ttr` — `def`, `schema`, `namespace`
  - `keyword.other.schema.ttr` — `db`, `er`, `map`, `query`, `cnc`
  - `keyword.other.kind.ttr` — `model`, `table`, `view`, `column`, `index`, `constraint`, `fk`, `procedure`, `entity`, `attribute`, `relation`, `er2dbEntity`, `er2dbAttribute`, `er2dbRelation`, `role`, `er2cncRole`
  - `keyword.other.property.ttr` — `description`, `tags`, `version`, `primaryKey`, `columns`, `indices`, `constraints`, `attributes`, `parameters`, `resultColumns`, `definitionSql`, `type`, `optional`, `isKey`, `searchable`, `indexed`, `labelPlural`, `nameAttribute`, `codeAttribute`, `aliases`, `cardinality`, `join`, `target`, `whereFilter`, `language`, `sourceText`, `length`, `precision`, `label`, `name`, `direction`, `displayLabel`, `valueLabels`, `roles`, `from`, `to`, `search`, `keywords`, `patterns`, `descriptions`, `examples`
  - `support.type.primitive.ttr` — `text`, `int`, `float`, `bool`, `datetime`, `string`, `boolean`, `number`, `integer`, `double`, `object`, `list`, `char`, `varchar`, `decimal`, `date`, `timestamp`
  - `constant.language.ttr` — `null`, `true`, `false`
  - `constant.language.indextype.ttr` — `primary`, `secondary`, `ordered`, `btree`, `fulltext`
  - `constant.language.constrainttype.ttr` — `unique`, `notNull`
  - `constant.language.querylang.ttr` — `SQL`, `TRANSFORMATION_DSL`, `DATAFRAME_DSL`, `REL_NODE`
  - `string.quoted.triple.ttr` — `"""..."""`
  - `string.quoted.double.ttr` — `"..."` with escape patterns inside
  - `constant.numeric.ttr` — number literal pattern
  - `comment.line.double-slash.ttr` — `// …`
  - `comment.block.ttr` — `/* … */`
  - `entity.name.tag.ttr` — the identifier immediately following any `def <kind>` (the name being defined)
  - `variable.other.qname.ttr` — dotted identifiers (refs)
  - `punctuation.separator.ttr` — `:`, `=`, `,`
  - `punctuation.section.braces.ttr` / `punctuation.section.brackets.ttr` / `punctuation.section.parens.ttr` — block punctuation
- [ ] **Generated file location.** Output to `packages/vscode-ext/syntaxes/ttr.tmLanguage.json` (existing path); commit the generated file.
- [ ] **Regeneration discipline.** Add `npm run regen-tmgrammar` to `packages/vscode-ext/package.json` that invokes the generator. Add a CI guard: a job that runs the generator and `git diff --exit-code packages/vscode-ext/syntaxes/ttr.tmLanguage.json` — fails if the committed file is stale.
- [ ] **Unit tests for the generator.** Vitest tests in `packages/vscode-ext/scripts/__tests__/generate-tm-grammar.test.ts` that assert: every category above is present in the output; specific known tokens land in the expected scope; no scope contains a duplicate pattern.
- [ ] **Manual verification.** Open every file in `samples/` in VS Code (Extension Development Host); spot-check that schema directives, def kinds, property names, primitive types, string literals (single and triple), numbers, and comments all colorize distinctly. The Czech-character identifiers (`artikl`, `obchodního_kanálu`, etc.) must highlight as identifiers, not as errors.

**Acceptance**: `npm run regen-tmgrammar` is idempotent on a clean tree; the CI guard passes; all sample files highlight correctly in the EDH.

## Section C — Full language configuration

Phase 0 (post-review Task 7) ships brackets, comments, and the basic auto-close pairs. Phase 1 adds indentation rules tuned for `def <kind> { ... }` and inline lists, smarter onEnter behavior, and a word pattern that handles dotted ids correctly.

- [ ] **`packages/vscode-ext/language-configuration.json` extended:**
  - Keep existing `comments`, `brackets`, `autoClosingPairs`, `surroundingPairs`
  - Add `wordPattern` covering `[a-zA-ZÀ-ɏ_][a-zA-Z0-9_À-ɏ]*` (matches the `IDENT` lexer rule) so word-based jumps respect Czech/Latin Extended characters
  - Add `indentationRules` with `increaseIndentPattern: ".*\\{[^}]*$"` and `decreaseIndentPattern: "^\\s*\\}"` so opening a `def` block auto-indents and closing `}` outdents
  - Add `onEnterRules` for the `def <kind> { … }` case: pressing Enter inside an empty `{ }` puts cursor on a new indented line and pushes the `}` down
- [ ] **Folding ranges.** Verify VS Code's bracket-based folding works for `{ ... }` and `[ ... ]` blocks across all sample files. If acceptable, no LSP-side folding provider is needed in Phase 1; if not, file a Phase 2 followup.
- [ ] **Manual verification.** In a `.ttr` file: type `def entity foo {` then Enter and confirm the cursor lands at correct indent and a closing `}` appears below; type `[` and confirm `]` auto-closes; double-click `er.entity.artikl` and confirm only `artikl` (or the whole dotted id, depending on word pattern choice) is selected.

**Acceptance**: indentation works as expected on every sample file; deliberate stress-test of nested `attributes: [ def attribute X { type: int } ]` indents correctly when re-typed.

## Section D — `.ttrl` layout sidecar support

The architecture (§6) defines `.ttrl` as a JSON file. Phase 1 registers it as a separate language and gives it the right developer affordances; the LSP does not parse it as TTR.

- [ ] **Language registration.** In `packages/vscode-ext/package.json`'s `contributes.languages`, add an entry for `ttrl` with `extensions: [".ttrl"]`, `aliases: ["TTR Layout"]`, `mimetypes: ["application/ttrl+json"]`, and `configuration` pointing at a new `language-configuration-ttrl.json` (JSON-style: `//` comments not allowed; brackets `{}`, `[]`; auto-close pairs identical to JSON).
- [ ] **TextMate grammar.** Reuse `source.json` — no per-`.ttrl` grammar needed. Add `"injectTo": ["source.json"]`-style binding only if we want to add layout-specific scope hints later (defer).
- [ ] **JSON schema.** Author `packages/vscode-ext/schemas/ttrl.schema.json` matching architecture §6's structure (`version`, `viewports`, `nodes`, `edges`). Contribute it via `contributes.jsonValidation` so VS Code shows IntelliSense and validates `.ttrl` files against the schema.
- [ ] **LSP behavior.** The LSP server must NOT attempt to parse `.ttrl` files as TTR. In `server.ts`, gate `onDidOpen`/`onDidChange` parse calls on `documentUri.endsWith('.ttr')`. Add a unit test confirming that `didOpen` of a `.ttrl` document does not produce parser diagnostics.
- [ ] **Manual verification.** Create a `samples/v1-metadata/.modeler/layout.ttrl` with one viewport and one node; open in VS Code; confirm syntax highlighting (JSON-style), schema validation hints, and no spurious diagnostics from the TTR LSP.

**Acceptance**: `.ttrl` files highlight as JSON, validate against the schema, and produce zero TTR LSP diagnostics.

## Section E — Diagnostic taxonomy

- [ ] **Pre-req from review-002:** confirm `walker.ts`'s `makeSourceLocation` uses the last-token's length for `endColumn` (not the span length). Run `node -e "import('./packages/parser/dist/index.js').then(({parseString}) => { const r = parseString('def entity foobar {}'); console.log(JSON.stringify(r.ast.definitions[0].source)); });"` and assert `endColumn === 20` before proceeding. (This was fixed in review-002 P0 Task 1; the test is a safety net.)

Phase 0 emits parser diagnostics with no `code` and no `source`. Phase 1 establishes the codes, severities, and sources for the Foundation-tier diagnostic set so Phase 2's semantic diagnostics can extend the same taxonomy cleanly.

- [ ] **`@modeler/parser` exports `DiagnosticCode` enum** in `packages/parser/src/diagnostics.ts`:
  - `ParseError = 'ttr/parse-error'`
  - `UnknownProperty = 'ttr/unknown-property'` (reserved for Phase 2 semantics layer — not emitted in Phase 1)
  - `ParseRecoveryInfo = 'ttr/parse-recovery-info'` (deferred; requires DefaultErrorStrategy subclass in Phase 2)
  - (Phase 2 will add: `UnresolvedReference = 'ttr/unresolved-reference'`, `DuplicateDefinition = 'ttr/duplicate-definition'`, `RequiredPropertyMissing = 'ttr/required-property-missing'`, etc.)
- [ ] **Each `ParseError` produced by `walker.ts` carries `code` and a structured `category`.** ANTLR syntax errors → `ParseError`; `UnknownProperty` and `ParseRecoveryInfo` are reserved for Phase 2.
- [ ] **`@modeler/lsp` propagates `code` and `source: 'modeler'`** on every `Diagnostic` it publishes. The mapping:

  | Parser code | LSP severity |
  |---|---|
  | `ttr/parse-error` | `Error` |
  | `ttr/unknown-property` | `Error` |
  | `ttr/parse-recovery-info` | `Information` |

- [ ] **Tests.** Per code: a parser-level Vitest test asserting the code is set; an LSP-level test asserting the `Diagnostic` carries the code, source, and correct severity.
- [ ] **Documentation.** `docs/design/diagnostics.md` (new) listing every code, what triggers it, the severity, and an example before/after fix. Phase 2's diagnostic codes are added to this file as they land.

**Acceptance**: every diagnostic in the LSP has a code; the codes match the documented set; severities match the table above.

## Section F — Parser error recovery

**Status:** Deferred — `ttr/parse-recovery-info` emission requires `DefaultErrorStrategy` subclass in Phase 2. ANTLR's built-in recovery already produces partial ASTs (verified by recovery fixtures). The `ParseRecoveryInfo` code is reserved and documented but not yet emitted.

- [ ] **Survey current behavior.** Write a fixtures file `packages/parser/src/__tests__/recovery-fixtures.ts` with 10 common-typo broken inputs (missing closing `}`, missing `:` between property and value, trailing comma in wrong place, unterminated string, missing `def` keyword, unknown property name, duplicate property within a def, malformed dotted id, missing comma between properties, invalid type literal). For each, document what the AST looks like today (often: empty `definitions`).
- [ ] **Add error-recovery hooks.** In `packages/parser/src/walker.ts`, hook the ANTLR parser's error-recovery strategy: emit a `ParseRecoveryInfo` diagnostic at the recovery point with a message describing what was assumed; continue producing AST. Use `DefaultErrorStrategy` extended subtype if needed.
- [ ] **Re-run the fixtures.** For each broken input, the parser must now produce **at least the partially-correct AST nodes** that precede the error point, plus a `ParseRecoveryInfo` diagnostic at the recovery boundary, plus the original `ParseError` for the syntactic problem.
- [ ] **Tests.** For each fixture, assert: `(a) errors.length >= 1`, `(b) at least one error has code 'ttr/parse-error'`, `(c) at least one informational diagnostic has code 'ttr/parse-recovery-info'`, `(d) ast.definitions.length >= expectedRecoveredDefs`.
- [ ] **Performance check.** Recovery-mode runs on the largest sample (`samples/v1-metadata/db.ttr`, ~80 KB) must still parse in <100 ms.

**Acceptance**: 10/10 recovery fixtures produce useful partial trees; parsing performance regression <2x on the worst case.

## Section G — Semantic tokens via LSP
- [ ] Deferred to Phase 1.1 — see review-003 §1G. Move to Phase 1.1 or Phase 2.A.
  - [ ] `textDocument/semanticTokens/full` handler in LSP server
  - [ ] Legend: 9 token types, 3 modifiers
  - [ ] Token producer walks AST definitions
  - [ ] Unit test for semantic tokens
  **Status:** Deferred — see review-003 §1G. Move to Phase 1.1 or Phase 2.A.

## Section H — File icons

Two icon files (one for `.ttr`, one for `.ttrl`) plus the icon-theme contribution that maps file extensions to them. Visually-themed but small.

- [ ] **Design / acquire two SVG icons.** `.ttr` should suggest "model + text"; `.ttrl` should suggest "layout sidecar." 16×16 base + retina variants. Place under `packages/vscode-ext/icons/`.
- [ ] **Icon-theme contribution.** Add a `contributes.iconTheme` entry in `package.json` pointing at a new `icons/ttr-icon-theme.json`; the theme file maps `ttr` and `ttrl` file extensions to the SVGs. Note: this is an optional theme the user opts into via `Files: Icon Theme`. If we want the icons to apply unconditionally we need to bind to `ttr` / `ttrl` language ids in the language registration's `icon` field instead — pick that path; it's the better UX.
- [ ] **Manual verification.** In VS Code Explorer, `samples/v1-metadata/er.ttr` shows the new icon; a synthetic `.ttrl` file shows its own icon.

**Acceptance**: icons render correctly in the Explorer, tab strip, and Quick Open at all standard sizes.

## Section I — ai-platform sync CI integration

Phase 0 shipped the local `sync-to-ai-platform.sh` and `check-sync.sh` scripts but no CI integration. Phase 1 adds the cross-repo CI job so silent grammar drift is impossible.

- [ ] **Cross-repo CI workflow.** Add `.github/workflows/grammar-sync.yml` that:
  - Triggers on PRs that touch `packages/grammar/src/TTR.g4`
  - Checks out both `modeler` and `ai-platform` (the latter via a token; for now, the workflow can run only when an `ai-platform` checkout exists in CI's environment — it's allowed to skip otherwise, with a clear "skipped: ai-platform not available" message)
  - Runs `bash packages/grammar/scripts/check-sync.sh "$AI_PLATFORM_PATH/shared/libs/kotlin/ttr-parser/src/main/antlr/shared/ttr/parser/generated/TTR.g4"`
  - On failure, posts a PR comment naming the diff and pointing at `sync-to-ai-platform.sh`
- [ ] **Local convenience.** Add `npm run sync-ai-platform` at the repo root that runs `bash packages/grammar/scripts/sync-to-ai-platform.sh "$AI_PLATFORM_PATH"`; documents the env var requirement in `packages/grammar/README.md`.
- [ ] **Document the contract** in `packages/grammar/README.md`: when the grammar changes here, the author runs `pnpm sync-ai-platform`, opens a paired PR in ai-platform, and links the two PRs in their descriptions. The CI job is the safety net; the convention is the primary mechanism.

**Acceptance**: a deliberate grammar change in this repo on a PR fails CI when ai-platform's vendored copy is stale; the failing CI message names the file and points at the sync script.

## Section J — VS Code smoke test

**Status:** Deferred — see review-003 §1J. Move to Phase 1.1. Scaffold removed
(placeholder test + broken runner script were cleaned up in review-003 Task 5).

## Section K — Broken-sample fixtures

Diagnostic-related code is hard to test without realistic broken input. We add a small set of broken variants of the existing samples for Foundation-tier diagnostic testing (and for Phase 2's semantic-diagnostic testing later).

- [ ] **`samples/broken/`** — new subdirectory. For each well-formed sample under `samples/v1-metadata/`, add a counterpart under `samples/broken/<name>-<defect>.ttr` with one specific defect. Suggested set:
  - `er-missing-brace.ttr` — drop a closing `}` somewhere in `er.ttr`
  - `er-unknown-property.ttr` — rename a property to a typo (`descriptin`)
  - `er-malformed-ref.ttr` — break a dotted id (`er..entity.foo`)
  - `db-trailing-comma-error.ttr` — extra comma in an unexpected position
  - `db-unterminated-string.ttr` — truncated string literal
  - `query-bad-language-value.ttr` — `language: NOTREAL` instead of `SQL`
- [ ] **README at `samples/broken/README.md`** explaining: these are intentionally broken; they're consumed by tests; do not "fix" them.
- [ ] **Integration tests** consume the broken fixtures: assert each one produces at least one diagnostic with the expected code.

**Acceptance**: integration tests reference each broken fixture; each one fires the expected diagnostic with the expected code.

## Section L — Documentation + progress

- [ ] **Update `docs/plan/progress-phase-01.md`** as work lands; one section per task list section above
- [ ] **`docs/design/diagnostics.md`** (new) — listing of every code with description, trigger, severity, fix
- [ ] **Update `docs/design/architecture.md`** §10 (Open questions) — close Question 1 (CST trivia attachment) if Section F's recovery work materially answers it; otherwise leave open with a Phase-2 reference
- [ ] **`packages/vscode-ext/README.md`** updated with the v1 feature list and screenshots of highlighting + diagnostics

## Acceptance criteria for Phase 1 as a whole

- [ ] All sections A–L complete
- [ ] `pnpm -r build` clean, `pnpm -r test` green, `pnpm -r lint` clean
- [ ] `pnpm --filter @modeler/integration-tests test` green
- [ ] `pnpm --filter @modeler/vscode-ext test:smoke` green
- [ ] Cross-repo grammar-sync CI passes (or skips with clear message when ai-platform not available)
- [ ] Hand-verified demo path: open every file under `samples/v1-metadata/` in EDH; visual highlighting accurate across all categories; brackets/comments/indentation behave correctly; introduce each defect from `samples/broken/` and confirm the expected diagnostic with the expected code; open a `samples/v1-metadata/.modeler/layout.ttrl` and confirm JSON-style highlighting + schema-driven IntelliSense
- [ ] PR reviewed and merged

## Risks and mitigations

- **TextMate generator complexity creep.** A fully-faithful generator is a small DSL of its own. Mitigation: scope is one-pass-over-grammar producing the categorized scopes; if a category needs grammar-context to disambiguate, push it to the LSP semantic-tokens layer (§G) instead.
- **ANTLR error recovery surprises.** ANTLR's `DefaultErrorStrategy` recovery is opinionated and sometimes deletes unexpected tokens silently. Mitigation: the 10 fixtures in §F are a guard rail; if any fixture produces nonsense partial trees, we extend `DefaultErrorStrategy` for that case rather than fight the framework.
- **Semantic-tokens performance.** Walking the entire AST on every change is fine for small files but might be costly on `db.ttr`-sized inputs. Mitigation: §G's tests include a perf check; if it's slow, add a stub `textDocument/semanticTokens/range` so VS Code only requests visible ranges.
- **`.ttrl` JSON schema drift.** Phase 3's Designer will be the first real producer of `.ttrl` files; the schema may need updates. Mitigation: ship the schema as a v1 first cut; bump to schema-v2 in Phase 3 if the Designer's serializer needs more.
- **VS Code smoke test flakiness.** `@vscode/test-electron` historically flakes on CI without GUI dependencies. Mitigation: pin to a known-good electron version; allow one retry; document the exact xvfb invocation for the Linux runner.

## Out of scope for Phase 1 (explicitly Phase 2 or later)

- Symbol table, cross-reference resolution, undefined-ref diagnostics (Phase 2.B/C)
- Validator (per-kind structural checks beyond what the parser already does) (Phase 2.D)
- Go-to-definition, find-references, hover (Phase 2.E/F/G)
- Workspace symbol search (Phase 2.H)
- Designer LSP integration beyond what Phase 0 (post-review-Task-4) ships (Phase 3)
- Designer schema/detail toggles, edge rendering, detail panel content (Phase 3)
- IntelliJ plugin (Phase 4)
- Rename, format, code actions, code lens (Polish tier — v1.2+)
- Completion (Productivity tier — v1.1)
