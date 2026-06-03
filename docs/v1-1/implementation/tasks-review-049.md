# Tasks — review-049 (Section G: migrate samples + docs)

Findings in [`review-049.md`](review-049.md). The reported "one harmless failing test" is a real regression **and** the smaller problem. Section G is done when H1–H4 are closed (samples resolve, graphs clean, both required tests pass green) and M1–M3 are addressed.

Do exactly what's written. **Verify with the resolver, not just the parser** — every issue here parses fine but fails resolution.

---

## H2 [High] — Make `samples/v1.1-mini` resolve cleanly (do this first; H3/H4 depend on it)

Decide one canonical layout and make every file's declared `package` equal its directory-inferred package, with no duplicate defs.

- [ ] **H2.1** Pick the directory layout. Recommended: keep it simple — one package per leaf directory, files directly in the package dir (as the migration CLI produces), e.g. `billing/invoicing/*.ttr` all declaring `package billing.invoicing`. If you keep `entities/` and `relations/` subfolders, then the files in them MUST declare `package billing.invoicing.entities` / `billing.invoicing.relations` (and cross-folder references then need imports). Simpler is better for a sample.
- [ ] **H2.2** Eliminate the **24 `ttr/package-declaration-mismatch`** errors: every `.ttr`'s `package` line must match `inferPackage(file, root)` for the chosen layout.
- [ ] **H2.3** Eliminate the **4 `ttr/duplicate-definition`** errors: the relations are defined in BOTH `billing/invoicing/er.ttr` and `billing/invoicing/relations/*.ttr`. Keep exactly one definition of each (either inline in `er.ttr` or split into `relations/`, not both).
- [ ] **H2.4** Add cross-package `import`s wherever the chosen layout creates real cross-package references (e.g. `billing.products` ↔ `billing.invoicing`). Re-running `modeler-migrate` on a correctly-laid-out v1 tree is the intended way to produce this — prefer regenerating over hand-editing.
- [ ] **H2.5** Verify: opening every `.ttr` under `v1.1-mini` through the LSP yields **zero** error diagnostics.

## H3 [High] — Repair the generated `.ttrg` graphs so `missingObjects === []`

After H2, the objects listed in each graph must match real qnames in the sample.

- [ ] **H3.1** `graphs/all_db.ttrg` (currently nodes=0, all 11 objects missing), `graphs/all_er.ttrg` (7 missing incl. all relations), `graphs/artikl_overview.ttrg` (2 missing): regenerate from the corrected sample (the migration CLI emits `_all_<schema>.ttrg`) or hand-fix the `objects:` lists so each qname resolves.
- [ ] **H3.2** Verify each graph via `getGraph`: `missingObjects === []` and `nodes.length > 0`.

## H4 [High] — Write BOTH required Tests-first (they must pass, not just exist)

- [ ] **H4.1** `tests/integration/src/v1.1-samples.test.ts`: for each migrated sample, open every `_all_<schema>.ttrg` (and the hand-authored `.ttrg`s) via `client.getGraph` and assert `missingObjects === []` and `nodes.length > 0`. Use the `PassThrough` LSP harness already in `tests/integration/`. (This fails today against all three graphs — that's the point.)
- [ ] **H4.2** Parser "samples parse cleanly": add a fixture run in `@modeler/parser` that parses every file under `samples/v1.1-*/` and asserts 0 error diagnostics. (Resolution lives in semantics/LSP, so also keep H4.1 as the resolution gate.)
- [ ] **H4.3** Both green.

## H1 [High] — De-fragilise `4.5b` (and siblings) so they stop scanning the v1.1 trees

- [ ] **H1.1** In `tests/integration/src/lsp-phase-03-custom-methods.test.ts`, the failing assertion is `edge.fromCardinality` is null because `getAllTtrFiles(samplesDir, ['broken', 'v1-mini'])` now ingests the new `v1.1-*` samples. Fix the exclude list to also skip `v1.1-mini`, `v1.1-metadata`, `v1.1-mini-migrated` — or, better, point the test at a single named project dir (e.g. `samples/v1-metadata`) instead of "all samples".
- [ ] **H1.2** Apply the same scoping to the other `getAllTtrFiles`-based tests in that file (`4.5`, `4.4`, etc.) so future samples don't silently change their input.
- [ ] **H1.3** `4.5b` passes with the v1.1 samples present.

---

## M1 [Med] — Clean up the sample tree

- [ ] **M1.1** Remove the duplicate `samples/v1.1-mini-migrated/` (keep one canonical migrated mini — `v1.1-mini`), or rename/repurpose it explicitly if it's meant to be a separate fixture.
- [ ] **M1.2** Delete the committed `samples/**/.modeler/migrate-report.json` build artifact(s), and add `.modeler/` to `.gitignore` if not already.
- [ ] **M1.3** Remove the nested `samples/v1.1-mini/billing/invoicing/modeler.toml` — there should be exactly one `modeler.toml` at each sample's root, not inside package subdirs (it would be read as a second project root).

## M2 [Med] — Decide on `v1.1-metadata` resolution

- [ ] **M2.1** `v1.1-metadata` has 49 `ttr/primary-key-column-not-found` (pre-existing: `v1-metadata` has 98). Either curate the metadata sample so it resolves cleanly, or document explicitly that this sample carries known pre-existing PK issues and is not part of the "resolves cleanly" guarantee. Don't let `progress-phase-v1.1.md` claim clean resolution if it isn't.

## M3 [Med] — Finish G.8

- [ ] **M3.1** Update `CLAUDE.md` per G.8: the key-invariants section (e.g. layout sidecar → per-graph `.ttrg` `layout` block; `.ttrl` removed), plus any other v1→v1.1 invariant changes. (`README.md` is already updated.)

---

## After the above — re-review the docs (not yet assessed)

- [ ] **D.1** Re-check G.5 (`grammar-v1-1-changes.md`), G.6 (`architecture.md` six sub-updates), G.7 (`progress-phase-v1.1.md`) — the progress doc must reflect the *fixed* state, with honest test totals.

---

## Done when

- [ ] Opening every `.ttr` in `samples/v1.1-mini` through the resolver yields 0 errors; every `.ttrg` opens with `missingObjects === []` and `nodes.length > 0`.
- [ ] `v1.1-samples.test.ts` + the parser samples-parse-clean run both exist and pass.
- [ ] `4.5b` (and the other `getAllTtrFiles` tests) pass with the v1.1 samples present.
- [ ] No duplicate/stray sample dir, no committed `.modeler/` artifacts, no nested `modeler.toml`.
- [ ] `pnpm -r build && pnpm -r test && pnpm -r typecheck && pnpm -r lint` all green.
- [ ] CLAUDE.md updated; docs re-reviewed against the fixed state.
