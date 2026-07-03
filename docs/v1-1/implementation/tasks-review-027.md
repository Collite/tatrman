# Tasks — Review 027 (Section B2 follow-ups)

Companion task list to [`review-027.md`](review-027.md). Five small blocks; the whole list is under a day's work. After this, B2 closes and B3 unblocks.

Right now: 56/61 semantics tests pass. The remaining five all stem from two root causes (Finding 1 — stock cnc doubling is half-implemented; Finding 2 — four v1-era tests still assert v1 qname shapes).

---

## Block 1 — Apply stock-cnc doubling uniformly (Finding 1)

The current heuristic in `makeQnameChild` (`isStockCncRoleChild`) only fires for children. The role itself goes through `makeQname` and skips the doubling, so `def role fact` ends up as `cnc.role.fact` instead of `cnc.cnc.role.fact`. Two problems with the current heuristic:

- it doesn't fire for the top-level def (this is the bug),
- it would also fire for *user* files that happen to declare `schema cnc namespace role` (false positive).

Fix both by detecting stock-ness at the document level via the URI.

- [ ] **1.1 — Add an `isStockCnc` helper to `DocumentSymbolTable`.**

  In `packages/semantics/src/symbol-table.ts`, near the top of the class (right after the existing private fields), add:

  ```ts
  private get isStockCnc(): boolean {
    return this.schemaCode === 'cnc'
        && !this.packageName
        && this.documentUri.startsWith('stock://');
  }
  ```

  This is the single source of truth for "is this file the stock cnc vocabulary." URI-based detection avoids false positives on user files that legitimately write `schema cnc namespace role`.

- [ ] **1.2 — Apply the doubling in `makeQname`.**

  Replace the existing `makeQname` (`packages/semantics/src/symbol-table.ts:32–39`) with:

  ```ts
  private makeQname(parts: string[], namespaceOrKind: string): string {
    // TODO(post-v1.1): the doubled `cnc.cnc.<ns-or-kind>.*` shape is a
    // transitional accommodation per v1-1-contracts §3.1 (open-question #10).
    // Revisit when the conceptual-model layer lands and we can model stock
    // cnc as an actual package.
    const segments: string[] = [];
    if (this.packageName) segments.push(this.packageName);
    if (this.isStockCnc)  segments.push('cnc');   // implicit stock-cnc package prefix
    segments.push(this.schemaCode);
    if (namespaceOrKind) segments.push(namespaceOrKind);
    segments.push(...parts);
    return segments.join('.');
  }
  ```

  Note the embedded TODO satisfies Finding 4 / Block 4 below — no need for a separate comment elsewhere.

- [ ] **1.3 — Apply the doubling in `makeQnameChild`. Remove the old `isStockCncRoleChild` branch.**

  Replace the existing `makeQnameChild` (`packages/semantics/src/symbol-table.ts:41–60`) with:

  ```ts
  private makeQnameChild(parentEntry: SymbolEntry, childName: string): string {
    const segments: string[] = [];
    if (this.packageName) segments.push(this.packageName);
    if (this.isStockCnc)  segments.push('cnc');   // implicit stock-cnc package prefix
    segments.push(this.schemaCode);
    if (this.namespace) {
      segments.push(this.namespace);
    } else {
      segments.push(parentEntry.kind);
    }
    segments.push(parentEntry.name, childName);
    return segments.join('.');
  }
  ```

  This deletes the old `isStockCncRoleChild` block. The new helper does the same job for both builders.

- [ ] **1.4 — Run the new B2 test in isolation.**

  ```bash
  pnpm --filter @modeler/semantics test -- symbol-table-v1.1
  ```

  All 11 cases must pass — including the stock-cnc `cnc.cnc.role.fact` case that was the only failure in this file.

---

## Block 2 — Update the four v1-era test assertions (Finding 2; finishes B2.7)

These tests exercise the no-namespace path under v1.1's kind-fallback rule. The *fixtures* stay; only the expected qnames change.

- [ ] **2.1 — Update `symbol-table.test.ts` "conflict detection > detects duplicate qname entries in project".**

  At `packages/semantics/src/__tests__/symbol-table.test.ts:102`, change:

  ```ts
  expect(dups.some((d) => d.qname === 'db.users')).toBe(true);
  ```

  to:

  ```ts
  expect(dups.some((d) => d.qname === 'db.table.users')).toBe(true);
  ```

