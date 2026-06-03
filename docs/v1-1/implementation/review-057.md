# Review 057 — Section I1 (rename: cross-package + `.ttrg` + rename-package)

**Date:** 2026-05-25
**Scope:** review of Section 1.1.I.1 against [`I1-rename.md`](../plan/tasks/I1-rename.md), and **creation of the missing integration test** the developer was blocked on. Verified against runtime:

- `pnpm --filter @modeler/edit test` → 58 passed, **2 skipped**.
- Built `@modeler/edit` + `@modeler/lsp`; **created `tests/integration/src/rename.test.ts`** and ran it → **3 / 3 failing** (the failures are the findings below).

Companion: [`tasks-review-057.md`](tasks-review-057.md).

**Verdict:** **Not done.** The pieces exist (`rename-symbol.ts`, `rename-package.ts`, `onPrepareRename`/`onRenameRequest`, the `renameProvider` capability), and the edit-level unit tests pass — but end-to-end through the LSP, **rename does not work at all** for a packaged project. The integration test I added fails on every case: `prepareRename` returns `null` for an entity, renaming an entity actually renames the *package*, and rename-package corrupts the file by dropping the `package` keyword. Three High bugs, all verified at runtime. (This is almost certainly why the developer was "stuck on the integration tests": a correct integration test can't pass against this code.)

The new `tests/integration/src/rename.test.ts` is committed as the spec of correct behaviour; it stays red until the bugs below are fixed.

---

## High — blockers (all reproduced by the new integration test)

### G1 [High] — `qnameOf` is not package-aware → `prepareRename` returns null; symbol-rename can't resolve
`server.ts:165` `qnameOf` builds `[schemaCode, namespace, def.name]` (e.g. `er.entity.produkt`) — **no package prefix**. But symbol-table keys are package-qualified (`billing.products.er.entity.produkt`, since B2). So `projectSymbols.get(qnameOf(...))` misses, and both rename handlers bail:
- `onPrepareRename` returns `null` (test: `prepareRename returned null for the entity name`).
- `onRenameRequest`'s symbol path would hit `projectSymbols.get(targetQname)` → `undefined` → empty edit (masked today by G2).

Fix: prepend the package, e.g. `[ast.packageDecl?.name, schemaCode, namespace, enclosing?.name, def.name].filter(Boolean).join('.')`.

### G2 [High] — Rename dispatch ignores the cursor: any packaged file is treated as a *package* rename
`server.ts:725` does `if (ast.packageDecl) { … return buildRenamePackageEdit(…) }` — it only checks that the file *has* a `package` declaration and that the name appears on its line; it **never checks `params.position`**. So F2 on an entity in a packaged file renames the package, not the entity. Runtime proof — renaming entity `produkt` (newName `produkt_v2`) produced:

```
produkt_v2

schema er namespace entity

def entity produkt {        ← entity NOT renamed
  description: "Produkt"
}
```

i.e. the `package billing.products` line was rewritten (to `produkt_v2`) and the entity left untouched. Fix: only take the package branch when the cursor is on the `package` declaration line (`params.position.line === ast.packageDecl.source.line - 1` and within the name span); otherwise fall through to symbol rename.

### G3 [High] — `buildRenamePackageEdit` drops the `package` keyword (corrupts the file)
`rename-package.ts:104-106` builds the package-decl edit over the range `[start of "package", end of name]` but replaces it with just `newPackageName`. So `package billing.products` → `billing.products_v2` — the keyword is gone and the file no longer parses. Runtime proof (rename-package case):

```
expected 'billing.products_v2\n\nschema …' to contain 'package billing.products_v2'
```

The existing unit test only asserts `toContain('billing.invoicing_v2')`, which is true even with the keyword dropped — too weak to catch it. Fix: edit only the name span (start at `idx + before.length + 'package '.length`), keeping the keyword. Add an assertion that the result still starts with `package `.

---

## Medium

### G4 [Med] — The idempotent test is skipped (required Tests-first case)
`rename-symbol.test.ts:226` `it.skip('idempotent: applying rename twice produces empty second edit', …)`. I1's Tests-first lists this case explicitly; skipping it means the "second `WorkspaceEdit` is empty" guarantee is unverified. `buildRenameSymbolEdit` has an early-return for `currentText === newBareName || currentText === newQname`, so it likely works — un-skip it and make it green (or fix what made it fail).

### G5 [Med] — Validation returns an empty edit instead of an LSP error (I1.5 deviation)
`server.ts:764-769`: an illegal new name or a colliding name returns `{ documentChanges: [] }` — a silent no-op. I1.5 / I1.4 require a `ResponseError` (`InvalidParams`) with a message so VS Code shows a refusal dialog; `onPrepareRename` should also reject unsupported positions with `null` (it does for unresolved refs, but never validates the *target* name since that's only known at rename time). Surface the error instead of swallowing it.

---

## Low

- **G6** — **I1.6 deviation:** the reference index was *not* extended to track `.ttrg` mentions (nothing in `reference-index.ts`). Instead `rename-symbol.ts` scans `.ttrg` `objects` directly (`:135-164`). That achieves the goal (once G1/G2 unblock the symbol path), but it diverges from the planned design — note it, or fold `.ttrg` scanning into the index as specified so other features can reuse it.
- **G7** — `rename-symbol.ts:151` the child-qname branch (`partQname.startsWith(oldQname + '.')`) computes the wrong replacement prefix (`segs.slice(0, len - oldQname.split('.').length)`), so a `.ttrg` object that is a *child* of the renamed symbol would be mangled. `.ttrg` objects are normally top-level defs, so it's latent — but fix or guard it.

---

## What's good

- `onPrepareRename`/`onRenameRequest` are registered and the `renameProvider: { prepareProvider: true }` capability is set.
- `buildRenameSymbolEdit` structurally does the right things — def site, references (with bare-vs-qualified handling), and a `.ttrg` `objects` rewrite — and the edit-level unit tests pass. The logic is sound; it's simply unreachable end-to-end because of G1/G2.
- `buildRenamePackageEdit` handles `package` decls, named + wildcard imports, and `.ttrg` prefixes — modulo the keyword bug (G3).

---

## Recommendation

The implementation is closer than the integration result suggests — the edit builders are mostly correct. Fix the three High bugs and it should come together: (G1) make `qnameOf` package-aware; (G2) gate the package-rename branch on the cursor being on the `package` declaration; (G3) keep the `package` keyword in the package-decl edit. Then un-skip the idempotent test (G4) and return a real error on invalid/colliding names (G5). The new `tests/integration/src/rename.test.ts` (3 cases) is the acceptance bar — it must go green. `tasks-review-057.md` has exact steps.

---

## Resolution (2026-05-25)

Fixed directly. **I1 now works end-to-end.** All review-057 items addressed, plus three further bugs the new integration test surfaced once the dispatch/qname blockers were cleared:

- **G1** ✅ `qnameOf` (server.ts) prepends the package; non-package files unchanged.
- **G2** ✅ Package-rename branch gated on `params.position.line === ast.packageDecl.source.line - 1`; otherwise falls through to symbol rename.
- **G3** ✅ `buildRenamePackageEdit` edits only the name span (keeps `package `); unit test strengthened.
- **G4** ✅ Idempotent test un-skipped — its fixture had a wrong line (`4`→`5`, 1-indexed) and column (`12`→`11`); now green.
- **G5** ✅ Invalid/colliding renames throw `ResponseError(InvalidParams)`; added a collision-rejection integration case.
- **G7** ✅ Child-qname `.ttrg` rewrite fixed (keeps the suffix).
- **Extra-1** ✅ Def-site edit targeted the *whole-definition* span (symbol-table stores `def.source`). Added `defNameSource` to compute the name span; also reads the def's *own* document (fixes rename-from-a-reference-in-another-file).
- **Extra-2** ✅ A fully-qualified cross-package reference was collapsed to the bare name (dangling). Now qualified→newQname, bare→newBareName, partial→suffix-swap.
- **Extra-3** ✅ Named `import <qname>` statements of the renamed symbol now follow it (wildcard imports untouched) — the common import+bare-usage pattern no longer dangles.
- **G6** — left as a noted deviation: `.ttrg` propagation lives in `rename-symbol.ts`, not the reference index. Acceptable; flagged for a future unify.
- Also cleared 3 pre-existing `edit` lint errors (unused `ReferenceLocation`, `SymbolEntry`, `positionToOffset`).

**`tests/integration/src/rename.test.ts` (4 cases) is green**: prepareRename; entity rename propagating to def + cross-package qualified ref + `.ttrg` + named import + bare usage (package untouched, all files re-parse clean); collision rejection; package rename (keyword kept, parses).

Gate: edit **60** · lsp **91** · integration **92 (+1 pre-existing skip)** · vscode-ext **24** · `pnpm -r typecheck` 8/8 · edit + lsp lint clean.
