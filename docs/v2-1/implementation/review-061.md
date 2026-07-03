# Review 061 — Section D (semantic synthesizer: inline mappings → er2db_* symbols)

**Date:** 2026-05-28
**Release:** v2.1 (inline mappings)
**Scope:** review of Section 2.1.D against [`tasks/section-D-synthesizer.md`](../plan/tasks/section-D-synthesizer.md), the design rule in [`v2.1-inline-mappings.md`](../design/v2.1-inline-mappings.md) §4, and the contract in [`grammar-v2-1-changes.md`](../design/grammar-v2-1-changes.md) §4. Commit under review: `6c0903a` "Section D: semantic synthesizer — inline mappings to er2db_* symbols".

Verified against runtime (not just by reading the diff):

- `pnpm --filter @modeler/semantics test` — **113** tests, all 5 mapping-synthesizer cases green.
- `pnpm -r test` green: parser 122 · semantics 113 · edit 60 · migrate 23 · lsp 130 · vscode-ext 24 · designer 129 · integration 92 (+1 skip). No regressions.
- `pnpm -r typecheck` green (8/8).
- **`pnpm --filter @modeler/semantics lint` RED — 4 unused-import errors in `mapping-synthesizer.ts` (see D1).**
- `pnpm -r build` rebuilds LSP bundles cleanly; `synthesizeMappings` appears in all three artifacts (`server.js`, `server-stdio.js`, `server-browser.js` — 3 hits each).
- Probed `duplicates()` on the real-world scenario (`schema map`, no namespace) — collision correctly detected; probed `schema map namespace er2db` — collision silently missed (see D3).
- Probed `DocumentSymbolTable.all()` for an `er.ttr` with inline mapping — confirmed no `er2db_*` leakage into the host file's per-file table (good — but the test for this is weak; see D2).

Companion: [`tasks-review-061.md`](tasks-review-061.md).

**Verdict: substantially done.** The synthesizer, symbol-table changes, and wiring are correct and the unit tests pass. The schemaless project-table-only invariant holds at runtime, and for the **real-world** `schema map` (no namespace) case — which is what `samples/v1.1-metadata/billing/map.ttr` and similar projects use — explicit and synthesized symbols correctly collide on the same qname so `duplicates()` will fire for Section E. But three real issues need fixing before E: a **red lint** (D1, must-fix), a **missing collision/schemaless test** (D2), and an **inherited qname mismatch when `map.ttr` declares a namespace** which Section E will be blind to and which the design doc §4.2 spells differently from what the code produces (D3, doc + code-comment work). D4/D5 are minor.

---

## What's good (verified)

