# Tasks — review-047 (Section F: Migration CLI)

Findings in [`review-047.md`](review-047.md). Work top-to-bottom: **H1–H3** are blockers (core transforms broken/missing), **M1–M3** close real correctness + test gaps, **L1–L5** are polish. Section F is done when H1–H3 and M1–M3 are closed and all gates (incl. lint) are green.

Do exactly what's written.

---

## H1 [High] — Make the generated `.ttrg` parse (use the canonical layout writer)

The bug: `convertTtrlToTtrg` builds the layout block with `JSON.stringify(vp)` / `JSON.stringify(nodes)`, producing quoted keys the grammar rejects (verified: `mismatched input '"zoom"'`).

- [ ] **H1.1** In `packages/edit/src/graph-edits.ts`, export the existing `serializeLayoutBlock` (it currently is module-private) — or add a small public `buildGraphFileContent({ name, schema, objects, layout })` helper that emits the full `.ttrg` with the canonical layout syntax.
- [ ] **H1.2** Add `"@modeler/edit": "workspace:*"` to `packages/migrate/package.json` dependencies.
- [ ] **H1.3** In `convertTtrlToTtrg` (`index.ts`), stop hand-rolling JSON. Build the layout block via the canonical writer so it emits unquoted keys:
  ```
  viewport: { zoom: 2, panX: 5, panY: 15, displayMode: "with-types" }
  nodes: { er.entity.x: { x: 300, y: 400 } }
  ```
- [ ] **H1.4** Verify by parsing: after generating, `parseString(generatedContent, path)` must return zero `severity:'error'` diagnostics. Add this as a unit assertion (see M3.3).

---

## H2 [High] — Read the right `.ttrl` key, and make the E2E actually exercise migration

- [ ] **H2.1** In `convertTtrlToTtrg`, read node positions from **`layout.nodes`**, not `layout.nodePositions` (the v1 `.ttrl` is a serialized `LayoutFile`, whose positions are under `nodes`). Confirm against `LayoutFile` in `packages/lsp/src/model-graph.ts`. (If you want to tolerate both, prefer `layout.nodes ?? layout.nodePositions ?? {}`, but `nodes` is the real key.)
- [ ] **H2.2** Build a **real migration fixture** that exercises the feature — a small project with: at least two packages via subdirectories (e.g. `billing/invoicing.ttr`, `billing/products.ttr`, `shared/types.ttr`), genuine cross-package references, and a `.modeler/layout.ttrl` with `viewports` (db + er) and `nodes` positions. Put it under `samples/` only if it's intended as a shipped sample; otherwise under `tests/integration/fixtures/migrate-v1/` so G's real-sample migration stays separate.
- [ ] **H2.3** Rewrite `tests/integration/src/migration.test.ts` to assert the spec's Tests-first on that fixture:
  - Every `.ttr` parses cleanly under v1.1 (already done — keep).
  - Every cross-reference **resolves** with no `ttr/unresolved-reference` / `ttr/unimported-reference` (boot the semantics resolver, or the LSP `didOpen` + diagnostics, over the migrated tree — use the `PassThrough` LSP harness pattern already in `tests/integration/`).
  - `_all_er.ttrg` **opens via `client.getGraph`** with `missingObjects === []`.
- [ ] **H2.4** Keep a real assertion that `--dry-run` writes **nothing**: snapshot the tree mtimes/contents before and after the dry run and assert no `.ttr` changed and no `.ttrg`/report file was created (see L1).

---

## H3 [High] — Implement the wildcard-vs-named threshold (F.4)

- [ ] **H3.1** Change `scanCrossReferences` to take the threshold: `scanCrossReferences(ast, fromPackage, projectSymbols, wildcardThreshold)`. Pass `args.wildcardThreshold` from `runMigration`.
- [ ] **H3.2** Decide named-vs-wildcard by **count of distinct referenced symbols per target package**, not by qname segment count:
  - Group the file's resolved cross-package references by target package.
  - If a package has `>= wildcardThreshold` distinct referenced defs → emit a single `import <pkg>.*` (wildcard).
  - Else → emit one named `import <pkg>.<schema>.<ns>.<def>` per referenced def.
- [ ] **H3.3** Remove the `parts.length === 3 ? wildcard : named` heuristic — derive `schema`/`namespace`/`defName` from the resolved symbol (or the symbol table), not from raw `split('.')` (ties into M2).
- [ ] **H3.4** Add a `scanCrossReferences` unit test (`packages/migrate/src/__tests__/scan-cross-references.test.ts`): one reference → named; `>= threshold` distinct refs to one package → wildcard; `--wildcard-threshold` change flips the boundary; idempotent.

