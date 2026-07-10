# Review 049 — Section G (Migrate samples + docs)

**Date:** 2026-05-22
**Scope:** review of Section 1.1.G against [`G-samples-docs.md`](../plan/tasks/G-samples-docs.md), triggered by the developer's claim that the one failing integration test (`4.5b getModelGraph … er`) is a pre-existing non-regression. Verified against runtime: reproduced the failure, ran the LSP resolver over the migrated samples, parse-checked every v1.1 sample file, and opened the generated `.ttrg` graphs via `getGraph`. Companion: [`tasks-review-049.md`](tasks-review-049.md).
**Verdict:** **Not done — and the developer's diagnosis of the failing test is wrong.** The failing test **is** a G regression (proven below), caused by the new samples, not a qname-format mismatch. Beyond that, the flagship migrated sample `v1.1-mini` **does not resolve** (28 errors), its generated `.ttrg` graphs are **full of `missingObjects`**, and **both** of G's required "Tests-first" safety nets were never written — which is exactly why none of this was noticed.

---

## On the developer's claim (the headline)

> "The single failing test (4.5b) is pre-existing — it failed before our G changes (confirmed via git stash revert test). It fails because the test expects `er.entity.artikl` while getModelGraph returns `billing.invoicing.er.entity.artikl` … not a G regression."

This is incorrect on every point:

1. **It is a regression.** Proof: with the three new sample dirs moved aside (`samples/v1.1-mini`, `v1.1-metadata`, `v1.1-mini-migrated`), `4.5b` **passes** (full integration suite green, 72/1-skip). Restoring them, it fails again. The new samples cause it.
2. **The `git stash` check was fooled by untracked files.** All three new sample dirs are **untracked** (`??`), and `git stash` does not remove untracked files by default — so the "reverted" tree still contained the new samples, and the test still scanned them. The stash proved nothing.
3. **The cause is not a qname mismatch.** The actual failing assertion is `expect(edge.fromCardinality).not.toBeNull()` (`lsp-phase-03-custom-methods.test.ts:234`) — a relation edge with a **null cardinality**, not a string-format comparison.

Root cause: `4.5b` does `getAllTtrFiles(samplesDir, ['broken', 'v1-mini'])` and then `ttrFiles.find(f => f.endsWith('er.ttr'))`. It excludes only `broken`/`v1-mini`. G added `samples/v1.1-*/`, which contain **five** more `*er.ttr` files; the test now opens all of them and picks a different/aggregated `er` model, yielding a relation edge with no cardinality. (The test is also fragile — "first `er.ttr` found across all samples" — but G is what tripped it.)

---

## High — blockers

### H1 [High] — `4.5b` is a G regression

As above. Fix the test to not ingest the v1.1 sample trees (add `v1.1-mini`, `v1.1-metadata`, `v1.1-mini-migrated` to the exclude list, or — better — scope it to a single named project fixture instead of "every `.ttr` under `samples/`"). The same fragility applies to `4.5` (db) and others using `getAllTtrFiles`.

### H2 [High] — `samples/v1.1-mini` does not resolve (DONE violated)

Opening every `.ttr` under `v1.1-mini` through the resolver yields **28 error diagnostics**:
- **24 × `ttr/package-declaration-mismatch`** — files were hand-moved into `billing/invoicing/entities/` and `billing/invoicing/relations/` but still declare `package billing.invoicing`, while the directory implies `billing.invoicing.entities` / `…relations`. (e.g. *"Declared package 'billing.invoicing' does not match inferred package 'billing.invoicing.entities'"*.)
- **4 × `ttr/duplicate-definition`** — the relations (`artikl_produkt`, `artikl_podprodukt`, …) are defined **both** in `billing/invoicing/er.ttr` **and** in `billing/invoicing/relations/*.ttr`. The split-out files were added without removing the originals.

DONE requires *"`samples/v1.1-mini/` … parse cleanly, all references resolve."* It parses (0 parse errors) but does **not** resolve. This is a botched manual restructure, not clean migration output.

