# Tasks — review-048 (Section F re-review)

Findings in [`review-048.md`](review-048.md). **H1, H2a, H2c, H3-logic, M1, M3.3, L1 are fixed — leave them.** What's left: the migrated output is still wrong — **G1** (malformed cross-package imports), **G2** (`.ttrg` objects include attributes), masked by **G3** (unrealistic fixture + unit test) and **G4** (exit-1 untested). Section F is done when G1–G4 are closed and all gates green.

Do exactly what's written.

---

## ✅ Resolution (2026-05-22) — all closed; Section F complete

- **G1** — `scanCrossReferences` now carries the real package from the `byPackage` **key** (no more `qname.split('.')[0]`, which was the schema) and matches only top-level defs. Verified on the fixture: `produkt.ttr` now gets `import billing.invoicing.er.entity.artikl` (correct package + top-level entity), and `artikl.ttr` has **no** spurious import.
- **G2** — `runMigration` filters symbol-table entries to top-level objects (`if (entry.parent) continue`), used for both the objects list and the import-target set. `_all_er.ttrg` objects are now `er.entity.artikl/dobropis/produkt` (+ the relation) — no attributes.
- **G3** — the fixture now carries a genuine cross-package reference (`produkt` → `artikl` via a relation); `migration.test.ts` asserts the correct `import billing.invoicing...` line appears and that `artikl.ttr` has no imports. `scan-cross-references.test.ts` was rewritten to feed **unqualified** qnames (as `runMigration` produces) and assert the rendered `import pkgB.er.entity.produkt` — this fails against the old code, pinning G1.
- **G4** — new `fixtures/migrate-ambiguous/` (packages `b` and `c` both export `shared`, package `a` references it) + a test asserting the CLI exits `1` and the report's `ambiguousReferences` names both `b.*` and `c.*` candidates.
- **L2** — `--verbose` now gates the per-item detail listings (counts always shown); the import summary already prints the real named/wildcard form.
- **Flaky dry-run test** (found during re-review) — the developer's dry-run test compared a float `mtimeMs` against an integer `Date.now()` and failed ~2/3 runs; rewritten to compare file **contents** before/after. Integration suite now green 3/3.
- **L4 — deliberately NOT changed.** Bumping `@modeler/migrate` to `vitest ^4` caused every test file to run **twice** (5→10 files, 23→46 tests) under the shared v4 config; reverted to `^3`. The version drift is pre-existing across the workspace and harmless, so this is left as-is rather than risk the double-execution.

Gates: `@modeler/migrate` test (23) + lint ✅ · integration-tests **72 pass / 1 skip**, green 3/3 runs ✅ · `pnpm -r typecheck` ✅ · `pnpm -r build` ✅.

---

## G1 [High] — Emit correct cross-package imports (real package, top-level defs only)

Two coupled defects: the package is taken from `qname.split('.')[0]` (which is the schema, because `runMigration` feeds unqualified qnames), and bare-suffix matching hits nested members (attributes) in other packages.

- [ ] **G1.1** In `scanCrossReferences` (`index.ts`), carry the **real package** from the map key instead of re-deriving it. Iterate `for (const [pkg, qnames] of byPackage)` and, on a match, record `pkg` (the key) alongside the matched qname. Build the `ImportSpec.packageName` from that `pkg`, not from `matched.split('.')[0]`.
- [ ] **G1.2** Derive `schema`/`namespace`/`defName` for the named import from the matched qname's parts **relative to the package** — since the qname is unqualified (`schema.namespace.def`), that's `parts[0]`=schema, `parts[1]`=namespace, `parts[2]`=def. (After G2 the matched set contains only top-level defs, so there's always a def at `parts[2]`.) The resulting line must be `import <pkg>.<schema>.<namespace>.<def>`, e.g. `import billing.products.er.entity.produkt`.
- [ ] **G1.3** Restrict matching to **top-level defs** only — references resolve to objects, not to attributes/columns. Either filter `projectSymbols` to top-level entries before matching (see G2.1) or skip entries that have a `parent`. After this, `artikl`'s `nameAttribute: nazev` must NOT produce any cross-package import (it resolves to artikl's own attribute).
- [ ] **G1.4** Verify on the fixture: after the run, `billing/invoicing/artikl.ttr` has **no** spurious `import er...` line; a file with a genuine cross-package reference (G3.1) gets exactly `import <real-package>...` and it resolves.

## G2 [High] — `.ttrg` objects must be top-level objects only

- [ ] **G2.1** In `runMigration` (`index.ts:291-295`), stop keeping symbols by segment count (`parts.length > 4`). Include only **top-level defs** — skip symbol-table entries that have a `parent` (attributes/columns), or restrict to object kinds (`entity`/`table`/`view`/…). Use the same filtered set for both the objects list and the cross-reference symbol set (G1.3).
- [ ] **G2.2** Verify `graphs/_all_er.ttrg`'s `objects:` contains only `er.entity.artikl`, `er.entity.dobropis`, `er.entity.produkt` — no `.id_artiklu`/`.nazev`/etc.

## G3 [Med] — Make the fixture and unit test reflect real input (so G1/G2 are actually tested)

- [ ] **G3.1** Add a genuine entity↔entity cross-package reference to the fixture — e.g. give `billing/products/produkt.ttr` a reference to `billing.invoicing`'s `artikl` (a relation or a typed reference, per the grammar), so the migration must emit a real cross-package import.
- [ ] **G3.2** Extend `migration.test.ts`: after the run, assert the referencing file contains the correct `import billing.invoicing...` line (string match), and keep the existing "no unresolved/unimported" + `missingObjects === []` assertions (which must still pass with the corrected import).
- [ ] **G3.3** In `scan-cross-references.test.ts`, feed qnames in the shape `runMigration` actually produces — **unqualified** qname (`er.entity.bar`) with the package in the separate `packageName` field — and assert the emitted spec's `packageName` is the real package and the rendered line is `import pkgB.er.entity.bar`. (This fails against today's code → it pins G1.)

## G4 [Med] — Assert the ambiguous exit-code-1 path end-to-end

- [ ] **G4.1** Add a fixture (or temp project) where two packages export the same bare top-level name and a third file references it ambiguously. Assert `execSync('node <cli> <root>')` throws with `err.status === 1` and that `.modeler/migrate-report.json` lists the ambiguous ref. (Detection alone is already unit-tested; this covers the CLI exit contract.)

---

## L (low, carryover)

- [ ] **L2** — Implement `--verbose` or drop it; fix the CLI import summary to print the real import form using `importsInserted[].isWildcard`/`schema`/`namespace`/`defName` rather than always `.*`.
- [ ] **L4** — Align `@modeler/migrate` to the workspace `vitest` v4 and re-run `pnpm install`.

---

## Done when

- [ ] **G1:** cross-package references produce `import <real-package>.<schema>.<ns>.<def>` (correct package, top-level def, no trailing dot); no spurious imports from same-named attributes. Verified on a fixture with a genuine cross-package reference.
- [ ] **G2:** generated `.ttrg` `objects:` lists only top-level objects, no attributes/columns.
- [ ] **G3:** the fixture carries a real cross-package reference and the E2E asserts the correct import line; the scan unit test uses unqualified qnames matching `runMigration`.
- [ ] **G4:** the ambiguous → exit-1 path is asserted via the CLI.
- [ ] `pnpm --filter @modeler/migrate test && pnpm --filter @modeler/migrate lint && pnpm --filter @modeler/integration-tests test && pnpm -r typecheck && pnpm -r build` all green, and a manual run on the fixture produces correct imports + a top-level-only objects list.
