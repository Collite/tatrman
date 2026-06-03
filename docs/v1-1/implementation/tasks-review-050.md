# Tasks — review-050 (Section G re-review)

Findings in [`review-050.md`](review-050.md). **H1, H3, M1, M2, M3 and the duplicate-definition half of H2 are fixed — leave them.** What's left: `v1.1-mini` still has 24 `package-declaration-mismatch` errors (**H2**), and the safety-net test doesn't catch them (**H4**). Section G is done when H2 + H4 are closed and all gates are green.

Do exactly what's written. **Verify with the resolver (open `.ttr` files, read `publishDiagnostics`), not just `getGraph`** — `getGraph` ignores packages and will not surface these errors.

---

## ✅ Resolution (2026-05-22) — all closed; Section G complete

- **H2** — `v1.1-mini` flattened: every entity/relation file moved out of the `entities/`/`relations/` subdirs into its package leaf dir (`billing/invoicing/`, `billing/products/`), so each declared `package` matches its directory. No content edits were needed (cross-package refs are fully-qualified and resolve without per-file imports). Verified: opening every `.ttr` through the resolver yields **0** errors (was 24 `package-declaration-mismatch`); graphs still open with `missingObjects === []`.
- **H4.1** — `tests/integration/src/v1.1-samples.test.ts` rewritten: each sample is loaded on its **own** connection initialized with `rootUri = <sampleDir>` (so package inference is relative to the sample root), every `.ttr` is opened and error-severity diagnostics collected. Asserts `v1.1-mini` → **zero** errors; `v1.1-metadata` → only the documented pre-existing `ttr/primary-key-column-not-found`. Graph assertions retained. **Teeth-checked**: moving one file back into a subdir makes the test fail with `package-declaration-mismatch`.
- **H4.2** — new `packages/parser/src/__tests__/samples-v1.1-parse.test.ts`: parses every file under `samples/v1.1-*/` and asserts 0 parse errors (+27 tests).
- **L1** — added a root `modeler.toml` to `samples/v1.1-mini/`; moved `samples/v1.1-metadata/billing/modeler.toml` up to the sample root, so `modeler.toml` walk-up and the workspace root agree.
- **D.1 (docs)** — `progress-phase-v1.1.md`: replaced placeholder/stale test totals (`N passed`, `70 passed`) with real numbers, and corrected the G.1 structure note (flat package layout; relations in their own files; `er.ttr` holds the imports; root `modeler.toml`).

Gates: `pnpm -r test` all green — parser 109, semantics 107, edit 42, lsp 53, migrate 23, vscode-ext 24, designer 128, integration 79/1-skip — and `pnpm -r typecheck` / `lint` / `build` clean.

---

## H4 [High] — Make the test assert `.ttr` resolution (do this first; it will then fail until H2 is fixed)

- [ ] **H4.1** In `tests/integration/src/v1.1-samples.test.ts`, add a per-sample test that opens **every `.ttr`** under the sample via `textDocument/didOpen`, collects `publishDiagnostics`, and asserts **zero error-severity diagnostics** (allow the documented `knownMissingPKs`/`primary-key-column-not-found` for `v1.1-metadata` only, since that's pre-existing and explicitly documented). For `v1.1-mini`, the allowance list must be **empty** — it should resolve with no errors at all. This assertion fails today (24 `package-declaration-mismatch`) — that's the point.
- [ ] **H4.2** Add the parser-level "samples parse cleanly" run (spec's first Tests-first bullet): a test in `@modeler/parser` that parses every file under `samples/v1.1-*/` and asserts 0 error diagnostics. (Parsing only — resolution stays in H4.1's LSP test.)
- [ ] **H4.3** Both green only after H2.

## H2 [High] — Make `v1.1-mini` resolve cleanly (eliminate the 24 `package-declaration-mismatch`)

Pick **one** layout and make every file's declared `package` equal `inferPackage(file, root)`.

- [ ] **H2.A (recommended) — flatten.** Move the files out of `billing/invoicing/entities/` and `billing/invoicing/relations/` up into `billing/invoicing/`, and out of `billing/products/entities/` into `billing/products/`. Each file then declares `package billing.invoicing` / `package billing.products`, matching its directory. (This is what `modeler-migrate` produces; regenerating from a clean v1 tree is preferable to hand-editing.)
- [ ] **H2.B (alternative) — keep subfolders, fix decls.** If you want `entities/`/`relations/` subdirs, change those files to declare `package billing.invoicing.entities` / `billing.invoicing.relations`, and add the cross-package `import`s the split now requires (er.ttr → entities, relations → entities, etc.). More moving parts; only do this if the nesting is intentional.
- [ ] **H2.1** After the fix, opening every `.ttr` under `v1.1-mini` through the resolver yields **0** errors (verified by H4.1).
- [ ] **H2.2** Re-confirm the graphs still pass: `all_db.ttrg`, `all_er.ttrg`, `artikl_overview.ttrg` → `missingObjects === []`, `nodes.length > 0` (regenerate the graphs if the flatten changed any qnames).

## L1 [Low] — Fix project-root markers

- [ ] **L1.1** Add a single `modeler.toml` at `samples/v1.1-mini/` root (it currently has none). Move `samples/v1.1-metadata/billing/modeler.toml` up to `samples/v1.1-metadata/` so the sample root — not `billing/` — is the project root. Re-verify resolution after moving (package inference is relative to the `modeler.toml` location).

## Docs — re-review after H2/H4 (deferred from review-049)

- [ ] **D.1** Once the sample resolves, re-review G.5 (`grammar-v1-1-changes.md`), G.6 (`architecture.md`), G.7 (`progress-phase-v1.1.md`). The progress doc's "samples resolve cleanly" / test-total claims must match the fixed state.

---

## Done when

- [ ] Opening every `.ttr` in `samples/v1.1-mini` through the resolver yields **0** errors (no `package-declaration-mismatch`); `v1.1-metadata` yields only the documented pre-existing PK diagnostics.
- [ ] `v1.1-samples.test.ts` asserts `.ttr` resolution (zero errors), not just clean graphs; the parser samples-parse-clean run exists. Both pass.
- [ ] Graphs still open with `missingObjects === []`.
- [ ] Each sample has exactly one root-level `modeler.toml`.
- [ ] `pnpm -r build && pnpm -r test && pnpm -r typecheck && pnpm -r lint` all green; docs re-reviewed.