- [ ] **2.2 — Update "stock vocabulary loading > handles stock:// prefixed URIs without conflict".**

  Two changes in the same test, around `symbol-table.test.ts:116–122`:

  - Line 116 `projectSymbols.get('cnc.role.fact')` — change to `projectSymbols.get('cnc.cnc.role.fact')`. This is the stock-cnc file; after Block 1 the doubled form is now correct.
  - Line 120 `projectSymbols.get('cnc.orders')` — change to `projectSymbols.get('cnc.entity.orders')`. This is the *user* file (`schema cnc; def entity orders`) and the v1.1 kind-fallback rule produces `cnc.entity.orders` (not doubled — the user file isn't `stock://`).

  Final shape:

  ```ts
  const factEntry = projectSymbols.get('cnc.cnc.role.fact');
  expect(factEntry).toBeDefined();
  expect(factEntry?.documentUri).toBe('stock://cnc-roles.ttr');

  const orderEntry = projectSymbols.get('cnc.entity.orders');
  expect(orderEntry).toBeDefined();
  expect(orderEntry?.documentUri).toBe('file:///project.ttr');
  ```

- [ ] **2.3 — Update "ProjectSymbolTable > upsertDocument replaces existing entries for same URI".**

  At `symbol-table.test.ts:201`, change:

  ```ts
  const userTable = projectSymbols.get('db.users');
  ```

  to:

  ```ts
  const userTable = projectSymbols.get('db.table.users');
  ```

- [ ] **2.4 — Update "ProjectSymbolTable > duplicates returns qnames with multiple entries".**

  At `symbol-table.test.ts:262`, change:

  ```ts
  expect(dups.some((d) => d.qname === 'db.users')).toBe(true);
  ```

  to:

  ```ts
  expect(dups.some((d) => d.qname === 'db.table.users')).toBe(true);
  ```

- [ ] **2.5 — Run the full semantics suite. Expect 61/61.**

  ```bash
  pnpm --filter @modeler/semantics test
  ```

  If anything *else* now fails, stop and re-read — Block 2 should be the only one updating expectations.

---

## Block 3 — Amend contracts §3.1 (Finding 3)

The "(v1 shape, unchanged)" parenthetical contradicts the rule it parenthesizes. Delete it and add a clarifying paragraph.

- [ ] **3.1 — Edit `docs/v1-1/design/v1-1-contracts.md` §3.1.**

  Around line 228 (the first bullet of the qname construction rule), change:

  ```
  - If `P === ""` (default package): `<schema>.<namespace-or-kind>.<defName>[.<subDef>]` (v1 shape, unchanged)
  ```

  to:

  ```
  - If `P === ""` (default package): `<schema>.<namespace-or-kind>.<defName>[.<subDef>]`
  ```

- [ ] **3.2 — Append a clarifying note below the bullet list, before the "For the stock `cnc` package…" paragraph.**

  ```markdown
  **Note on v1 behavior.** This rule changes v1's qname shape for files without
  a `namespace` clause: v1 produced `<schema>.<defName>` (e.g. `db.users`), v1.1
  produces `<schema>.<kind>.<defName>` (e.g. `db.table.users`). Files that
  declared an explicit `namespace` are unaffected. The migration tool (1.1.F)
  writes namespace clauses where they were absent, but pre-migration files still
  parse and resolve under the new rule.
  ```

- [ ] **3.3 — Add a §12 changelog entry.**

  Prepend to the changelog list:

  ```markdown
  - **v4, 2026-05-19** — clarified §3.1: removed the "(v1 shape, unchanged)" parenthetical, which was inaccurate. v1.1's qname construction always uses the kind as namespace fallback when no `namespace` clause is present; this changes the shape for unpackaged, no-namespace files (e.g. `db.users` → `db.table.users`). Stock-cnc doubling rule unchanged.
  ```

---

## Block 4 — Add the missing TODO comment (Finding 4)

Already inlined in Block 1.2 if you applied that change verbatim. If you wrote the body of `makeQname` differently, double-check the `// TODO(post-v1.1): …` comment landed somewhere readable in `symbol-table.ts`. The Done-when criterion in task B2 specifically requires this comment.

- [ ] **4.1 — Verify the TODO is present.**

  ```bash
  grep -n "TODO(post-v1.1)" packages/semantics/src/symbol-table.ts
  ```

  Expect at least one match. If none, paste the comment from Block 1.2 into `makeQname`.

---

## Block 5 — Final verification

- [ ] **5.1 — Semantics suite.**

  ```bash
  pnpm --filter @modeler/semantics test
  ```

  61/61, no failures.

- [ ] **5.2 — Whole workspace.**

  ```bash
  pnpm -r typecheck
  pnpm -r build
  pnpm -r test
  ```

  All four exit 0. (Watch for downstream parser/integration regressions — none expected, since B2 only changes the *shape* of qnames produced by `DocumentSymbolTable`, and the `model-graph.test.ts` cases referenced earlier go through entity references with explicit namespaces.)

- [ ] **5.3 — Integration tests.**

  ```bash
  pnpm --filter @modeler/integration-tests test
  ```

  29/29.

- [ ] **5.4 — Flip `STATUS.md`.**

  Change `[ ] B2 symbol table` to `[x] B2 symbol table`. Commit per the section's commit convention (suggested message):

  ```
  Section B2: package-aware symbol table (review-027 follow-ups)

  - SymbolEntry gains packageName and schemaCode (both required, '' default).
  - DocumentSymbolTable reads ast.packageDecl?.name; qnames are
    [package.][stock-cnc.]<schema>.<ns-or-kind>.<defName>[.<subDef>] per
    contracts §3.1.
  - Stock-cnc doubling now URI-driven (documentUri.startsWith('stock://'))
    and applied uniformly to both top-level defs and their children.
  - ProjectSymbolTable.getByPackage / getBySuffix / listPackages added.
  - Four v1-era test assertions updated to the v1.1 kind-fallback shape
    (B2.7).
  - Contracts §3.1 clarified; changelog v4.
  ```

---

## Out of scope

- Resolver six-step chain (the `lexical → same-package → named-import → wildcard-import → auto-import → fully-qualified` order) — B3. Note: resolver.ts:41 still builds lookups as `${schemaCode}.${namespace}.${localName}`, which double-dots for empty namespace and will silently mismatch the new kind-fallback shape; B3 must rewrite this.
- Validator diagnostics for unimported references, unused imports, circular packages, etc. — B4.
- `.ttrg` parsing / file-extension awareness — C1.
- `getBySuffix` strict "dot-separated path" semantics — flag for the B3 contract amendment when the resolver wires it up; current loose suffix match is sufficient for the B2 accessors.

If you find yourself editing `packages/semantics/src/resolver.ts` or `packages/semantics/src/validator.ts`, you've drifted out of B2's scope.
