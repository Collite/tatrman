# Review 048 — Section F re-review (after `tasks-review-047`)

**Date:** 2026-05-22
**Scope:** re-review of Section 1.1.F after the developer reported the `tasks-review-047` fixes done. Verified against runtime: `pnpm --filter @modeler/migrate test` (23 pass) + `lint` (clean), `integration-tests` (70/1 skip), `pnpm -r typecheck`/`build` (clean), plus two probes — one feeding `scanCrossReferences` the qnames `runMigration` actually produces, and one running the CLI on the fixture and inspecting the migrated files. Companion: [`tasks-review-048.md`](tasks-review-048.md).
**Verdict:** **Not done — real fixes landed, but the migrated output is still wrong.** H1 (`.ttrg` parses), the `.ttrl` `nodes` key, the LSP-backed E2E assertions, lint, the parse-the-output test, and dry-run-writes-nothing are all genuinely fixed and verified. But running the CLI on the fixture produces (a) a **malformed/incorrect cross-package import** (`import er.entity.produkt.nazev`) and (b) a `.ttrg` **objects list polluted with attributes**. Both are masked because the unit test feeds package-qualified qnames `runMigration` never produces, and the fixture has no genuine entity↔entity cross-package reference.

> 23 + 70 green, but the actual migrated `artikl.ttr` and `_all_er.ttrg` (pasted below) are invalid — the assertions don't look at the import lines or the objects content.

---

## Fixed and verified

- **H1 — generated `.ttrg` parses.** `convertTtrlToTtrg` now uses the exported `serializeLayoutBlock` from `@modeler/edit` (unquoted keys). The E2E parses every generated `.ttrg` with zero errors, and `ttrl-to-ttrg.test.ts` now parses its own output (M3.3). 
- **H2a — `.ttrl` `nodes` key.** Reads `layout.nodes` (was `nodePositions`); positions survive into the `.ttrg` layout block (verified in the output below).
- **H2c — real LSP-backed E2E.** `migration.test.ts` now boots `createServerConnection`, opens the migrated tree, and asserts `getGraph(_all_er.ttrg).missingObjects === []` and no `ttr/unresolved-reference`/`ttr/unimported-reference`. Good — this is the right harness.
- **H3 (threshold logic) — correct in isolation.** `scanCrossReferences` takes `wildcardThreshold`, counts distinct referenced defs per package, and flips named↔wildcard at the boundary; `scan-cross-references.test.ts` covers it (incl. ambiguity). The `--wildcard-threshold` flag is now consumed.
- **M1 — lint clean.** **M3.3 — output parsed in the unit test.** **L1 — `--dry-run` writes nothing** (no report file; asserted in the E2E).

---

## High — still broken

### G1 [High] — Cross-package imports are malformed (wrong package, and they import attributes)

Running the CLI on the fixture (`/tmp` copy) produces this `billing/invoicing/artikl.ttr`:

```
package billing.invoicing
import er.entity.produkt.nazev      ← WRONG
schema er namespace entity

def entity artikl {
  ...
  nameAttribute: nazev,
  attributes: [ ... def attribute nazev { type: text } ]
}
```

Two defects in that one import:
1. **Package is the schema, not the package.** `scanCrossReferences` derives the import package via `qname.split('.')[0]` (`index.ts:121, 143`), but `runMigration` feeds qnames built by `DocumentSymbolTable` **without a package prefix** — e.g. `er.entity.produkt.nazev`. So `parts[0]` is `er` (the schema). Probe confirms: feeding the real (unqualified) qnames, the generated import line is `import er.entity.produkt.nazev` (and for an entity ref, `import er.entity.produkt.` with an empty `defName` + trailing dot). The package association *is* available — `byPackage` is keyed by `entry.packageName` — but the matching loop discards the key (`for (const [, qnames] of byPackage)`) and re-derives from the qname string. Use the `byPackage` key.
2. **It matched an attribute in another package.** `artikl`'s `nameAttribute: nazev` resolves to its *own* `nazev`, but the bare-suffix match (`qname.endsWith('.' + refStr)`) also hit `er.entity.produkt.nazev` (produkt's attribute, different package) and emitted a spurious cross-package import. References should resolve to top-level defs, not match arbitrary nested members across packages.

