# Tasks — review-059 (Section B: grammar)

> **STATUS (2026-05-27, commit `2b673b5`): all closed.** B1 (grammar fixed + 6-form test green + task-file spec corrected), B2 (`pnpm -r test` green, both integration call sites guarded, plan acceptance fixed, Section F revert-reminder added), and L2 (CLAUDE.md + Section B task file reconciled) are done. L1 remains a carry-forward tracking item (pre-existing, non-blocking). See the "Resolution" section of [`review-059.md`](review-059.md). Gate: parser 115 · lsp 130 · integration 92(+1 skip) · typecheck 8/8 · lint clean.

Findings in [`review-059.md`](review-059.md). Section B's `.g4` edits match the task list and the package builds + typecheck are green, but the grammar **cannot parse the design's canonical entity example** (B1) and the **full test suite is red** (B2). Do B1 first (it changes the grammar and forces a regen), then B2, then the Low items. Each step has an exact verification command — run it and confirm the stated result before ticking the box.

> Work on branch `feat/v2.1-inline-mappings`. Do **not** run `sync-to-ai-platform.sh` — Section G owns the sync.

---

## B1 — Fix `mappingColumnValue` so column-entry forms (b) and (c) parse  *(BLOCKER)*

The current rule `mappingColumnValue : id | object_` cannot parse `{ target: ... }` because `target` (the `TARGET` token) is not a valid generic object key (`idPart` does not list it). Restore the contract-doc grammar (`docs/v2-1/design/grammar-v2-1-changes.md` §3.2).

- [ ] **Edit `packages/grammar/src/TTR.g4`.** Find the `mappingColumnValue` rule (around lines 240–243). Replace exactly this:
  ```antlr
  mappingColumnValue
      : id
      | object_
      ;
  ```
  with this:
  ```antlr
  mappingColumnValue
      : id
      | LBRACE TARGET propSep? mappingTargetValue RBRACE
      | object_
      ;

  mappingTargetValue
      : id
      | object_
      ;
  ```
  Do **not** change any other rule. (`idPart` stays as-is — do **not** add `TARGET` to it; the dedicated alternative above is the correct fix, not widening the generic object-key set.)

- [ ] **Regenerate the parser:**
  ```bash
  cd packages/parser && pnpm run prebuild
  ```
  Expect: `Parser generated to .../parser/src/generated`, no errors.

- [ ] **Rebuild the parser:**
  ```bash
  pnpm --filter @modeler/parser build
  ```
  Expect: clean `tsc`.

