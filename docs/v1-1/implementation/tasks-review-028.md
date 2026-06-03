# Tasks — Review 028 (Section B3 follow-ups)

Companion task list to [`review-028.md`](review-028.md). Eight blocks; estimate one focused day.

The semantics unit suite is at 71/71. After this list, the two failing integration tests turn green (29/29) and `package-graph.ts` gets its first real coverage.

---

## Block 1 — Fix `enclosingQnameOf` so integration tests turn green (Finding 3)

This is the single highest-leverage fix in the list. The reference-index is constructing qnames with the v1 shape while the symbol-table now uses v1.1's kind-fallback. Once these two agree, the two failing integration tests pass.

- [ ] **1.1 — Make `enclosingQnameOf` v1.1-aware in `packages/semantics/src/reference-index.ts`.**

  Replace lines 5–13 (the entire function) with:

  ```ts
  function enclosingQnameOf(
    def: Definition,
    schemaCode: string,
    namespace: string,
    packageName?: string,
  ): string | undefined {
    if (
      def.kind === 'entity' || def.kind === 'table' || def.kind === 'view' ||
      def.kind === 'procedure' || def.kind === 'er2dbEntity' || def.kind === 'er2dbRelation' ||
      def.kind === 'relation' || def.kind === 'role' || def.kind === 'er2cncRole'
    ) {
      const nsOrKind = namespace || def.kind;
      const segments: string[] = [];
      if (packageName) segments.push(packageName);
      segments.push(schemaCode);
      if (nsOrKind) segments.push(nsOrKind);
      segments.push(def.name);
      return segments.join('.');
    }
    return undefined;
  }
  ```

  Notes:

  - This mirrors `DocumentSymbolTable.makeQname` byte-for-byte (minus the stock-cnc doubling — see 1.3).
  - The kind list is preserved as-is; don't shrink it.

- [ ] **1.2 — Thread `packageName` into the call site.**

  In `reference-index.ts`, around line 45 (`const referrerQname = enclosingQnameOf(ownerDef, schemaCode, namespace) ?? null;`), change to:

  ```ts
  const referrerQname = enclosingQnameOf(ownerDef, schemaCode, namespace, packageName) ?? null;
  ```

  `packageName` is already in scope from the new `upsertDocument` signature.

- [ ] **1.3 — Handle the stock-cnc doubling.**

  When `schemaCode === 'cnc' && !packageName && uri.startsWith('stock://')`, prepend an extra `'cnc'` segment — the same logic `DocumentSymbolTable.isStockCnc` uses. Inline check at the top of the kind branch, before assembling segments:

  ```ts
  const isStockCnc = schemaCode === 'cnc' && !packageName && uri.startsWith('stock://');
  // ... in segment assembly:
  if (packageName) segments.push(packageName);
  if (isStockCnc)  segments.push('cnc');
  segments.push(schemaCode);
  ```

  This means `enclosingQnameOf` now needs the `uri` parameter too. Thread it through:

  - Add `uri: string` to the function signature.
  - Pass `uri` at the single call site (`uri` is already in scope inside `upsertDocument`).

  > **Why not centralize?** The cleanest long-term fix is to export a single `buildDocumentQname(def, ctx)` helper from `symbol-table.ts` and reuse it from both `DocumentSymbolTable.makeQname` and `enclosingQnameOf`. That's a separate refactor; do it in a follow-up commit after the tests turn green, not now. Block 7 picks this up.

- [ ] **1.4 — Audit `packages/lsp/src/server.ts` for a second `enclosingQnameOf` (or equivalent) usage.**

  Run:

  ```bash
  grep -n "enclosingQnameOf\|schemaCode.*namespace.*name" packages/lsp/src/server.ts
  ```

  If `lsp/server.ts` has its own copy (likely around line 419 where `enclosingQnameOf(found.from, ast)` is called), apply the same v1.1 fix there. If it imports `enclosingQnameOf` from `reference-index.ts`, just confirm the signature change is honored.

