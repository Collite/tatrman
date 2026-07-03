# Tasks — review-032 (search block feature)

> Findings in [`review-032.md`](review-032.md). T1–T4 are essentially done and correct. This is a short follow-up list: 1 real fix, 2 optional polish, 1 separate-ticket flag, 1 coordination item.

## 1. Regenerate the stale TextMate generator artifact — ✅ DONE

> Done. `generate-tm-grammar.js:111` now has `case 'FUZZY': return 'keyword.other.property.ttr';`, and `ttr.tmLanguage.json` is unchanged (as expected — see task 4). Committed.

- [x] **1.1.** Run the canonical regen.
- [x] **1.2.** `FUZZY` case present in the `.js`; `ttr.tmLanguage.json` unchanged.
- [x] **1.3.** Committed.

## 2. Guard the two new broken fixtures — ✅ DONE

> Done. The two rows are in the B7 table (`integration.test.ts:158–159`), and the suite is green — integration now **48 passed | 2 skipped** (was 46+2). Both fixtures emit exactly their advertised code with no stray extras.

- [x] **2.1.** Two rows added to the B7 table.
- [x] **2.2.** `pnpm --filter @modeler/integration-tests test` green; exact-set assertions pass.

## 3. Tighten `searchBlocksOf` typing — OPTIONAL

- [ ] **3.1.** In `packages/semantics/src/validator.ts`, change `searchBlocksOf`'s parameter from the structural `{ kind: string; search?…; columns?: unknown[]; attributes?: unknown[] }` to the `Definition` union type (import it from `@modeler/parser`), narrowing on `def.kind` instead of casting. Keep behaviour identical. (Cosmetic; current code is lint-clean and works.)

## 4. TextMate property-keyword highlighting — SEPARATE TICKET (not a blocker)

- [ ] **4.1.** File a ticket: the generated `ttr.tmLanguage.json` has a `keywords` repository entry that `include`s `#keyword_other_property_ttr` (and `#keyword_control_def_ttr`, etc.), but the generator never emits those repository blocks — the includes are dangling, so no property keyword (`search`, `searchable`, `fuzzy`, `patterns`, `keywords`, …) is highlighted. Pre-existing; affects all property keywords, not just the new ones. Until fixed, the feature README's manual-smoke ("confirm `search`/`searchable`/`fuzzy` highlight") cannot pass.

## 5. T5 (ai-platform) — COORDINATE / VERIFY

- [ ] **5.1.** In `~/Dev/ai-platform`: confirm `packages/grammar/scripts/check-sync.sh ~/Dev/ai-platform` reports matching hashes, the Kotlin parser was regenerated, and the YAML→TTR converter emits `search { searchable: … }` (merged with any other search content). Run ai-platform's metadata test suite.
- [ ] **5.2.** Coordinate merge order: T5 must merge **before** any ai-platform metadata produced with the old top-level `searchable` is re-validated against the new grammar, or that metadata fails to parse.

## Verify

```bash
pnpm -r build && pnpm -r typecheck && pnpm -r lint && pnpm -r test
```
All green today; re-run after tasks 1–3.
