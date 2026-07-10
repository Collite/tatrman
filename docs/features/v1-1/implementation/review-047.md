# Review 047 — Section F (Migration CLI)

**Date:** 2026-05-22
**Scope:** first review of Section 1.1.F against [`F-migration-cli.md`](../plan/tasks/F-migration-cli.md) and [contracts §10](../design/v1-1-contracts.md#10-migration-cli-surface). Verified against runtime: `pnpm --filter @modeler/migrate test` (16 pass), `integration-tests` (67/1 skip), `pnpm -r typecheck` (clean), `pnpm -r build` (ok), `pnpm --filter @modeler/migrate lint` (**5 errors**), plus a full read of `index.ts`/`cli.ts`/tests and two empirical probes (generated `.ttrg` parse, `.ttrl` key). Companion: [`tasks-review-047.md`](tasks-review-047.md).
**Verdict:** **Not done.** The skeleton, `inferPackage`, `insertPackageDecl`, `insertImports` rendering, and CLI flag/exit-code wiring are in place — but the **core `.ttrl → .ttrg` synthesis produces files that don't parse** (verified), the **wildcard-threshold logic (F.4) is neither implemented nor tested**, the `.ttrl` reader uses the **wrong key** so positions are dropped, and the **integration test exercises none of the real migration** (v1-mini has no packages, no subdirs, no `.ttrl`) while omitting the two resolver/`getGraph` assertions the spec requires. Lint is also red. The green suite is, again, hiding broken core features.

> Migrate suite green at 16, but every "interesting" path (package insert, cross-package imports, `.ttrg` generation, ambiguity) is either untested or tested only through fabricated, self-consistent-but-wrong fixtures.

---

## High — blockers

### H1 [High] — The generated `.ttrg` does not parse (F.6 is broken)

`convertTtrlToTtrg` builds the layout block with `JSON.stringify`:

```ts
`...\n  layout: {\n    viewport: ${JSON.stringify(vp)},\n    nodes: ${JSON.stringify(nodes)}\n  }\n}\n`
```

That emits **quoted** keys (`{"zoom":2,...}`, `{"er.entity.x":{...}}`), which the grammar rejects. I fed the exact generated shape through `parseString`:

```
ttr/parse-error: mismatched input '"zoom"' expecting { ... 'er', ... 'layout', ... IDENT }
  (and cascade errors on "panX", ",", ...)
```

So **every synthesized `.ttrg` is syntactically invalid**, which makes the DONE criterion *"`_all_er.ttrg` opens via `client.getGraph` with `missingObjects === []`"* impossible. The canonical layout-block syntax uses **unquoted** keys and is already produced by `serializeLayoutBlock` in `@modeler/edit` (`packages/edit/src/graph-edits.ts:278`):

```
viewport: { zoom: 2, panX: 5, panY: 15, displayMode: "with-types" }
nodes: { er.entity.x: { x: 300, y: 400 } }
```

**Fix:** reuse `serializeLayoutBlock` (export it from `@modeler/edit`, add the dep) or replicate its exact unquoted syntax. (The graph header — `schema: er,` + multi-line `objects: [...]` — *does* parse; only the layout block is broken.)

### H2 [High] — `.ttrl` read uses the wrong key, and the end-to-end test exercises none of the migration

- **Wrong key:** `convertTtrlToTtrg` reads `layout.nodePositions` (`index.ts:214`). The v1 `.ttrl` is a serialized `LayoutFile`, whose positions live under **`nodes`** (`packages/lsp/src/model-graph.ts` `LayoutFile.nodes`), not `nodePositions`. So against a real `.ttrl`, `nodePositions ?? {}` is always `{}` — **all node positions are dropped**, contradicting F.6 ("preserves the layout positions"). The unit test hides this by writing a fabricated `.ttrl` that *also* uses `nodePositions`.
- **Vacuous E2E:** `tests/integration/src/migration.test.ts` runs only on `samples/v1-mini/`, which has **no `.ttrl`, no subdirectories, and all files in the default package** (`db.ttr`/`er.ttr`/`map.ttr` at root). So the run does **no** package insertion, **no** cross-package imports, and **no** `.ttrg` generation. The "dry-run didn't write" test checks an empty set of `.ttrg` files (there are none). 
- **Missing required assertions:** the spec's Tests-first requires (1) "every cross-reference resolves with no `ttr/unresolved-reference`/`ttr/unimported-reference`" and (2) "`_all_er.ttrg` opens via `client.getGraph` with `missingObjects === []`." Neither is present — the test only checks `parseString` errors, never boots the resolver or an LSP client. (Both would currently fail anyway, given H1.)

### H3 [High] — Wildcard-vs-named threshold (F.4) is not implemented and not tested

The spec: *"named form when only one symbol is referenced, or `import B.*` when ≥3 symbols from package B are referenced; threshold configurable via `--wildcard-threshold`."* The implementation does none of this:

