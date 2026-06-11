import {
  InitializeParams,
  InitializeResult,
  TextDocuments,
  TextDocumentSyncKind,
  Diagnostic,
  DiagnosticSeverity,
  TextDocumentChangeEvent,
  Connection,
  Location,
  SymbolInformation,
  SymbolKind,
  Hover,
  SemanticTokensBuilder,
  ResponseError,
  ErrorCodes,
  type CodeAction,
} from 'vscode-languageserver';
import { TextDocument } from 'vscode-languageserver-textdocument';
import fuzzysort from 'fuzzysort';
import {
  parseString,
  type ParseError,
  type Document,
  type Definition,
  type Reference,
  type SourceLocation,
} from '@modeler/parser';
import {
  ProjectSymbolTable,
  Resolver,
  resolveManifest,
  loadProjectFromOpenDocuments,
  collectReferences,
  nestedDefs,
  ReferenceIndex,
  PackageGraphBuilder,
  enclosingQnameOf,
  inferPackageFromUri,
  synthesizeMappings,
  type ResolvedManifest,
  type PackageGraph,
} from '@modeler/semantics';
import { buildProjectModelGraph, emptyLayout, buildSymbolDetail, type LayoutFile, type RenderableSchemaCode } from './model-graph.js';
import { listGraphs, getGraph, getPackageGraphFromCache } from './graph-methods.js';
import { buildAddObjectEdit, buildRemoveObjectEdit, buildCreateGraphEdit, buildSetLayoutEdit, buildRenameSymbolEdit, buildRenamePackageEdit, type WorkspaceEdit } from '@modeler/edit';
import { getReferenceCompletions, extractQueryPrefix } from './completion-reference.js';
import {
  getPropertyNameCompletions,
  getSchemaCodeCompletions,
  getDefKindCompletions,
  getPackageNameCompletions,
  detectCompletionContext,
} from './completion-property.js';
import { buildDocumentSymbols } from './document-symbol.js';
import { loadCompletionConfig, getCompletionConfig, invalidateCompletionConfig } from './config-completion.js';
import { formatDocument, DEFAULT_FORMAT_CONFIG, type FormatConfig } from '@modeler/format';
import { lintDocument, lintProject, recommendedConfig, loadLintConfig, type LintDiagnostic, type ResolvedLintConfig } from '@modeler/lint';
import {
  quickFixUnimportedReference,
  quickFixUnusedImport,
  quickFixMissingPackageDeclaration,
  quickFixPackageDeclarationMismatch,
  refactorExtractDefToNewFile,
} from './code-actions.js';
import { getCodeLenses } from './code-lens.js';

export interface ServerOptions {
  /**
   * Optional callback to load the project manifest for a workspace.
   * The stdio entry wires this to `findProjectRoot` + `loadProject` from
   * `@modeler/semantics/node-only`; the browser entry leaves it undefined.
   */
  loadManifest?: (rootUri: string) => Promise<ResolvedManifest>;

  /**
   * Optional callback to pre-load stock vocabulary documents. Each entry's
   * `uri` is used as the URI in the symbol table (typically
   * `stock://<name>.ttr`).
   */
  loadStock?: () => Promise<
    Array<{ uri: string; ast: Document; schemaCode: string; namespace: string }>
  >;

  /**
   * Optional in-memory layout store for browser mode. Maps project root URI
   * to the current LayoutFile.
   */
  layoutStore?: Map<string, LayoutFile>;

  /**
   * Whether auto-import is enabled for reference completion suggestions.
   * When true (default), selecting an unimported symbol inserts the
   * appropriate `import` line. Set to false to disable.
   */
  completionAutoImport?: boolean;
}

type FoundNode =
  | { kind: 'def'; def: Definition; enclosing?: Definition }
  | { kind: 'ref'; ref: Reference; from: Definition };

function isPositionInRange(line: number, char: number, loc: SourceLocation): boolean {
  if (line < loc.line || line > loc.endLine) return false;
  if (line === loc.line && char < loc.column) return false;
  if (line === loc.endLine && char > loc.endColumn) return false;
  return true;
}

function rangeArea(loc: SourceLocation): number {
  return (loc.endLine - loc.line) * 1000 + (loc.endColumn - loc.column);
}

function sourceLocationToRange(source: SourceLocation) {
  return {
    start: { line: source.line - 1, character: source.column },
    end: { line: source.endLine - 1, character: source.endColumn },
  };
}

