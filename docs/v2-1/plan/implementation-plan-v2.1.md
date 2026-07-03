# Tatrman Modeler v2.1 — Implementation Plan

**Status:** Plan v1, 2026-05-27. Covers the v2.1 release: inline mappings syntactic sugar. Design rationale in [`docs/v2-1/design/v2.1-inline-mappings.md`](../design/v2.1-inline-mappings.md). Grammar diff for ai-platform in [`docs/v2-1/design/grammar-v2-1-changes.md`](../design/grammar-v2-1-changes.md).

## Plan shape

v2.1 is organised into eight sections (A–H). Sections A–F are modeler-only; Section G mirrors the language layers on the ai-platform Kotlin side; Section H wraps up. The whole feature is a **single deployment unit** — one PR per repo, both reviewed and merged together.

| Section | Goal                                                                    | Time     | Dependencies      |
| ------- | ----------------------------------------------------------------------- | -------- | ----------------- |
| 2.1.A   | Design docs (this file + design + grammar-changes contract)             | done     | —                 |
| 2.1.B   | Grammar: TTR.g4 + property-map regen + TextMate regen                   | 1 day    | A                 |
| 2.1.C   | Parser AST + walker                                                     | 1–2 days | B                 |
| 2.1.D   | Semantic synthesizer                                                    | 2–3 days | C                 |
| 2.1.E   | Conflict validator                                                      | 1 day    | D                 |
| 2.1.F   | Integration tests + sample cleanup                                      | 1 day    | E                 |
| 2.1.G   | ai-platform mirror (Kotlin AST + synth + validator + tests)             | 2–3 days | F                 |
| 2.1.H   | Wrap-up: progress log, final verification on both repos, PR descriptions| 0.5 day  | G                 |

**Critical path:** A → B → C → D → E → F → G → H, total ~8–11 days for one developer.

**Branch:** `feat/v2.1-inline-mappings` on both repos.

**Commit style:** `Section <X>: <short description>` per CLAUDE.md convention.

**Per-section detailed task lists** for the implementer live under [`tasks/`](tasks/):
[B](tasks/section-B-grammar.md) · [C](tasks/section-C-parser-ast.md) · [D](tasks/section-D-synthesizer.md) · [E](tasks/section-E-conflict-validator.md) · [F](tasks/section-F-integration-tests.md) · [G](tasks/section-G-ai-platform.md) · [H](tasks/section-H-wrap-up.md). Start with [`tasks/README.md`](tasks/README.md).

## Section A — Design docs (done)

**Goal:** ship the design contract before any code changes.

**Deliverables:**
- [`docs/v2-1/design/v2.1-inline-mappings.md`](../design/v2.1-inline-mappings.md) — design doc with decisions C1–C10, the four surface forms, semantic synthesis rules, conflict semantics.
- [`docs/v2-1/design/grammar-v2-1-changes.md`](../design/grammar-v2-1-changes.md) — ai-platform contract: grammar diff, semantic-table representation, new diagnostic.
- [`docs/v2-1/plan/implementation-plan-v2.1.md`](implementation-plan-v2.1.md) — this file.

**Acceptance:** docs reviewed by Bora; no changes to the four surface forms or conflict semantics in subsequent sections without a doc amendment.

## Section B — Grammar changes (1 day)

**Goal:** extend `TTR.g4` with `mapping`, regenerate parser and TextMate grammar, no behaviour change yet (semantics still ignores the new property).

**Deliverables:**
- `packages/grammar/src/TTR.g4`:
  - Bump `@grammar-version:` marker to `2.1`.
  - Add `MAPPING : 'mapping' ;` lexer token.
  - Add `mappingProperty` and its sub-rules (`mappingValue`, `mappingBlock`, `mappingBlockProperty`, `mappingColumnsProperty`, `mappingColumnMap`, `mappingColumnEntry`, `mappingColumnValue`, `mappingTargetValue`) per grammar-changes §3.2.
  - Relax `targetProperty` to accept `id` per §3.3.
  - Add `mappingProperty` to `entityProperty`, `attributeProperty`, `relationProperty`.
  - Add `MAPPING` to `idPart`.
