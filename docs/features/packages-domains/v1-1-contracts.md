# v1.1 — Contracts

**Status:** v6, 2026-05-21. Companion to [`docs/v1-1/design/v1.1-packages-and-graphs.md`](v1.1-packages-and-graphs.md), [`docs/v1-1/design/grammar-v1-1-changes.md`](grammar-v1-1-changes.md), and the per-sub-phase mini-task-lists under [`docs/v1-1/plan/tasks/`](../plan/tasks/).

**Audience:** the implementer (junior dev or coding agent) executing v1.1. The contracts here are non-negotiable — every type, every method signature, every grammar token, every diagnostic code is the single source of truth. If a mini-task-list shows a snippet that conflicts with this document, **this document wins**. Open a PR against this file to amend.

**Scope:** every new or modified type, every LSP custom-method request/response shape, the canonical diagnostic-code list, the `.ttrg` grammar shape, the migration CLI surface. Behaviour and rationale live in the design doc; this file is the *shape* only.

> **Implementer note — verifying library APIs.** When a contract or task references an external library (antlr4ng's `DefaultErrorStrategy`, `vscode-languageserver`'s completion APIs, Cytoscape, etc.), do not trust snippets verbatim. Run the `context7` MCP server first:
>
> ```
> mcp__context7__resolve-library-id  { libraryName: "<lib>", query: "<your focused question>" }
> mcp__context7__query-docs          { libraryId: "<id from resolve>", query: "<focused>" }
> ```
>
> Library APIs drift; our contracts do not.

## Index

