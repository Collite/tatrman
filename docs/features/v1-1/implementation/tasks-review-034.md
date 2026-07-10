# Tasks — review-034 (Section C1 re-review)

Findings in [`review-034.md`](review-034.md). **All review-033 items (F1–F6) are signed off — do not redo them.** There is exactly **one** required fix: G1 (cardinality contract adherence). Pick path A (recommended) or path B, not both.

> Baseline (currently green, keep it): parser 82, semantics 107, lsp 53, designer 61, vscode-ext 7, integration 50 passed | 1 skipped.

---

## G1 — Make cardinality match the §8 contract

The contract `docs/v1/design/phase-03-contracts.md` §8 says cardinality entries are **string-valued only** (non-string → `null`) and the accepted "many" inputs are **`"n"` and `"*"`** (not `"many"`). The new fixture broke that and the parser was widened to compensate.

### Path A — fix the fixture, revert the parser (RECOMMENDED, no contract change)

- [ ] **A.1** In `samples/v1.1-mini/entities/artikl.ttr`, change the relation's cardinality from the non-canonical form to canonical quoted strings. Replace:
  ```
  cardinality: { from: 1, to: many }
  ```
  with:
  ```
  cardinality: { from: "1", to: "0..*" }
  ```
  (Use `"n"` or `"*"` instead of `"0..*"` if you prefer; all three map to `'many'`.)
- [ ] **A.2** In `packages/lsp/src/__tests__/graph-resolve.test.ts`, the cardinality case (the test titled "relation with cardinality extracted correctly", ~line 96) uses `cardinality: { from: 1, to: n }`. Change it to string form:
  ```
  cardinality: { from: "1", to: "n" }
  ```
  Keep the existing expectations (`fromCardinality` → `'one'`, `toCardinality` → `'many'`).
- [ ] **A.3** In `packages/lsp/src/model-graph.ts`, revert `parseCardinality`: remove the added `case 'many': return 'many';` line so the `'many'` group is back to just `case '0..*': case 'n': case '*': return 'many';`. (Do **not** touch the other cases.)
- [ ] **A.4** In `packages/lsp/src/model-graph.ts`, revert `extractCardinality`'s `lookup` to the contract form — string-only:
  ```ts
  const lookup = (key: string): Cardinality | null => {
    const entry = obj.entries.find((e) => e.key === key);
    if (!entry || entry.value.kind !== 'string') return null;
    return parseCardinality(entry.value.value);
  };
  ```
  Remove the `id` and `number` branches.
- [ ] **A.5** Verify:
  ```bash
  pnpm --filter @modeler/lsp test
  pnpm --filter @modeler/parser test
  pnpm --filter @modeler/integration-tests test
  pnpm -r typecheck && pnpm -r lint
  ```
  All green. The `graph-resolve` cardinality case and the v1 `model-graph-cardinality.test.ts` cases must still pass; the `artikl_overview.ttrg` parse sweep still passes.

### Path B — keep the broadened syntax, but amend the contract (only if this is a deliberate v1.1 decision)

Do this **instead of** Path A only if you intend `.ttr`/`.ttrg` to accept unquoted/numeric/`many` cardinality going forward. If you're unsure, take Path A.

- [ ] **B.1** Amend the cardinality contract: edit `docs/v1/design/phase-03-contracts.md` §8 (or add an explicit override section in `docs/v1-1/design/v1-1-contracts.md`) to state that cardinality entries may also be `number` (`1`) and bare `id` (`many`, `n`), and add `"many"`/`many` to the accepted "many" inputs. Update the reference `extractCardinality` snippet so it no longer says string-only.
- [ ] **B.2** Bump the contract version again (header + a new §12 changelog entry in `v1-1-contracts.md`, e.g. `v6, 2026-05-21 — §8 cardinality now accepts numeric and bare-id values …`).
- [ ] **B.3** Add a note in the task list / a ticket to coordinate with ai-platform: confirm its Kotlin cardinality mapping accepts the same broadened forms, or the modeler and ai-platform will disagree on what parses.
- [ ] **B.4** Add explicit `parseCardinality` test cases for the new accepted forms in `model-graph-cardinality.test.ts` (e.g. `parseCardinality('many')` → `'many'`), so the broadening is intentional and locked, not incidental.
- [ ] **B.5** Verify (same commands as A.5), all green.

---

## Done when

- [ ] Either Path A (parser matches the existing §8 contract) **or** Path B (contract amended + version-bumped + ai-platform coordination noted) is complete — not a mix.
- [ ] Full suite green: `pnpm -r build && pnpm -r typecheck && pnpm -r lint && pnpm -r test`.
- [ ] No cardinality value in `samples/` or in the LSP tests uses a form the contract doesn't sanction.