- `packages/grammar/src/generated/property-map.ts` — regenerated via `pnpm --filter @modeler/grammar prebuild`. New `mapping` entry under each affected kind.
- `packages/grammar/src/generated/version.ts` — regenerated; `TTR_GRAMMAR_VERSION` updates to `'2.1'`.
- `packages/grammar/CHANGELOG.md` — append `## 2.1 — 2026-05-27` entry summarising the additions.
- `packages/vscode-ext/syntaxes/ttr.tmGrammar.json` (or whatever the TM grammar output filename is) — regenerated via `node scripts/generate-tm-grammar.ts`. New keyword scopes for `mapping`.

**Acceptance:**
- `pnpm --filter @modeler/grammar build` clean.
- `pnpm --filter @modeler/parser build` clean (the generated TS parser regenerates without errors).
- Every existing v1.1 sample (`samples/v1.1-mini`, `samples/v1.1-metadata`) parses unchanged.
- `samples/2.1/er.ttr` (the user's raw sketch) is expected to still produce parse errors at Section B (it uses pre-design forms — `attributes:` not `columns:`, standalone `def mapping`, unbalanced braces); the parser must return `ttr/parse-error` diagnostics and not throw. Section F rewrites it to parse cleanly.

**Notes:** this is grammar-only. The synthesizer in Section D is what gives the new shapes their meaning. Sections B and C produce a parser that *recognises* but doesn't yet *understand* inline mappings.

**Commit:** `Section B: grammar — add MAPPING token and inline-mapping rules`

## Section C — Parser AST + walker (1–2 days)

**Goal:** the parser produces structured AST nodes for inline mappings with accurate source locations.

**Deliverables:**
- `packages/parser/src/ast.ts` (or equivalent) — new AST node types:
  - `MappingProperty { kind: 'mappingProperty', value: MappingValue, location }`
  - `MappingValue` — discriminated union: `{ kind: 'bareId', id, location } | { kind: 'block', target?, columns?, fk?, location }`
  - `MappingColumnEntry { name, value: MappingColumnValue, location }`
  - `MappingColumnValue` — discriminated union of the three forms (a)/(b)/(c) per design doc §3.1.
- `packages/parser/src/walker.ts` — walker construction methods for each new node type. Source locations follow the ANTLR-style invariant from CLAUDE.md: `endColumn = stopToken.column + stopTokenLength` (not `startColumn + spanLength`).
- Parser unit tests under `packages/parser/src/__tests__/`:
  - One test per surface form (entity full, attribute bare, attribute full, relation bare, relation full, `target:` shorthand).
  - One test asserting source locations point at the *value*, not the `mapping` keyword.

**Acceptance:**
- `pnpm --filter @modeler/parser test` green.
- AST nodes round-trip: parse → walk → re-render via the edit synthesizer's CST view produces the original text byte-for-byte (or as close as the edit synthesizer guarantees today).

**Commit:** `Section C: parser — AST nodes and walker for inline mappings`

## Section D — Semantic synthesizer (2–3 days)

**Goal:** inline mappings produce the same symbol-table entries as explicit `def er2db_*` declarations.

**Deliverables:**
- `packages/semantics/src/synthesizer.ts` (or new sibling file) — a `MappingSynthesizer` that walks the AST after the per-file parse + symbol pass, and for each `MappingProperty` produces the corresponding `Er2dbEntitySymbol` / `Er2dbAttributeSymbol` / `Er2dbRelationSymbol` synthesized entries.
- Synthesized symbols carry a `source: 'inline'` discriminator (currently every existing er2db_* symbol is implicitly `'explicit'`; add the field).
- Synthesized symbols have source locations pointing at the inline `mapping:` *value* per design doc §C7.
- Project symbol table additions: synthesized symbols are added to the project-level table but NOT to any `map`-schema `DocumentSymbolTable` per design doc §C6 / grammar-changes §4.4.
- Reference resolution: cross-references from the inline `target:` (e.g. `db.dbo.QZBOZI_DF`) resolve via the existing resolver chain — no special-casing.
- Unit tests under `packages/semantics/src/__tests__/`:
  - One test per surface form, asserting the right shape of synthesized symbol.
  - Test that synthesized symbols have correct source locations.
  - Test that synthesized symbols appear in `workspace/symbol` queries.

**Acceptance:**
- `pnpm --filter @modeler/semantics test` green.
- A round-trip test: parse a file with inline mappings, list all `er2db_entity.*` symbols, assert the count and qnames match what explicit declarations would have produced.

**Commit:** `Section D: semantics — synthesize er2db_* symbols from inline mappings`

## Section E — Conflict validator (1 day)

**Goal:** detect inline + explicit collisions and emit `ttr/duplicate-mapping`.

**Deliverables:**
- `packages/semantics/src/validators/duplicate-mapping.ts` — validator that runs after symbol-table assembly, walks the project table grouping mapping symbols by target qname, and emits a diagnostic on each side of any collision.
- `packages/semantics/src/diagnostics.ts` — register `ttr/duplicate-mapping` (Error severity) with a clear message format: e.g., `Mapping for "billing.products.map.er2db_entity.artikl" declared in two places: inline on entity at <loc1>, explicit def at <loc2>.`
- Fixtures under `samples/broken/v2.1/`:
  - `duplicate-mapping-entity/` — one entity inline-mapped + one explicit `def er2db_entity` for the same entity.
  - `duplicate-mapping-attribute/` — one attribute inline-mapped + one explicit `def er2db_attribute` for the same attribute.
  - `duplicate-mapping-relation/` — one relation inline-mapped + one explicit `def er2db_relation` for the same relation.
  - `duplicate-mapping-mixed/` — inline entity-level `columns.X` + explicit `def er2db_attribute X` for the same attribute.
- Unit + integration tests asserting the diagnostic fires on both source locations.

**Acceptance:**
- `pnpm --filter @modeler/semantics test` green.
- Each fixture produces exactly one `ttr/duplicate-mapping` diagnostic per side (two total per fixture).

**Commit:** `Section E: semantics — duplicate-mapping conflict validator`

## Section F — Integration tests + sample cleanup (1 day)

**Goal:** sanitize the user's `samples/2.1/` sketch into a canonical sample; add cross-package integration tests via the `tests/integration/` harness.

**Deliverables:**
- `samples/2.1/er.ttr` — rewritten to use the agreed four surface forms cleanly, with balanced braces. Demonstrates each form with a representative entity. Old explicit `def er2db_*` declarations in `samples/2.1/map.ttr` are kept for one entity to demonstrate inline + explicit coexistence in the same project (without conflict — different entities).
- `samples/2.1/db.ttr` — unchanged.
- `samples/2.1/map.ttr` — trimmed to retain only the explicit declarations for entities not inline-mapped in `er.ttr`.
- New integration tests under `tests/integration/src/__tests__/`:
  - `inline-mappings-parse.test.ts` — every form in `samples/2.1/` parses cleanly.
  - `inline-mappings-resolve.test.ts` — synthesized symbols resolve, and `er.entity.artikl` ↔ `db.dbo.QZBOZI_DF` references navigate both ways.
  - `inline-mappings-conflict.test.ts` — each fixture under `samples/broken/v2.1/` produces the expected diagnostic.
- Backward-compat: `samples/v1.1-mini` and `samples/v1.1-metadata` continue to parse and resolve unchanged.

**Acceptance:**
- `pnpm --filter @modeler/integration-tests test` green.
- `pnpm -r build && pnpm -r test && pnpm -r typecheck && pnpm -r lint` green across the modeler workspace.

**Commit:** `Section F: integration tests + sample cleanup`

## Section G — ai-platform mirror (2–3 days)

**Goal:** mirror Sections B–E on the ai-platform Kotlin side.

**Deliverables (on the ai-platform repo, on a `feat/v2.1-inline-mappings` branch):**
- Run `sync-to-ai-platform.sh ~/Dev/ai-platform` from the modeler branch — the new `TTR.g4` lands in `<ai-platform>/shared/libs/kotlin/ttr-parser/src/main/antlr/shared/ttr/parser/generated/TTR.g4`.
- Regenerate the Kotlin parser via ai-platform's existing ANTLR build step.
- New Kotlin AST node types matching modeler's TypeScript shape: `MappingProperty`, `MappingValue` (sealed class with `BareId` and `Block` data classes), `MappingColumnEntry`, `MappingColumnValue` sealed class.
- Kotlin synthesizer matching modeler's: walks the AST after symbol-table assembly, produces synthesized `Er2dbEntitySymbol` / `Er2dbAttributeSymbol` / `Er2dbRelationSymbol` entries with `source: MappingSource.Inline(...)`.
- Kotlin duplicate-mapping validator emitting the `ttr/duplicate-mapping` diagnostic on both sides of any conflict.
- Kotlin tests covering each surface form and each conflict shape.
- `check-sync.sh` from modeler passes (hashes match).

**Acceptance:**
- ai-platform's build is green.
- Kotlin tests cover the same shapes as modeler's integration tests.
- `check-sync.sh ~/Dev/ai-platform` from modeler exits 0.

**Commit (on ai-platform branch):** `Section G: ai-platform — Kotlin parser, synthesizer, validator for inline mappings`

**Notes:** ai-platform's existing parser-suite tests (per `grammar-v1-1-changes.md` §6 they have a 17-case suite) must continue to pass. Run them as part of the acceptance check.

## Section H — Wrap-up + PR (0.5 day)

**Goal:** progress log, final verification, PR descriptions ready.

**Deliverables:**
- `docs/v2-1/plan/progress-phase-v2.1.md` — task-completion log mirroring `docs/v1-1/plan/progress-phase-v1.1.md`'s shape. One line per section, with the commit hash.
- `CLAUDE.md` updated if any new invariants emerged (e.g., "inline mappings synthesize into the project symbol table, not the `map` schema's per-file table" if that turns out to bite during testing).
- Final verification:
  - On modeler: `pnpm -r build && pnpm -r test && pnpm -r typecheck && pnpm -r lint` green.
  - On ai-platform: equivalent build/test command green.
  - `check-sync.sh` green.
- PR descriptions drafted for both repos. Each PR description:
  - Links to the design doc and grammar-changes doc.
  - Summarises the section-by-section commits.
  - Notes the cross-repo dependency: ai-platform PR cannot merge without modeler PR's grammar being adopted, and vice versa.

**Acceptance:**
- Both PRs ready to be opened.
- Progress log committed.

**Commit:** `Section H: wrap-up — progress log and final verification`

## Acceptance summary

v2.1 ships when:

- Every surface form in design doc §3 parses, synthesizes, and resolves correctly on both modeler and ai-platform.
- `ttr/duplicate-mapping` fires on every conflict fixture on both sides.
- Every existing v2.0 sample parses and resolves unchanged.
- `samples/2.1/er.ttr` is a clean, canonical demonstration of all four forms.
- `check-sync.sh` green.
- All CI checks green on both repos.
- Both PRs reviewed; merging coordinated.

## Risks

- **Grammar ambiguity between `mappingColumnValue` forms (b) and (c).** Form (b) is `{ target: <bareId|object> }` and form (c) is `object_` (which can include `{ target: ... }`). ANTLR's longest-match should pick (b) when the object's only key is `target`, and (c) otherwise — but worth testing explicitly. Mitigation: parser unit tests in Section C with both forms in the same fixture.
- **Source-location accuracy on synthesized symbols.** The CLAUDE.md `endColumn = stopToken.column + stopTokenLength` invariant has bit before (per the note in `walker.ts`). Mitigation: explicit source-location assertions in Section C tests, not relaxed bounds.
- **Kotlin/TypeScript divergence on synthesis behaviour.** Two implementations of the same rules — easy for them to drift. Mitigation: share a small JSON-fixture file (or Markdown table) listing input → expected synthesized symbols, and both test suites consume it.
- **`workspace/symbol` performance with synthesized symbols.** A large project with thousands of inline-mapped attributes doubles (roughly) the size of the project symbol table. Mitigation: profile in Section F if the integration-test suite slows noticeably; defer optimisation to v2.1.1 if needed.

## v2.2+ outlook

Not committed; brainstorm only:

- **Designer UI hint** distinguishing inline vs. explicit mappings in the inspector ("referenced by") panel.
- **Code action: "convert explicit mapping to inline"** in the LSP, for users who want to migrate gradually.
- **`def mapping { type: ... }` standalone form** (deferred from v2.1 per decision C4). Would warrant its own design pass.