1. [Grammar tokens and parser rules added](#1-grammar-tokens-and-parser-rules-added)
2. [AST additions](#2-ast-additions)
3. [Symbol-table changes](#3-symbol-table-changes)
4. [Resolver changes](#4-resolver-changes)
5. [PackageGraph module](#5-packagegraph-module)
6. [Diagnostic codes (v1.1 additions)](#6-diagnostic-codes-v11-additions)
7. [`.ttrg` graph file shape](#7-ttrg-graph-file-shape)
8. [LSP custom-method contracts](#8-lsp-custom-method-contracts)
9. [VS Code language-registration shape](#9-vs-code-language-registration-shape)
10. [Migration CLI surface](#10-migration-cli-surface)
11. [Designer state types](#11-designer-state-types)
12. [Changelog](#12-changelog)
13. [Packages & Domains increment (2026-06-19)](#13-packages--domains-increment-2026-06-19)

---

## 1. Grammar tokens and parser rules added

Owned by `packages/grammar/src/TTR.g4`. Grammar version bumped from `1.x.x` to `2.0.0` (major bump per the rules in [`grammar-v1-1-changes.md`](grammar-v1-1-changes.md) §2).

### 1.1 New lexer tokens

| Token name | Lexeme       | Notes                                                         |
| ---------- | ------------ | ------------------------------------------------------------- |
| `PACKAGE`  | `'package'`  | Placed before `IDENT` so keyword wins                         |
| `IMPORT`   | `'import'`   | Same                                                          |
| `GRAPH`    | `'graph'`    | Same                                                          |
| `OBJECTS`  | `'objects'`  | Property keyword inside `graph { ... }` blocks                |
| `LAYOUT`   | `'layout'`   | Property keyword inside `graph { ... }` blocks                |
| `STAR`     | `'*'`        | Wildcard for `import x.y.*`; placed with `DOT` / `COMMA` etc. |

### 1.2 New parser rules

```antlr
document
  : packageDecl? importDecl* (schemaDirective | graphBlock)? definition* EOF
  ;

packageDecl
  : PACKAGE qualifiedName
  ;

importDecl
  : IMPORT qualifiedName (DOT STAR)?
  ;

graphBlock
  : GRAPH id LBRACE (graphProperty (COMMA? graphProperty)* COMMA?)? RBRACE
  ;

graphProperty
  : graphSchemaProperty
  | descriptionProperty
  | tagsProperty
  | graphObjectsProperty
  | graphLayoutProperty
  ;

graphSchemaProperty   : SCHEMA  propSep? schemaCode ;
graphObjectsProperty  : OBJECTS propSep? LBRACK ( id (COMMA id)* )? COMMA? RBRACK ;
graphLayoutProperty   : LAYOUT  propSep? object_ ;

qualifiedName
  : id
  ;
```

### 1.3 `idPart` extension

`idPart` already accepts schema-code and kind keywords as identifier components. Extend to include the five new keywords so they're usable as identifier fragments:

```antlr
idPart
  : IDENT
  | DB | ER | MAP | QUERY | CNC
  | ROLE | ER2CNC_ROLE
  | TABLE | VIEW | COLUMN | INDEX | CONSTRAINT
  | FK | PROCEDURE | ENTITY | ATTRIBUTE | RELATION
  | ER2DB_ENTITY | ER2DB_ATTRIBUTE | ER2DB_RELATION
  | MODEL
  | NAME | LABEL | DIRECTION
  | FROM | TO
  | PACKAGE | IMPORT | GRAPH       // NEW
  | OBJECTS | LAYOUT               // NEW
  ;
```

### 1.4 File ordering (decision from open-question #1)

The canonical file ordering is **Java-style**: `package` → `import`s → `schemaDirective` (or `graphBlock` for `.ttrg`) → definitions.

The grammar is **order-strict** (`packageDecl? importDecl* (schemaDirective | graphBlock)? definition* EOF`). Out-of-order tokens produce `ttr/parse-error`, not `ttr/file-ordering`. `ttr/file-ordering` exists as a placeholder for tooling (e.g. a future formatter) that operates on a permissive AST builder; it is not currently emittable from regular parsing of v1.1 source files.

---

## 2. AST additions

Owned by `packages/parser/src/ast.ts`. All new node types carry `source: SourceLocation` per the v1 invariant.

```ts
export interface PackageDecl {
  kind: 'packageDecl';
  /** Dotted name as written, e.g. "billing.invoicing" */
  name: string;
  parts: string[];
  source: SourceLocation;
}

export interface ImportDecl {
  kind: 'importDecl';
  /** Full qname being imported (without trailing .*), e.g. "billing.products" or "accounting.er.entity.Invoice" */
  target: string;
  targetParts: string[];
  /** True for `import x.y.*`, false for `import x.y.z.Foo` */
  wildcard: boolean;
  source: SourceLocation;
}

export interface GraphBlock {
  kind: 'graphBlock';
  /** Bare name of the graph (file-name sans .ttrg should match this) */
  name: string;
  /** Required: schema kind this graph renders (er / db / ...). */
  schema?: 'db' | 'er' | 'map' | 'query' | 'cnc';
  /** Optional description, same shape as on def entities. */
  description?: string;
  tags?: string[];
  /** Required, non-empty: the qnames this graph contains. */
  objects: string[];
  /** Optional layout block. Shape per §7.3. */
  layout?: GraphLayout;
  source: SourceLocation;
}

export interface GraphLayout {
  viewport?: {
    zoom: number;
    panX: number;
    panY: number;
    displayMode: string; // Validated against the DisplayMode union in §11.2 by the semantics layer.
  };
  nodes: Record<string, { x: number; y: number }>;
  edges: Record<string, { bendPoints?: [number, number][] }>;
}
```

The existing `Document` interface gains three optional fields:

```ts
export interface Document {
  packageDecl?: PackageDecl;          // NEW
  imports: ImportDecl[];              // NEW — always present, may be empty
  schemaDirective?: SchemaDirective;  // existing
  graph?: GraphBlock;                 // NEW — present only for .ttrg files
  definitions: Definition[];          // existing
  source: SourceLocation;             // existing
}
```

A document with both `graph` and non-empty `definitions` is a parse-time recoverable error (`ttr/wrong-file-kind`, Error).

---

## 3. Symbol-table changes

Owned by `packages/semantics/src/symbol-table.ts` and `packages/semantics/src/project-symbols.ts`.

```ts
export interface DocumentSymbolTable {
  /** NEW: declared package name; "" for the default (empty) package. */
  packageName: string;
  /** Existing field; values are PACKAGE-prefixed qnames in v1.1. */
  symbols: Map<string, SymbolEntry>;
  /** Existing field. */
  documentUri: string;
}

export interface SymbolEntry {
  /** Package-prefixed qname, e.g. "billing.invoicing.er.entity.artikl". */
  qname: string;
  /** Bare name within the file, e.g. "artikl". */
  name: string;
  kind: DefinitionKind;
  /** NEW: which package this symbol belongs to. */
  packageName: string;
  /** NEW: which schema (er/db/...) this symbol belongs to. */
  schemaCode: string;
  /** Existing fields. */
  source: SourceLocation;
  ast: Definition;
}

export class ProjectSymbolTable {
  /** Existing: get by full qname. v1.1 qname includes package prefix. */
  get(qname: string): SymbolEntry | undefined;

  /** NEW: get every symbol in a given package. */
  getByPackage(packageName: string): SymbolEntry[];

  /** NEW: get the (possibly multi-match) set of symbols whose qname ends with a given suffix. */
  getBySuffix(suffix: string): SymbolEntry[];

  /** NEW: list every distinct package name seen in the project. */
  listPackages(): string[];
}
```

### 3.1 Qname construction rule

For each `Definition` in a `Document` with `packageDecl?.name = P`, the qname is:

- If `P === ""` (default package): `<schema>.<namespace-or-kind>.<defName>[.<subDef>]`
- If `P !== ""`: `P.<schema>.<namespace-or-kind>.<defName>[.<subDef>]`

**Note on v1 behavior.** This rule changes v1's qname shape for files without
a `namespace` clause: v1 produced `<schema>.<defName>` (e.g. `db.users`), v1.1
produces `<schema>.<kind>.<defName>` (e.g. `db.table.users`). Files that
declared an explicit `namespace` are unaffected. The migration tool (1.1.F)
writes namespace clauses where they were absent, but pre-migration files still
parse and resolve under the new rule.

For the stock `cnc` package per open question #10 resolution (B.3): accept the doubled `cnc.cnc.role.fact` form for v1.1; revisit when the conceptual model lands. Document this in the contracts doc changelog when it ships.

---

## 4. Resolver changes

Owned by `packages/semantics/src/resolver.ts`.

```ts
export type ResolutionStep =
  | 'lexical'
  | 'same-package'
  | 'named-import'
  | 'wildcard-import'
  | 'auto-import'
  | 'fully-qualified';

export interface ResolutionAttempt {
  step: ResolutionStep;
  /** The candidate qname tried at this step. */
  candidate: string;
  /** Why this attempt failed (only present when the step failed). */
  reason?:
    | 'unknown-symbol'
    | 'not-imported'
    | 'wildcard-non-recursive'
    | 'shadowed-by-named-import'
    | 'lexical-scope-empty';
}

export type ResolutionResult =
  | { resolved: true; symbol: SymbolEntry; viaStep: ResolutionStep }
  | { resolved: false; reason: 'not-found' | 'ambiguous'; tried: ResolutionAttempt[]; candidates?: SymbolEntry[] };
```

The resolver's six-step chain runs in this order (per [`v1.1-packages-and-graphs.md`](v1.1-packages-and-graphs.md) §4.2):

1. `lexical` — attribute-within-entity, column-within-table, role-within-stock (unchanged from v1)
2. `same-package` — any def in the same package as the reference site
3. `named-import` — exact-match qname imports
4. `wildcard-import` — top-level defs in any wildcard-imported package (non-recursive)
5. `auto-import` — the `cnc` package
6. `fully-qualified` — match against the full `ProjectSymbolTable.get(qname)` lookup

Step 4 ambiguity: if ≥2 wildcard imports both expose a def with the same bare name, return `{ resolved: false, reason: 'ambiguous', candidates: [...] }` and the validator emits `ttr/ambiguous-reference`.

### 4.1 Fully-qualified-but-unique relaxation (decision from open-question #4)

When a bare reference is unique across the whole project (no ambiguity), the resolver allows resolution via step 6 even if the symbol's package is not imported. This is the "relax FQN if unique" decision. Practical effect: in small projects the user often won't have to write `import`s at all. The validator still emits `ttr/unimported-reference` at **Info** severity (downgraded from Error in the original draft) to nudge toward explicit imports in larger projects.

---

## 5. PackageGraph module

New file: `packages/semantics/src/package-graph.ts`.

```ts
export interface PackageNode {
  name: string;                     // "billing.invoicing"
  documentUris: string[];           // every .ttr/.ttrg file in this package
}

export interface PackageEdge {
  from: string;                     // dependent package
  to: string;                       // dependency package
  /** Documents in `from` that have at least one import targeting `to`. */
  citedBy: string[];
}

export interface PackageGraph {
  nodes: PackageNode[];
  edges: PackageEdge[];
}

export class PackageGraphBuilder {
  constructor(private projectSymbols: ProjectSymbolTable, private documents: Map<string, Document>) {}

  build(): PackageGraph;

  /** Returns SCCs of size ≥2 (i.e. cycles). Used by `ttr/circular-package-dependency`. */
  findCycles(): string[][];

  /** Returns packages that transitively depend on `pkg`. */
  getDependents(pkg: string): string[];

  /** Returns packages `pkg` transitively depends on. */
  getDependencies(pkg: string): string[];
}
```

---

## 6. Diagnostic codes (v1.1 additions)

All codes follow the existing `ttr/*` taxonomy and `source: 'modeler'` convention (`docs/v1/design/diagnostics.md`). Emitted by the validator unless noted.

| Code                                  | Severity   | Emitter   | Trigger                                                                                  |
| ------------------------------------- | ---------- | --------- | ---------------------------------------------------------------------------------------- |
| `ttr/unimported-reference`            | Info       | validator | Bare reference to a def in a non-imported package; resolved via step-6 fallback           |
| `ttr/unused-import`                   | Warning    | validator | An `import` statement whose targets are never referenced                                  |
| `ttr/wildcard-with-no-matches`        | Warning    | validator | `import x.y.*` where `x.y` has no defs                                                    |
| `ttr/duplicate-import`                | Warning    | validator | Same package imported twice, or named import shadows wildcard                             |
| `ttr/circular-package-dependency`     | Warning    | validator | Package A imports B (transitively); detected via `PackageGraphBuilder.findCycles()`       |
| `ttr/package-declaration-mismatch`    | Error      | validator | Declared `package X.Y` doesn't match the file's directory under the project root          |
| `ttr/missing-package-declaration`     | Info       | validator | File is in the default (empty) package (no `package` keyword)                             |
| `ttr/ambiguous-reference`             | Error      | validator | Bare reference matches defs in 2+ wildcard-imported packages                              |
| `ttr/wrong-file-kind`                 | Error      | parser    | `.ttrg` with no `graph` block, or `.ttr` with one                                         |
| `ttr/graph-object-not-found`          | Warning    | validator | `.ttrg` `objects` lists a qname that doesn't resolve                                      |
| `ttr/graph-layout-stale-node`         | Warning    | validator | `.ttrg` `layout.nodes` references a qname not in `objects`                                |
| `ttr/graph-objects-empty`             | Warning    | validator | `.ttrg` `objects` is the empty list — the graph would render nothing                       |
| `ttr/graph-name-mismatch`             | Warning    | validator | `.ttrg` filename (sans extension) doesn't match the inner `graph X { ... }` name           |
| `ttr/file-ordering`                   | Warning    | validator | Imports follow `schemaDirective`/defs, or `packageDecl` follows imports                   |

`ttr/unresolved-reference` (existing) keeps its severity but its `tried[]` now contains `ResolutionAttempt[]` not `string[]`.

---

## 7. `.ttrg` graph file shape

### 7.1 Concrete-syntax example

```
package billing.invoicing.graphs

import billing.invoicing.*
import billing.products.*

graph artikl_overview {
    schema: er,
    description: "Overview of artikl entity and its direct relations",
    tags: ["billing", "core-domain"],

    objects: [
        billing.invoicing.er.entity.artikl,
        billing.products.er.entity.produkt,
        billing.products.er.entity.podprodukt
    ],

    layout: {
        viewport: { zoom: 1.0, panX: 0, panY: 0, displayMode: "with-types" },
        nodes: {
            billing.invoicing.er.entity.artikl:    { x: 320, y: 180 },
            billing.products.er.entity.produkt:    { x: 580, y: 180 },
            billing.products.er.entity.podprodukt: { x: 580, y: 380 }
        },
        edges: {}
    }
}
```

### 7.2 Edge inclusion semantics (open-question #6 resolution)

**Objects are explicit; edges are computed.** A relation/fk edge appears in the graph iff both endpoint qnames are present in the `objects` list. The `objects` list itself does NOT need to enumerate edges. The Designer's `getModelGraph` response computes the edge set on-the-fly from the symbol table + the `objects` set.

### 7.3 Validation rules

- `schema` is required; must be one of `'db' | 'er' | 'map' | 'query' | 'cnc'`
- `objects` is required, non-empty; each entry must resolve (else `ttr/graph-object-not-found`)
- `layout.nodes` keys must be in `objects` (else `ttr/graph-layout-stale-node`)
- Filename (sans extension) must match `graph.name` (else `ttr/graph-name-mismatch`, Warning)

---

## 8. LSP custom-method contracts

All registered in both `server-stdio.ts` and `server-browser.ts`.

### 8.1 `modeler/listGraphs` (NEW)

**Request:**

```ts
interface ListGraphsParams {
  projectRoot: string;   // file URI
}
```

**Response:**

```ts
interface GraphMetadata {
  uri: string;                                  // file URI of the .ttrg
  name: string;                                 // graph name (from `graph X { ... }`)
  schema: 'db' | 'er' | 'map' | 'query' | 'cnc';
  description?: string;
  tags: string[];
  objectCount: number;
  missingObjectCount: number;
}

interface ListGraphsResponse {
  graphs: GraphMetadata[];
}
```

### 8.2 `modeler/getGraph` (NEW)

**Request:**

```ts
interface GetGraphParams {
  uri: string;   // .ttrg file URI
}
```

**Response:** the resolved graph for Designer rendering. Reuses `ModelGraphNode` / `ModelGraphRow` / `ModelGraphEdge` from v1 (`packages/lsp/src/model-graph.ts`).

```ts
interface GetGraphResponse {
  schema: 'db' | 'er' | 'map' | 'query' | 'cnc';
  nodes: ModelGraphNode[];
  edges: ModelGraphEdge[];
  layout: GraphLayout;
  /** qnames listed in `objects` that didn't resolve. */
  missingObjects: string[];
}
```

### 8.3 `modeler/addObjectToGraph` (NEW)

**Request:**

```ts
interface AddObjectToGraphParams {
  uri: string;                  // .ttrg URI
  qname: string;                // fully-qualified
  /** When true, also synthesize an `import` if `qname`'s package isn't already in scope. */
  autoImport: boolean;
}
```

**Response:** standard LSP `WorkspaceEdit`. The Designer applies via `workspace/applyEdit`.

### 8.4 `modeler/removeObjectFromGraph` (NEW)

**Request:**

```ts
interface RemoveObjectFromGraphParams {
  uri: string;
  qname: string;
  /** When true, also remove the corresponding `import` if no other listed object needs it. */
  pruneUnusedImport: boolean;
}
```

**Response:** standard LSP `WorkspaceEdit`.

### 8.5 `modeler/createGraph` (NEW)

**Request:**

```ts
interface CreateGraphParams {
  /** Target file URI; must end in .ttrg; parent directory must exist. */
  uri: string;
  name: string;                                              // graph name
  schema: 'db' | 'er' | 'map' | 'query' | 'cnc';
  packages: string[];                                        // wildcards to import
  objects: string[];                                         // initial set
  description?: string;
  tags?: string[];
}
```

**Response:** standard LSP `WorkspaceEdit` (create-file edit + initial content).

### 8.6 `modeler/getPackageGraph` (NEW)

**Request:**

```ts
interface GetPackageGraphParams {
  projectRoot: string;
}
```

**Response:**

```ts
interface GetPackageGraphResponse {
  packages: PackageNode[];
  dependencies: PackageEdge[];
  cycles: string[][];
}
```

### 8.7 Updated existing methods

| Method                        | Change                                                                                       |
| ----------------------------- | -------------------------------------------------------------------------------------------- |
| `modeler/getModelGraph`       | **Unchanged** — kept as the whole-schema render (decision D3); the new `.ttrg`-scoped render is `modeler/getGraph` (§8.2). Both share the node/edge builders. |
| `modeler/getLayout`           | Takes `graphUri`; reads the `layout` block inside that `.ttrg`. The project-wide `.modeler/layout.ttrl` sidecar is removed (D4). An in-memory `projectRoot`/`layoutStore` fallback is retained only as a host/test seam. |
| `modeler/setLayout`           | Takes `graphUri`; returns a `WorkspaceEdit` that rewrites the `.ttrg`'s `layout` block (unquoted dotted-id node keys, §7.1) — the host applies it. No on-disk sidecar. |
| `modeler/exportLayout`        | Takes `graphUri`; delegates to `getLayout` (reads the `.ttrg` `layout` block). The `.ttrl` sidecar is removed (D4).                                              |
| `modeler/getProjectInfo`      | Response gains `packages: PackageInfo[]`                                                     |

```ts
interface PackageInfo {
  name: string;
  documentUris: string[];
  dependents: string[];      // packages that import this one
  dependencies: string[];    // packages this one imports
}
```

---

## 9. VS Code language-registration shape

Owned by `packages/vscode-ext/package.json`'s `contributes.languages`, `contributes.grammars`, and `contributes.iconThemes` sections.

```jsonc
{
  "contributes": {
    "languages": [
      { "id": "ttr",  "extensions": [".ttr"],  "configuration": "./language-configuration.json" },
      { "id": "ttrg", "extensions": [".ttrg"], "configuration": "./language-configuration.json" }
    ],
    "grammars": [
      { "language": "ttr",  "scopeName": "source.ttr",  "path": "./syntaxes/ttr.tmGrammar.json" },
      { "language": "ttrg", "scopeName": "source.ttrg", "path": "./syntaxes/ttrg.tmGrammar.json" }
    ]
  }
}
```

`ttrg.tmGrammar.json` is generated by extending `ttr.tmGrammar.json` with patterns for `graph`, `objects`, `layout`. The `.ttrl` registration is removed entirely.

---

## 10. Migration CLI surface

New package: `packages/migrate/`. CLI entry: `pnpm exec modeler migrate-to-packages <project-root>`.

```ts
interface MigrateArgs {
  projectRoot: string;
  dryRun: boolean;                  // --dry-run; default false
  commitTtrlRemoval: boolean;       // --commit-ttrl-removal; default false
  verbose: boolean;                 // --verbose; default false
}

interface MigrateReport {
  filesTouched: string[];           // .ttr files modified
  packagesCreated: string[];        // distinct package names inserted
  importsInserted: { uri: string; package: string }[];
  ttrgFilesCreated: string[];       // .ttrg files synthesized from .ttrl
  ttrlRemoved: string | null;       // path of removed .ttrl, or null
  ambiguousReferences: { uri: string; line: number; ref: string; candidates: string[] }[];
}
```

Exit codes: `0` clean, `1` ambiguous references requiring manual fix, `2` IO or parse error.

---

## 11. Designer state types

Owned by the Designer (`packages/designer/src/state/designer-state.ts`); not exposed over the LSP. Consumed by `App.tsx` and every Designer-side component via `useReducer`.

### 11.1 Locked design decision: schema toggle removed

Per the open-question-#1-equivalent for the Designer (settled 2026-05-18): **the schema-toggle UI from v1 is removed in v1.1**. Each `.ttrg` file is locked to a single schema (per design B3), and switching schemas means opening a different `.ttrg`. There is no `activeSchema` field, no per-schema viewports, no `RenderableSchemaCode`-keyed maps in the state. A future v1.x may add a "find the equivalent graph for this scope under schema X" affordance, but it's out of scope for v1.1.

### 11.2 State shape

```ts
import type { GraphMetadata, GetGraphResponse, GraphLayout } from '@modeler/lsp';

export type DisplayMode = 'just-names' | 'with-types' | 'with-constraints';

export interface ViewportState {
  zoom: number;       // Cytoscape's cy.zoom() value, default 1.0
  panX: number;       // cy.pan().x, default 0
  panY: number;       // cy.pan().y, default 0
  displayMode: DisplayMode;
}

export interface DesignerState {
  /** The current project root URI; null before any project is loaded. */
  projectUri: string | null;

  /** Every .ttrg discovered in the project. Populated by modeler/listGraphs on project open;
   *  refreshed when files are added/removed (workspace/didChangeWatchedFiles). */
  availableGraphs: GraphMetadata[];

  /** The currently open .ttrg URI; null shows the graph picker. */
  currentGraphUri: string | null;

  /** The resolved current graph data. Populated by modeler/getGraph after openGraph. */
  currentGraph: GetGraphResponse | null;

  /** Per-graph viewport. Single viewport because each .ttrg is one schema (no toggle). */
  currentViewport: ViewportState | null;

  /** Node positions for the current graph, keyed by qname. Saved to .ttrg.layout via setLayout. */
  nodePositions: Record<string, { x: number; y: number }>;

  /** Cached symbol-detail responses, keyed by qname. Survives graph switches within a project. */
  symbolDetails: Record<string, SymbolDetail>;

  /** Currently selected node/edge for the inspector panel. */
  selectedSymbol: { qname: string } | null;

  /** Whether the create-new-graph wizard is open. When true, the picker shows the wizard. */
  creatingGraph: boolean;

  /** Last error message; shown in a non-modal toast. */
  error: string | null;
}

export const initialDesignerState: DesignerState = {
  projectUri: null,
  availableGraphs: [],
  currentGraphUri: null,
  currentGraph: null,
  currentViewport: null,
  nodePositions: {},
  symbolDetails: {},
  selectedSymbol: null,
  creatingGraph: false,
  error: null,
};
```

### 11.3 Reducer actions

```ts
export type DesignerAction =
  // Project lifecycle
  | { type: 'loadProject'; projectUri: string }
  | { type: 'storeGraphList'; graphs: GraphMetadata[] }
  // Graph lifecycle
  | { type: 'openGraph'; graphUri: string }
  | { type: 'closeGraph' }
  | { type: 'storeGraph'; graph: GetGraphResponse }
  | { type: 'loadLayout'; layout: GraphLayout }
  // Creation wizard
  | { type: 'startCreateWizard' }
  | { type: 'cancelCreateWizard' }
  // Viewport / positions / display mode
  | { type: 'setViewport'; viewport: Omit<ViewportState, 'displayMode'> }
  | { type: 'setDisplayMode'; mode: DisplayMode }
  | { type: 'setNodePosition'; qname: string; x: number; y: number }
  // Selection / inspector
  | { type: 'selectSymbol'; qname: string | null }
  | { type: 'storeSymbolDetail'; detail: SymbolDetail }
  // Errors
  | { type: 'setError'; message: string | null };
```

The reducer is a pure function `(state, action) => state` with no I/O. All LSP calls live in App-level `useEffect`s that dispatch the resulting actions. This is unchanged from the v1 pattern; only the state shape and the action set have evolved.

### 11.4 Component contract — `<GraphPicker />` and `<CreateGraphWizard />`

```ts
export interface GraphPickerProps {
  projectUri: string;
  graphs: GraphMetadata[];
  onSelect: (graphUri: string) => void;
  onCreateNew: () => void;
}

export interface CreateGraphWizardProps {
  projectUri: string;
  onCancel: () => void;
  onCreated: (graphUri: string) => void;
}
```

`<GraphPicker />` is mounted when `currentGraphUri === null && !creatingGraph`. `<CreateGraphWizard />` is mounted when `creatingGraph === true`. Otherwise the canvas is mounted.

---

## 12. Changelog

- **v6, 2026-05-21** — §8.7 clarified to match the shipped C2 surface: `getModelGraph` is **unchanged** (kept as the whole-schema render alongside the new `.ttrg`-scoped `getGraph`, per decision D3); `getLayout`/`setLayout`/`exportLayout` are `graphUri`-scoped and read/write the `.ttrg`'s `layout` block (`setLayout` returns a `WorkspaceEdit`); the project-wide `.modeler/layout.ttrl` sidecar is removed (D4), with an in-memory `layoutStore` retained only as a host/test seam.
- **v5, 2026-05-21** — §7.1 `layout.nodes` keys changed from quoted strings to unquoted dotted-ids (grammar `key : id` accepts only unquoted ids; `setLayout` will emit unquoted — see C2.7).
- **v4, 2026-05-19** — clarified §3.1: removed the "(v1 shape, unchanged)" parenthetical, which was inaccurate. v1.1's qname construction always uses the kind as namespace fallback when no `namespace` clause is present; this changes the shape for unpackaged, no-namespace files (e.g. `db.users` → `db.table.users`). Stock-cnc doubling rule unchanged.
- **v3, 2026-05-19** — relaxed GraphLayout.viewport.displayMode in §2 from the three-member union to string; the union narrowing now happens in semantics, not parsing. Designer's DisplayMode in §11.2 unchanged.
- **v2, 2026-05-18** — added §11 (Designer state types) reflecting the locked schema-toggle-removed decision; added `ttr/graph-objects-empty` and `ttr/graph-name-mismatch` to §6.
- **v7, 2026-06-19** — added §13 (Packages & Domains increment): `[packages].root`/`[packages].layout` config, `DomainBlock` AST + `.ttrd`, new diagnostics (`ttr/package-prefix-divergence`, `ttr/domain-*`), the resolved-packages artifact JSON shape, and the cross-repo `ai-models` agent-schema diff. These extend (do not replace) §1–§12; where §4.4-era "mismatch = Error" conflicts, §13.1 wins.
- **v8, 2026-06-20** — PD2: §13.3 `DomainBlock` gains optional `packageSources?` / `entitySources?` (per-member `SourceLocation[]`, parallel to the string members) for editor go-to-def/find-refs. Additive and editor-only — the artifact and `DomainTable` consume the string members only. Grammar bumped to 2.3 (additive `.ttrd`).
- **v9, 2026-06-20** — PD3: added §13.6 (`DomainTable`/`ResolvedDomain` in `@modeler/semantics`, `domainPackageClosure`, and the `getProjectInfo.domains: DomainInfo[]` field). Domain diagnostics (§13.2) are emitted by a new `domains` rule category in `@modeler/lint`; `.ttrd` file-kind is parser-emitted (`ttr/wrong-file-kind`, walker).
- **v1, 2026-05-18** — initial draft. All sections subject to amendment under the contract-amendment discipline (mini-task-lists never override; PRs against this file first).

---

## 13. Packages & Domains increment (2026-06-19)

Canonical shapes for the increment specified in [`v1.1-packages-and-graphs.md` §14](v1.1-packages-and-graphs.md#14-addendum-2026-06-19--nested-packages-finalised-root-prefix-no-cascade-domains). Implemented by phases **PD1–PD5** under [`docs/v1-1/plan/packages-domains/`](../plan/packages-domains/README.md). These extend §1–§12; they do not remove anything already shipped.

### 13.1 `modeler.toml` `[packages]` block

Owned by the project-config loader (`packages/lsp` project-info path; mirror in `packages/migrate`).

```toml
[packages]
root   = ""          # string; optional module-style prefix. Default "" (no prefix).
layout = "flexible"  # "flexible" (default) | "strict" | "off"
```

```ts
export interface PackagesConfig {
  /** Module-style prefix prepended to directory-derived package names. "" = none. */
  root: string;
  /** Severity policy for a declaration that mismatches its directory. */
  layout: 'flexible' | 'strict' | 'off';
}
export const defaultPackagesConfig: PackagesConfig = { root: '', layout: 'flexible' };
```

**Derivation contract** (`packages/semantics`, used by symbol table + migrate):

```ts
/** Directory-derived package name for a file, including the configured root prefix. */
function derivedPackage(fileUri: string, projectRoot: string, cfg: PackagesConfig): string;
/** Effective package: declaration if present (authoritative), else derivedPackage(). */
function effectivePackage(doc: Document, fileUri: string, projectRoot: string, cfg: PackagesConfig): string;
```

`layout` severity mapping: `flexible` → `ttr/package-declaration-mismatch` = Warning; `strict` → Error; `off` → suppressed. `ttr/package-prefix-divergence` is Warning under `flexible`/`off`-relaxed-to-warn and Error under `strict` (see §6 additions). The `root` prefix is **elidable**: a reference omitting it resolves as if present (resolver normalises both forms to the canonical prefixed qname).

### 13.2 Diagnostic additions (extend §6)

| Code                              | Severity            | Emitter   | Trigger                                                                         |
| --------------------------------- | ------------------- | --------- | ------------------------------------------------------------------------------- |
| `ttr/package-prefix-divergence`   | Warning / Error¹    | validator | A declaration's non-leaf (prefix) segments differ from the file's directory path |
| `ttr/domain-member-not-found`     | Warning             | validator | A `.ttrd` `packages:`/`entities:` member doesn't resolve                        |
| `ttr/domain-empty`                | Warning             | validator | A `domain` block has no members                                                 |
| `ttr/duplicate-domain`            | Error               | validator | Two `domain` blocks share a name across the project                            |
| `ttr/domain-redundant-member`     | Info                | validator | An `entities:` entry is already covered by a recursive `packages:` member       |

¹ Warning under `[packages].layout = "flexible"`/`"off"`; Error under `"strict"`. `ttr/package-declaration-mismatch` (§6) likewise becomes severity-by-config rather than fixed Error. `ttr/wrong-file-kind` (§6) extends to cover a `.ttrd` with no `domain` block, or a `domain` block appearing in a `.ttr`/`.ttrg`.

### 13.3 `.ttrd` grammar + `DomainBlock` AST

New tokens (contracts §1 extension): `DOMAIN : 'domain'`, `PACKAGES : 'packages'`, `ENTITIES : 'entities'`. New `document` alternative and rules per [grammar-changes §9.3](grammar-v1-1-changes.md#93-new-ttrd-domain-file-editor-only--ai-platform-does-not-load-it).

```ts
export interface DomainBlock {
  kind: 'domainBlock';
  /** Bare domain name (file-name sans .ttrd should match if one-domain-per-file lands). */
  name: string;
  description?: string;
  tags?: string[];
  /** Recursive members: each pulls the package and all descendants. May be empty. */
  packages: string[];
  /** Individual entity qnames loaded in addition to whole packages. May be empty. */
  entities: string[];
  /**
   * Per-member source locations, parallel to `packages` / `entities` (editor-only,
   * added PD2). Optional and additive: downstream consumers (DomainTable, the
   * resolved-packages artifact) read only the string members; the LSP uses these
   * for go-to-def / find-refs on members.
   */
  packageSources?: SourceLocation[];
  entitySources?: SourceLocation[];
  source: SourceLocation;
}
```

`Document` gains one optional field: `domain?: DomainBlock` (present only for `.ttrd` files; mutually exclusive with `graph` and non-empty `definitions`, enforced via `ttr/wrong-file-kind`).

### 13.4 Resolved-packages artifact

Emitted by the new CLI subcommand `modeler resolve-packages <project-root> [--out <file>]` (package `packages/migrate` or a new `packages/cli`; PD4 decides). CLI default output `<project-root>/.modeler/resolved-packages.json` (gitignored build artifact). **The `ai-models` consumer commits the snapshot** at `model-ttr/resolved-packages.json` (non-ignored, via `--out`); the agent gate reads it Node-free and a dedicated `model-ttr` workflow runs `resolve-packages --check` to block drift (decided 2026-06-19, design §14.7 Q1).

```ts
interface ResolvedPackagesArtifact {
  formatVersion: 1;                 // bump on breaking shape changes
  generatedFrom: string;            // project-root path or repo ref (informational)
  root: string;                     // the configured [packages].root ("" if none)
  packages: ResolvedPackage[];      // sorted by canonicalName
  entities: ResolvedEntity[];       // sorted by qname; every def that is an "entity"-kind object
  domains: ResolvedDomain[];        // sorted by name
}

interface ResolvedPackage {
  canonicalName: string;            // root-prefixed, e.g. "cz.dfpartner.ucetnictvi" (or bare if root="")
  declaredName: string;             // as written in files (may omit root)
  nested: boolean;                  // true if it has >1 segment after root
  directory: string;                // path relative to project root
}

interface ResolvedEntity {
  qname: string;                    // canonical, package-prefixed
  package: string;                  // canonicalName of owning package
  schema: string;                   // er / db / ...
}

interface ResolvedDomain {
  name: string;
  resolvedPackages: string[];       // canonicalName[], the RECURSIVE closure of `packages:`
  resolvedEntities: string[];       // qname[] from `entities:`
}
```

**Determinism contract:** all arrays sorted (packages by `canonicalName`, entities by `qname`, domains by `name`, inner arrays lexicographically); 2-space JSON; trailing newline. Re-running with no model change yields a byte-identical file (a CI drift check can diff it).

### 13.5 Cross-repo: `ai-models` agent-schema diff (PD5)

Lives in the `ai-models` repo (`agents/agent.schema.json`); recorded here because it is part of this increment's contract surface. Changes:

```jsonc
// shem.packages — widen to allow nested dotted packages
"packages": {
  "items": { "type": "string", "pattern": "^[a-z0-9_]+(\\.[a-z0-9_]+)*$" }  // was ^[a-z0-9_]+$
},
// shem.domains — NEW optional field
"domains": {
  "type": "array", "uniqueItems": true,
  "items": { "type": "string", "pattern": "^[a-z0-9_]+$" },
  "description": "Domain names (from model-ttr/*.ttrd) → expanded to packages/entities at Golem runtime."
},
// shem — require at least one of packages|domains (replace `required: ["packages"]`)
"anyOf": [ { "required": ["packages"] }, { "required": ["domains"] } ]
```

`tools/validate_agents.py` rule changes: rule 2 (`package-exists`) and rule 3 (`entity-format`) validate against the **resolved-packages artifact** (§13.4) — `shem.packages` ⊆ artifact `packages[].declaredName`/`canonicalName`, `shem.domains` ⊆ artifact `domains[].name`, `shem.entities` ⊆ artifact `entities[].qname` — instead of listing `model-ttr/` directories or `split(".",1)` parsing. Domain→package expansion is **runtime** (Golem), so CI validates names exist but does not expand them.

### 13.6 `DomainTable` + `getProjectInfo` domains (PD3)

`@modeler/semantics` owns the resolution (`domain-table.ts`); `@modeler/lint` owns the diagnostics (the `domains` rule category, joining `packages`/`graph`/…).

```ts
// @modeler/semantics
interface ResolvedDomain {
  name: string;
  resolvedPackages: string[];   // RECURSIVE closure of `packages:`, canonical, sorted
  resolvedEntities: string[];   // canonical qnames from `entities:`, sorted
  source: SourceLocation;
  documentUri: string;
}
function domainPackageClosure(symbols, member, root?): string[];  // the only recursive prefix-match (§14.3)
class DomainTableBuilder { build(entries: {block: DomainBlock; documentUri: string}[]): Map<string, ResolvedDomain> }
```

`modeler/getProjectInfo` response gains `domains: DomainInfo[]` (alongside `packages` from §8.7):

```ts
interface DomainInfo {
  name: string;
  packageMemberCount: number;   // block.packages.length (as authored)
  entityMemberCount: number;    // block.entities.length
  resolvedPackageCount: number; // size of the recursive closure
  resolvedEntityCount: number;
}
```

Recursion lives ONLY in `domainPackageClosure` (domain "load" is recursive; `import X.*` is not — B20). `.ttrd` file-kind (`ttr/wrong-file-kind`) is parser-emitted in the walker, mirroring `.ttrg`.