- [ ] **Add parser unit tests — one per surface form — proving valid input parses with zero errors.** Create `packages/parser/src/__tests__/inline-mapping-grammar.test.ts`. For each case below, call `parseString(src)` and assert `result.errors` has length 0. Use `schema er namespace entity` as the first line where an entity/attribute/relation is defined.
  - [ ] **Entity full (a+b+c + entity target shorthand)** — this is the case that currently fails; it MUST pass after the fix:
    ```ttr
    schema er namespace entity
    def entity artikl {
      mapping: {
        target: { table: db.dbo.QZBOZI_DF },
        columns: {
          id_artiklu:    IDZBOZI,
          kód_artiklu:   { target: KOD_ZBOZI },
          název_artiklu: { target: { column: NAZEV_ZBOZI } }
        }
      },
      attributes: [ def attribute id_artiklu { type: int, isKey: true } ]
    }
    ```
  - [ ] **Attribute bare-id:** `def attribute id_produktu { type: int, mapping: IDSKUPZBOZI }` (inside an entity's `attributes:`).
  - [ ] **Attribute full:** `def attribute n { type: text, mapping: { target: { column: NAZEV_ZBOZI } } }`.
  - [ ] **Relation bare-fk:** `def relation r { from: er.entity.a, to: er.entity.b, mapping: db.dbo.fk_artikl_produkt }`.
  - [ ] **Relation full:** `def relation r { from: er.entity.a, to: er.entity.b, mapping: { fk: db.dbo.fk_artikl_produkt } }`.
  - [ ] **`target:` shorthand in explicit er2db (the relaxation):** `schema map namespace m` + `def er2db_attribute foo { target: KOD_ZBOZI }`.

- [ ] **Run the new tests:**
  ```bash
  pnpm --filter @modeler/parser test -- inline-mapping-grammar
  ```
  Expect: all green. If the entity-full case still fails with `mismatched input 'target'`, the `.g4` edit or regen did not take — recheck.

- [ ] **Correct the Section B task file so the spec matches the shipped grammar.** In `docs/v2-1/plan/tasks/section-B-grammar.md`:
  - Replace the `mappingColumnValue : id | object_` block (lines ~100–104) with the corrected three-alternative rule + `mappingTargetValue` (as above).
  - Fix the note at line ~108 ("`mappingColumnValue` collapses forms (b) and (c) … into one `object_` alternative") — it is incorrect; `{ target: … }` needs the dedicated `LBRACE TARGET …` alternative because `TARGET` is not a valid generic object key. Reword to match §3.2 of the contract doc.
  - Fix the same claim in the "Notes / gotchas" bullet at line ~188.

---

## B2 — Make the test suite green (guard the WIP `samples/2.1/` sketch)  *(HIGH)*

`samples/2.1/er.ttr` is the user's pre-design sketch and is **expected** to be broken until Section F rewrites it. Two "parse all samples" tests pick it up and fail. Exclude `samples/2.1` from those globs until Section F.

- [ ] **`packages/parser/src/__tests__/parser.test.ts`** — at line 94 (inside the "parses all sample files without errors" test), change:
  ```ts
  const ttrFiles = await getAllTtrFiles(samplesDir, ['broken']);
  ```
  to:
  ```ts
  const ttrFiles = await getAllTtrFiles(samplesDir, ['broken', '2.1']); // 2.1 sketch is WIP until Section F (review-059 B2)
  ```

- [ ] **`tests/integration/src/integration.test.ts`** — at line 126 (the `beforeAll` feeding the "parses all sample files (non-broken) without errors" test at line 195), change:
  ```ts
  sampleFiles = await getAllTtrFiles(samplesDir, ['broken']);
  ```
  to:
  ```ts
  sampleFiles = await getAllTtrFiles(samplesDir, ['broken', '2.1']); // 2.1 sketch is WIP until Section F (review-059 B2)
  ```

- [ ] **Grep for any other call site that asserts all samples parse cleanly**, so none is missed:
  ```bash
  grep -rn "getAllTtrFiles(samplesDir, \['broken'\])" packages/ tests/
  ```
  For each hit, decide: if its test asserts zero parse errors over all samples, add `'2.1'` to the exclude list (with the same comment). Leave call sites that don't assert clean-parse untouched.

- [ ] **Add a Section F reminder.** In `docs/v2-1/plan/tasks/section-F-integration-tests.md`, add a checkbox under its sample-cleanup tasks: "Remove the `'2.1'` entry from the `getAllTtrFiles(samplesDir, ['broken', '2.1'])` exclusions in `parser.test.ts` and `integration.test.ts` (added in review-059 B2) once `samples/2.1/*.ttr` parses cleanly." (If `section-F-integration-tests.md` doesn't yet have a sample-cleanup section, add the note near the `samples/2.1/er.ttr` rewrite deliverable.)

- [ ] **Fix the plan's contradictory acceptance bullet.** In `docs/v2-1/plan/implementation-plan-v2.1.md`, Section B → Acceptance, the bullet currently reads:
  > `samples/2.1/er.ttr` (the user's sketch, even if semantically not yet implemented) parses without grammar errors.

  Replace it with:
  > `samples/2.1/er.ttr` (the user's raw sketch) is expected to still produce parse errors at Section B (it uses pre-design forms — `attributes:` not `columns:`, standalone `def mapping`, unbalanced braces); the parser must return `ttr/parse-error` diagnostics and not throw. Section F rewrites it to parse cleanly.

---

## Low (do L2 now; L1 is a ticket)

- [ ] **L2 — Reconcile the "commit generated files" docs with the actual `.gitignore`.** Generated files under `packages/*/src/generated/` are gitignored and never committed; the docs that tell you to commit them are wrong and mislead reviewers.
  - In `docs/v2-1/plan/tasks/section-B-grammar.md`, fix the Verification bullet that says `git status` should show regenerated files under `packages/parser/src/generated/` and `packages/grammar/src/generated/` and to "stage them … in one commit." State instead: those directories are gitignored (regenerated by the parser `prebuild`); only the `.g4` edit, `CHANGELOG.md`, the property-map/TextMate **scripts**, and the tracked `packages/vscode-ext/syntaxes/ttr.tmLanguage.json` are committed.
  - In the root `CLAUDE.md`, "Grammar regeneration" section, fix step 3 ("Commit the generated files alongside the grammar change") to reflect that `src/generated/` is gitignored and regenerated at build time; only `TTR.g4`, the scripts, and the TextMate `syntaxes/*.json` are committed.
- [ ] **L1 — File a follow-up ticket (do not fix here):** severely malformed input (e.g. the current `samples/2.1/er.ttr`) yields an uncoded captured exception `Cannot read properties of null (reading 'tableProperty')` in `result.errors` instead of a typed `ttr/parse-error`. Parser does not throw, so it's non-blocking, but the walker should guard the null and emit a coded diagnostic. Pre-existing; owner = error-recovery hardening.

---

## Done when

- [ ] B1: `pnpm --filter @modeler/parser test -- inline-mapping-grammar` green (all six forms parse, **including** the entity full a+b+c case); the Section B task file's `mappingColumnValue` spec + notes match the shipped grammar.
- [ ] B2: `pnpm -r test` green across the whole workspace; the `'2.1'` exclusion is noted for Section F to revert; the plan's Section B acceptance bullet is corrected.
- [ ] L2: the Section B task file and CLAUDE.md no longer instruct committing gitignored generated files.
- [ ] Re-run the Section B gate and confirm: `pnpm --filter @modeler/grammar build` clean · `pnpm --filter @modeler/parser build` clean · `pnpm -r typecheck` green · `pnpm -r test` green · `TTR_GRAMMAR_VERSION === '2.1'`.
- [ ] Commit on `feat/v2.1-inline-mappings`, e.g. `Section B (review-059): fix mappingColumnValue target forms + guard WIP 2.1 sample`.
