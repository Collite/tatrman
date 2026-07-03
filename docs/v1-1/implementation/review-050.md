# Review 050 — Section G re-review (after `tasks-review-049`)

**Date:** 2026-05-22
**Scope:** re-review of Section 1.1.G after the developer reported the `tasks-review-049` fixes done. Verified against runtime: full integration suite (77 pass / 1 skip), parser (82), and independent resolver probes over `samples/v1.1-mini` and `samples/v1.1-metadata`. Companion: [`tasks-review-050.md`](tasks-review-050.md).
**Verdict:** **Close, but not done.** Real progress — the `4.5b` regression is fixed, the `.ttrg` graphs now open clean, the stray dir / committed artifact / nested project marker are gone, the duplicate-definition errors are resolved, and CLAUDE.md is updated. But the flagship sample **`v1.1-mini` still does not resolve: 24 `ttr/package-declaration-mismatch` errors remain**, and the new `v1.1-samples.test.ts` — titled "resolve cleanly" — only checks `getGraph`, so it stays green while the sample is broken. That's the same blind spot as last round, one layer in.

---

## Fixed and verified

- **H1 — `4.5b` regression fixed.** `lsp-phase-03-custom-methods.test.ts` no longer ingests the v1.1 sample trees; `4.5b` (and the suite) pass with the new samples present.
- **H3 — `.ttrg` graphs open clean.** `all_db.ttrg`, `all_er.ttrg`, `artikl_overview.ttrg` now return `missingObjects === []` with `nodes.length > 0` (the previously-all-missing graphs are fixed). `v1.1-samples.test.ts` asserts this and passes.
- **H2 (partial) — duplicate definitions gone.** The 4 `ttr/duplicate-definition` errors (relations defined in both `er.ttr` and `relations/*.ttr`) are resolved.
- **M1 — sample tree cleanup.** The stray `samples/v1.1-mini-migrated/` is removed, `.gitignore` now ignores `.modeler/`, and the nested `billing/invoicing/modeler.toml` is gone.
- **M2 — metadata PK issues documented.** `v1.1-metadata`'s pre-existing `primary-key-column-not-found` set is captured as `knownMissingPKs` in the test rather than silently passed.
- **M3 — CLAUDE.md updated.** The layout invariant is per-graph `.ttrg`, and a new invariant notes `.modeler/` is a build artifact (never commit).

---

## Still open

### H2 [High] — `v1.1-mini` still doesn't resolve: 24 `package-declaration-mismatch` errors

Opening every `.ttr` under `v1.1-mini` through the resolver still yields **24 error-severity** diagnostics, all the same shape:

```
ttr/package-declaration-mismatch: Declared package 'billing.invoicing'
  does not match inferred package 'billing.invoicing.entities'
  (and '…relations' for the relations/ folder)
```

The files were left in `billing/invoicing/entities/` and `billing/invoicing/relations/` subfolders but still declare `package billing.invoicing`, while the directory implies `billing.invoicing.entities` / `…relations`. DONE requires *"`samples/v1.1-mini/` … all references resolve"* and *"every cross-reference resolves"* — 24 errors means it doesn't. Two clean fixes (pick one):
- **Flatten** the files into `billing/invoicing/` (drop the `entities/`/`relations/` subdirs) so `package billing.invoicing` matches — simplest for a sample, and what the migration CLI produces.
- **Or** declare `package billing.invoicing.entities` / `…relations` in those files and add the cross-package `import`s the split then requires.

### H4 [High] — the safety-net test doesn't assert what it's named for; parser parse-clean still missing

- **`v1.1-samples.test.ts` only checks graphs.** Its `describe` says *"v1.1 samples resolve cleanly,"* but every `it` calls `getGraph` and checks `missingObjects`/`nodes`. `getGraph` builds its qname index as `schema.namespace.name` and **ignores the package**, so package-declaration-mismatch (and any other `.ttr`-level diagnostic) is invisible to it. That's precisely why H2's 24 errors pass unnoticed. The test must also open every `.ttr` and assert **zero error diagnostics** from the resolver (the `publishDiagnostics` pattern). Add that and it will (correctly) fail until H2 is fixed.
- **H4.2 not done.** There's still no `@modeler/parser` "samples parse cleanly under `samples/v1.1-*/`" fixture run (the spec's first Tests-first bullet). The parser test count is unchanged and none reference the v1.1 samples.

---

## Low

- **L1 — project-root markers are off.** `v1.1-mini` has **no** `modeler.toml` at its root (relies on the LSP `workspaceFolder` fallback), and `v1.1-metadata`'s only `modeler.toml` sits at `billing/modeler.toml` (a subdirectory), so the metadata project root is effectively `billing/`, not the sample root. For shippable samples, put exactly one `modeler.toml` at each sample's top level so package inference is unambiguous.
- **Docs (G.5–G.7) still not deep-reviewed.** `architecture.md`, `grammar-v1-1-changes.md`, and the new `progress-phase-v1.1.md` were touched; once H2/H4 land I'll review their content. The progress doc must not claim "samples resolve cleanly" while H2 stands.

---

## Recommendation

One real blocker left: make `v1.1-mini` actually resolve (H2 — flatten the dirs or fix the package decls), and close the test blind spot that's hiding it (H4 — assert zero `.ttr` resolver diagnostics, not just clean graphs; add the parser parse-clean run). Those two are tightly linked: once the test asserts resolution, it fails until the sample is fixed, which is the behavior you want. Then tidy the `modeler.toml` placement (L1) and the docs can be re-reviewed. Everything else from `tasks-review-049` is genuinely done. `tasks-review-050.md` has the steps.