---

## M1 [Med] — Clear lint

- [ ] **M1.1** `ttrl-to-ttrg.test.ts:1` — remove unused `vi` from the import.
- [ ] **M1.2** `index.ts:3` — remove unused `Reference` import. `index.ts:109` — remove the unused `pkg` binding in the `byPackage` loop.
- [ ] **M1.3** `index.ts:197` — type `layout` (e.g. `{ viewports?: Record<string, ViewportState>; nodes?: Record<string, { x: number; y: number }> }`) instead of `any`. `cli.ts:73` — type `report: MigrateReport` instead of `any`.
- [ ] **M1.4** `pnpm --filter @modeler/migrate lint` exits 0.

---

## M2 [Med] — Derive qnames from the symbol table, not string-built `pkg.schema.ns.name`

- [ ] **M2.1** Replace the hand-built qname in `runMigration` (`index.ts:280-282`) — which yields `pkg.schema..name` for files with no `namespace` clause — with qnames from the actual symbol table (`@modeler/semantics`), which uses the kind as the namespace segment when no `namespace` clause is present. This makes the generated `objects:` lists and named imports match what the resolver/`getGraph` expects (verified gap on v1-mini's `map.ttr`).
- [ ] **M2.2** Ensure `convertTtrlToTtrg`'s `objects` and the `schema.<x>` filtering use those resolver-consistent qnames.

---

## M3 [Med] — Add the missing required tests

- [ ] **M3.1** Ambiguous-reference exit-code-1 test (DONE requirement): a hand-crafted fixture where two packages export the same bare name; assert the run records `ambiguousReferences` and the CLI exits `1`. (Drive `runMigration` for the report, and/or `execSync` the CLI and assert exit code.)
- [ ] **M3.2** `scanCrossReferences` test — covered by H3.4.
- [ ] **M3.3** In `ttrl-to-ttrg.test.ts`, after generating, **parse** the produced `.ttrg` content and assert zero error diagnostics (this is what would have caught H1). (The migrate package can add `@modeler/parser` — already a dep — for this.)
- [ ] **M3.4** Verify positions survive end-to-end: a `.ttrl` with `nodes` positions produces a `.ttrg` whose layout block contains those positions (this catches H2.1 regressions).

---

## L (low) — polish

- [ ] **L1** — `cli.ts`: do not write `.modeler/migrate-report.json` on `--dry-run` (gate the `writeReport` call on `!args.dryRun`; print the report to stdout for dry-run).
- [ ] **L2** — Either implement `--verbose` (extra progress logging) or drop the flag; fix the imports summary line (`cli.ts:39`) to print the actual import form (named vs `.*`), not always `.*`.
- [ ] **L3** — `migration.test.ts`: resolve the CLI path relative to the package (e.g. `join(__dirname, '../../../packages/migrate/dist/cli.js')` or run via the `bin`), not a hardcoded `/Users/bora/...`; avoid `require()` in the ESM test.
- [ ] **L4** — Align `@modeler/migrate` to the workspace `vitest` v4 (match the version other packages use), then re-run `pnpm install`.
- [ ] **L5** — `insertPackageDecl`: detect an existing `package` decl at line start (e.g. `/^package\s/m`), not `content.includes('package ')`, so a comment mentioning "package " doesn't suppress insertion.

---

## Done when

- [ ] **H1:** generated `.ttrg` parses cleanly (asserted by parsing the output); layout uses the canonical unquoted syntax.
- [ ] **H2:** `.ttrl` `nodes` positions are preserved; the E2E runs on a fixture with packages + subdirs + a `.ttrl` and asserts cross-references resolve (no unresolved/unimported) and `_all_er.ttrg` opens via `getGraph` with `missingObjects === []`.
- [ ] **H3:** wildcard-vs-named is decided by referenced-symbol count vs `--wildcard-threshold`, with a `scanCrossReferences` test proving it.
- [ ] **M1:** lint green. **M2:** qnames match the resolver. **M3:** ambiguous-exit-1, scan, and `.ttrg`-parses tests exist.
- [ ] `pnpm --filter @modeler/migrate test && pnpm --filter @modeler/migrate lint && pnpm --filter @modeler/integration-tests test && pnpm -r typecheck && pnpm -r build` all green.
- [ ] `modeler-migrate --dry-run` writes nothing; full run on the fixture produces v1.1-valid, resolvable output.
