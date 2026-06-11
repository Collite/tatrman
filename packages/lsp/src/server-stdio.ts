import { createServerConnection } from './server.js';
import { createConnection, ProposedFeatures } from 'vscode-languageserver/lib/node/main.js';
import { findProjectRoot, loadProject, loadStockVocabularies } from '@modeler/semantics/node-only';

const connection = createConnection(ProposedFeatures.all, process.stdin, process.stdout);

createServerConnection(connection, {
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
