# Review 062 — Section E (conflict validator: `ttr/duplicate-mapping`)

**Date:** 2026-05-28
**Release:** v2.1 (inline mappings)
**Scope:** review of Section 2.1.E against [`tasks/section-E-conflict-validator.md`](../plan/tasks/section-E-conflict-validator.md), the design rule in [`v2.1-inline-mappings.md`](../design/v2.1-inline-mappings.md) §5, and the contract in [`grammar-v2-1-changes.md`](../design/grammar-v2-1-changes.md) §5. Commit under review: `53e107e` "Section E: conflict validator ttr/duplicate-mapping".

Verified against runtime (not just by reading the diff):

- `pnpm -r build` clean.
- `pnpm -r test` green: parser 122 · semantics **120** (was 114; +6 dup-mapping tests) · edit 60 · migrate 23 · lsp 130 · vscode-ext 24 · designer 129 · integration **99** (+1 skip; was 92; +7 new dup-mapping fixture cases). No regressions.
- `pnpm -r typecheck` green (8/8). `pnpm -r lint` green (8/8).
- **Probed each of the 4 broken fixtures end-to-end** with `parseFile` + `synthesizeMappings` + `Validator.validateProject()` — every fixture emits exactly **2 `ttr/duplicate-mapping`** diagnostics, **0 `ttr/duplicate-definition`**, **0 other** diagnostics. Source lines land on the actual `mapping:` value and the `def er2db_*` header.

Companion: [`tasks-review-062.md`](tasks-review-062.md).

**Verdict: DONE.** The diagnostic is registered, the validator is small and surgically targeted, the DuplicateDefinition skip prevents double-firing, all four fixtures emit cleanly with no diagnostic noise, the unit suite covers entity/attribute/relation/mixed/clean-only-inline/clean-only-explicit, the integration suite asserts the precise codes per file, and the full workspace gate is green across the board. A handful of cosmetic cleanups remain (E1–E7) — none block Section F, all are well under 30 minutes of polish.

---

## What's good (verified)

- **`DiagnosticCode.DuplicateMapping = 'ttr/duplicate-mapping'`** added in the right spot in `packages/parser/src/diagnostics.ts:27`, matching the kebab-case + `ttr/` prefix of existing codes.
- **`ProjectSymbolTable.allQnames()` + `getAll(qname)`** added (project-symbols.ts:84–91). Returns the underlying `byQname` list (not the single-entry `get`), exactly what collision detection needs. Cleanly extends the surface; no privacy break.
- **`Validator.validateDuplicateMappings`** is short, correct, and targeted. The guards run in cheap-first order: `entries.length < 2` → `kind` is er2db_* → at least one entry has `mappingSource === 'inline'`. Emits one diagnostic per entry pointing at *that* entry's source — Designer red squiggles will land on the correct text. Message format follows the spec ("Duplicate mapping for `<qname>` — declared in `<N>` places: `<locations>`") and does **not** expose the synthesized-vs-explicit distinction, per spec gotcha.
- **DuplicateDefinition skip refinement** (validator.ts:211–218): in the existing `for (const dup of this.symbols.duplicates())` loop, the dev added a guard that skips er2db_* qnames where any entry is `'inline'` — preventing double-fire (DuplicateDefinition was the *old* behavior for any qname collision; now inline-vs-explicit collisions get only `DuplicateMapping`). This is a refinement not literally in the spec but exactly the right thing to do; without it, the user would see both codes on the same lines.
- **Validator runs `validateDuplicateMappings` from `validateProject`** so it integrates with the existing diagnostic pipeline — no separate wiring needed.
- **Four broken fixtures** under `samples/broken/v2.1/` (entity / attribute / relation / mixed), each in its own package (`broken.v2_1.dup_entity`, …) to keep them isolated. Every fixture has the three files (`db.ttr` stub, `er.ttr` inline, `map.ttr` explicit) and uses `schema map` **without** a namespace — the production convention required for the qnames to actually collide (per review-061 D3). Probed all four:

  | Fixture                          | `ttr/duplicate-mapping` | `ttr/duplicate-definition` | Other |
  | -------------------------------- | ----------------------- | -------------------------- | ----- |
  | duplicate-mapping-entity         | 2 (lines 6, 5)          | 0                          | 0     |
  | duplicate-mapping-attribute      | 2 (lines 7, 5)          | 0                          | 0     |
  | duplicate-mapping-relation       | 2 (lines 22, 5)         | 0                          | 0     |
  | duplicate-mapping-mixed          | 2 (lines 6, 5)          | 0                          | 0     |

  Cleanest possible: every fixture fires exactly the contract-required count, with no incidental noise from other validators.