- [ ] **1.5 — Run integration tests. Expect 29/29.**

  ```bash
  pnpm -r build           # rebuild lsp dist/ before integration tests
  pnpm --filter @modeler/integration-tests test
  ```

  If still failing, the failure mode should now be *different* — likely the `workspace/symbol` 100-symbol cap (see review finding 8). Investigate that separately.

---

## Block 2 — Add `package-graph.test.ts` (Finding 1)

The Tests-first deliverable is missing. Add it now; finding 2's `getDependents` bug is one of the cases.

- [ ] **2.1 — Create `packages/semantics/src/__tests__/package-graph.test.ts`.**

  ```ts
  import { describe, it, expect } from 'vitest';
  import { parseString } from '@modeler/parser';
  import { ProjectSymbolTable } from '../project-symbols.js';
  import { PackageGraphBuilder } from '../package-graph.js';
  import type { Document } from '@modeler/parser';

  function loadProject(files: Array<{ uri: string; src: string }>) {
    const table = new ProjectSymbolTable();
    const docs = new Map<string, Document>();
    for (const f of files) {
      const ast = parseString(f.src, f.uri).ast!;
      const schemaCode = ast.schemaDirective?.schemaCode ?? 'er';
      const namespace = ast.schemaDirective?.namespace ?? '';
      const packageName = ast.packageDecl?.name ?? '';
      table.upsertDocument(f.uri, ast, schemaCode, namespace, packageName);
      docs.set(f.uri, ast);
    }
    return new PackageGraphBuilder(table, docs);
  }

  describe('PackageGraphBuilder', () => {
    it('A imports B imports C: 3 nodes, 2 edges, transitive accessors', () => {
      const builder = loadProject([
        {
          uri: 'a/a.ttr',
          src:
            'package A\nimport B.*\nschema er namespace entity\ndef entity a {}\n',
        },
        {
          uri: 'b/b.ttr',
          src:
            'package B\nimport C.*\nschema er namespace entity\ndef entity b {}\n',
        },
        {
          uri: 'c/c.ttr',
          src: 'package C\nschema er namespace entity\ndef entity c {}\n',
        },
      ]);

      const graph = builder.build();
      expect(graph.nodes.map((n) => n.name).sort()).toEqual(['A', 'B', 'C']);
      expect(graph.edges).toHaveLength(2);

      expect(builder.getDependencies('A').sort()).toEqual(['B', 'C']);
      expect(builder.getDependents('C').sort()).toEqual(['A', 'B']);
    });

    it('cycle A→B→A: findCycles returns [["A", "B"]]', () => {
      const builder = loadProject([
        {
          uri: 'a/a.ttr',
          src: 'package A\nimport B.*\nschema er namespace entity\ndef entity a {}\n',
        },
        {
          uri: 'b/b.ttr',
          src: 'package B\nimport A.*\nschema er namespace entity\ndef entity b {}\n',
        },
      ]);

      const cycles = builder.findCycles();
      expect(cycles).toHaveLength(1);
      expect(cycles[0].sort()).toEqual(['A', 'B']);
    });

    it('self-import: not a cycle (size-1 SCCs filtered out)', () => {
      const builder = loadProject([
        {
          uri: 'a/a.ttr',
          src: 'package A\nimport A.*\nschema er namespace entity\ndef entity a {}\n',
        },
      ]);

      const cycles = builder.findCycles();
      expect(cycles).toEqual([]);
    });
  });
  ```

- [ ] **2.2 — Run it.**

  ```bash
  pnpm --filter @modeler/semantics test -- package-graph
  ```

  The first case will fail until Block 3 lands (`getDependents` returns the wrong values). That's expected — leave the failure in until Block 3 fixes it.

---

## Block 3 — Fix `getDependents` direction (Finding 2)

