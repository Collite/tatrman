# Review 027 — Section B2 (Package-aware symbol table)

**Branch:** `v1-1` (commits up through `63bdb61` + uncommitted working-tree changes for B1 and B2).
**Scope reviewed:** the symbol-table extension described in [`docs/v1-1/plan/tasks/B2-symbol-table.md`](../plan/tasks/B2-symbol-table.md) and the type/qname contract in [`docs/v1-1/design/v1-1-contracts.md §3`](../design/v1-1-contracts.md#3-symbol-table-changes).
**Files in scope:**

- `packages/semantics/src/symbol-table.ts` (modified — `SymbolEntry` gains `packageName` + `schemaCode`; `DocumentSymbolTable` reads `ast.packageDecl?.name`; new `makeQname` / `makeQnameChild`)
- `packages/semantics/src/project-symbols.ts` (modified — `getByPackage` / `getBySuffix` / `listPackages` added)
- `packages/semantics/src/__tests__/symbol-table-v1.1.test.ts` (new, 11 cases — 10 pass, 1 fails)

**Verification runs:**

| Command                                       | Result               | Notes |
| --------------------------------------------- | -------------------- | ----- |
| `pnpm --filter @modeler/semantics test`       | ❌ **5 failing**      | 1 in the new B2.3 stock-cnc case; 4 in the v1-era `symbol-table.test.ts`. |
| `pnpm --filter @modeler/parser test`          | ✅ 57/57             | B1 work still green. |
| `pnpm --filter @modeler/integration-tests test` | ✅ 29/29             |       |
| `pnpm -r typecheck`                           | ✅                   |       |

The 5 failures break down into one real bug and four task-spec migrations the developer flagged as pending. See findings 1 and 2.

---

## Verdict

**B2 is not ready** to flip to `[x]`. The developer's note is honest about the state — 56/61 tests pass and the remaining 5 are known. But two things have to land before the section can close:

1. **(real bug)** Stock-cnc doubling is half-implemented. It fires for child symbols (attributes/columns) via the `isStockCncRoleChild` branch in `makeQnameChild` but not for the role's own qname in `makeQname`. Contracts §3.1 and task B2.3 require the doubled form uniformly. The new B2.3 test asserts `cnc.cnc.role.fact` against the role itself, and it fails.
2. **(spec migration)** Four v1-era tests in `symbol-table.test.ts` assert literal-v1 qname shapes (`db.users`, `cnc.orders`) that the v1.1 rule deliberately replaces with `db.table.users` / `cnc.entity.orders`. Task B2.7 calls this out as a required step ("Migrate test fixtures to use the v1.1 shape where the test is exercising v1.1 behaviour"). The developer skipped B2.7.

Two minor follow-ups also need attention:

3. **(contract hygiene)** Contracts §3.1 contains an internal contradiction: the formula `<schema>.<namespace-or-kind>.<defName>` mandates the kind-fallback, but the parenthetical "(v1 shape, unchanged)" claims it matches v1, which it doesn't (v1 produced `er.artikl` for an unpackaged, no-namespace entity, not `er.entity.artikl`). The B2 test-spec and the developer's implementation both follow the formula; the parenthetical is wrong. Amend the contracts.
4. **(missing TODO)** Task B2 Done-when says "a TODO comment in `symbol-table.ts` flags [the doubled-cnc form] for revisit when the conceptual model lands." No such comment is present.

Everything else is in order. The three new accessors on `ProjectSymbolTable` (`getByPackage` / `getBySuffix` / `listPackages`) match contracts §3 byte-for-byte, are covered by passing tests, and have the right semantics around the empty-string default-package sentinel.

---

## Findings

### 1. (Blocker — real bug) Stock-cnc doubling fires only for child symbols

`packages/semantics/src/symbol-table.ts:32–60` defines two qname builders:

- `makeQname(parts, namespaceOrKind)` — used at line 64 for every top-level definition (the entity, the table, the role, etc.).
- `makeQnameChild(parentEntry, childName)` — used at lines 82 / 99 / 116 / 133 for attributes, columns, result columns.

`makeQname` has no stock-cnc branch. `makeQnameChild` has one:

```ts
const isStockCncRoleChild =
  this.schemaCode === 'cnc' && !this.packageName && this.namespace === 'role';

if (isStockCncRoleChild) {
  segments.push(this.schemaCode);  // second 'cnc' segment
  segments.push(this.namespace);   // 'role'
}
```

Result for the stock cnc roles file (`schema cnc namespace role; def role fact { ... }`):

| Symbol                | Produced qname            | Required (contracts §3.1)        |
| --------------------- | ------------------------- | -------------------------------- |
| `def role fact`       | `cnc.role.fact`           | **`cnc.cnc.role.fact`** ← doubled |
| `attribute on fact`   | `cnc.cnc.role.fact.<x>`   | `cnc.cnc.role.fact.<x>`          |

The new test at `symbol-table-v1.1.test.ts:76–84` does `table.get('cnc.cnc.role.fact')` against the role itself and gets `undefined`. That's the failure.

**Suggested fix.** Drop the schema/namespace heuristic and detect stock files by URI prefix, then apply the doubling uniformly in both builders. URI-based detection is also more robust against user files that happen to write `schema cnc namespace role`:

```ts
private get isStockCnc(): boolean {
  return this.schemaCode === 'cnc'
      && !this.packageName
      && this.documentUri.startsWith('stock://');
}

private makeQname(parts: string[], namespaceOrKind: string): string {
  const segments: string[] = [];
  if (this.packageName) segments.push(this.packageName);
  if (this.isStockCnc)  segments.push('cnc');   // implicit stock-cnc package
  segments.push(this.schemaCode);
  if (namespaceOrKind) segments.push(namespaceOrKind);
  segments.push(...parts);
  return segments.join('.');
}

private makeQnameChild(parentEntry: SymbolEntry, childName: string): string {
  // … same prefix logic, then push namespace (or kind), parent name, childName.
}
```

This change also means the existing `symbol-table.test.ts` "handles stock:// prefixed URIs without conflict" test (line 116) — which currently asserts `cnc.role.fact` for the role — needs its expectation updated to `cnc.cnc.role.fact`. The fixture URI is already `stock://cnc-roles.ttr`, so the URI prefix check fires naturally. The same test's `cnc.orders` lookup (line 120, the *user* file, not the stock one) is finding 4: see Finding 2.

### 2. (Blocker — task spec) B2.7 not done; four v1-era tests still assert v1 qname shapes

Task B2.7 says:

> Tests that asserted `qname === 'er.entity.artikl'` for files that will get a package now need to either (a) declare a package in the fixture, or (b) accept the empty-package qname. Migrate test fixtures to use the v1.1 shape where the test is exercising v1.1 behaviour; leave v1-shape fixtures alone (they continue to test the default-package path).

The developer's note acknowledges this. Four tests in `symbol-table.test.ts` still assert literal-v1 qname shapes that the v1.1 kind-fallback rule replaces:

| Test                                                                 | Current expectation | v1.1 expectation         |
| -------------------------------------------------------------------- | ------------------- | ------------------------ |
| `conflict detection > detects duplicate qname entries in project`   | `db.users`          | `db.table.users`         |
| `stock vocabulary loading > handles stock:// prefixed URIs without conflict` (user-file `cnc.orders` lookup) | `cnc.orders`        | `cnc.entity.orders`      |
| `ProjectSymbolTable > upsertDocument replaces existing entries for same URI` | `db.users`          | `db.table.users`         |
| `ProjectSymbolTable > duplicates returns qnames with multiple entries` | `db.users`          | `db.table.users`         |

All four fixtures declare `schema db` or `schema cnc` with no namespace clause. Under v1, namespace defaulted to `''` and the qname became `db.users`. Under v1.1's formula (contracts §3.1), `<namespace-or-kind>` falls back to the def's kind, producing `db.table.users`. The new B2.3 test (`symbol-table-v1.1.test.ts:55–62`) asserts exactly this behavior for an `er entity artikl` fixture: `qname === 'er.entity.artikl'`, labelled "v1 shape" (see Finding 3 for why that label is misleading).

**Action.** Update the four assertions to the v1.1 shape. Do not change the fixtures themselves — the no-namespace path is exactly what the tests are exercising, and it remains a valid configuration in v1.1. Just rename the expected qname.

### 3. (Contract hygiene) §3.1 contradicts itself; the formula wins, parenthetical is wrong

`docs/v1-1/design/v1-1-contracts.md` §3.1 (lines 226–231):

```
For each `Definition` in a `Document` with `packageDecl?.name = P`, the qname is:

- If `P === ""` (default package): `<schema>.<namespace-or-kind>.<defName>[.<subDef>]` (v1 shape, unchanged)
- If `P !== ""`: `P.<schema>.<namespace-or-kind>.<defName>[.<subDef>]`
```

The parenthetical "(v1 shape, unchanged)" is factually wrong. v1's `packages/semantics/src/qname.ts` `qnameToString` skipped the namespace segment when it was empty:

```ts
export function qnameToString(q: Qname): string {
  const segments: string[] = [q.schemaCode];
  if (q.namespace) segments.push(q.namespace);   // ← no kind fallback
  segments.push(...q.parts);
  return segments.join('.');
}
```

For `schema db; def table users {}` v1 produced `db.users`, not `db.table.users`. The v1.1 rule (with `<namespace-or-kind>`) is a deliberate change, not a preservation.

The B2 task spec is unambiguous about the new behavior — it gives `er.entity.artikl` as the expected output for an unpackaged no-namespace entity (B2 Tests-first, second bullet) — so the formula wins. The parenthetical should be deleted or rephrased.

**Suggested amendment.** In contracts §3.1, replace:

```
- If `P === ""` (default package): `<schema>.<namespace-or-kind>.<defName>[.<subDef>]` (v1 shape, unchanged)
```

with:

```
- If `P === ""` (default package): `<schema>.<namespace-or-kind>.<defName>[.<subDef>]`
```

and append a paragraph below the bullet list:

> **Note on v1 behavior.** This rule changes v1's qname shape for files without a `namespace` clause: v1 produced `<schema>.<defName>` (e.g. `db.users`), v1.1 produces `<schema>.<kind>.<defName>` (e.g. `db.table.users`). Files that declared an explicit `namespace` are unaffected. Existing v1 tests asserting bare-schema qnames need updating; the migration tool (1.1.F) writes namespace clauses where they were absent, but pre-migration files still parse and resolve under the new rule.

Add the corresponding `v4, <today>` entry to the §12 changelog.

### 4. (Hygiene) Missing TODO for the doubled-cnc transitional shape

B2 Done-when (line 53 of the task spec):

> Stock cnc qnames are `cnc.cnc.role.*` (doubled) — this is the v1.1 transitional shape per open-question #10; a TODO comment in `symbol-table.ts` flags it for revisit when the conceptual model lands.

There is no `TODO` in `symbol-table.ts`. Add one above the stock-cnc detection in `makeQname` once Finding 1 is fixed:

```ts
// TODO(post-v1.1): the doubled `cnc.cnc.role.*` shape is a transitional
// accommodation per v1-1-contracts §3.1 (open-question #10). Revisit when the
// conceptual-model layer lands and we can model stock cnc as an actual package.
```

### 5. (Hygiene) `STATUS.md` was not flipped

Same as B1's review-026 #1: `STATUS.md` still shows `[ ] B2 symbol table`. The developer's working tree is mid-work, which is fine, but per project convention should be marked `under review` at minimum. Flip to `[x]` only after Findings 1 and 2 are closed.

### 6. (Informational, non-deviation) New accessors match contracts §3 byte-for-byte

For the record:

| Method                | Contract §3 lines | `project-symbols.ts` lines | Behavior                                                                    |
| --------------------- | ----------------- | -------------------------- | --------------------------------------------------------------------------- |
| `getByPackage(name)`  | 215               | 81–89                      | Linear scan, returns matching entries.                                       |
| `getBySuffix(suffix)` | 218               | 91–99                      | Returns entries where `qname.endsWith('.' + suffix)` or `qname === suffix`. |
| `listPackages()`      | 221               | 101–107                    | Distinct sorted package names, including `''` sentinel for default-package. |

All three are tested in `symbol-table-v1.1.test.ts`. The empty-string default-package sentinel is explicit (the B2.6 test asserts `['']` for a project with no packages).

The `getBySuffix` semantics deserve one caveat: it does a literal suffix match on the dotted form, which means `getBySuffix('users')` would also match `db.table.users` *and* `db.table.permissions.users`. The contracts don't currently address this — they just say "set of symbols whose qname ends with a given suffix" — but B3's resolver step-6 will want the stricter interpretation (suffix is a dot-separated path, not a substring). Worth a note in the contracts when B3 lands; not actionable here.

### 7. (Informational, non-deviation) `SymbolEntry` extension matches contracts §3

`SymbolEntry` now has `packageName: string` and `schemaCode: string`, both required and defaulting to `''` when absent. Every code path that constructs a `SymbolEntry` populates them (verified by reading the file end-to-end). Contracts §3 line 200 — match.

### 8. (Informational) Resolver tests still pass — but they exercise namespace-explicit fixtures only

`packages/semantics/src/resolver.ts:41` builds lookup qnames as `${schemaCode}.${namespace}.${localName}`. When `namespace === ''` this produces double-dots (`er..artikl`), which never matches the new kind-fallback qnames (`er.entity.artikl`). All 6 resolver tests still pass, but that's because their fixtures supply explicit namespaces. The first test to use a no-namespace fixture against the resolver will fail.

This is B3's problem to fix (and the task spec explicitly says "No resolver changes yet — that's B3" — Done-when, line 54), but flag it now so B3 doesn't get blindsided.

---

## What "done" looks like after the follow-ups

B2 is fully closed when:

1. The stock-cnc doubling fires for the role itself (Finding 1). The new B2.3 test for `cnc.cnc.role.fact` passes; the existing `stock vocabulary loading > handles stock:// prefixed URIs without conflict` test is updated to expect `cnc.cnc.role.fact` instead of `cnc.role.fact`.
2. The four v1-era assertions in `symbol-table.test.ts` are updated to the v1.1 kind-fallback shape (Finding 2). `pnpm --filter @modeler/semantics test` reports 61/61.
3. Contracts §3.1 is amended to delete the misleading "(v1 shape, unchanged)" parenthetical and a §12 changelog entry is added (Finding 3).
4. The `TODO(post-v1.1)` comment lands in `symbol-table.ts` (Finding 4).
5. `STATUS.md` flipped to `[x] B2 symbol table` (Finding 5).

B3 unblocks at the same moment.
