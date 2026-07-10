# Phase 3 — Contracts

**Status:** v2, 2026-05-15. Companion to `tasks-phase-03-designer.md` and the per-section mini-task-lists under `docs/plan/phase-03/`.

**Audience:** the implementer (junior dev or coding agent) executing Phase 3. The contracts here are non-negotiable — every type, every method signature, every JSON shape below is the single source of truth. If a mini-task-list shows a snippet that conflicts with this document, **this document wins**. Open a PR against this file to amend.

**Scope:** every new or modified type, every LSP custom method's request/response shape, and the JSON sidecar schema. Behaviour and rationale live in the mini-task-lists; this file is the *shape* only.

> **Implementer note — verifying library APIs.** When a contract references an external library (Cytoscape extension registration, antlr4ng's `DefaultErrorStrategy`, `@vscode/test-electron`'s `runTests`, etc.), do not trust the snippet's API surface verbatim. Run the `context7` MCP server to fetch current docs first:
>
> ```
> mcp__context7__resolve-library-id  { libraryName: "cytoscape", query: "<your focused question>" }
> mcp__context7__query-docs          { libraryId: "<id from resolve>", query: "<focused>" }
> ```
>
> The contracts in this file describe *the shapes we are agreeing on*; the library snippets in the mini-task-lists describe *how to call into those shapes*. Library APIs drift; our contracts do not.

## Index

