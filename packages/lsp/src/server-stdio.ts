import { createServerConnection } from './server.js';
import { createConnection, ProposedFeatures } from 'vscode-languageserver/lib/node/main.js';
import { findProjectRoot, loadProject, loadStockVocabularies } from '@modeler/semantics/node-only';
// Desktop-only: the SQL parsers + ref extraction. E11 keeps these out of the
// browser Worker bundle, so this import lives in the stdio entry — never server.ts.
import { parseSql, extract } from '@modeler/sql';
import type { SqlDialect } from '@modeler/parser';

const connection = createConnection(ProposedFeatures.all, process.stdin, process.stdout);

createServerConnection(connection, {
  analyzeSqlBlock(value: string, dialect: SqlDialect) {
    const { tree } = parseSql(value, dialect);
    return tree ? extract(tree, dialect) : undefined;
  },
  async readConfigFile(path: string): Promise<string | undefined> {
    try {
      const fs = await import('node:fs/promises');
      return await fs.readFile(path, 'utf-8');
    } catch {
      return undefined;
    }
  },
  async loadManifest(rootUri: string): Promise<import('@modeler/semantics').ResolvedManifest> {
    const docPath = rootUri.startsWith('file://') ? rootUri.slice(7) : rootUri;
    const root = await findProjectRoot(docPath, docPath);
    const project = await loadProject(root);
    return project.manifest;
  },
  async scanProjectFiles(root: string) {
    const { pathToFileURL } = await import('node:url');
    const { readFile } = await import('node:fs/promises');
    const project = await loadProject(root);
    const out: Array<{ uri: string; text: string }> = [];
    for (const file of project.ttrFiles) {
      try {
        out.push({ uri: pathToFileURL(file).href, text: await readFile(file, 'utf-8') });
      } catch {
        // skip files that vanish between the walk and the read
      }
    }
    return out;
  },
  async readProjectFile(uri: string): Promise<string | undefined> {
    if (!uri.startsWith('file://')) return undefined;
    try {
      const { fileURLToPath } = await import('node:url');
      const { readFile } = await import('node:fs/promises');
      return await readFile(fileURLToPath(uri), 'utf-8');
    } catch {
      return undefined;
    }
  },
  async loadStock() {
    const vocabs = await loadStockVocabularies(['cnc-roles']);
    const out: Array<{ uri: string; ast: import('@modeler/parser').Document; schemaCode: string; namespace: string }> = [];
    for (const [name, ast] of vocabs) {
      // TODO(pkg-schema-defaults): stock-vocab files always declare `schema cnc`,
      // so this default never fires; presentation-layer, out of scope for the
      // schema-by-kind fix. Should later use defaultSchemaForKind.
      const schemaCode = ast.schemaDirective?.schemaCode ?? 'cnc';
      const namespace = ast.schemaDirective?.namespace ?? 'role';
      out.push({ uri: `stock://${name}.ttr`, ast, schemaCode, namespace });
    }
    return out;
  },
});

connection.listen();
