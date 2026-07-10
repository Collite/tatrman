# Review 032 — `search` block rework + fuzzy hint

> Feature plan lives at `docs/features/search-block/` (README + T1–T5).

**Scope reviewed:** T1–T4 (this repo). T5 lives in the unmounted `ai-platform` repo and cannot be verified here.
**Verdict:** **Pass with minor follow-ups.** The editor-tooling implementation (grammar, parser/AST/walker, semantics, samples, forward-looking docs) is faithful to the plan, well-tested, and clean. Build / typecheck / lint / tests are all green:

```
pnpm -r build        green
pnpm -r typecheck    green
pnpm -r lint         green
parser tests         70 passed (incl. 13 new in search-block.test.ts)
semantics tests      106 passed (incl. 6 new search-validation cases)
integration tests    46 passed | 2 skipped
```

Three small follow-ups remain (one real-but-inert artifact, one pre-existing highlighting gap the feature's own DONE-criterion exposes, one test-coverage gap), plus T5 which is unverifiable from here. None block merging T1–T4.

---

## T1 — Grammar + regeneration

**Grammar (`TTR.g4`): correct.** Diff matches the plan exactly — `searchableProperty` removed from `columnProperty`/`attributeProperty`; `searchBlockProperty` added to `tableProperty`/`viewProperty`/`columnProperty`/`relationProperty`; `fuzzyProperty` production added; `searchable` + `fuzzy` added to `searchSubProperty`; `FUZZY` lexer token added; block comments updated. ✅

**ANTLR regeneration: done.** The generated parser is gitignored (build artifact — `git check-ignore` confirms), so it correctly doesn't appear in `git status`, but on disk it contains `Fuzzy` / `fuzzyProperty` and the widened `*Property` contexts. The 13 new parser tests exercise these contexts and pass, so regeneration is real and consistent. ✅ (The README's DONE bullet "generated parser files committed" is unreachable given the repo gitignores them — a pre-existing contradiction with `CLAUDE.md`, not this feature's problem.)

**TextMate — two issues:**

1. **[Minor / real] `generate-tm-grammar.js` is stale.** The developer edited `generate-tm-grammar.ts` (added `case 'FUZZY'`) but never ran the canonical regen. The repo's actual flow is `pnpm --filter @modeler/vscode-ext run regen-tmgrammar`, which compiles `.ts → .js` and then runs the `.js`. The committed `.js` still lacks the `FUZZY` case (verified: `diff` of the `.ts`/`.js` `tokenToScope` cases shows `.js` missing `FUZZY`; running `regen-tmgrammar` adds exactly that one line to the `.js`). So the `.ts` and its build artifact are out of sync. **Fix:** run `regen-tmgrammar` and commit the regenerated `.js`.

2. **[Pre-existing / flag] The TextMate grammar does not highlight property keywords at all.** Running `regen-tmgrammar` produces **zero** change to `ttr.tmLanguage.json` — because the generator never emits a `keyword_other_property_ttr` repository block. The `keywords` repository entry `include`s `#keyword_other_property_ttr` (and `#keyword_control_def_ttr`, etc.), but **those repository definitions don't exist** in the emitted grammar — the includes are dangling. So `fuzzy`, `searchable`, `search`, `patterns`, `keywords`, … are not scoped/highlighted, and never were. This means the README's manual-smoke DONE item — "confirm `search`/`searchable`/`fuzzy` highlight" — **cannot pass for any property keyword**, new or old. This is a **pre-existing generator gap**, not a regression introduced here, but it's worth recording because the feature's acceptance criterion surfaces it. The `tokenToScope('FUZZY')` case is therefore correct-but-inert until the generator is fixed to emit the property-keyword patterns. Recommend a separate ticket for the generator; do not block this feature on it.

---

## T2 — Parser AST + walker

**Excellent — matches the plan precisely.**

- `SearchBlock` gains `searchable?`, `fuzzy?`, `duplicateProperties?`. `ColumnDef` drops `searchable`, adds `search?`. `AttributeDef` drops `searchable` (keeps `search?`). `TableDef`/`ViewDef`/`RelationDef` add `search?`. ✅
- `walkSearchBlock` reads both booleans and records duplicates via the `seen`/`bump` bookkeeping → `duplicateProperties`. ✅
- Leaf `searchable` reads removed from **all four** call sites (block + inline forms of column and attribute); `search` wired into table/view/column/relation walkers (block + inline). ✅
- `grep "\.searchable"` across `packages/**/src` (excluding `generated/`) returns only legitimate uses: `sb.searchable` (validator), `p.searchableProperty()` (the ANTLR accessor), and `search?.searchable` (tests). No leftover `column.searchable` / `attribute.searchable`. ✅
- 13 tests cover all newly-allowed kinds, the still-allowed kinds, the two negative parse-error cases (top-level `searchable` on column/attribute), duplicate bookkeeping, and the mixed-sub-property mapping. ✅

---

## T3 — Semantics validation

**Correct, with one sensible deviation from the written plan.**

- Two codes added to `DiagnosticCode`: `FuzzyWithoutSearchable = 'ttr/fuzzy-without-searchable'`, `DuplicateSearchProperty = 'ttr/duplicate-search-property'`. The plan's literal example omitted the `ttr/` prefix, but every existing enum value carries it (`ttr/duplicate-import`, …); the developer correctly matched the **actual** enum style rather than the example. ✅
- **Deviation (correct):** the plan said "inside `validateProject()`", but `validateProject()` has no AST access (it iterates `symbols.duplicates()`). The developer placed the pass inside `validateDocument`'s existing `for (const def of ast.definitions)` loop instead — which is both where the AST is available **and** the method actually wired into the LSP `publishDiagnostics` flow. Good adaptation. ✅
- `searchBlocksOf` yields the def's own top-level `search` (covers `relation`/`query`/`role`/`table`/`view`/`column` top-level defs) plus nested `columns`/`attributes` for `table`/`view`/`entity`. Complete. ✅
- 6 validator tests cover all 5 plan cases (fuzzy-without-searchable; searchable+fuzzy clean; searchable:false+fuzzy warns; duplicate keywords errors; clean table; clean column). ✅
- **Minor (style):** `searchBlocksOf`'s parameter is typed structurally (`{ kind: string; search?…; columns?: unknown[]; attributes?: unknown[] }`) with an `as Array<{ search?: SearchBlock }>` cast, rather than using the `Definition` union. It's lint-clean (no `any`) and works, but a `Definition`-typed signature would be tighter. Optional.
- **Not done (optional):** the plan's "optional but recommended" LSP-level integration test (open a `.ttr` with a bad `search` block, assert the diagnostic surfaces via `publishDiagnostics`) was not added. Given the diagnostics run in `validateDocument` (which the LSP does call), this is low-risk, but an integration test would lock it in.

---

## T4 — Samples + docs

- `samples/v1-metadata/db.ttr`: table-level `search { searchable: true }` + a column `search { searchable: true, fuzzy: true }`. ✅
- `samples/v1-metadata/er.ttr`: an attribute `search { searchable: true, fuzzy: true, keywords: { cs: […], en: […] } }` (booleans + localized list together). ✅ Both files still parse with 0 errors (the "parses all sample files without errors" integration test passes).
- Broken fixtures `search-fuzzy-without-searchable.ttr` and `search-duplicate-subproperty.ttr` exist and are well-formed for their target diagnostics. ✅
  - **[Minor] not asserted by any automated test.** They were placed under `samples/broken/v1.1/`, which is loaded by the B7 guardrail (`integration.test.ts`), but the B7 table doesn't list them, and the parser broken-sweep excludes `v1.1`. So nothing asserts they emit their intended code (they're manual-smoke only). They don't break the existing B7 aggregate checks (no duplicate-definition; package-mismatch only on its fixture — verified, suite still 46-pass). Consider adding two rows to the B7 table so they're guarded.
