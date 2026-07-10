# Tasks — review-062 (Section E: duplicate-mapping validator)

> **STATUS (2026-05-28, commit `36ab4dc`): all closed.** E1 (sample-sweep restored), E2 (dead `sampleFiles` removed from lsp describe), E3 (it.skip re-indented), E4 (own location filtered from diagnostic message — verified at runtime that each location names the *other*), E5 (db.ttr clean assertion added to all 4 fixtures), E6 (clean-only-inline / clean-only-explicit added for attribute + relation), E7 (file already had trailing newline; no change needed). Gate: parser 122 · semantics 124 · edit 60 · migrate 23 · lsp 130 · vscode-ext 24 · designer 129 · integration **104**(+1 skip) · typecheck 8/8 · lint 8/8. Section F can proceed. See the "Resolution" section of [`review-062.md`](review-062.md).

Findings in [`review-062.md`](review-062.md). Section E ships clean — the validator, 4 broken fixtures, unit suite, and integration wiring are all correct, and the full workspace gate is green (parser 122 · semantics 120 · integration 99(+1 skip) · typecheck 8/8 · lint 8/8). All findings below are cleanup/polish — none block Section F. Do **E1 and E2 now** (restoring the deleted guardrail test + removing dead code); fold E3–E7 into the Section F commit if convenient.

> Work on branch `feat/v2.1-inline-mappings`. The fixtures all use `schema map` without a namespace — that's the production convention from review-061 D3 that makes the qnames actually collide. Don't change that.

---

## E1 — Restore the deleted "parses all sample files (non-broken) without errors" integration test  *(LOW; do now)*

The Section E commit replaced this test (it lived just before `describe('v2.1 inline-mapping broken fixture diagnostics', …)`) with the new fixture-diagnostic block. The same coverage exists at the parser level (`packages/parser/src/__tests__/parser.test.ts:93`), so it isn't a hard regression — but the **LSP-pipeline reflection** is gone, and you'll want it for Section F anyway.

- [ ] **Edit `tests/integration/src/integration.test.ts`.** Find the new `describe('v2.1 inline-mapping broken fixture diagnostics', …)` (around line 196) inside `describe('parser integration', …)`. Immediately **above** that new describe, restore the deleted test exactly as it was before commit `53e107e`:

  ```ts
  it('parses all sample files (non-broken) without errors', async () => {
    for (const file of sampleFiles) {
      const result = await parseFile(file);
      expect(result.errors, `Errors in ${file}: ${result.errors.map(e => e.message).join(', ')}`).toHaveLength(0);
    }
  });
  ```

  This uses the existing `sampleFiles` declared at line 123, which is currently dead (see E2 — E1 restores its only consumer).

- [ ] **Verify:**
  ```bash
  pnpm --filter @modeler/integration-tests test
  ```
  Test count should go up by 1 (integration 99 → 100).

## E2 — Remove the dead `sampleFiles` in the second describe block  *(LOW; do now)*

After E1 lands, the FIRST `sampleFiles` (line 123, in `describe('parser integration', …)`) is alive again. But the SECOND `sampleFiles` (line 245, in `describe('lsp integration', …)`) is still dead — nothing in that describe reads it.

- [ ] **Edit `tests/integration/src/integration.test.ts`**, inside `describe('lsp integration', …)` (around line 244). Delete:
  ```ts
    let sampleFiles: string[];
  ```
  and the assignment inside `beforeAll`:
  ```ts
      sampleFiles = await getAllTtrFiles(samplesDir, ['broken', '2.1']); // 2.1 sketch is WIP until Section F (review-059 B2)
  ```
  Keep the rest of the `beforeAll` (the LSP-connection setup) untouched.

- [ ] **Verify:**
  ```bash
  pnpm --filter @modeler/integration-tests test
  pnpm --filter @modeler/integration-tests typecheck
  ```
  Both green; no `TS6133` ("declared but never used") and no "unused-vars" lint complaint.

## E3 — Fix `it.skip` indentation drift  *(LOW; fold into Section F)*