So a real v1 project with same-named attributes across packages — or any genuine cross-package reference — migrates to **wrong/invalid imports**. The E2E's "no unresolved/unimported errors" check passes anyway because the bogus import doesn't *remove* a resolution (artikl's `nazev` still resolves locally) and importing a non-existent package `er` isn't surfaced as one of the two filtered error codes.

### G2 [High] — Generated `.ttrg` objects list includes attributes/columns, not just top-level objects

The migrated `graphs/_all_er.ttrg`:

```
graph _all_er {
  schema: er,
  objects: [
    er.entity.artikl,
    er.entity.artikl.id_artiklu,    ← attribute
    er.entity.artikl.nazev,         ← attribute
    er.entity.dobropis,
    er.entity.dobropis.id_dobropis, ← attribute
    er.entity.dobropis.cislo,       ← attribute
    er.entity.produkt,
    er.entity.produkt.id_produktu,  ← attribute
    er.entity.produkt.nazev,        ← attribute
    er.entity.produkt.artikl_ref    ← attribute
  ],
  layout: { viewport: { zoom: 1.5, ... } nodes: { er.entity.artikl: { x: 320, y: 180 } ... } }
}
```

F.6 says each `.ttrg` "lists every **object** of that schema." Objects are top-level defs (entities/tables/views) — **not** their attributes/columns. The bug: `runMigration` keeps any symbol with `parts.length <= 4` (`index.ts:293`), but an attribute qname (`er.entity.artikl.nazev`) is exactly 4 parts, so children leak in. The Designer would try to render each attribute as its own graph node. Filter by the symbol's top-level-ness (e.g. skip entries with a `parent`, or restrict to object kinds), not by segment count. (`missingObjects === []` still passes only because `getGraph`'s `buildQnameToDef` happens to register nested members too — it's masking, not validating.)

---

## Medium

### G3 [Med] — The fixture and the scan unit test don't reflect what `runMigration` feeds, so G1/G2 are invisible to the suite

- The fixture (`tests/integration/fixtures/migrate-v1/`) has **no genuine entity↔entity cross-package reference** — `produkt.artikl_ref` is a plain `int` field, not a reference. The only cross-package "match" is the accidental same-named `nazev` attribute, which is itself the bug. Add a real reference (e.g. `produkt` has `nameAttribute` or a relation pointing at `billing.invoicing`'s `artikl`) so the E2E asserts a *correct* `import billing.invoicing...` line appears and resolves.
- `scan-cross-references.test.ts` feeds **package-qualified** qnames (`pkgB.er.entity.bar`), so `split('.')[0]` = `pkgB` works — but `runMigration` feeds **unqualified** qnames (`er.entity.bar`). The unit test passes while production is broken. Feed the test the same shape `runMigration` produces (unqualified qname + separate `packageName`), and assert the emitted import line equals `import pkgB...` (this fails today → pins G1).

### G4 [Med] — Ambiguous exit-code-1 not asserted end-to-end

`scan-cross-references.test.ts` checks that an ambiguous ref is *recorded*, but nothing asserts the **CLI process exits `1`** (DONE: "Ambiguous-reference exit-code-1 path tested"). Add a fixture with two packages exporting the same bare name and assert `execSync(...)` throws with `status === 1`.

---

## Low (carryover)

- **L2** — `--verbose` is still parsed but unused; the CLI import summary line should use the now-available `isWildcard`/`schema`/`defName` fields rather than always printing `.*`.
- **L4** — `@modeler/migrate` still pins `vitest ^3` (3.2.4) while the workspace is on v4. Align.

---

## Recommendation

Good, real progress: the `.ttrg` now parses, the `.ttrl` key is right, and the E2E is a proper LSP round-trip. The remaining blockers are both in the reference→import mapping and the objects list, and they're the same root issue surfacing twice: `scanCrossReferences`/`runMigration` work on **unqualified** qnames but treat `parts[0]` as the package and keep 4-part child qnames. Fix: (1) carry the real package from the `byPackage` key into the `ImportSpec`, and resolve references to top-level defs only (G1); (2) exclude child symbols from both the objects list and the symbol set (G2); (3) make the fixture carry a genuine cross-package reference and the scan test use unqualified qnames so both bugs are actually exercised (G3); (4) assert the exit-1 path (G4). `tasks-review-048.md` has the steps.