- [ ] **3.1 — Flip the edge direction in `packages/semantics/src/package-graph.ts:145–159`.**

  Replace the existing `getDependents` body with:

  ```ts
  getDependents(pkg: string): string[] {
    const graph = this.build();
    const dependentSet = new Set<string>();
    const queue: string[] = [pkg];
    while (queue.length > 0) {
      const current = queue.shift()!;
      for (const edge of graph.edges) {
        if (edge.to === current && !dependentSet.has(edge.from)) {
          dependentSet.add(edge.from);
          queue.push(edge.from);
        }
      }
    }
    return Array.from(dependentSet);
  }
  ```

  The diff vs the current code: `edge.from === current` → `edge.to === current`; `edge.to` → `edge.from` in the two places that use it.

- [ ] **3.2 — Re-run `package-graph.test.ts` from Block 2.**

  ```bash
  pnpm --filter @modeler/semantics test -- package-graph
  ```

  All three cases must pass now.

---

## Block 4 — Fix the step 6 test (Finding 4)

The current "step 6" test exercises step 2. Rewrite it to actually test step 6.

- [ ] **4.1 — In `packages/semantics/src/__tests__/resolver-v1.1.test.ts`, replace the existing step 6 case (around lines 186–205) with two cases.**

  ```ts
  describe('step 6: fully-qualified-but-unique', () => {
    it('FQN reference to a def in an unimported package resolves via fully-qualified', () => {
      const table = new ProjectSymbolTable();
      const astTarget = parseString(
        `package billing.invoicing
         schema er namespace entity
         def entity artikl { attributes: [def attribute id { type: int }] }`,
        'billing/invoicing/a.ttr'
      ).ast!;
      table.upsertDocument(
        'billing/invoicing/a.ttr',
        astTarget,
        'er',
        'entity',
        'billing.invoicing'
      );

      const astSource = parseString(
        `package app
         schema er namespace entity
         def relation r { from: billing.invoicing.er.entity.artikl, to: billing.invoicing.er.entity.artikl }`,
        'app/source.ttr'
      ).ast!;
      table.upsertDocument('app/source.ttr', astSource, 'er', 'entity', 'app');

      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        {
          path: 'billing.invoicing.er.entity.artikl',
          parts: ['billing', 'invoicing', 'er', 'entity', 'artikl'],
        },
        { schemaCode: 'er', namespace: 'entity', packageName: 'app' }
      );
      expect(res.resolved).toBe(true);
      if (res.resolved) expect(res.viaStep).toBe('fully-qualified');
    });

    // This case depends on Block 5 — if step 6 still has the multi-part guard,
    // mark this with `.skip(`)` and a TODO(post-B3) comment.
    it('bare reference unique across the project resolves via fully-qualified', () => {
      const table = new ProjectSymbolTable();
      const astTarget = parseString(
        `package billing.invoicing
         schema er namespace entity
         def entity unique_name { attributes: [] }`,
        'billing/invoicing/a.ttr'
      ).ast!;
      table.upsertDocument(
        'billing/invoicing/a.ttr',
        astTarget,
        'er',
        'entity',
        'billing.invoicing'
      );

      const astSource = parseString(
        `package app
         schema er namespace entity
         def relation r { from: unique_name, to: unique_name }`,
        'app/source.ttr'
      ).ast!;
      table.upsertDocument('app/source.ttr', astSource, 'er', 'entity', 'app');

      const resolver = new Resolver(table);
      const res = resolver.resolveReference(
        { path: 'unique_name', parts: ['unique_name'] },
        { schemaCode: 'er', namespace: 'entity', packageName: 'app' }
      );
      expect(res.resolved).toBe(true);
      if (res.resolved) expect(res.viaStep).toBe('fully-qualified');
    });
  });
  ```

- [ ] **4.2 — Run the resolver suite.**

  ```bash
  pnpm --filter @modeler/semantics test -- resolver-v1
  ```

  The first case must pass after Block 1 fixes integration tests (it doesn't depend on Block 1 but they're often green together). The second case is the trigger for Block 5.

---

## Block 5 — Step 6: bare-unique acceptance (Finding 5)

Make the call: drop the guard, or amend the contract. Default recommendation: drop the guard.

- [ ] **5.1 — Decide direction.**

  Option A (recommended): drop the `ref.parts.length > 1` guard.

  Option B: keep the guard, amend contracts §4.1.

  Option A is more aligned with the contract's stated intent ("small-project ergonomics"). Pick A unless you can articulate why bare-unique resolution causes a problem.

- [ ] **5.2 — If Option A: drop the guard in `packages/semantics/src/resolver.ts:146`.**

  Change:

  ```ts
  if (ref.parts.length > 1) {
    const uniqueMatches = this.symbols.getBySuffix(ref.path).filter(/* … */);
    // …
  }
  ```

  to:

  ```ts
  const uniqueMatches = this.symbols.getBySuffix(ref.path).filter(/* … */);
  if (uniqueMatches.length === 1) {
    tried.push(attempt('fully-qualified', uniqueMatches[0].qname));
    return { resolved: true, symbol: uniqueMatches[0], viaStep: 'fully-qualified' };
  }
  ```

  Verify the second test case from Block 4.1 now passes.

- [ ] **5.3 — If Option B: amend contracts §4.1.**

  Edit `docs/v1-1/design/v1-1-contracts.md` §4.1 — replace the "Practical effect: in small projects the user often won't have to write `import`s at all" paragraph with a paragraph stating that step 6 only handles fully-qualified references (multi-part path), and that bare-unique resolution is deferred. Add a `v5, <date>` changelog entry. Skip-mark the bare-unique test case from Block 4.1 with `it.skip` and a `TODO(post-v1.1)` comment.

---

## Block 6 — Wire `PackageGraphBuilder` into the LSP server (Finding 6)

B3.7 minimum-viable wiring. No `modeler/getPackageGraph` LSP method yet — that's C2's job.

- [ ] **6.1 — Add a `PackageGraphBuilder` instance to `packages/lsp/src/server.ts`.**

  Around where `projectSymbols` and `refIndex` are constructed, add:

  ```ts
  import { PackageGraphBuilder } from '@modeler/semantics';
  // …
  const documents = new Map<string, Document>();   // already exists somewhere; reuse if so
  let packageGraphBuilder: PackageGraphBuilder | null = null;

  function rebuildPackageGraph(): void {
    packageGraphBuilder = new PackageGraphBuilder(projectSymbols, documents);
  }
  ```

  > If a `Map<string, Document>` doesn't already exist, the cheapest path is to track it alongside the existing `projectSymbols.upsertDocument` calls — set it on upsert, delete on remove.

- [ ] **6.2 — Call `rebuildPackageGraph()` after each `upsertDocument` and `removeDocument`.**

  Find each call site of `projectSymbols.upsertDocument` and `projectSymbols.removeDocument` in `server.ts`. Add a `rebuildPackageGraph()` call right after each. There are at least two: the user-document path (around line 228) and the stock-vocab loader (around line 268).

- [ ] **6.3 — Sanity check.**

  ```bash
  pnpm -r typecheck
  pnpm --filter @modeler/integration-tests test
  ```

  Still 29/29 (Block 1 should already have made this true). No new test needed — C2 will exercise the builder via `modeler/getPackageGraph`. For now we're just making sure the cache exists.

---

## Block 7 — Cleanup: dead cnc branch + qname centralization (Finding 7)

- [ ] **7.1 — Decide whether the legacy `cnc.role.*` branch in the resolver is a feature or dead code.**

  Check whether any non-stock file could legitimately produce a `cnc.role.X` qname. Under B2's symbol-table logic:

  - Stock file (`stock://…`, `schema cnc namespace role`) → `cnc.cnc.role.X` (doubled).
  - User file (`schema cnc namespace role` but not under `stock://`) → `cnc.role.X` (non-doubled — namespace explicit, no stock-cnc rule).

  So the legacy branch *can* legitimately fire for user-written `schema cnc namespace role` files. Decide whether v1.1 supports that or not.

  - **If supported:** add a test in `resolver.test.ts` that exercises a user file with `schema cnc namespace role` and asserts `viaStep === 'auto-import'` with qname `cnc.role.X`. Leave the legacy branches in.
  - **If unsupported:** delete `resolver.ts:139–144` and `resolver.ts:186–191`, add a comment at the remaining `cncQname` block: `// Only the stock-cnc form is auto-imported; user-defined cnc schemas must use explicit imports.`

  Either path is defensible; just pick and document.