- **Unit tests** (`duplicate-mapping.test.ts`, 6 tests): entity collision (`toHaveLength(2)`), only-inline entity clean (`0`), only-explicit entity clean (`0`), attribute collision (`2`), entity-columns + explicit-attribute mixed (`>=2` — correctly loose; runtime emits exactly 2), relation collision (`2`).
- **Integration fixture wiring** (`integration.test.ts:196–225`): a new describe `v2.1 inline-mapping broken fixture diagnostics` iterates the four fixture dirs and asserts each `er.ttr`/`map.ttr` emits **exactly** `Set(['ttr/duplicate-mapping'])`. Strict set-equality, so an unexpected extra code would fail the assertion. The `synthesizeMappings` call was added to `collectFixtureCodes` (integration.test.ts:71) so the helper now matches what the LSP load pipeline does.
- **No regressions** on the `v1.1` broken-fixture guardrails (B7 / circular / file-ordering / `pkg_a` exclusion) — they still pass unchanged.
- **`brokenFiles` exclusion correctly extended** to `['v1.1', 'v2.1']` (line 132) so the existing `broken fixtures produce ttr/parse-error diagnostics` test doesn't sweep over the v2.1 fixtures (which should produce `duplicate-mapping`, not `parse-error`).

---

## Low (cleanup / polish — none block Section F)

### E1 — Deleted "parses all sample files (non-broken) without errors" guardrail in `integration.test.ts`

The previous test at integration.test.ts (the `for (const file of sampleFiles) ... expect.errors.toHaveLength(0)` block) is **gone** in this commit — replaced by the new `describe('v2.1 inline-mapping broken fixture diagnostics', ...)`. The diff splice landed at the same line range and consumed the old test. Net coverage is preserved because `packages/parser/src/__tests__/parser.test.ts:93` still has an equivalent `parses all sample files without errors` test, so the basic sanity is intact at the parser level. But the LSP-pipeline reflection of "all real samples load cleanly through the integration harness" is now gone — and re-introducing it is one block of code. Looks like collateral damage of the edit, not a considered removal.

Restore: re-add the deleted `it('parses all sample files (non-broken) without errors', …)` block immediately above the new `v2.1 inline-mapping broken fixture diagnostics` describe.

### E2 — `sampleFiles` is now dead code in both describe blocks

Because of E1, `sampleFiles` is declared and assigned in `beforeAll` at integration.test.ts:123/127 and 245/250 but no longer read anywhere. ESLint's config evidently tolerates assigned-but-unread `let` in this pattern (the gate passed), but it's dead code regardless. Remove the two `let sampleFiles: string[];` declarations and the corresponding `sampleFiles = …` lines in both `beforeAll` blocks.

### E3 — `it.skip` indentation drift

`integration.test.ts:193` lost its 4-space indent during the edit:

```ts
    // N/A fixtures: documented in samples/broken/v1.1/README.md as not emittable
    // under the order-strict grammar (file-ordering). graph-layout-stale-node is now
    // fixed (unquoted keys) and covered above.
it.skip('file-ordering.ttr — N/A (order-strict grammar; see README)', () => {});
  });
```

Cosmetic only — lint doesn't enforce indentation in this config — but a continuation of the same drift pattern seen during Section D (review-061 D5). Re-indent to 4 spaces.

### E4 — `otherLocations` includes the entry's own location