1. [Schema and namespace constants](#1-schema-and-namespace-constants)
2. [Designer-side state types](#2-designer-side-state-types)
3. [File-system shim types](#3-file-system-shim-types)
4. [Shared graph DTOs](#4-shared-graph-dtos)
5. [Symbol detail DTO](#5-symbol-detail-dto)
6. [Layout sidecar (`.ttrl`) types and JSON schema](#6-layout-sidecar-ttrl-types-and-json-schema)
7. [LSP custom-method contracts](#7-lsp-custom-method-contracts)
8. [Cardinality mapping](#8-cardinality-mapping)
9. [URI and path conventions](#9-uri-and-path-conventions)
10. [Diagnostic codes (Phase 3 additions)](#10-diagnostic-codes-phase-3-additions)

---

## 1. Schema and namespace constants

Centralized in `packages/lsp/src/model-graph.ts` (new) and re-exported via `@modeler/lsp`. The Designer imports them via `import type { SchemaCode } from '@modeler/lsp'`.

```ts
export type SchemaCode = 'db' | 'er' | 'map' | 'query' | 'cnc';

/** Designer v1 renders only db and er. The other schemas are valid in the model
 *  and reachable via getSymbolDetail, but never become a getModelGraph target. */
export type RenderableSchemaCode = 'db' | 'er';

export type DisplayMode = 'just-names' | 'with-types' | 'with-constraints';
```

The string values are wire-stable: do not rename them without bumping the `.ttrl` `version` (see §6).

---

## 2. Designer-side state types

Owned by the Designer; not exposed over the LSP. Lives at `packages/designer/src/state/designer-state.ts`.

```ts
import type { RenderableSchemaCode, DisplayMode } from '@modeler/lsp';

export interface ViewportState {
  zoom: number;        // Cytoscape's cy.zoom() value, default 1.0
  panX: number;        // cy.pan().x, default 0
  panY: number;        // cy.pan().y, default 0
  displayMode: DisplayMode;
}

export interface DesignerState {
  activeSchema: RenderableSchemaCode;
  viewports: Record<RenderableSchemaCode, ViewportState>;
  nodePositions: Record<string, { x: number; y: number }>;
  symbolDetails: Record<string, SymbolDetail>;
  selectedSymbol: { qname: string } | null;
  projectUri: string | null;
  error: string | null;
  /** Per-schema graph cache. Flipping the schema toggle reads from here
   *  before falling back to a getModelGraph round-trip. Cleared on loadProject. */
  graphsBySchema: Record<RenderableSchemaCode, ModelGraph | null>;
}

export const initialDesignerState: DesignerState = {
  activeSchema: 'db',
  viewports: {
    db: { zoom: 1.0, panX: 0, panY: 0, displayMode: 'with-types' },
    er: { zoom: 1.0, panX: 0, panY: 0, displayMode: 'just-names' },
  },
  nodePositions: {},
  symbolDetails: {},
  selectedSymbol: null,
  projectUri: null,
  error: null,
  graphsBySchema: { db: null, er: null },
};
```

### Reducer actions

```ts
export type DesignerAction =
  | { type: 'loadProject'; projectUri: string }
  | { type: 'loadLayout';  layout: LayoutFile }
  | { type: 'switchSchema'; schema: RenderableSchemaCode }
  | { type: 'setDisplayMode'; schema: RenderableSchemaCode; mode: DisplayMode }
  | { type: 'setViewport';   schema: RenderableSchemaCode; viewport: Omit<ViewportState, 'displayMode'> }
  | { type: 'setNodePosition'; qname: string; x: number; y: number }
  | { type: 'selectSymbol';  qname: string | null }
  | { type: 'storeSymbolDetail'; detail: SymbolDetail }
  | { type: 'storeGraph'; schema: RenderableSchemaCode; graph: ModelGraph }
  | { type: 'setError'; message: string | null };
```

The reducer must be a pure function `(state, action) => state` with no I/O. All LSP calls live in App-level `useEffect`s that dispatch the resulting actions.

---

## 3. File-system shim types

Lives at `packages/designer/src/fs/file-system.ts`.

```ts
export interface ProjectFiles {
  /** Display name; e.g. 'v1-metadata'. Used for the synthetic projectUri. */
  rootName: string;

  /** relativePath → file content. Paths are forward-slash, no leading slash. */
  files: Map<string, string>;
}

/** Uses showDirectoryPicker; returns null if the API is unavailable (Safari). */
export async function loadProjectViaFileSystemAccessAPI(): Promise<ProjectFiles | null>;

/** Reads from an <input type="file" webkitdirectory multiple>. */
export async function loadProjectViaUpload(input: HTMLInputElement): Promise<ProjectFiles>;

/** Triggers a browser download via synthetic <a> click. No-op in Node mode. */
export async function downloadFile(filename: string, content: string): Promise<void>;
```

**Invariants:**
- `files` only contains files whose extension is `.ttr`, `.ttrl`, or `.toml`. Other files are silently skipped (the picker may surface them, but the shim filters).
- `files` keys must not start with `/`. Tests assert this.
- Empty `files` is a valid `ProjectFiles` (the caller decides what to do).

---

## 4. Shared graph DTOs

These DTOs are returned by `modeler/getModelGraph` and consumed by the Designer's Cytoscape adapter. Defined once in `packages/lsp/src/model-graph.ts` and re-exported.

```ts
export interface ModelGraph {
  schemaCode: RenderableSchemaCode;
  nodes: ModelGraphNode[];
  edges: ModelGraphEdge[];
}

export interface ModelGraphNode {
  /** Canonical qname; matches ProjectSymbolTable.get(qname). */
  qname: string;

  /** Definition kind from the parser's discriminated union. */
  kind: 'table' | 'view' | 'entity';

  /** Local name (last segment of qname). */
  name: string;

  schemaCode: RenderableSchemaCode;

  /** Localized per manifest.preferredLanguage when a displayLabel exists;
   *  otherwise === name. */
  label: string;

  /** file:// URI of the .ttr file the node is defined in. */
  sourceUri: string;

  /** 1-indexed line, 0-indexed column — matches SourceLocation convention. */
  sourceLocation: { line: number; column: number };

  /** Inline rows: columns for tables/views, attributes for entities.
   *  Empty array, not undefined, when no rows exist. */
  rows: ModelGraphRow[];
}

export interface ModelGraphRow {
  /** Local name within the parent node. */
  name: string;

  /** Full qname; e.g. db.dbo.QZBOZI_DF.IDZBOZI. */
  qname: string;

  kind: 'column' | 'attribute';

  /** Rendered as a single string: 'int', 'varchar(40)', 'decimal(10,2)'.
   *  null when the def omits a type. See §4.1 for the rendering rule. */
  type: string | null;

  /** Either column.isKey, attribute.isKey, or true when the column is named
   *  in TableDef.primaryKey. */
  isKey: boolean;

  /** Optional == nullable in db; optional == not-required in er. */
  optional: boolean;
}

export interface ModelGraphEdge {
  /** Stable id within the graph; equals the def's qname. */
  id: string;
  qname: string;

  kind: 'fk' | 'relation';

  /** qname of the source node (must match a node.qname in the same graph). */
  fromNode: string;
  toNode: string;

  /** Crow's-foot enum; null when the def omits cardinality (e.g. FKs). */
  fromCardinality: Cardinality | null;
  toCardinality:   Cardinality | null;

  sourceUri: string;
  sourceLocation: { line: number; column: number };
}

export type Cardinality = 'one' | 'zero-or-one' | 'many' | 'one-or-many';
```

### 4.1 Type-rendering rule

`ModelGraphRow.type` is rendered from `DataType` (parser AST) by the LSP via this exact function:

```ts
export function renderDataType(t: DataType | undefined): string | null {
  if (!t) return null;
  if (t.kind === 'simple') return t.name;
  // structured form
  const parts: string[] = [];
  if (typeof t.length === 'number') parts.push(String(t.length));
  if (typeof t.precision === 'number') parts.push(String(t.precision));
  return parts.length === 0 ? t.typeName : `${t.typeName}(${parts.join(',')})`;
}
```

Test cases (assert exact strings):
- `{ kind: 'simple', name: 'int' }` → `'int'`
- `{ kind: 'structured', typeName: 'varchar', length: 40 }` → `'varchar(40)'`
- `{ kind: 'structured', typeName: 'decimal', length: 10, precision: 2 }` → `'decimal(10,2)'`
- `undefined` → `null`

### 4.2 Edge resolution

Edges only land in the graph when both `from` and `to` resolve to known nodes via the Phase 2 `Resolver`:

- For `db`: walk every `FkDef` in the project; call `extractReference` on `from` / `to`; resolve each; if both resolve to known tables in the same graph, emit an edge. Skip with one-line LSP log message otherwise.
- For `er`: walk every `RelationDef`; same pattern but resolves to entities.

Unresolved edges produce a `ttr/unresolved-reference` validator diagnostic via the Phase 2 path (no separate Phase 3 code).

---

## 5. Symbol detail DTO

Returned by `modeler/getSymbolDetail`. Lives at `packages/lsp/src/symbol-detail.ts`.

```ts
export interface SymbolDetail {
  qname: string;
  kind: Definition['kind'];          // from @modeler/parser
  name: string;

  /** Same localization rule as ModelGraphNode.label. */
  label: string;

  /** Picked per manifest.preferredLanguage; null when no description in the def. */
  description: string | null;

  /** Always an array (possibly empty). */
  tags: string[];

  sourceUri: string;
  sourceLine: number;                // 1-indexed

  /** Per-kind payload. Discriminated by `kind`. See §5.1. */
  perKindData: PerKindData;

  /** Reverse-index results from ReferenceIndex.findByQname(qname).
   *  Empty array when the symbol is unreferenced. */
  referencedBy: Array<{ qname: string; sourceUri: string; sourceLine: number }>;
}
```

### 5.1 Per-kind payload

```ts
export type PerKindData =
  | { kind: 'table';  columns: ModelGraphRow[]; primaryKey: string[] }
  | { kind: 'view';   columns: ModelGraphRow[] }
  | { kind: 'entity'; attributes: ModelGraphRow[]; nameAttributeQname: string | null;
                      codeAttributeQname: string | null; roleQnames: string[] }
  | { kind: 'fk';     fromQname: string; toQname: string }
  | { kind: 'relation'; fromQname: string; toQname: string;
                        fromCardinality: Cardinality | null; toCardinality: Cardinality | null }
  | { kind: 'role';   labelByLanguage: Record<string, string> }
  | { kind: 'other';  /* every other Definition.kind */ };
```

Implementer note: `nameAttributeQname` / `codeAttributeQname` / `roleQnames` / `fromQname` / `toQname` are all post-resolution qnames. Unresolved refs become `null` (single) or omitted (array). The inspector treats `null` as "ref defined in source but doesn't resolve" — distinct from "no ref provided".

---

## 6. Layout sidecar (`.ttrl`) types and JSON schema

One file per project at `<project-root>/.modeler/layout.ttrl`. JSON. The LSP owns reads and writes — hosts (VS Code, Designer) never touch the file directly.

### 6.1 TypeScript shape

```ts
export interface LayoutFile {
  version: 1;
  viewports: Record<RenderableSchemaCode, ViewportState>;     // see §2
  nodes: Record<string /* qname */, { x: number; y: number }>;
  edges: Record<string /* qname */, EdgeLayout>;
}

export interface EdgeLayout {
  /** Cytoscape unbundled-bezier control points; empty array == straight edge. */
  bendPoints: Array<[number, number]>;
}

export function emptyLayout(): LayoutFile {
  return {
    version: 1,
    viewports: {
      db: { zoom: 1.0, panX: 0, panY: 0, displayMode: 'with-types' },
      er: { zoom: 1.0, panX: 0, panY: 0, displayMode: 'just-names' },
    },
    nodes: {},
    edges: {},
  };
}
```

### 6.2 JSON Schema (draft 2020-12)

Place in `packages/lsp/schemas/layout.schema.json`. The LSP's `validateLayout` consults this schema via `ajv` (already in the workspace from Phase 1 `.ttrl` work — confirm before installing).

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://tatrman.org/schemas/layout/1.json",
  "title": "Tatrman Modeler layout sidecar",
  "type": "object",
  "required": ["version", "viewports", "nodes", "edges"],
  "additionalProperties": false,
  "properties": {
    "version": { "const": 1 },
    "viewports": {
      "type": "object",
      "required": ["db", "er"],
      "additionalProperties": false,
      "properties": {
        "db": { "$ref": "#/$defs/viewport" },
        "er": { "$ref": "#/$defs/viewport" }
      }
    },
    "nodes": {
      "type": "object",
      "patternProperties": {
        "^.+$": {
          "type": "object",
          "required": ["x", "y"],
          "additionalProperties": false,
          "properties": {
            "x": { "type": "number" },
            "y": { "type": "number" }
          }
        }
      },
      "additionalProperties": false
    },
    "edges": {
      "type": "object",
      "patternProperties": {
        "^.+$": {
          "type": "object",
          "required": ["bendPoints"],
          "additionalProperties": false,
          "properties": {
            "bendPoints": {
              "type": "array",
              "items": {
                "type": "array",
                "items": { "type": "number" },
                "minItems": 2,
                "maxItems": 2
              }
            }
          }
        }
      },
      "additionalProperties": false
    }
  },
  "$defs": {
    "viewport": {
      "type": "object",
      "required": ["zoom", "panX", "panY", "displayMode"],
      "additionalProperties": false,
      "properties": {
        "zoom": { "type": "number", "exclusiveMinimum": 0 },
        "panX": { "type": "number" },
        "panY": { "type": "number" },
        "displayMode": { "enum": ["just-names", "with-types", "with-constraints"] }
      }
    }
  }
}
```

### 6.3 Validation behavior

`validateLayout(unknown): LayoutFile | null`:
- Validates via ajv against the schema above.
- Returns `null` on any validation error (caller falls back to `emptyLayout()`).
- Never throws.

The caller in Node mode logs validation errors at `warn` level so corruption is observable in the VS Code Output panel without breaking the Designer.

---

## 7. LSP custom-method contracts

Every method registered via `connection.onRequest('modeler/<name>', handler)`. Method names are wire-stable.

### 7.1 `modeler/getProjectInfo` (existing, documented for completeness)

**Request:** `{ textDocument: { uri: string } }`

**Response:**
```ts
ResolvedManifest & { root: string; ttrFileCount: number }
```

No Phase 3 changes; documented here so the contract surface is complete.

### 7.2 `modeler/getModelGraph` (modified)

**Request:**
```ts
{ textDocument: { uri: string }, schema: 'db' | 'er' }
```

**Response:** `ModelGraph` (§4).

**Errors:**
- If `schema` is unsupported (anything other than `db` / `er`), the handler returns `{ schemaCode: <requested>, nodes: [], edges: [] }` and logs a warning. Do not throw — the Designer must be able to ask before knowing what's renderable.
- If the document isn't open, return an empty graph for the requested schema.

**Migration note:** the Phase 0 implementation returned `{ nodes: [{ qname, kind, label }], edges: [] }` with no `schemaCode`. Update the Designer to send `schema` and consume the new shape in one PR; do not maintain both.

### 7.3 `modeler/getLayout`

**Request:** `{ projectRoot: string }`  — absolute path; the Designer passes the value it got from `getProjectInfo().root`.

**Response:** `LayoutFile` (§6.1).

**Behaviour:**
- Node mode: read `<projectRoot>/.modeler/layout.ttrl`. Parse JSON. Validate via §6.3. On any failure, return `emptyLayout()`.
- Browser mode: return the in-memory layout for this `projectRoot`, or `emptyLayout()` if none.

### 7.4 `modeler/setLayout`

**Request:** `{ projectRoot: string, layout: LayoutFile }`

**Response:** `{ ok: true }` on success; the method does not return data.

**Behaviour:**
- Node mode: ensure `<projectRoot>/.modeler/` exists. Write to `<projectRoot>/.modeler/layout.ttrl.tmp` then `fs.rename` to `layout.ttrl` (atomic). Pretty-print with 2-space indent.
- Browser mode: update the in-memory record keyed by `projectRoot`. No I/O.

The handler does not re-validate the incoming `layout` — the caller is the LSP's own typed wrapper, and we trust the Designer. (A stale `.ttrl` on disk is caught at `getLayout` time, where corruption could be third-party.)

### 7.5 `modeler/exportLayout` (browser-only)

**Request:** `{ projectRoot: string }`

**Response:** `LayoutFile`

**Behaviour:** browser mode returns the in-memory layout (same value `getLayout` would). Node mode registers the handler but returns `emptyLayout()` and logs an info-level message — the method is only meaningful for the static-site download flow.

The Designer's "Download layout" button calls this and then runs `downloadFile('layout.ttrl', JSON.stringify(result, null, 2))`.

### 7.6 `modeler/applyGraphEdit` (placeholder)

**Request:** `{ /* unspecified */ }` — see v1.1 spec.

**Response:**
```ts
{ ok: false; reason: 'edit-mode-not-available-in-v1' }
```

No side effects. The Designer wires read-only buttons against this method so v1.1 edit-mode integration is a pure server-side change.

### 7.7 `modeler/getSymbolDetail` (new)

**Request:** `{ qname: string }`

**Response:** `SymbolDetail` (§5) — or `null` when no symbol with that qname exists in the project.

**Behaviour:**
- Look up in `ProjectSymbolTable.get(qname)`.
- Look up the AST (via the LSP's cached document map) and rebuild `perKindData` per §5.1.
- Look up reverse references via `ReferenceIndex.findByQname(qname)`.
- Localize `label` and `description` per `manifest.preferredLanguage`.

---

## 8. Cardinality mapping

`RelationDef.cardinality` is parsed as an `ObjectValue` (Phase 2.A.2). The LSP maps it to the wire enum (§4) via this exact rule:

```ts
/**
 * Inputs accepted as string-valued entries on a cardinality object:
 *   "1"          → 'one'
 *   "0..1"       → 'zero-or-one'
 *   "n" | "*"    → 'many'
 *   "1..n" | "1..*" → 'one-or-many'
 * Anything else returns null.
 */
export function parseCardinality(s: string): Cardinality | null;

export function extractCardinality(obj: ObjectValue | undefined):
  { from: Cardinality | null; to: Cardinality | null } {
  if (!obj) return { from: null, to: null };
  const lookup = (key: string) => {
    const entry = obj.entries.find((e) => e.key === key);
    if (!entry || entry.value.kind !== 'string') return null;
    return parseCardinality(entry.value.value);
  };
  return { from: lookup('from'), to: lookup('to') };
}
```

Test cases for `parseCardinality`:
- `'1'` → `'one'`
- `'0..1'` → `'zero-or-one'`
- `'0..*'` → `'many'`
- `'n'` → `'many'`
- `'*'` → `'many'`
- `'1..n'` → `'one-or-many'`
- `'1..*'` → `'one-or-many'`
- `'foo'` → `null`
- `''` → `null`

If a user writes the cardinality as a non-string value (e.g. a list), `lookup` returns `null` and the edge gets `null` cardinality (rendered as a straight line with no glyph).

---

## 9. URI and path conventions

| Use | Shape | Producer |
|---|---|---|
| Project root (Node) | absolute filesystem path | Phase 2 `findProjectRoot` |
| Project root (Browser) | synthetic absolute path = `'/' + rootName` | Designer file-system shim |
| Document URI (Node) | `file://<absolute-path>` | VS Code |
| Document URI (Browser) | `file:///<rootName>/<relativePath>` | Designer |
| Demo-mode bundle path | `samples/v1-metadata/` under `packages/designer/dist/` | build-time copy in §G |
| Layout sidecar (Node) | `<projectRoot>/.modeler/layout.ttrl` | LSP `setLayout` |
| Layout sidecar (Browser) | in-memory, keyed by `projectRoot` | LSP browser worker |

The Designer's synthetic URIs intentionally mirror Node-mode `file://` URIs so the LSP's symbol-table and resolver code paths don't fork by host.

---

## 10. Diagnostic codes (Phase 3 additions)

Phase 3 adds one new code and revives one carryover:

| Code | Severity | Trigger | Owner |
|---|---|---|---|
| `ttr/parse-recovery-info` | Information | ANTLR error strategy recovered at a token (Section I) | parser |
| (no new error codes in Phase 3) | — | — | — |

The validator codes (`ttr/unresolved-reference`, `ttr/duplicate-definition`, etc.) defined in Phase 2 fire unchanged on Phase 3 inputs.

Update `docs/design/diagnostics.md` Section I closes out: remove any "reserved / not yet emitted" annotation on `parse-recovery-info`.

---

## Amendments

When a mini-task-list discovers a contract that needs to change, the implementer:

1. Opens a PR that edits *this file first*, then the mini-task-list, then the implementation.
2. Bumps the version note at the top of this file (e.g. v0 → v1) and adds a one-line changelog entry below.
3. Coordinates with anyone working on a sibling mini-task-list, since contract changes cross sections.

The contracts are versioned by document, not per-type. We do not need finer granularity in v1.

### Changelog

- **v0 (2026-05-15)** — initial draft for Phase 3 kickoff.
- **v1 (2026-05-15)** — Section A review (review-005): selected path (a) — kept early LSP work from Section B. Added `parseCardinality`, `extractCardinality`, `renderDataType`, `validateLayout`, `emptyLayout`, `LayoutFile`, `ViewportState`, `SymbolDetail`, `PerKindData`, `ModelGraph`, `ModelGraphNode`, `ModelGraphRow`, `ModelGraphEdge`, `DataType`, `DataTypeSimple`, `DataTypeStructured`, `Cardinality`, `RenderableSchemaCode`, `DisplayMode` to `@modeler/lsp` exports; all covered by unit tests. Deleted `lsp-index.ts`, collapsed re-exports to `index.ts` only.
- **v2 (2026-05-15)** — Section C: added `graphsBySchema: Record<RenderableSchemaCode, ModelGraph | null>` to `DesignerState` (cleared on `loadProject`) and the `storeGraph` action; cache exists so schema toggles after the first round-trip don't refetch. Name shortened from the mini-list's `graphsByCachedSchema` suggestion to `graphsBySchema`.
- **v3 (2026-05-16)** — Section D (review-012): added `"0..*"` → `'many'` to accepted cardinality strings (§8). This is the only string used in `samples/v1-metadata/er.ttr`; omitting it would cause all relation edges in the demo to lose their glyphs.
- **v4 (2026-05-16)** — Section D (review-012): chose approach A (SVG overlay) for glyph rendering over the implemented centered-label approach. Approach A places glyphs at edge endpoints oriented along the edge tangent, which is the correct visual semantics for Crow's-foot cardinality notation. The centered-label approach was abandoned because glyphs appeared at the edge midpoint with fixed (not tangent-derived) orientation — wrong for diagonal edges and semantically misleading. Documented in `docs/plan/phase-03/D-er-rendering.md` § D.4.
- **v5 (2026-05-16)** — Section D (review-012): `ModelGraphNode.label` now localized per `manifest.preferredLanguage` for entity nodes (was always `def.name`). `ModelGraphRow` extended with `isNameAttribute: boolean` and `isCodeAttribute: boolean` for er attribute markers (★ / #).