- [ ] **Edit `tests/integration/src/integration.test.ts:193`.** Re-indent `it.skip('file-ordering.ttr — N/A (order-strict grammar; see README)', () => {});` to 4 spaces (it currently sits at column 0).

## E4 — Tweak `otherLocations` message wording  *(LOW; optional cosmetic)*

`validateDuplicateMappings` (validator.ts) joins **all** entries' locations into `otherLocations`, including the entry the diagnostic is being emitted for. Reads slightly redundant ("declared in 2 places: A:5, B:8" when emitted at A:5). Two options:

- (a) Rename `otherLocations` → `allLocations` in the variable and **don't** change the message wording (acceptable as-is). OR
- (b) Filter out the entry's own URI+line before joining; the message then names *the other* location(s) only.

Both are fine. Pick (a) for minimal churn if you want this closed.

- [ ] In `packages/semantics/src/validator.ts`, in `validateDuplicateMappings`, either rename for clarity or filter:
  ```ts
  // option (b) — filter the entry's own location:
  const others = entries
    .filter((other) => !(other.documentUri === e.documentUri && other.source.line === e.source.line))
    .map((o) => `${o.documentUri}:${o.source.line}`)
    .join(', ');
  ```
  Update the message to interpolate `others` instead of `otherLocations`.

## E5 — Tighten the integration fixture tests to assert `db.ttr` is clean  *(LOW; fold into Section F)*

Each fixture has `db.ttr` (a stub table). The probe shows `db.ttr` emits zero diagnostics in all four fixtures, but the test doesn't assert this — any future change that accidentally fires a diagnostic on `db.ttr` would silently pass.

- [ ] **Edit `tests/integration/src/integration.test.ts`**, in the `v2.1 inline-mapping broken fixture diagnostics` describe (around line 212). Add a third case row:
  ```ts
  const cases: Array<[string, string[]]> = [
    ['er.ttr', ['ttr/duplicate-mapping']],
    ['map.ttr', ['ttr/duplicate-mapping']],
    ['db.ttr', []],
  ];
  ```
  And handle the empty-expected case in the assertion (the current `new Set(expected)` works because `new Set([])` is an empty set; `got` falls back to `new Set<string>()`, so `expect(got).toEqual(new Set([]))` passes when there are no diagnostics).

## E6 — Add clean-only-inline / clean-only-explicit cases for attribute and relation  *(LOW; coverage symmetry)*

The unit suite has both clean cases for entity but only the collision case for attribute and relation.

- [ ] **Edit `packages/semantics/src/__tests__/duplicate-mapping.test.ts`.** In the `ttr/duplicate-mapping — attribute` describe, add two `it(...)`'s mirroring the entity pattern (only inline → 0; only explicit → 0). Do the same in the `ttr/duplicate-mapping — relation` describe.

## E7 — Add trailing newline to `duplicate-mapping.test.ts`  *(LOW; fold into Section F)*

- [ ] **Edit `packages/semantics/src/__tests__/duplicate-mapping.test.ts`.** Ensure the file ends with a newline (the current `\ No newline at end of file` should disappear from `git show`).

---

## Done when

- [ ] E1: integration `parses all sample files (non-broken) without errors` test restored; integration suite count = 100.
- [ ] E2: dead `sampleFiles` removed from `lsp integration` describe; typecheck/lint clean.
- [ ] E3: `it.skip(...)` re-indented.
- [ ] (Optional) E4: `validateDuplicateMappings` message tweaked.
- [ ] (Optional) E5: integration fixture tests assert `db.ttr` is clean.
- [ ] (Optional) E6: clean-only cases added for attribute + relation.
- [ ] (Optional) E7: EOF newline on `duplicate-mapping.test.ts`.
- [ ] Re-run the Section E gate: `pnpm -r build` · `pnpm -r test` green · `pnpm -r typecheck` green · `pnpm -r lint` green.
- [ ] Commit on `feat/v2.1-inline-mappings`, e.g. `Section E (review-062): restore integration sample sweep + dead-code cleanup`.