`validateDuplicateMappings` builds `otherLocations` as `entries.map(...)` — every entry, including the one the diagnostic is being emitted *for*. So a diagnostic on file A line 5 reads "declared in 2 places: file://A:5, file://B:8" — `A:5` is its own location. Mildly redundant; user-facing. Spec template I wrote had the same flaw, so this is inherited. Two options: rename to `allLocations` in the message ("declared in 2 places: …"), or filter out the entry's own URI+line before joining. The current wording reads acceptably even with the redundancy; do **not** re-spin just for this — flag for whoever next polishes diagnostic copy.

### E5 — Integration fixture tests don't assert `db.ttr` is clean

Each `collectFixtureCodes()` returns a `Map<basename, Set<code>>` over all three files in the fixture, but the test only asserts on `er.ttr` and `map.ttr` (lines 213–214). Any unexpected codes in `db.ttr` would silently pass. The probe shows `db.ttr` emits no diagnostics in all four fixtures, so practically fine — but tightening is one line: add `['db.ttr', []]` and assert empty set.

### E6 — Minor unit-test coverage gaps

The unit suite has clean-only-inline and clean-only-explicit cases for entity but not for attribute or relation. The validator code is uniform across kinds (single `firstKind === 'er2dbEntity' || …` check), so the risk is minimal, but two extra `toHaveLength(0)` assertions would close the symmetry. Two-inline collisions are reserved per design §5.2 and explicitly out of scope for v2.1 — no test needed.

### E7 — EOF newline missing on `duplicate-mapping.test.ts`

Same pattern as Sections C/D. The diff ends with `\ No newline at end of file`. Add a trailing newline.

---

## Recommendation

Section E ships. The functional contract is met cleanly, the diagnostic noise floor is zero, and the full workspace gate is green for the first time in this release (parser 122 · semantics 120 · integration 99 · typecheck 8/8 · lint 8/8). Address **E1** (restore the integration sample-cleanliness test) and **E2** (clean up the now-dead `sampleFiles`) before Section F, since F is where you'll want that broad integration guardrail anyway. E3–E7 are sub-30-minute polish; bundle them with another Section F change. `tasks-review-062.md` has the concrete, ordered steps.

---

## Resolution (2026-05-28, commit `36ab4dc`)

**Verdict now: DONE — all cleanup items closed in a single commit.** Re-verified each item against runtime.

- **E1 ✅** — `it('parses all sample files (non-broken) without errors', …)` restored at integration.test.ts (just before the new v2.1 broken-fixture describe), reading the existing `sampleFiles`. Integration suite count went 99 → 104 (+1 sweep + 4 db.ttr clean cases from E5).
- **E2 ✅** — `let sampleFiles` and its `beforeAll` assignment removed from the `lsp integration` describe. The `parser integration` declaration (line 123) is now the only one, and it's consumed by the restored E1 test.
- **E3 ✅** — `it.skip('file-ordering.ttr …')` re-indented to 4 spaces, matching surrounding style.
- **E4 ✅** — `validateDuplicateMappings` now filters out the entry's own location before joining. Verified at runtime: the diagnostic at `er.ttr:6` now lists only `map.ttr:5` (the *other* side), and vice versa. Cross-reference reads cleanly.
- **E5 ✅** — Each v2.1 fixture describe now also asserts `db.ttr` emits an empty set. Tightens the guardrail against future regressions where a stub-table file might accidentally fire a diagnostic.
- **E6 ✅** — Added `only inline → 0` and `only explicit → 0` cases for both attribute and relation. Semantics suite went 120 → 124 (+4).
- **E7 ✅** — Dev confirmed via `xxd` that the file already had a trailing newline; the earlier `\ No newline at end of file` was a git-diff display artifact. No change needed.

**Gate after fix:** parser 122 · semantics 124 · edit 60 · migrate 23 · lsp 130 · vscode-ext 24 · designer 129 · integration **104** (+1 skip) · `pnpm -r typecheck` 8/8 · `pnpm -r lint` 8/8 · `pnpm -r build` clean. **Section E fully complete; Section F (integration tests + sample cleanup) can proceed.**
