# TTR Diagnostic Codes

**Status:** v1.1, 2026-05-16

This document lists every diagnostic code the TTR LSP can emit, organized by tier.

---

## Foundation Tier (Phase 1)

These diagnostics are produced by the parser layer (`@modeler/parser`) and propagated by the LSP (`@modeler/lsp`).

| Code | Severity | Trigger | Fix |
|------|----------|---------|-----|
| `ttr/parse-error` | Error | ANTLR syntax error — missing token, unexpected token, malformed input | Correct the syntactic structure |
| `ttr/parse-recovery-info` | Information | ANTLR error strategy recovered at a token — parser resynchronized and produced a partial AST | The input had a syntax error; recovery kept parsing to produce a usable result |
| `ttr/unknown-property` | Error | Property name not recognized for the current `def <kind>` context | Use a valid property name; check for typos |

### `ttr/parse-error`

The parser's ANTLR error listener emits this for every syntactic violation. Severity is always `Error`. The LSP maps it to `DiagnosticSeverity::Error`.

Example:
```
def entity foo {
```
The opening `{` has no matching `}` on the same line. The parser's ANTLR error listener emits `ttr/parse-error` at end-of-file.

### `ttr/parse-recovery-info`

When ANTLR encounters a syntax error it cannot fix with single-token insertion/deletion, it enters recovery mode — consuming tokens until it finds one that allows parsing to resume. The parser's `RecoveryReportingStrategy` (a subclass of `DefaultErrorStrategy`) captures each recovery point and emits a `ttr/parse-recovery-info` diagnostic at `Information` severity.

This diagnostic is always accompanied by at least one `ttr/parse-error` from the original syntax violation. The partial AST produced by the walk is still usable — recovery fixtures assert that recovered definitions are still populated.

Example for `def entity {` (missing entity name):
```
def entity {
  description: "Test"
```
ANTLR recovers by synthesizing a placeholder name; one `ttr/parse-error` (unexpected end of input) and one `ttr/parse-recovery-info` ("parser resumed after syntax error at '{'") are both emitted.

### `ttr/unknown-property`

Emitted when a property name is not recognized for the current `def <kind>` context.

Example:
```
def entity foo {
  descriptin: "Test"  # "descriptin" is not a valid entity property
}
```

## Core Tier (Phase 2)

These diagnostics are produced by the semantics layer (`@modeler/semantics`) and are out of scope for Phase 1.

| Code | Severity | Trigger | Fix |
|------|----------|---------|-----|
| `ttr/unresolved-reference` | Warning | A dotted reference (e.g. `er.entity.artikl`) does not resolve against the symbol table | Define the referenced symbol, or correct the qname |
| `ttr/duplicate-definition` | Error | A qname is defined more than once in the same scope | Rename or remove the duplicate |
| `ttr/required-property-missing` | Warning | A `def <kind>` is missing a required property for its kind | Add the required property |
| `ttr/invalid-type` | Error | The value for `type:` is not a valid data type | Use a recognized type name or remove the property |
| `ttr/entity-attribute-not-found` | Error | `nameAttribute` or `codeAttribute` points to an attribute that does not exist on the entity | Add the attribute or correct the path |
| `ttr/primary-key-column-not-found` | Error | `primaryKey` lists a column that does not exist on the table | Add the column or update the primaryKey list |

### `ttr/unresolved-reference`

The resolver (`@modeler/semantics/src/resolver.ts`) emits this when a dotted reference cannot be resolved against the symbol table. This includes stock vocabulary references (e.g. `fact` in `roles: [fact]`) that don't match any loaded stock role.

Example:
```
def entity orders {
  roles: [fact_role]  # "fact_role" is not a known stock role
}
```

### `ttr/duplicate-definition`

The validator's `validateProject()` emits this when the symbol table contains multiple entries with the same fully-qualified name.

Example:
```
# file1.ttr
schema db { def table users { columns: [...] } }
# file2.ttr
schema db { def table users { columns: [...] } }  # duplicate db.users
```

### `ttr/required-property-missing`

Emitted when a definition is missing a required property for its kind:
- `entity` must have at least one `attributes` entry
- `table` must have at least one `columns` entry
- `column` must have a `type` property
- `attribute` must have a `type` property
- When `lint.requireDescriptions: true`, any definition missing `description` emits a warning

### `ttr/invalid-type`

Reserved for Phase 2 type validation. Currently the parser accepts any identifier as a type name; semantic validation of type names against declared schema types is Phase 2 work.

### `ttr/entity-attribute-not-found`

Emitted when `nameAttribute` or `codeAttribute` on an entity does not match any attribute in the entity's `attributes` list.

Example:
```
def entity order {
  nameAttribute: id_order  # "id_order" not in attributes list
  attributes: [
    def attribute id { type: integer }
  ]
}
```

### `ttr/primary-key-column-not-found`

Emitted when `primaryKey` on a table lists a column name that doesn't exist in the table's `columns` list.

Example:
```
def table orders {
  primaryKey: [order_id]  # "order_id" not in columns list
  columns: [
    def column id { type: integer }
  ]
}
```

---

## Severity mapping

The LSP maps parser codes to LSP severities as follows:

