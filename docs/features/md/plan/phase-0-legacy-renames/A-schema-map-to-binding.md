# Stage A — `schema map` → `schema binding`

Goal: rename the cross-model mapping **schema code** from `map` to `binding`. The `er2db_entity` /
`er2db_attribute` / `er2db_relation` defs are **unchanged** — only the schema directive they live
under is renamed. This frees the words "map"/"mapping" for the MD primitive.

Prereq: clean tree, all gates green. TDD: do A1 (tests) before A2–A4.

References (verified file pointers):
- Grammar: `packages/grammar/src/TTR.g4` — `schemaCode : DB | ER | MAP | QUERY | CNC ;` (≈ line 103),
  the same alternation inside `idPart` (≈ line 493), and `MAP : 'map' ;` (≈ line 528).
- Semantics: `packages/semantics/src/default-schema.ts`, `manifest.ts`,
  `mapping-references.ts`, `mapping-synthesizer.ts`, `resolver.ts` (search for the `'map'` schema literal).
- Printer/format: `packages/format/src/printer.ts`.
- Fixtures + docs: every file containing `schema map` (search below).

---

- [ ] **A1 — Tests first (red).**
  - Add a conformance fixture `tests/conformance/fixtures/NN-schema-binding.ttrm` (use the next free
    number) containing `schema binding` + one `er2db_entity` def; assert it parses with zero
    diagnostics in the conformance runner.
  - Add a **negative** fixture/test asserting `schema map` now produces an "unknown schema code"
    diagnostic (the token is being removed as a schema code).
  - Update any existing unit test in `packages/semantics/src/__tests__/**` that asserts the `map`
    schema string to expect `binding`.
  - Run the suites; confirm they fail for the right reason before touching source.

- [ ] **A2 — Grammar.** In `packages/grammar/src/TTR.g4`:
  - Add lexer token `BINDING : 'binding' ;` near the other schema-code tokens.
  - Add `BINDING` to the `schemaCode` alternation and to `idPart` (so it stays usable in
    cross-references).
  - **Remove** `MAP` from the `schemaCode` alternation (keep the `MAP` token itself — it will become
    the MD `def map` keyword in a later phase; leave it in `idPart`).
  - Bump the `@grammar-version:` marker comment toward `3.0` (final value set in Stage D) and add a
    CHANGELOG note in the header comment block.

- [ ] **A3 — Regenerate + wire semantics.**
  - Run the two regen steps (CLAUDE.md → Grammar regeneration): `cd packages/parser && pnpm run
    prebuild`, then `cd packages/vscode-ext && node scripts/generate-tm-grammar.ts`.
  - Update the schema-code handling in `packages/semantics/src/default-schema.ts` and wherever the
    `'map'` literal is matched (`mapping-references.ts`, `resolver.ts`, `manifest.ts`) to use
    `'binding'`. Keep symbol/namespace semantics identical — this is a string rename only.
  - Update `packages/format/src/printer.ts` if it special-cases the `map` schema keyword.

- [ ] **A4 — Migrate fixtures + docs.**
  - Replace `schema map` → `schema binding` in every fixture and doc:
    `rg -l 'schema[[:space:]]+map' --glob='!**/node_modules/**' --glob='!**/.vscode-test/**'`
    then sed each. (Fixtures will be re-extensioned in Stage C — leave their names alone here.)
  - Grep for stray prose references to "the map schema" in `docs/**` and `CLAUDE.md`; update wording
    to "the binding schema".

- [ ] **A5 — Verify.**
  - `pnpm --filter @modeler/parser test && pnpm --filter @modeler/semantics test && pnpm --filter @modeler/format test`
  - `pnpm -r typecheck && pnpm -r lint`
  - `rg 'schema[[:space:]]+map' --glob='!**/node_modules/**' --glob='!**/.vscode-test/**'` returns
    **no** results outside CHANGELOG.

- [ ] **A6 — Commit.** `Section Phase0-A: rename schema map → binding`. Do **not** tag a grammar
  release yet (Stage D).