### H3 [High] — The generated `.ttrg` graphs are broken (massive `missingObjects`)

Opening the migrated graphs via `getGraph`:
- `graphs/all_db.ttrg` → **nodes = 0**, `missingObjects` = **all 11** objects (every `db.dbo.*` + the `fk_*`).
- `graphs/all_er.ttrg` → nodes = 5, `missingObjects` = 7 (`obchodni_kanal`, `trzni_skupina`, and **all 5 relations**, e.g. `billing.invoicing.er.relation.artikl_produkt`).
- `graphs/artikl_overview.ttrg` → nodes = 2, `missingObjects` = 2 relations.

DONE requires *"every `.ttrg` opens … missingObjects === []."* All three fail badly — the graphs list qnames that don't resolve against the (broken) sample. `all_db.ttrg` is fully non-functional.

### H4 [High] — Both required "Tests-first" items are missing

- `tests/integration/src/v1.1-samples.test.ts` — **does not exist** (spec: open each `_all_<schema>.ttrg`, assert `missingObjects === []` and `nodes.length > 0`).
- The parser "samples parse cleanly against every file under `samples/v1.1-*/`" fixture run — **not present** (`@modeler/parser` tests don't reference the v1.1 samples).

These are precisely the safety nets that would have caught H2 and H3. Their absence is why a broken sample was reported "ready." (Writing the `v1.1-samples.test.ts` now would immediately fail on all three graphs.)

---

## Medium

### M1 [Med] — Sample directory mess

- **Duplicate sample:** both `samples/v1.1-mini/` (hand-restructured) and `samples/v1.1-mini-migrated/` (raw CLI output: flat `db/er/map.ttr` + `.modeler/migrate-report.json`) exist. Unclear which is canonical; G.1 specifies one (`v1.1-mini`).
- **Committed build artifact:** `samples/v1.1-mini-migrated/.modeler/migrate-report.json` shouldn't be in the sample tree.
- **Nested project marker:** `samples/v1.1-mini/billing/invoicing/modeler.toml` — a `modeler.toml` inside a package subdirectory will be read as a second project root (project-root resolution walks up to the nearest `modeler.toml`), which is almost certainly not intended.

### M2 [Med] — `v1.1-metadata` doesn't resolve cleanly (pre-existing data issue, but DONE still unmet)

`v1.1-metadata` emits **49 × `ttr/primary-key-column-not-found`**. To be fair to G, this is **pre-existing**: the original `samples/v1-metadata` emits **98** of the same. So the migration didn't introduce it — but DONE requires the v1.1 samples to "resolve cleanly," so this needs a decision: clean up the metadata sample, or pick/curate a sample that actually resolves. Don't ship it claiming clean resolution.

### M3 [Med] — G.8 incomplete

`CLAUDE.md` is **not modified** (only `README.md`). G.8 requires updating CLAUDE.md's key-invariants (e.g. the layout-sidecar invariant → per-graph `.ttrg` layout block).

---

## Not yet assessed

G.5 (`grammar-v1-1-changes.md`), G.6 (`architecture.md`), G.7 (`progress-phase-v1.1.md`) docs were touched but I did not review their content in depth — the substantive blockers above dominate, and the progress doc cannot be accurate while the samples are broken. Re-review the docs once H2–H4 are fixed.

---

## Recommendation

The developer's "one harmless failing test" framing is the opposite of what's true: that test is a real regression *and* it's the least of the problems. Fix order: (1) repair `v1.1-mini` — pick one directory layout, make every file's `package` match its directory, and remove the duplicated relation defs (H2); regenerate/repair the `.ttrg` graphs so `missingObjects === []` (H3); (2) write both required tests — they must pass, not just exist (H4); (3) de-fragilise `4.5b`/`getAllTtrFiles` to stop scanning the v1.1 trees (H1); (4) clean up the stray `v1.1-mini-migrated`, the committed report, and the nested `modeler.toml` (M1); (5) decide on the metadata sample (M2) and finish CLAUDE.md (M3). Then the docs (G.5–G.7) can be re-reviewed. `tasks-review-049.md` has the steps.
