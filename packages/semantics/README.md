# @tatrman/semantics

**Status:** v1, 2026-05-18. Full implementation shipped in Phase 2; Phase 3.H added kind-aware indexing for `relation`, `query`, `role`, `er2db*`, `er2cncRole`.

Symbol table, reference resolver, validator, and reverse reference index for TTR. Consumed by `@tatrman/lsp` for diagnostics, hover, go-to-definition, find-references, workspace symbols, and the Designer's `referencedBy` payloads.

## Public API

### `ProjectSymbolTable`

Project-wide symbol store. `upsertDocument` builds a per-document table and merges its entries into the project view; `removeDocument` rolls them back. Duplicate qnames are tracked via `duplicates()`.

```ts
import { ProjectSymbolTable } from '@tatrman/semantics';

const table = new ProjectSymbolTable();
table.upsertDocument(uri, ast, schemaCode, namespace);

const entry = table.get('er.entity.artikl');   // SymbolEntry | undefined
const all = table.all();                       // SymbolEntry[] (de-duplicated by qname)
const entities = table.all().filter(e => e.kind === 'entity');
const byName = table.findByName('artikl');     // SymbolEntry[] across all qnames
const dupes = table.duplicates();              // [{ qname, entries }] for collisions
```

### `Resolver`

Resolves dotted references and bare identifiers against the table. The resolution context carries the document's `schemaCode` / `namespace` and an optional `enclosingQname` for in-scope bare-id resolution (so `nameAttribute: id` inside `def entity artikl` resolves to `er.entity.artikl.id`).

```ts
import { Resolver } from '@tatrman/semantics';

const resolver = new Resolver(table);
const result = resolver.resolveReference(
  { path: 'er.entity.artikl', parts: ['er', 'entity', 'artikl'] },
  { schemaCode: 'er', namespace: 'entity' },
);
if (result.resolved) {
  console.log(result.symbol.qname);
} else {
  console.log('tried:', result.tried);
}
```

### `Validator`

Runs structural and reference validation. Produces `ValidationDiagnostic` objects with `DiagnosticCode` from `@tatrman/parser` (`ttr/required-property-missing`, `ttr/entity-attribute-not-found`, `ttr/unresolved-reference`, `ttr/duplicate-definition`, etc.).

```ts
import { Validator, resolveManifest } from '@tatrman/semantics';

const manifest = resolveManifest(undefined, '/path/to/project');
const validator = new Validator(table, resolver, manifest);
const structural = validator.validateDocument(uri, ast);
const refs = validator.validateReferences(uri, ast);
```

### `ReferenceIndex` (Phase 2.H / 3.H)

Reverse index keyed by target qname — for each symbol, the list of `ReferenceLocation`s pointing at it. Powers `textDocument/references` and `referencedBy` in `modeler/getSymbolDetail`. Each entry carries the **referrer's** qname (the enclosing def) plus the source location of the reference itself.

```ts
import { ReferenceIndex } from '@tatrman/semantics';

const refIndex = new ReferenceIndex();
refIndex.upsertDocument(uri, ast, schemaCode, namespace, resolver);

for (const loc of refIndex.findByQname('er.entity.artikl')) {
  console.log(`${loc.referrerQname ?? '<unknown>'} at ${loc.documentUri}:${loc.source.line}`);
}
```

### Manifest helpers

```ts
import { parseManifest, resolveManifest } from '@tatrman/semantics';

const parsed = parseManifest(tomlContent);                       // ProjectManifest
const manifest = resolveManifest(parsed, '/path/to/project');    // ResolvedManifest, fills defaults
```

`ResolvedManifest` shape: `{ name, preferredLanguage, declaredSchemas, namespaces, stockVocabularies, lint }`. Pass `undefined` as the first argument to get an all-defaults manifest.

### `loadProjectFromOpenDocuments`

Browser-safe project loader — no `node:fs`. Builds a `Project` (`{ root, manifest, ttrFiles }`) from open documents in memory. The LSP's browser worker uses this on every document sync.

```ts
import { loadProjectFromOpenDocuments } from '@tatrman/semantics';

const project = loadProjectFromOpenDocuments(
  [{ uri: 'file:///path/to/model.ttrm' }],
  '/project/root',
  manifest,
);
```

## Worked example

```ts
import { parseString } from '@tatrman/parser';
import {
  ProjectSymbolTable,
  Resolver,
  Validator,
  ReferenceIndex,
  resolveManifest,
} from '@tatrman/semantics';

// 1. Parse.
const result = parseString(
  `schema er namespace entity
def entity artikl {
  nameAttribute: id_artiklu,
  attributes: [
    def attribute id_artiklu { type: int, isKey: true },
  ],
}`,
  'artikl.ttrm',
);
if (!result.ast) throw new Error('parse failed');
const ast = result.ast;
const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
const namespace = ast.schemaDirective?.namespace ?? '';

// 2. Build the symbol table.
const table = new ProjectSymbolTable();
table.upsertDocument('artikl.ttrm', ast, schemaCode, namespace);

// 3. Resolve a bare-id reference scoped to the enclosing entity.
const resolver = new Resolver(table);
const res = resolver.resolveReference(
  { path: 'id_artiklu', parts: ['id_artiklu'] },
  { schemaCode, namespace, enclosingQname: 'er.entity.artikl' },
);
console.log(res.resolved); // true

// 4. Index references for find-references / referencedBy queries.
const refIndex = new ReferenceIndex();
refIndex.upsertDocument('artikl.ttrm', ast, schemaCode, namespace, resolver);

// 5. Validate against an all-defaults manifest.
const manifest = resolveManifest(undefined, '.');
const validator = new Validator(table, resolver, manifest);
const diags = [
  ...validator.validateDocument('artikl.ttrm', ast),
  ...validator.validateReferences('artikl.ttrm', ast),
];
console.log(diags.length, 'diagnostics');
```

Last verified to compile: 2026-05-18 — covered by `src/__tests__/readme-example.test.ts`, which fails the build if the snippet drifts from the live API.

## Node / Browser split

The main `@tatrman/semantics` entry is browser-safe — no `node:fs`, no `node:path`. The Designer's browser worker (`@tatrman/lsp/browser`) imports only from this entry.

Node-only helpers live in `@tatrman/semantics/node-only`:

```ts
import {
  findProjectRoot,
  loadProject,
  loadStockVocabularies,
} from '@tatrman/semantics/node-only';

const root = findProjectRoot(someFileUri);       // walks up looking for modeler.toml
const project = await loadProject(root);         // reads .ttrm files from disk
const stockDocs = await loadStockVocabularies(['cnc-roles']);
```

`@tatrman/lsp`'s stdio entry (`server-stdio.ts`) wires these into the LSP for VS Code and IntelliJ. Don't import them from the main entry — the build configuration will fail to bundle a browser target.