- `scanCrossReferences` decides `isWildcard` purely from the matched qname's **segment count** — `parts.length === 3` → wildcard, `>= 4` → named (`index.ts:117-136`) — which has nothing to do with how many symbols of a package are referenced.
- `args.wildcardThreshold` is **never passed to `scanCrossReferences`** and never consumed anywhere — the `--wildcard-threshold` flag is dead.
- The `insert-imports` tests pre-set `isWildcard` on the input specs, so they test **rendering**, not the decision. There is **no `scanCrossReferences` test at all**, so the actual threshold behavior is entirely unverified (the test titled "produces wildcard import when ≥wildcard-threshold symbols are referenced" doesn't exercise any threshold).

---

## Medium

### M1 [Med] — Lint fails (5 errors)

`pnpm --filter @modeler/migrate lint`:
```
__tests__/ttrl-to-ttrg.test.ts:1  'vi' is defined but never used
cli.ts:73                          Unexpected any (report: any)
index.ts:3                         'Reference' is defined but never used
index.ts:109                       'pkg' is assigned a value but never used
index.ts:197                       Unexpected any (let layout: any)
```
`any` is ESLint-forbidden per CLAUDE.md. Lint is part of the standard gate; the section can't sign off red. (Type `layout` as a minimal interface; type `report` as `MigrateReport`.)

### M2 [Med] — qname construction diverges from the resolver for namespace-less files

`runMigration` builds qnames as `${pkg}.${schemaCode}.${ns}.${def.name}` using the schema directive's `namespace` (`index.ts:278-282`). When a file has no `namespace` clause — e.g. v1-mini's `map.ttr` (`schema map`) — `ns` is `''`, producing double-dotted qnames like `map..Foo`. The real symbol table uses the **kind** as the namespace segment when no `namespace` clause is present (`pkg.schema.kind.name`, per `symbol-table.ts` / CLAUDE.md). So migrate's qnames won't match what the resolver/`getGraph` expects → the generated `objects:` lists and named imports would be unresolved. Derive qnames from the actual symbol table / resolver rather than re-deriving them here.

### M3 [Med] — Required tests missing

- No `scanCrossReferences` test (threshold, named-vs-wildcard, ambiguity).
- No **ambiguous-reference exit-code-1** test — DONE explicitly requires *"Ambiguous-reference exit-code-1 path tested with a hand-crafted fixture."* (F.8 code path exists but is unverified.)
- `ttrl-to-ttrg` test never **parses** its output (it only `toContain`-checks substrings — which is why H1 slipped through).
- Integration test lacks a fixture with packages/subdirs/a `.ttrl`, and the resolver + `getGraph`/`missingObjects` assertions.

---

## Low

- **L1** — `--dry-run` still **writes** `.modeler/migrate-report.json` (`cli.ts:54`). Dry-run must not write anything; print the report to stdout instead (or gate the file write on `!dryRun`).
- **L2** — `--verbose` is parsed but never used; the imports summary always prints `import X.*` even for named imports (`cli.ts:39`). Cosmetic but misleading.
- **L3** — `migration.test.ts` hardcodes `/Users/bora/Dev/modeler/packages/migrate/dist/cli.js` (non-portable / CI-hostile), depends on `dist` being pre-built, and mixes `require()` in an ESM test. Use a path relative to the package and ensure build ordering.
- **L4** — `@modeler/migrate` pins `vitest ^3` (resolves 3.2.4) while the workspace is on v4.x. Align to the workspace version to avoid config/behavior drift and lockfile bloat.
- **L5** — `insertPackageDecl` idempotency uses `content.includes('package ')`, which would false-positive on the word "package " inside a comment. Check for a `package` declaration at line start.

---

## What's genuinely good

- Package skeleton is correct (F.1): `package.json` with `bin: modeler-migrate`, `tsconfig` extending base, `index.ts` programmatic API + `cli.ts`, wired into the workspace.
- `inferPackage` (F.2) is correct and tested (root → `''`, nested → dotted).
- `insertPackageDecl` (F.3) handles leading comment blocks and is idempotent; tested.
- `insertImports` (F.5) renders named + wildcard, dedups existing imports, and places the block before `schema`; the *rendering* is tested.
- CLI flags and exit codes (F.7) are structurally per contract §10 (`0`/`1`/`2`), and the report is written to `.modeler/migrate-report.json`.
- No samples were modified — DONE criterion "No samples touched yet" holds.

---

## Recommendation

The plumbing is there but the load-bearing transforms aren't trustworthy. Fix order: (1) make `.ttrg` generation emit parseable layout via the canonical `serializeLayoutBlock` and read the correct `.ttrl` `nodes` key (H1, H2a); (2) implement the real wildcard threshold in `scanCrossReferences` and consume `--wildcard-threshold` (H3); (3) derive qnames from the symbol table so they match the resolver (M2); (4) build a real fixture (packages + subdirs + `.ttrl`) and assert the spec's resolution + `getGraph`/`missingObjects` outcomes, add the `scanCrossReferences` and ambiguous-exit-1 tests (H2bc, M3); (5) clear lint (M1) and the dry-run write (L1). `tasks-review-047.md` has the steps.
