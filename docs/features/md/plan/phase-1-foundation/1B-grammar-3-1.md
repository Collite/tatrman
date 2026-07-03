# Stage 1B — Grammar 3.1 + regen + parser fixtures

Goal: extend `TTR.g4` (additive 3.0 → 3.1) to accept the `md` schema, the six logical `def` kinds,
the `md2db_*` / `md2er_cubelet` binding kinds, the `DOTDOT` range literal, and the new body keyword
tokens — keeping every new keyword in `idPart`. Regenerate the parser + TextMate grammar. This stage
makes the constructs **parse**; AST/walker shaping is 1C/1D.

Prereq: Stage 1A may be in parallel. Clean tree, gates green. TDD: 1B1 (fixtures/tests) before 1B2.

References (verified):
- Grammar: `packages/grammar/src/TTR.g4` — `schemaCode` (≈ line 100), `objectDefinition` (≈ line
  108), the per-kind `*Def` / `*Property` blocks (≈ lines 133–201), generic value forms
  (`object_`, `value`, `functionCall`, `listOfIds`, `localizedString` ≈ lines 360–487), `idPart`
  (≈ line 500), lexer tokens (≈ lines 522–681). `MAP : 'map'` exists (≈ line 541); **`DOMAIN`
  token does NOT exist** — it was deleted in 3.0 and must be re-added.
- The full token + production list to add: [`../../grammar-md-changes.md`](../../grammar-md-changes.md)
  §2–§8. **That sketch is the spec for this stage.**
- Regen procedure: `CLAUDE.md` → "Grammar regeneration" — `cd packages/parser && pnpm run prebuild`
  (also runs `packages/grammar`'s `extract-property-map.ts`), then
  `cd packages/vscode-ext && node scripts/generate-tm-grammar.ts`.

---

- [x] **1B1 — Fixtures + parser tests first (red).** Under `packages/parser/src/__tests__/`
  (+ a fixture dir) add `.ttrm` snippets, one per construct, asserting **parse-success** and basic
  CST shape:
  - a `schema md` file with `def domain` (scalar + member-set + `kind: calc`/`bound` + a
    `restrict: { range: 1..12 }` using the new range literal),
  - `def dimension` with inline `attributes: [def attribute …]`, a `key`, and `hierarchies`,
  - `def map` (calc form `calc: truncToDay`, parameterised `calc: fiscalYearOfDate(fiscalYearStartMonth: 4)`, and table-backed `from`/`to`/`cardinality`),
  - `def hierarchy` with `levels: [Day, Month via md.m2q, Quarter]`,
  - `def measure` (simple `aggregation: sum` and object `{ default: sum, time: latestValid }`),
  - `def cubelet` with `grain` + `measures` (refs and inline),
  - a `schema binding` file with `md2db_cubelet` (wide + long), `md2db_domain`, `md2db_map`,
    `md2er_cubelet`.
  - Add a **negative** test: a bare-word `domain`/`measure`/`cubelet` used as a cross-reference
    fragment (e.g. `db.dbo.measure`) still parses (idPart coverage).
  - Run; confirm red (tokens/rules don't exist yet).

- [x] **1B2 — Lexer tokens.** In `TTR.g4`, before `IDENT`, add per
  [`../../grammar-md-changes.md`](../../grammar-md-changes.md) §2: `MD`; **re-add `DOMAIN`**;
  `DIMENSION`, `HIERARCHY`, `MEASURE`, `CUBELET`; `MD2DB_CUBELET`, `MD2DB_DOMAIN`, `MD2DB_MAP`,
  `MD2ER_CUBELET`; body keywords `RESTRICT`, `MEMBERS`, `KIND`, `CALC`, `KEY`, `HIERARCHIES`,
  `LEVELS`, `VIA`, `CLASS`, `AGGREGATION`, `VALID_BY`, `GRAIN`, `MEASURES`, `SHAPE`, `JOURNALING`,
  `SOURCE`; and punctuation `DOTDOT : '..'` placed **before** `DOT`.

- [x] **1B3 — Parser rules.** Add per the sketch §3–§7:
  - `MD` into `schemaCode`;
  - the ten new `objectDefinition` alternatives;
  - the `*Def` bodies and `*Property` rules for domain/dimension/map/hierarchy/measure/cubelet and
    the four binding kinds, reusing existing rules where the sketch says so (`typeProperty`,
    `attributesProperty`, `fromProperty`/`toProperty`, `cardinalityProperty`, `targetProperty`,
    `listOfIds`, `object_`, `functionCall`, `localizedString`);
  - new value rules `rangeLiteral`, `restrictBlock`/`restrictClause`, `membersBlock`/`memberEntry`,
    `levelList`/`levelEntry`, `aggregationValue`, `shapeValue`, `journalingValue`,
    `measureInlineList`;
  - extend `idPart` with all new keywords (sketch §8), including the re-added `DOMAIN`.

- [x] **1B4 — Version + header.** Bump the `@grammar-version:` marker to `3.1` and add a
  `Changes in 3.1 (additive — MD model)` block to the header comment. Update
  `packages/grammar/package.json` `version`. (Phase 0 Stage D owns the 3.0 release line; coordinate
  so 3.1 publishes on top of it — do **not** tag here.)

- [x] **1B5 — Regenerate.**
  - `cd packages/parser && pnpm run prebuild` (regenerates `packages/parser/src/generated/*` and
    `packages/grammar/src/generated/property-map.ts` via `extract-property-map.ts` — confirm the new
    properties appear there).
  - `cd packages/vscode-ext && node scripts/generate-tm-grammar.ts` (new keywords now highlight).
  - Do not hand-edit any `generated/**`.

- [x] **1B6 — Verify.**
  - The 1B1 parser tests now pass (parse-success; AST detail comes in 1C/1D).
  - `pnpm --filter @modeler/parser test && pnpm --filter @modeler/grammar test`
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build`
  - All **existing** parser/semantics fixtures still parse (additive change — no regressions).

- [x] **1B7 — Commit.** `Section MD-1B: grammar 3.1 — md schema, MD def kinds, bindings`.