- Forward-looking docs: `H2-other-completion.md` updated to offer `search` (not top-level `searchable`) and to list `searchable`/`fuzzy` inside the block. ✅ `E1-graph-picker.md`'s only "searchable" is prose ("a searchable, filterable list") describing the picker UI — not the TTR property — correctly left untouched. The architecture doc doesn't document the property surface (grep empty) — correctly a no-op. ✅
- Breaking-change verification: no `.ttr` in `samples/` uses top-level `searchable` anymore; `samples/yaml/**` retains `searchable:` as intended T5 input. ✅

---

## T5 — ai-platform (cannot verify here)

The `ai-platform` repo is not mounted, so the grammar sync, Kotlin regen, and YAML loader/converter changes are taken on the developer's word. Two reminders carried forward from T5's own notes:

- **Merge ordering matters.** Per T5's closing note: T5 must land **before** any ai-platform metadata generated with the old top-level `searchable` is re-validated against the new grammar, or that metadata fails to parse. Confirm the coordination before/at merge.
- **Verify, don't assume.** The DONE item "grammar synced + Kotlin regenerated + YAML emits `search { searchable }`" should be checked against the actual ai-platform PR (run `check-sync.sh` there; confirm the round-trip test).

---

## Punch list (see `tasks-review-032.md`)

1. **[do]** Regenerate and commit `generate-tm-grammar.js` (run `regen-tmgrammar`); it's stale (missing the `FUZZY` case).
2. **[optional]** Add the two new broken fixtures to the B7 table in `integration.test.ts` so they're guarded.
3. **[optional]** Tighten `searchBlocksOf`'s parameter type to `Definition`.
4. **[separate ticket]** TextMate generator emits dangling `#keyword_*_ttr` includes → no property-keyword highlighting (pre-existing). The README's highlighting smoke-test can't pass until this is fixed; not a blocker for T1–T4.
5. **[coordinate]** Verify T5 in `ai-platform` and the merge order.