| Parser code | LSP severity |
|---|---|
| `ttr/parse-error` | `Error` |
| `ttr/parse-recovery-info` | `Information` |
| `ttr/unknown-property` | `Error` |
| (Phase 2 codes) | |
| `ttr/unresolved-reference` | `Warning` (configurable to `Error` via `[lint].strict`) |
| `ttr/duplicate-definition` | `Error` |
| `ttr/required-property-missing` | `Warning` (error for entity/table/column/attribute without type; warning for missing description) |
| `ttr/invalid-type` | `Error` |
| `ttr/entity-attribute-not-found` | `Error` |
| `ttr/primary-key-column-not-found` | `Error` |
| (v1.1 codes) | |
| `ttr/unimported-reference` | `Information` |
| `ttr/unused-import` | `Warning` |
| `ttr/wildcard-with-no-matches` | `Warning` |
| `ttr/duplicate-import` | `Warning` |
| `ttr/circular-package-dependency` | `Warning` |
| `ttr/package-declaration-mismatch` | `Error` |
| `ttr/missing-package-declaration` | `Information` |
| `ttr/ambiguous-reference` | `Error` |
| `ttr/wrong-file-kind` | `Error` |
| `ttr/graph-object-not-found` | `Warning` |
| `ttr/graph-layout-stale-node` | `Warning` |
| `ttr/graph-objects-empty` | `Warning` |
| `ttr/graph-name-mismatch` | `Warning` |
| `ttr/file-ordering` | `Warning` |

All diagnostics carry `source: "modeler"` in the LSP `Diagnostic` payload. Phase 2 diagnostics may carry additional structured data in `data` fields for quick-fix actions.

---

## v1.1 Diagnostic codes

These codes were added in v1.1 as part of the package-aware resolver work (sub-phase B.4).

### `ttr/unimported-reference` (Information)

A reference resolves via the fully-qualified step (step 6) to a def in a package that is not in the document's import list. The reference is valid, but the user should add an explicit import.

Trigger: Fully-qualified ref like `pkg_b.er.entity.some_rel` where `pkg_b` is not imported.
Fix: Add `import pkg_b.*` or `import pkg_b.er.entity.some_rel`.

### `ttr/unused-import` (Warning)

A named import statement has no matching references in the document.

Trigger: `import pkg_b.entity_x` but `entity_x` is never referenced.
Fix: Remove the import or use the imported symbol.

### `ttr/wildcard-with-no-matches` (Warning)

A wildcard import targets a package that has zero definitions.

Trigger: `import pkg_b.*` but `pkg_b` has no definitions.
Fix: Remove the wildcard import or add definitions to `pkg_b`.

### `ttr/duplicate-import` (Warning)

The same package is imported more than once, or a named import shadows a wildcard import of the same package.

Trigger: `import pkg_b.*` followed by `import pkg_b.some_entity`.
Fix: Remove the duplicate or shadowing import.

### `ttr/circular-package-dependency` (Warning)

A cycle is detected in the package dependency graph via `PackageGraphBuilder.findCycles()`.

Trigger: `pkg_a` imports `pkg_b` and `pkg_b` imports `pkg_a` (directly or transitively).
Fix: Restructure imports to break the cycle.

### `ttr/package-declaration-mismatch` (Error)

The declared `package` doesn't match the file's path under the project root.

Trigger: File at `/project/pkg_a/sub/file.ttr` declares `package wrong.pkg`.
Fix: Change the package declaration to `package pkg_a.sub`.

### `ttr/missing-package-declaration` (Information)

A file in a subdirectory has no `package` declaration.

Trigger: File at `/project/pkg_a/file.ttr` has no `package` keyword.
Fix: Add `package pkg_a` at the top of the file.

### `ttr/ambiguous-reference` (Error)

A bare reference matches defs in 2+ packages via wildcard imports.

Trigger: `nameAttribute: shared_name` resolves to both `pkg_b.er.entity.shared_name` and `pkg_c.er.entity.shared_name`.
Fix: Qualify with the full path: `pkg_b.er.entity.shared_name`.

### `ttr/wrong-file-kind` (Error)

A `.ttr` file contains a `graph { ... }` block, or a `.ttrg` file contains top-level `def` statements.

Trigger: `graph test { schema: er }` in a `.ttr` file.
Fix: Rename to `.ttrg` for graphs; use `.ttr` for definitions.

### `ttr/graph-object-not-found` (Warning)

A `.ttrg` `objects` entry doesn't resolve to any known definition.

Trigger: `objects: [er.entity.nonexistent]` in a `.ttrg` file.
Fix: Define the entity or correct the qname.

### `ttr/graph-layout-stale-node` (Warning)

A `.ttrg` `layout.nodes` key doesn't appear in `objects`.

Trigger: `objects: [er.entity.artikl]` but `layout.nodes."er.entity.other"` is set.
Fix: Add `er.entity.other` to `objects`, or remove the stale layout entry.

### `ttr/graph-objects-empty` (Warning)

A `.ttrg` has `objects: []` — the graph would render nothing.

Trigger: `graph test { schema: er, objects: [] }`.
Fix: Add at least one object to the graph.

### `ttr/graph-name-mismatch` (Warning)

A `.ttrg` filename (sans extension) doesn't match the inner `graph X { ... }` name.

Trigger: File `artikl.ttrg` contains `graph wrong_name { ... }`.
Fix: Rename the graph or the file.

### `ttr/file-ordering` (Warning)

> **Note for v1.1:** The grammar is order-strict. Out-of-order tokens produce `ttr/parse-error`, not `ttr/file-ordering`. This code exists as a placeholder for a future formatter that operates on a permissive AST builder and is not currently emittable on regular v1.1 input.

File elements are out of canonical order: `package` → `imports` → `schema`/`graph` → `definitions`.

Trigger: `schema er` appears before `import pkg_b.*`.
Fix: Reorder the file contents.

---

## Adding a new diagnostic code

1. Add the code to the appropriate tier enum in `@modeler/parser/src/diagnostics.ts`
2. Emit it from the layer that detects the condition (parser for syntax errors, semantics for cross-reference errors)
3. Update this document with the code, severity, trigger, and fix
4. Add a test in the emitting layer asserting the code is set
5. If the code maps to a non-default severity, update `packages/lsp/src/server.ts`'s `publishDiagnostics` mapping