- **`SymbolEntry` field + explicit-source tagging.** `mappingSource?: 'explicit' | 'inline'` added (symbol-table.ts:18) with a clear doc-comment; `addEntry` sets `'explicit'` on all three er2db_* kinds (lines 86–88). Matches D.1 exactly.
- **`ProjectSymbolTable` synthesis store.** `synthesizedByDocument` parallel map, `upsertSynthesizedSymbols(uri, entries)` clears prior synth for the URI (filtered by `mappingSource === 'inline'`, so it can't accidentally purge explicit entries) and pushes new ones into `byQname` only — never `byDocument`. `removeDocument` also clears synthesized entries. Matches D.2 exactly.
- **Synthesizer produces correct entries.** Probed all four forms:
  - Entity full → one `er2dbEntity` + one `er2dbAttribute` per `columns:` entry (parent qname linked).
  - Attribute bare-id (inside entity) → one `er2dbAttribute` at `<pkg>.map.er2dbAttribute.<entity>.<attr>`.
  - Relation bare-id → one `er2dbRelation` at `<pkg>.map.er2dbRelation.<rel>`.
  - Relation `{ fk: ... }` form → same (covered by walker, not by synthesizer-specific test).
- **Source locations point at the inline `mapping:` value**, not the def header (entry-level `col.source`, `attr.mapping.source`, `rel.mapping.source`, `block.source`). The C-test line-number assertion passes (line 5 = the line containing `mapping: IDX`).
- **Package name** uses `ast.packageDecl?.name ?? ''` (the host file's package), per design §4.2 and spec gotcha.
- **Wiring covers both LSP load paths.** `synthesizeMappings` called immediately after `upsertDocument` in both the per-file load loop (`server.ts:291`) and the stock-load loop (`server.ts:333`). After `pnpm -r build`, the symbol is present in all three esbuild outputs.
- **Schemaless invariant holds at runtime.** A direct `DocumentSymbolTable` over an inline-mapping `er.ttr` shows only the `entity` entry — no `er2db_*` leakage. Per design §C6 / grammar-changes §4.4.
- **Top-level `def attribute X { mapping: ... }` is silently dropped** (the synthesizer's `else if (def.kind === 'attribute') {}` empty branch), as the spec explicitly allows.
- **Real-world collision detection works.** With `er.ttr` (inline mapping) + same-package `map.ttr` (`schema map`, no namespace, `def er2db_entity X`), both produce `<pkg>.map.er2dbEntity.X` and `duplicates()` returns `[{qname, entries:[inline,explicit]}]`. This is exactly what Section E needs.

---

## High

### D1 — `pnpm --filter @modeler/semantics lint` is RED (4 unused-import errors)

```
packages/semantics/src/mapping-synthesizer.ts
  4:3  error  'AttributeDef' is defined but never used        @typescript-eslint/no-unused-vars
  6:3  error  'MappingProperty' is defined but never used     @typescript-eslint/no-unused-vars
  7:3  error  'MappingColumnEntry' is defined but never used  @typescript-eslint/no-unused-vars
  8:3  error  'SourceLocation' is defined but never used      @typescript-eslint/no-unused-vars
```

The spec's Section D code block imported these as scaffolding; the developer didn't trim them after the implementation didn't end up referencing them directly (types come through `entity.attributes` / `entity.mapping` / etc. via declared inference). The Section D verification list runs `typecheck`, not `lint`, so this slipped through — but Section F's gate and any CI that runs `pnpm -r lint` will fail. 30-second fix: remove the four names from the type-import list.

---

## Medium

### D2 — Missing collision test; "schemaless" test is too weak

The whole reason `mappingSource: 'inline' | 'explicit'` exists is so Section E can detect duplicates. The mapping-synthesizer test never exercises a collision (an `er.ttr` with `mapping:` + same-package `map.ttr` with explicit `def er2db_entity X`). I probed this scenario by hand and confirmed it works — but the dev has no automated guard. A future refactor (e.g. changing how `mappingSource` is set or how synth purges) could silently break collision detection and only Section E's tests would surface it.

Separately, the "schemaless" test (D.0 last case) only asserts the synthesized symbol is present in `symbols.get(qname)`. It does **not** assert that it's absent from the host file's `DocumentSymbolTable`. The spec's draft acknowledged this hole — `// (assert via a helper that returns documentSymbols(uri) — if present.)` — and the developer dropped both the comment and the missing assertion. Implementation honors the invariant (I verified by constructing a `DocumentSymbolTable` directly), but the test doesn't guard it.

Add two tests:

- **Collision smoke test** — load `er.ttr` (inline mapping) and `map.ttr` (`schema map`, no namespace, matching explicit `def er2db_entity`) in the same project; assert `symbols.duplicates()` includes the colliding qname with both `mappingSource` values in the `entries`. Use the `schema map` (no-namespace) form so qnames actually match (see D3 for why namespaced map.ttr files don't collide).
- **Schemaless absence test** — for the inline-mapping `er.ttr`, construct a `DocumentSymbolTable` directly (the same pattern my probe used) and assert `table.all().filter(e => String(e.kind).startsWith('er2db'))` is empty.

### D3 — Synthesized qname doesn't match explicit qname when `map.ttr` uses `schema map namespace <X>`

Probed:

| `map.ttr` declaration                   | Explicit qname                            | Synthesized qname                         | `duplicates()` |
| --------------------------------------- | ----------------------------------------- | ----------------------------------------- | -------------- |
| `schema map` (no namespace)             | `pkg.map.er2dbEntity.artikl` (`def.kind`) | `pkg.map.er2dbEntity.artikl`              | ✅ detected     |
| `schema map namespace er2db`            | `pkg.map.er2db.artikl` (namespace)        | `pkg.map.er2dbEntity.artikl`              | ❌ silently missed |

Root cause is **pre-existing**: `DocumentSymbolTable.makeQname` (symbol-table.ts:43–55) uses `namespace || def.kind` as the qname segment between schema and name. So if a project's `map.ttr` declares a namespace, explicit and synthesized entries live at different qnames and Section E will be blind to the conflict; `all()` will also silently dedupe them so they coexist as if they were unrelated symbols.

This is the implicit qname-convention assumption the synthesizer makes — and it's the right one for `samples/v1.1-metadata/billing/map.ttr` and the production convention shown in the actual fixtures (no namespace on `map`-schema files). But two related items need attention:

- **The design doc is wrong.** `v2.1-inline-mappings.md` §4.2 specifies the synthesized qname as `billing.products.map.er2db_entity.artikl` (**underscored**). Neither the synthesizer nor `addEntry` produces underscored qnames — both produce camelCase (`er2dbEntity`). The doc has been wrong since v2.0 (the addEntry behavior is older); D inherited the discrepancy. Update §4.2 to match what the code produces.
- **The synthesizer's implicit convention should be documented in code.** Add a comment at `synthQname` saying "this assumes explicit `def er2db_*` declarations live in a `schema map` file with **no** namespace; if a project's `map.ttr` declares a namespace, explicit and synthesized qnames will diverge and the duplicate-mapping validator will be blind to collisions there." Optionally surface this in the design doc as a known limitation, deferred to whoever revisits qname conventions.

---

## Low

- **D4 — Bundle staleness.** Section D's commit didn't run `pnpm -r build`, so the LSP esbuild bundles (`server-stdio.js`, `server-browser.js`) didn't include `synthesizeMappings` until I rebuilt. Operational, not a code defect — but worth adding `pnpm -r build` to Section H's final gate (it's not currently in D's verification list either).
- **D5 — Cosmetic cleanup.**
  - `packages/semantics/src/mapping-synthesizer.ts` and `packages/semantics/src/index.ts` end with no trailing newline (the diff shows `\ No newline at end of file` for both).
  - `mapping-synthesizer.ts` keeps an empty `} else if (def.kind === 'attribute') {}` branch but the spec's explanatory comment ("Top-level attribute defs … silent skip is fine") was dropped. Restore the comment or remove the branch.
  - The Section D task file's `D.0` status annotation cites commit `05b0748` (a typo of review-058's hash); the actual D commit is `6c0903a`. Fix the hash.
- **D6 — D's verification list overlaps Section F.** Items 3–6 of the spec's Verification section (round-trip via the LSP test harness, no-leakage assertion, `workspace/symbol`, `textDocument/references`) are inherently LSP-level integration checks, which Section F owns. Not dinging D for not doing F's work; the spec should clarify or fold those bullets into F's deliverable list.

---

## Recommendation

D is one trivial fix (D1) and two test additions (D2) away from a clean, fully-guarded synthesizer; D3 is a doc/code-comment reconciliation. **Do D1 first** — it's an immediate red gate. Then D2 to guard the two invariants the spec already cares about. D3 needs ~10 lines of doc updates and an in-code comment. D4/D5 are cleanup. Once D1–D3 land, Section E can build its validator on a green, well-tested foundation. `tasks-review-061.md` has the concrete, ordered steps.

---

## Resolution (2026-05-28, commit `5f2324b`)

**Verdict now: D itself is DONE; one carry-over from Section C surfaces (D7).** Re-verified against runtime — all six D-items resolved, full workspace green except for a pre-existing Section C lint debt that this review's `pnpm -r lint` run surfaced.

- **D1 ✅** `mapping-synthesizer.ts` trimmed to import only `Document`, `EntityDef`, `RelationDef`; `pnpm --filter @modeler/semantics lint` clean. Bonus: the dev proactively also trimmed 4 corresponding unused imports in `packages/parser/src/walker.ts` from Section C — good adjacent cleanup, not asked for.
- **D2 ✅** Two tests added to `mapping-synthesizer.test.ts`: a collision test asserting `duplicates()` returns the colliding qname with both `mappingSource` values (`['explicit', 'inline']`), and a rewritten schemaless test that asserts **absence** from the host `DocumentSymbolTable` (constructed directly, exactly the pattern the spec asked for). Semantics suite is now 114 (was 113; the schemaless test was rewritten in place, the collision test is net-new).
- **D3 ✅** Both design docs (`v2.1-inline-mappings.md` §4.1/§4.2, `grammar-v2-1-changes.md` §4.2) updated from `er2db_entity`/`er2db_attribute`/`er2db_relation` (underscored) to `er2dbEntity`/`er2dbAttribute`/`er2dbRelation` (camelCase — matching what the code produces). The `Namespace assumption` paragraph in `v2.1-inline-mappings.md` §4.2 documents the `schema map`-no-namespace requirement and points at the namespaced limitation. `synthQname` carries the same note in code. The dev also tightened `source: 'inline'` → `mappingSource: 'inline'` to match the actual field name — a small useful correction.
- **D4 ✅** `pnpm -r build` is already at line 67 of `docs/v2-1/plan/tasks/section-H-wrap-up.md` (predates this review; satisfied).
- **D5 ✅ mostly** EOF newlines added on `mapping-synthesizer.ts` and `index.ts`; explanatory comment restored on the empty `attribute` branch; commit-hash typo fixed (`05b0748` → `6c0903a`). Two minor leftovers: the restored comment introduced inconsistent indentation around the `} else if` lines (cosmetic; lint doesn't enforce), and the test file `inline-mappings.test.ts` was edited but still has no trailing newline. Not worth a re-spin.
- **D6 ✅** Section D Verification list bullets 3–6 marked deferred to Section F; bullets 1–2 marked done.

### Newly surfaced — D7 (Section C carry-over, not D's fault)

`pnpm -r lint` is still red because of **6 `@typescript-eslint/no-explicit-any` errors** in `packages/parser/src/__tests__/inline-mappings.test.ts` (lines 25, 55, 70, 90, 108, 129). They were introduced in Section C (commit `df56393`) — the test file uses `result.ast!.definitions[0] as any` to access `.mapping` on a `Definition` union. Neither review-060 nor review-061 ran `pnpm -r lint` workspace-wide, so the debt sat hidden. CLAUDE.md is explicit: `ESLint forbids any outside generated/**`. The dev's review-061 commit removed 4 lint errors in walker.ts as a bonus (taking parser from 10 errors → 6), but didn't get to zero. Fix is mechanical: replace each `as any` with `as EntityDef` / `as AttributeDef` / `as RelationDef` and an enforcing `kind` narrowing assertion. Tracked as D7 in `tasks-review-061.md`.

**Gate after fix (modulo D7):** parser 122 · semantics 114 · edit 60 · migrate 23 · lsp 130 · vscode-ext 24 · designer 129 · integration 92 (+1 skip) · `pnpm -r typecheck` 8/8 · `pnpm -r build` green · LSP esbuild bundles contain `synthesizeMappings` (3 hits in each of `server.js`, `server-stdio.js`, `server-browser.js`). Section D's deliverable is complete; once D7 lands, the workspace lint gate is also green and Section E can proceed.