export function createServerConnection(
  connection: Connection,
  opts: ServerOptions = {}
): void {
  const documents = new TextDocuments(TextDocument);

  const projectSymbols = new ProjectSymbolTable();
  let manifest: ResolvedManifest = resolveManifest(undefined, '');
  let resolver = new Resolver(projectSymbols);
  const refIndex = new ReferenceIndex();
  let packageGraph: PackageGraph | null = null;
  let supportsConfiguration = false;
  // Resolved `.ttrlint.toml` config, cached and refreshed asynchronously
  // (publishDiagnostics is sync). Starts at the legacy-aware recommended config.
  let cachedLintConfig: ResolvedLintConfig = recommendedConfig({});

  /**
   * Locate the most-specific AST node under the cursor.
   *
   * Walks every top-level def, recurses into nested attribute / column /
   * resultColumn children, and inspects every reference-valued property.
   * Returns the smallest range that contains the cursor; prefers
   * reference matches over their enclosing defs (so Cmd-clicking on
   * `er.entity.foo` inside a `nameAttribute:` lands on the reference).
   */
  function findNodeAtPosition(
    ast: Document,
    position: { line: number; character: number }
  ): FoundNode | null {
    const line = position.line + 1;
    const char = position.character;
    let best: FoundNode | null = null;
    let bestArea = Number.POSITIVE_INFINITY;

    function consider(node: FoundNode, source: SourceLocation): void {
      if (!isPositionInRange(line, char, source)) return;
      const area = rangeArea(source);
      if (area < bestArea) {
        best = node;
        bestArea = area;
      }
    }

    function visit(def: Definition, enclosing?: Definition): void {
      if (!isPositionInRange(line, char, def.source)) return;
      consider({ kind: 'def', def, enclosing }, def.source);

      for (const child of nestedDefs(def)) {
        visit(child, def);
      }
      for (const ref of collectReferences(def)) {
        consider({ kind: 'ref', ref, from: def }, ref.source);
      }
    }

    for (const def of ast.definitions) visit(def);
    return best;
  }

  function qnameOf(def: Definition, ast: Document, enclosing?: Definition): string {
    // Symbol-table keys are package-qualified (B2), so the package prefix must
    // lead. For package-less files `pkg` is '' and is filtered out, leaving the
    // v1 shape unchanged.
    const pkg = ast.packageDecl?.name ?? '';
    // TODO(pkg-schema-defaults): the `?? 'db'` display/lookup defaults in this
    // file are presentation-layer and out of scope for the schema-by-kind
    // correctness fix; they should later derive via defaultSchemaForKind.
    const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
    const namespace = ast.schemaDirective?.namespace ?? '';
    const tail = enclosing ? [enclosing.name, def.name] : [def.name];
    return [pkg, schemaCode, namespace, ...tail].filter((s) => s !== '').join('.');
  }

  // A symbol's stored `source` is the whole `def <kind> <name> { … }` span, but
  // rename / prepareRename need just the name token. Locate it after the
  // `def <kind> ` prefix on the definition's first line. Falls back to the full
  // span if the shape is unexpected.
  function defNameSource(content: string, symbol: { name: string; source: SourceLocation }): SourceLocation {
    const line = content.split('\n')[symbol.source.line - 1] ?? '';
    const m = line.slice(symbol.source.column).match(/^def\s+\S+\s+/);
    if (!m) return symbol.source;
    const col = symbol.source.column + m[0].length;
    return { ...symbol.source, endLine: symbol.source.line, column: col, endColumn: col + symbol.name.length };
  }

  function symbolKindOf(kind: string): SymbolKind {
    if (kind === 'entity' || kind === 'table' || kind === 'view') return SymbolKind.Class;
    if (kind === 'column' || kind === 'attribute') return SymbolKind.Field;
    if (kind === 'procedure' || kind === 'query') return SymbolKind.Method;
    return SymbolKind.File;
  }

  function getDocument(uri: string): TextDocument | undefined {
    return documents.get(uri);
  }

  function parseDocument(content: string, uri: string): Document | undefined {
    const result = parseString(content, uri);
    return result.ast;
  }

  function rebuildSemantics(projectRoot?: string): void {
    resolver = new Resolver(projectSymbols);
    if (projectRoot) manifest.projectRoot = projectRoot;
    packageGraph = null;
  }

  function getPackageGraph(): PackageGraph {
    if (!packageGraph) {
      const docs = new Map<string, Document>();
      for (const uri of documents.keys()) {
        const doc = parseDocument(documents.get(uri)?.getText() ?? '', uri);
        if (doc) docs.set(uri, doc);
      }
      packageGraph = new PackageGraphBuilder(projectSymbols, docs).build();
    }
    return packageGraph;
  }

  function publishDiagnostics(uri: string, content: string): void {
    if (uri.endsWith('.ttrl')) return;

    const result = parseString(content, uri);
    const diagnostics: Diagnostic[] = result.errors.map((err: ParseError) => ({
      range: sourceLocationToRange(err.source),
      message: err.message,
      severity: severityToLsp(err.severity),
      code: err.code,
      source: 'modeler',
    }));

    if (result.ast) {
      const pkgGraph = getPackageGraph();
      const deps = { manifest, symbols: projectSymbols, resolver };
      const config = lintConfig();

      // Document-scoped rules for this file.
      const docDiags = lintDocument(uri, result.ast, deps, config);
      // Project-scoped rules (duplicate-definition/-mapping, circular), bucketed
      // by uri and filtered to this file — mirrors the old `.filter(d.source.file)`.
      const docs = getProjectDocuments();
      const projectByUri = lintProject(docs, pkgGraph, deps, config);
      const projDiags = projectByUri.get(uri) ?? [];

      for (const d of [...docDiags, ...projDiags]) {
        diagnostics.push(toLspDiagnostic(d));
      }
    }

    connection.sendDiagnostics({ uri, diagnostics });
  }

  function lintConfig(): ResolvedLintConfig {
    return cachedLintConfig;
  }

  /**
   * Re-read `.ttrlint.toml` (legacy `[lint]` fallback), cache it, surface its
   * config-level diagnostics, and re-lint open documents. Safe to call whenever
   * the manifest/project root changes or the config file is edited.
   */
  async function refreshLintConfig(): Promise<void> {
    cachedLintConfig = await loadLintConfig(manifest.projectRoot, manifest.lint);
    publishConfigDiagnostics();
    for (const doc of documents.all()) {
      publishDiagnostics(doc.uri, doc.getText());
    }
  }

  /** Publish ttrlint/* config diagnostics on the `.ttrlint.toml` document. */
  function publishConfigDiagnostics(): void {
    const root = manifest.projectRoot;
    if (!root) return;
    const configUri = `file://${root.replace(/\/$/, '')}/.ttrlint.toml`;
    connection.sendDiagnostics({
      uri: configUri,
      diagnostics: cachedLintConfig.diagnostics.map(toLspDiagnostic),
    });
  }

  function getProjectDocuments(): Map<string, Document> {
    const docs = new Map<string, Document>();
    for (const u of documents.keys()) {
      const doc = parseDocument(documents.get(u)?.getText() ?? '', u);
      if (doc) docs.set(u, doc);
    }
    return docs;
  }

  function toLspDiagnostic(d: LintDiagnostic): Diagnostic {
    return {
      range: sourceLocationToRange(d.source),
      message: d.message,
      severity: severityToLsp(d.severity),
      code: d.code,
      source: 'modeler',
    };
  }

  function severityToLsp(s: 'error' | 'warning' | 'info' | 'off'): DiagnosticSeverity {
    return s === 'warning' ? DiagnosticSeverity.Warning
      : s === 'info' ? DiagnosticSeverity.Information
      // 'off' is unreachable here (off rules are never emitted), but keeps the
      // mapping total for the lint Severity type.
      : s === 'off' ? DiagnosticSeverity.Information
      : DiagnosticSeverity.Error;
  }

  function updateSymbolTable(uri: string, content: string): void {
    const result = parseString(content, uri);
    if (!result.ast) return;
    // '' (no directive) ⇒ the semantics layer derives the schema per definition
    // from its kind (defaultSchemaForKind). Do NOT default to 'db' here.
    const schemaCode = result.ast.schemaDirective?.schemaCode ?? '';
    const namespace = result.ast.schemaDirective?.namespace ?? '';
    const packageName = result.ast.packageDecl?.name ?? '';
    projectSymbols.upsertDocument(uri, result.ast, schemaCode, namespace, packageName);
    synthesizeMappings(projectSymbols, uri, result.ast);
    refIndex.upsertDocument(uri, result.ast, schemaCode, namespace, resolver, packageName);
  }

  documents.onDidOpen(async (event: TextDocumentChangeEvent<TextDocument>) => {
    // First open in a workspace: ask the host for the manifest. Cheap when
    // no callback is wired (browser worker).
    if (opts.loadManifest) {
      try {
        const root = event.document.uri.replace(/\/[^/]+$/, '');
        const loaded = await opts.loadManifest(root);
        manifest = loaded;
        rebuildSemantics();
        await refreshLintConfig();
      } catch {
        // keep the default manifest
      }
    }
    updateSymbolTable(event.document.uri, event.document.getText());
    publishDiagnostics(event.document.uri, event.document.getText());
  });

  documents.onDidChangeContent((event: TextDocumentChangeEvent<TextDocument>) => {
    updateSymbolTable(event.document.uri, event.document.getText());
    publishDiagnostics(event.document.uri, event.document.getText());
  });

  documents.onDidClose((event: TextDocumentChangeEvent<TextDocument>) => {
    projectSymbols.removeDocument(event.document.uri);
    refIndex.removeDocument(event.document.uri);
    packageGraph = null;
  });

  documents.onDidSave(() => {
    // nothing yet
  });

  connection.onInitialize(async (params: InitializeParams): Promise<InitializeResult> => {
    if (opts.loadStock) {
      try {
        const docs = await opts.loadStock();
        for (const d of docs) {
          projectSymbols.upsertDocument(d.uri, d.ast, d.schemaCode, d.namespace, '');
          synthesizeMappings(projectSymbols, d.uri, d.ast);
        }
      } catch {
        // stock loading is best-effort
      }
    }
    const wsUri = params.workspaceFolders?.[0]?.uri
      ?? params.rootUri
      ?? (params.rootPath ? `file://${params.rootPath}` : null);
    supportsConfiguration = !!params.capabilities?.workspace?.configuration;
    if (wsUri) {
      const projectRoot = wsUri.startsWith('file://') ? new URL(wsUri).pathname : wsUri;
      manifest = resolveManifest(undefined, projectRoot);
      rebuildSemantics(projectRoot);
    }
    return {
      capabilities: {
        textDocumentSync: {
          openClose: true,
          willSave: false,
          save: false,
          change: TextDocumentSyncKind.Full,
        },
        definitionProvider: true,
        referencesProvider: true,
        hoverProvider: true,
        workspaceSymbolProvider: true,
        documentSymbolProvider: true,
        completionProvider: {
          triggerCharacters: ['.'],
          resolveProvider: true,
        },
        renameProvider: { prepareProvider: true },
        documentFormattingProvider: true,
        codeActionProvider: { codeActionKinds: ['quickfix', 'refactor.extract'] },
        codeLensProvider: { resolveProvider: false },
        semanticTokensProvider: {
          legend: {
            tokenTypes: [
              'namespace',
              'type',
              'class',
              'property',
              'string',
              'number',
              'comment',
              'keyword',
              'variable',
              // v1.1 additions (indices 9-12). Appended to keep existing indices stable.
              'packageName',
              'importedSymbol',
              'localSymbol',
              'unimportedReference',
            ],
            tokenModifiers: ['declaration', 'readonly', 'deprecated'],
          },
          full: true,
        },
      },
    };
  });

  connection.onInitialized(async () => {
    if (supportsConfiguration) {
      await loadCompletionConfig(connection);
    }
    await refreshLintConfig();
  });

  connection.onDidChangeConfiguration(async () => {
    if (supportsConfiguration) {
      await loadCompletionConfig(connection);
    } else {
      invalidateCompletionConfig();
    }
  });

  // A `.ttrlint.toml` change re-reads the lint config and re-lints open docs.
  // Real editors: a `.ttrlint.toml` change re-reads the lint config (the client
  // must watch `**/.ttrlint.toml`). The Designer / browser host (and tests) can
  // also trigger a reload deterministically via `modeler/reloadLintConfig`.
  connection.onDidChangeWatchedFiles(async (params) => {
    if (params.changes.some((c) => c.uri.endsWith('.ttrlint.toml'))) {
      await refreshLintConfig();
    }
  });
  connection.onRequest('modeler/reloadLintConfig', async () => {
    await refreshLintConfig();
    return { reloaded: true };
  });

  connection.onRequest('modeler/getProjectInfo', async (params: { textDocument: { uri: string } }) => {
    const allDocs = documents.all();
    const project = loadProjectFromOpenDocuments(
      allDocs.map((d) => ({ uri: d.uri })),
      params.textDocument.uri.replace(/\/[^/]+$/, ''),
      manifest
    );
    return { ...project.manifest, root: project.root, ttrFileCount: project.ttrFiles.length };
  });

  // Lets hosts without a workspace folder (the browser worker uses rootUri:null)
  // declare the project root after init. Package inference is relative to this
  // root; without it, nested files mis-infer their package and emit spurious
  // ttr/package-declaration-mismatch errors. Re-validates already-open docs so
  // it is order-independent with respect to didOpen.
  connection.onRequest('modeler/setProjectRoot', async (params: { projectRoot: string }) => {
    const root = params.projectRoot.startsWith('file://')
      ? new URL(params.projectRoot).pathname
      : params.projectRoot;
    manifest = resolveManifest(undefined, root);
    rebuildSemantics(root);
    for (const doc of documents.all()) {
      updateSymbolTable(doc.uri, doc.getText());
    }
    // Re-reads `.ttrlint.toml` under the new root and re-publishes all open docs.
    await refreshLintConfig();
    return { projectRoot: root };
  });

  connection.onRequest('modeler/getModelGraph', (params: { textDocument: { uri: string }; schema: RenderableSchemaCode }) => {
    if (params.schema !== 'db' && params.schema !== 'er') {
      return { schemaCode: params.schema, nodes: [], edges: [] };
    }

    const allDocs = documents.all();
    const asts: Document[] = [];
    for (const doc of allDocs) {
      const content = doc.getText();
      const result = parseString(content, doc.uri);
      if (result.ast) asts.push(result.ast);
    }

    return buildProjectModelGraph(asts, params.schema, manifest.preferredLanguage);
  });

  connection.onRequest('modeler/listGraphs', (_params: { projectRoot: string }) => {
    const docMap = new Map<string, string>();
    for (const doc of documents.all()) docMap.set(doc.uri, doc.getText());
    const allDocs: import('@modeler/parser').Document[] = [];
    for (const doc of documents.all()) {
      const result = parseString(doc.getText(), doc.uri);
      if (result.ast) allDocs.push(result.ast);
    }
    const qnameToDef = new Map<string, { def: import('@modeler/parser').Definition; schemaCode: string; namespace: string }>();
    for (const ast of allDocs) {
      const schemaCode = ast.schemaDirective?.schemaCode ?? 'er';
      const namespace = ast.schemaDirective?.namespace ?? '';
      for (const def of ast.definitions) {
        const qname = [schemaCode, namespace, def.name].filter(s => s !== '').join('.');
        qnameToDef.set(qname, { def, schemaCode, namespace });
      }
    }
    return { graphs: listGraphs(docMap, qnameToDef) };
  });

  connection.onRequest('modeler/getGraph', (_params: { uri: string }) => {
    const docMap = new Map<string, string>();
    for (const doc of documents.all()) docMap.set(doc.uri, doc.getText());
    return getGraph(_params.uri, docMap, manifest.preferredLanguage);
  });

  connection.onRequest('modeler/getPackageGraph', () => {
    const pkgGraph = getPackageGraph();
    return getPackageGraphFromCache(pkgGraph);
  });

  connection.onRequest('modeler/getLayout', async (_params: { graphUri?: string; projectRoot?: string }): Promise<LayoutFile> => {
    if (_params.graphUri) {
      const content = documents.get(_params.graphUri)?.getText();
      if (content) {
        const result = parseString(content, _params.graphUri);
        if (result.ast?.graph?.layout) {
          const layout = result.ast.graph.layout;
          const viewport = layout.viewport ? {
            zoom: layout.viewport.zoom,
            panX: layout.viewport.panX,
            panY: layout.viewport.panY,
            displayMode: layout.viewport.displayMode as 'with-types' | 'just-names' | 'with-constraints',
          } : undefined;
          return {
            version: 1,
            viewport,
            nodes: layout.nodes ?? {},
            edges: (layout.edges ?? {}) as Record<string, { bendPoints: [number, number][] }>,
          } as LayoutFile;
        }
      }
      return emptyLayout();
    }
    if (opts.layoutStore && _params.projectRoot) {
      return opts.layoutStore.get(_params.projectRoot) ?? emptyLayout();
    }
    return emptyLayout();
  });

  connection.onRequest('modeler/setLayout', async (_params: { graphUri?: string; projectRoot?: string; layout: LayoutFile }): Promise<WorkspaceEdit> => {
    if (_params.graphUri) {
      const content = documents.get(_params.graphUri)?.getText();
      if (!content) return { documentChanges: [] };
      return buildSetLayoutEdit(content, _params.graphUri, { nodes: _params.layout.nodes, edges: _params.layout.edges, viewport: _params.layout.viewport });
    }
    if (opts.layoutStore && _params.projectRoot) {
      opts.layoutStore.set(_params.projectRoot, _params.layout);
      return { documentChanges: [] };
    }
    return { documentChanges: [] };
  });

  connection.onRequest('modeler/exportLayout', async (_params: { graphUri?: string; projectRoot?: string }): Promise<LayoutFile> => {
    if (_params.graphUri) {
      return connection.sendRequest('modeler/getLayout', { graphUri: _params.graphUri }) as Promise<LayoutFile>;
    }
    return connection.sendRequest('modeler/getLayout', { projectRoot: _params.projectRoot }) as Promise<LayoutFile>;
  });

  connection.onRequest('modeler/applyGraphEdit', (_params: unknown): { ok: false; reason: string } => {
    return { ok: false, reason: 'edit-mode-not-available-in-v1' };
  });

  connection.onRequest('modeler/addObjectToGraph', (_params: { uri: string; qname: string; autoImport: boolean }) => {
    const content = documents.get(_params.uri)?.getText();
    if (!content) return { documentChanges: [] };
    let packageToImport: string | null = null;
    if (_params.autoImport) {
      const symbol = projectSymbols.get(_params.qname);
      if (symbol?.packageName) {
        packageToImport = symbol.packageName;
      } else {
        const firstSegment = _params.qname.split('.')[0];
        const schemaCodes = ['db', 'er', 'map', 'query', 'cnc'];
        if (!schemaCodes.includes(firstSegment)) {
          packageToImport = firstSegment;
        }
      }
    }
    return buildAddObjectEdit(content, _params.uri, _params.qname, packageToImport);
  });

  connection.onRequest('modeler/removeObjectFromGraph', (_params: { uri: string; qname: string; pruneUnusedImport: boolean }) => {
    const content = documents.get(_params.uri)?.getText();
    if (!content) return { documentChanges: [] };
    return buildRemoveObjectEdit(content, _params.uri, _params.qname, _params.pruneUnusedImport);
  });

  connection.onRequest('modeler/createGraph', (_params: { uri: string; name: string; schema: 'db' | 'er' | 'map' | 'query' | 'cnc'; packages: string[]; objects: string[]; description?: string; tags?: string[] }) => {
    if (!_params.uri.endsWith('.ttrg')) {
      return { documentChanges: [] };
    }
    return buildCreateGraphEdit(_params);
  });

  connection.onRequest('modeler/getSymbolDetail', (params: { qname: string }) => {
    return buildSymbolDetail(params.qname, projectSymbols, resolver, refIndex, manifest, (uri) => {
      const doc = documents.get(uri);
      return doc ? doc.getText() : null;
    }, (content, uri) => parseString(content, uri));
  });

  connection.onRequest('modeler/listSymbols', (params: { kinds?: string[]; limit?: number }) => {
    const limit = params.limit ?? 500;
    const allowed = params.kinds ? new Set(params.kinds) : null;
    return projectSymbols.all()
      .filter((s) => !allowed || allowed.has(s.kind))
      .slice(0, limit)
      .map((s) => ({ qname: s.qname, kind: s.kind, name: s.name, packageName: s.packageName ?? null }));
  });

  connection.onDefinition((params) => {
    const uri = params.textDocument.uri;
    const doc = getDocument(uri);
    if (!doc) return null;

    const ast = parseDocument(doc.getText(), uri);
    if (!ast) return null;

    const found = findNodeAtPosition(ast, params.position);
    if (!found) return null;

    const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
    const namespace = ast.schemaDirective?.namespace ?? '';

    if (found.kind === 'ref') {
      const res = resolver.resolveReference(
        { path: found.ref.path, parts: found.ref.parts },
        { schemaCode, namespace, enclosingQname: enclosingQnameOf(found.from, schemaCode, namespace, ast.packageDecl?.name ?? ''), packageName: ast.packageDecl?.name ?? '' }
      );
      if (!res.resolved) return null;
      return {
        uri: res.symbol.documentUri,
        range: sourceLocationToRange(res.symbol.source),
      } satisfies Location;
    }

    // cursor on a def: return its canonical declaration location
    const qname = qnameOf(found.def, ast, found.enclosing);
    const symbol = projectSymbols.get(qname);
    if (!symbol) return null;
    return {
      uri: symbol.documentUri,
      range: sourceLocationToRange(symbol.source),
    } satisfies Location;
  });

  connection.onReferences((params) => {
    const uri = params.textDocument.uri;
    const doc = getDocument(uri);
    if (!doc) return [];

    const ast = parseDocument(doc.getText(), uri);
    if (!ast) return [];

    const found = findNodeAtPosition(ast, params.position);
    if (!found) return [];

    const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
    const namespace = ast.schemaDirective?.namespace ?? '';

    let targetQname: string | null = null;
    if (found.kind === 'ref') {
      const res = resolver.resolveReference(
        { path: found.ref.path, parts: found.ref.parts },
        { schemaCode, namespace, enclosingQname: enclosingQnameOf(found.from, schemaCode, namespace, ast.packageDecl?.name ?? ''), packageName: ast.packageDecl?.name ?? '' }
      );
      if (res.resolved) targetQname = res.symbol.qname;
    } else {
      targetQname = qnameOf(found.def, ast, found.enclosing);
    }

    if (!targetQname) return [];

    const locations: Location[] = [];

    if (params.context?.includeDeclaration ?? true) {
      const declSymbol = projectSymbols.get(targetQname);
      if (declSymbol) {
        locations.push({
          uri: declSymbol.documentUri,
          range: sourceLocationToRange(declSymbol.source),
        });
      }
    }

    for (const refLoc of refIndex.findByQname(targetQname)) {
      locations.push({
        uri: refLoc.documentUri,
        range: sourceLocationToRange(refLoc.source),
      });
    }

    return locations;
  });

  connection.onHover((params) => {
    const uri = params.textDocument.uri;
    const doc = getDocument(uri);
    if (!doc) return null;

    const ast = parseDocument(doc.getText(), uri);
    if (!ast) return null;

    const found = findNodeAtPosition(ast, params.position);
    if (!found) return null;

    const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
    const namespace = ast.schemaDirective?.namespace ?? '';

    let qname: string | null = null;
    let def: Definition | null = null;

    if (found.kind === 'ref') {
      const res = resolver.resolveReference(
        { path: found.ref.path, parts: found.ref.parts },
        { schemaCode, namespace, enclosingQname: enclosingQnameOf(found.from, schemaCode, namespace, ast.packageDecl?.name ?? ''), packageName: ast.packageDecl?.name ?? '' }
      );
      if (!res.resolved) return null;
      qname = res.symbol.qname;
    } else {
      qname = qnameOf(found.def, ast, found.enclosing);
      def = found.def;
    }

    const symbol = projectSymbols.get(qname);
    if (!symbol) return null;

    const lines: string[] = [];
    lines.push(`**${symbol.qname}** *(${symbol.kind})*`);
    if (def && 'description' in def && def.description) {
      const desc = def.description;
      if (desc.kind === 'string' || desc.kind === 'tripleString') {
        lines.push(desc.value);
      }
    }
    const fileBaseName = symbol.documentUri.split('/').pop() ?? symbol.documentUri;
    lines.push(`- **Defined at:** ${fileBaseName}:${symbol.source.line}`);

    return {
      contents: { kind: 'markdown', value: lines.join('\n\n') },
    } satisfies Hover;
  });

  connection.onPrepareRename((params) => {
    const uri = params.textDocument.uri;
    const doc = getDocument(uri);
    if (!doc) return null;

    const ast = parseDocument(doc.getText(), uri);
    if (!ast) return null;

    const found = findNodeAtPosition(ast, params.position);
    if (!found || found.kind === 'ref' && !found.ref) return null;

    const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
    const namespace = ast.schemaDirective?.namespace ?? '';
    const packageName = ast.packageDecl?.name ?? '';

    let qname: string | null = null;
    if (found.kind === 'def') {
      qname = qnameOf(found.def, ast, found.enclosing);
    } else if (found.kind === 'ref') {
      const res = resolver.resolveReference(
        { path: found.ref.path, parts: found.ref.parts },
        { schemaCode, namespace, enclosingQname: enclosingQnameOf(found.from, schemaCode, namespace, packageName), packageName }
      );
      if (res.resolved) qname = res.symbol.qname;
    }

    if (!qname) return null;
    const symbol = projectSymbols.get(qname);
    if (!symbol) return null;

    // The editable range is the token under the cursor: the def's name span, or
    // the reference span when renaming from a use site.
    const range = found.kind === 'def'
      ? sourceLocationToRange(defNameSource(getDocument(symbol.documentUri)?.getText() ?? doc.getText(), symbol))
      : sourceLocationToRange(found.ref.source);
    return { range, placeholder: symbol.name };
  });

  connection.onRenameRequest((params) => {
    const uri = params.textDocument.uri;
    const doc = getDocument(uri);
    if (!doc) return { documentChanges: [] };

    const ast = parseDocument(doc.getText(), uri);
    if (!ast) return { documentChanges: [] };

    // Package rename only when the cursor is on the `package` declaration line;
    // otherwise fall through to symbol rename. (Previously this fired for any
    // file that had a package declaration, so renaming an entity renamed the
    // package instead.)
    if (ast.packageDecl && params.position.line === ast.packageDecl.source.line - 1) {
      const content = doc.getText();
      const lines = content.split('\n');
      const pkgLine = ast.packageDecl.source.line - 1;
      const lineText = lines[pkgLine] ?? '';
      const nameStart = lineText.indexOf(ast.packageDecl.name);
      if (nameStart >= 0) {
        const allDocs = new Map<string, string>();
        for (const d of documents.all()) allDocs.set(d.uri, d.getText());
        return buildRenamePackageEdit({
          oldPackageName: ast.packageDecl.name,
          newPackageName: params.newName,
          allDocuments: allDocs,
        });
      }
    }

    const found = findNodeAtPosition(ast, params.position);
    if (!found) return null;

    const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
    const namespace = ast.schemaDirective?.namespace ?? '';
    const packageName = ast.packageDecl?.name ?? '';

    let targetQname: string | null = null;
    if (found.kind === 'def') {
      targetQname = qnameOf(found.def, ast, found.enclosing);
    } else if (found.kind === 'ref') {
      const res = resolver.resolveReference(
        { path: found.ref.path, parts: found.ref.parts },
        { schemaCode, namespace, enclosingQname: enclosingQnameOf(found.from, schemaCode, namespace, packageName), packageName }
      );
      if (res.resolved) targetQname = res.symbol.qname;
    }

    if (!targetQname) return { documentChanges: [] };
    const symbol = projectSymbols.get(targetQname);
    if (!symbol) return { documentChanges: [] };

    // Surface invalid / colliding renames as LSP errors (I1.5) so VS Code shows
    // a refusal dialog, instead of silently producing an empty edit.
    if (params.newName && !/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(params.newName)) {
      throw new ResponseError(ErrorCodes.InvalidParams, `'${params.newName}' is not a valid identifier.`);
    }
    const newBareName = params.newName ?? symbol.name;
    const conflictCheck = projectSymbols.findByName(newBareName).filter(e => e.qname !== targetQname && e.qname.endsWith('.' + newBareName));
    if (conflictCheck.length > 0) {
      throw new ResponseError(ErrorCodes.InvalidParams, `Cannot rename to '${newBareName}': a symbol with that name already exists (${conflictCheck[0].qname}).`);
    }

    const allRefs = refIndex.findByQname(targetQname);
    const ttrgDocs = new Map<string, string>();
    for (const d of documents.all()) {
      ttrgDocs.set(d.uri, d.getText());
    }
    // Read the def's own document (it may differ from the file the rename was
    // invoked in) and target the name span, not the whole-definition span.
    const defContent = getDocument(symbol.documentUri)?.getText() ?? doc.getText();

    return buildRenameSymbolEdit({
      oldQname: targetQname,
      newBareName,
      defEntry: { ...symbol, source: defNameSource(defContent, symbol) },
      defDocumentContent: defContent,
      references: allRefs,
      ttrgDocuments: ttrgDocs,
    });
  });

  connection.onWorkspaceSymbol((params) => {
    const query = params.query ?? '';
    const allSymbols = projectSymbols.all();

    if (!query) {
      return allSymbols.slice(0, 100).map((symbol) => ({
        name: symbol.qname,
        kind: symbolKindOf(symbol.kind),
        location: {
          uri: symbol.documentUri,
          range: sourceLocationToRange(symbol.source),
        },
      })) satisfies SymbolInformation[];
    }

    const scored = fuzzysort.go(query, allSymbols, {
      keys: ['qname', 'name'],
      limit: 100,
    });

    const queryLower = query.toLowerCase();

    // H3.4: per-package query mode. If query ends with '.', treat it as a
    // package-prefix filter (e.g. "billing." → all symbols in billing.*).
    // Match prefix + '.' so "billing." doesn't also match "billingsystem.*".
    if (query.endsWith('.')) {
      const prefix = query.slice(0, -1).toLowerCase();
      const packageFiltered = allSymbols.filter((s) => {
        const qnameLower = s.qname.toLowerCase();
        return qnameLower.startsWith(prefix + '.');
      });
      return packageFiltered.slice(0, 100).map((symbol) => ({
        name: symbol.qname,
        kind: symbolKindOf(symbol.kind),
        location: {
          uri: symbol.documentUri,
          range: sourceLocationToRange(symbol.source),
        },
      })) satisfies SymbolInformation[];
    }

    // Kind-name boost: when the query is a prefix of a definition kind (e.g.
    // "rel" -> "relation"), float every symbol of that kind, drawn from the
    // *full* index, above the fuzzy matches. fuzzysort only searches qname and
    // name, so a kind-name query would otherwise be drowned out: "rel" matches
    // the 111 `er2dbRelation` qnames and saturates the limit before any
    // `relation`-kind def (whose qname is `er.entity.<name>`, no "rel"
    // substring) is ever reached. Gated at 3+ chars so short name-fragment
    // queries aren't hijacked by an accidental kind prefix.
    const isKindQuery = (kind: string): boolean => {
      const k = kind.toLowerCase();
      return k === queryLower || k.startsWith(queryLower);
    };
    const kindMatched =
      query.length >= 3 ? allSymbols.filter((s) => isKindQuery(s.kind)) : [];
    const seen = new Set(kindMatched.map((s) => s.qname));
    const results =
      kindMatched.length > 0
        ? [...kindMatched, ...scored.map((e) => e.obj).filter((s) => !seen.has(s.qname))].slice(0, 100)
        : scored.map((e) => e.obj).slice(0, 100);

    return results.map((symbol) => ({
      name: symbol.qname,
      kind: symbolKindOf(symbol.kind),
      location: {
        uri: symbol.documentUri,
        range: sourceLocationToRange(symbol.source),
      },
    })) satisfies SymbolInformation[];
  });

  connection.onDocumentSymbol((params) => {
    const uri = params.textDocument.uri;
    const doc = getDocument(uri);
    if (!doc) return [];

    const content = doc.getText();
    const result = parseString(content, uri);
    if (!result.ast) return [];

    return buildDocumentSymbols(result.ast);
  });

  connection.onCodeLens((params) => {
    const uri = params.textDocument.uri;
    const doc = getDocument(uri);
    if (!doc) return [];
    const ast = parseDocument(doc.getText(), uri);
    if (!ast) return [];
    return getCodeLenses({ ast, refIndex, projectSymbols });
  });

  connection.onDocumentFormatting(async (params) => {
    const uri = params.textDocument.uri;
    const doc = getDocument(uri);
    if (!doc) return [];
    const content = doc.getText();
    const result = parseString(content, uri);
    if (!result.ast || result.errors.some((e) => e.severity === 'error')) {
      // Don't reformat a file that doesn't parse — we'd risk dropping content.
      return [];
    }

    const config = await loadFormatConfig();
    const formatted = formatDocument(result.ast, content, config);
    if (formatted === content) return [];

    const lines = content.split('\n');
    const endLine = lines.length - 1;
    return [{
      range: { start: { line: 0, character: 0 }, end: { line: endLine, character: lines[endLine].length } },
      newText: formatted,
    }];
  });

  connection.onCodeAction(async (params) => {
    const uri = params.textDocument.uri;
    const doc = getDocument(uri);
    if (!doc) return [];
    const content = doc.getText();
    const ast = parseDocument(content, uri);
    if (!ast) return [];

    const actions: CodeAction[] = [];
    const { inferred } = inferPackageFromUri(uri, manifest.projectRoot);

    for (const diag of params.context.diagnostics) {
      switch (diag.code) {
        case 'ttr/unused-import':
          actions.push(quickFixUnusedImport(uri, diag));
          break;
        case 'ttr/missing-package-declaration':
          if (inferred) actions.push(quickFixMissingPackageDeclaration(uri, inferred, diag));
          break;
        case 'ttr/package-declaration-mismatch': {
          const a = inferred ? quickFixPackageDeclarationMismatch(uri, content, ast, inferred, diag) : null;
          if (a) actions.push(a);
          break;
        }
        case 'ttr/unimported-reference': {
          const found = findNodeAtPosition(ast, diag.range.start);
          if (found && found.kind === 'ref') {
            const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
            const namespace = ast.schemaDirective?.namespace ?? '';
            const packageName = ast.packageDecl?.name ?? '';
            const res = resolver.resolveReference(
              { path: found.ref.path, parts: found.ref.parts },
              { schemaCode, namespace, enclosingQname: enclosingQnameOf(found.from, schemaCode, namespace, packageName), packageName },
            );
            if (res.resolved && res.symbol.packageName) {
              const a = quickFixUnimportedReference(uri, content, ast, res.symbol.packageName, diag);
              if (a) actions.push(a);
            }
          }
          break;
        }
      }
    }

    // Refactor: extract the top-level def under the cursor into its own file.
    const atCursor = findNodeAtPosition(ast, params.range.start);
    if (atCursor && atCursor.kind === 'def' && !atCursor.enclosing) {
      const a = refactorExtractDefToNewFile(uri, content, atCursor.def, ast, await loadFormatConfig());
      if (a) actions.push(a);
    }

    return actions;
  });

  async function loadFormatConfig(): Promise<FormatConfig> {
    if (!supportsConfiguration) return DEFAULT_FORMAT_CONFIG;
    try {
      const cfg = await connection.sendRequest('workspace/configuration', {
        items: [{ section: 'modeler.format' }],
      }) as Array<Record<string, unknown> | null>;
      const m = cfg[0] ?? {};
      const sep = m['separator'];
      return {
        separator: sep === 'newline' || sep === 'comma' || sep === 'preserve' ? sep : DEFAULT_FORMAT_CONFIG.separator,
        alignKeys: typeof m['alignKeys'] === 'boolean' ? m['alignKeys'] : DEFAULT_FORMAT_CONFIG.alignKeys,
        indentSpaces: typeof m['indentSpaces'] === 'number' ? m['indentSpaces'] : DEFAULT_FORMAT_CONFIG.indentSpaces,
        width: typeof m['width'] === 'number' ? m['width'] : DEFAULT_FORMAT_CONFIG.width,
      };
    } catch {
      return DEFAULT_FORMAT_CONFIG;
    }
  }

  /**
   * Emit one `class`-typed `declaration`-modified semantic token per
   * definition name. The token's start is computed by scanning the def's
   * opening line for the name immediately after the def-kind keyword.
   */
  connection.onRequest('textDocument/semanticTokens/full', (params) => {
    const uri = params.textDocument.uri;
    const doc = getDocument(uri);
    if (!doc) return { data: [] };

    const content = doc.getText();
    const result = parseString(content, uri);
    if (!result.ast) return { data: [] };

    const ast = result.ast;
    const lines = content.split('\n');

    // Token type indices into the legend (see initialize). 2=class, 9=packageName,
    // 10=importedSymbol, 11=localSymbol, 12=unimportedReference.
    interface Tok { line: number; char: number; len: number; type: number; mod: number }
    const toks: Tok[] = [];

    function emitForDef(def: Definition): void {
      const lineIndex = def.source.line - 1; // 0-based
      const lineText = lines[lineIndex] ?? '';
      const nameStart = locateName(lineText, def.name, def.source.column);
      if (nameStart >= 0) toks.push({ line: lineIndex, char: nameStart, len: def.name.length, type: 2, mod: 1 });
      for (const child of nestedDefs(def)) emitForDef(child);
    }
    for (const def of ast.definitions) emitForDef(def);

    // package declaration qname → packageName.
    if (ast.packageDecl) {
      const pd = ast.packageDecl;
      toks.push({ line: pd.source.line - 1, char: pd.source.column, len: pd.name.length, type: 9, mod: 0 });
    }

    // References → localSymbol (same package) / importedSymbol (package imported) /
    // unimportedReference (resolved via package search, not imported).
    const currentPkg = ast.packageDecl?.name ?? '';
    const imports = ast.imports ?? [];
    for (const refLoc of refIndex.getForDocument(uri)) {
      const targetPkg = projectSymbols.get(refLoc.targetQname)?.packageName ?? '';
      let type: number;
      if (targetPkg === currentPkg) {
        type = 11; // localSymbol
      } else {
        // Imported if a named import targets this exact symbol, or a wildcard
        // import targets its package.
        const imported = imports.some((imp) =>
          (!imp.wildcard && imp.target === refLoc.targetQname) || (imp.wildcard && imp.target === targetPkg));
        type = imported ? 10 : 12; // importedSymbol : unimportedReference
      }
      const s = refLoc.source;
      const len = s.endLine === s.line ? s.endColumn - s.column : (lines[s.line - 1]?.length ?? 0) - s.column;
      if (len > 0) toks.push({ line: s.line - 1, char: s.column, len, type, mod: 0 });
    }

    // SemanticTokensBuilder requires tokens in (line, char) order.
    toks.sort((a, b) => a.line - b.line || a.char - b.char);
    const builder = new SemanticTokensBuilder();
    for (const t of toks) builder.push(t.line, t.char, t.len, t.type, t.mod);
    return builder.build();
  });

  connection.onCompletion(async (params) => {
    const uri = params.textDocument.uri;
    const doc = getDocument(uri);
    if (!doc) return { isIncomplete: false, items: [] };

    const content = doc.getText();
    const result = parseString(content, uri);
    if (!result.ast) return { isIncomplete: false, items: [] };

    const context = detectCompletionContext({
      position: params.position,
      content,
      doc: result.ast,
    });

    if (context === 'reference') {
      const query = extractQueryPrefix(content, params.position);

      const config = getCompletionConfig();
      const autoImport = opts.completionAutoImport ?? config.autoImport;

      const completions = getReferenceCompletions({
        position: params.position,
        content,
        doc: result.ast,
        projectSymbols,
        autoImport,
        query,
      });

      return completions ?? { isIncomplete: false, items: [] };
    }

    if (context === 'property') {
      return getPropertyNameCompletions({
        position: params.position,
        content,
        doc: result.ast,
      }) ?? { isIncomplete: false, items: [] };
    }

    if (context === 'schemaCode') {
      return getSchemaCodeCompletions({
        position: params.position,
        content,
        doc: result.ast,
      }) ?? { isIncomplete: false, items: [] };
    }

    if (context === 'defKind') {
      return getDefKindCompletions({
        position: params.position,
        content,
        doc: result.ast,
      }) ?? { isIncomplete: false, items: [] };
    }

    if (context === 'packageName') {
      const projectPackages = projectSymbols.listPackages();
      const projectRoot = manifest.projectRoot ?? '';
      return getPackageNameCompletions({
        position: params.position,
        content,
        doc: result.ast,
        projectPackages,
        documentUri: uri,
        projectRoot,
        projectSymbols,
      }) ?? { isIncomplete: false, items: [] };
    }

    return { isIncomplete: false, items: [] };
  });

  connection.onExit(() => {
    // nothing cleanup needed
  });

  documents.listen(connection);
}

/**
 * Find the column where `name` appears after `startCol` on the line. Returns
 * -1 if the name isn't found (e.g. on a continuation line where the def
 * keyword and name are on different lines).
 */
function locateName(lineText: string, name: string, startCol: number): number {
  // Search forward from startCol for the first occurrence of `name` as a
  // whole word. For inline defs (`def attribute id { ... }`), `name` is
  // usually a few tokens to the right of the def's first column.
  const idx = lineText.indexOf(name, startCol);
  if (idx < 0) return -1;
  // Sanity: prefer the name when preceded by whitespace or the def-kind word.
  const before = idx === 0 ? '' : lineText[idx - 1];
  if (before && /[A-Za-z0-9_]/.test(before)) {
    // adjacent to an identifier — try a later occurrence
    return lineText.indexOf(name, idx + name.length);
  }
  return idx;
}