- [ ] **7.2 — (Optional, follow-up commit) Centralize qname construction.**

  After Block 1 lands, the qname-building logic exists in three places: `DocumentSymbolTable.makeQname`, `enclosingQnameOf` in `reference-index.ts`, and (likely) `lsp/server.ts`. Extract to a shared helper:

  ```ts
  // packages/semantics/src/qname-builder.ts (new)
  export function buildDefQname(opts: {
    packageName: string;
    schemaCode: string;
    namespace: string;
    defKind: string;
    defName: string;
    uri: string;     // for stock-cnc detection
  }): string { /* … */ }
  ```

  and rewrite `makeQname` and `enclosingQnameOf` as one-liners. This isn't a blocker but it's the right place to land before B4 starts emitting new diagnostics that may also need qname construction.

---

## Block 8 — Final verification

- [ ] **8.1 — Semantics suite.**

  ```bash
  pnpm --filter @modeler/semantics test
  ```

  Expect ≥75 tests passing (71 today + 3 package-graph cases + Block 4's two cases). No failures.

- [ ] **8.2 — Integration suite.**

  ```bash
  pnpm --filter @modeler/integration-tests test
  ```

  29/29. The two long-standing failures are gone after Block 1.

- [ ] **8.3 — Workspace.**

  ```bash
  pnpm -r typecheck
  pnpm -r build
  pnpm -r test
  ```

  All exit 0.

- [ ] **8.4 — Flip `STATUS.md`.**

  Change `[ ] B3 resover` to `[x] B3 resolver` (typo fix included).

- [ ] **8.5 — Commit.**

  Suggested message:

  ```
  Section B3: resolver chain + PackageGraph (review-028 follow-ups)

  - Six-step resolveReference: lexical → same-package → named-import →
    wildcard-import → auto-import → fully-qualified per contracts §4.
  - PackageGraphBuilder with Tarjan SCC; getDependents now correctly
    walks inbound edges.
  - reference-index enclosingQnameOf rewritten to v1.1 kind-fallback +
    stock-cnc doubling, fixing two integration regressions from B2.
  - references.ts now recurses into list/object values so join: [{from,
    to}] inner ids are tracked.
  - LSP server caches a PackageGraphBuilder; C2 will wire the
    modeler/getPackageGraph method.
  - package-graph.test.ts adds three cases (transitive, cycle,
    self-import).
  - resolver-v1.1.test.ts step-6 case rewritten to actually exercise
    step 6; bare-unique acceptance enabled per contracts §4.1.
  ```

---

## Out of scope

- B4 diagnostics (`ttr/unimported-reference`, `ttr/circular-package-dependency`, etc.). The resolver now exposes the data B4 needs; emitting is B4's job.
- `modeler/getPackageGraph` LSP method — C2.
- `.ttrg` parsing / file-extension awareness — C1.
- Migration CLI (`pnpm exec modeler migrate-to-packages`) — F.

If you find yourself editing `packages/semantics/src/validator.ts` for new diagnostic codes, stop — you've slipped into B4.
