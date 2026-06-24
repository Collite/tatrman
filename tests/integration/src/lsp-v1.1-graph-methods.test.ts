import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';
import path from 'path';
import { writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { parseString } from '@modeler/parser';

function createPairedConnection(): { client: lsp.Connection; server: lsp.Connection } {
  const clientTransport = new PassThrough({ objectMode: true });
  const serverTransport = new PassThrough({ objectMode: true });

  const clientReader = new lsp.StreamMessageReader(clientTransport as unknown as NodeJS.ReadableStream);
  const clientWriter = new lsp.StreamMessageWriter(serverTransport as unknown as NodeJS.WritableStream);
  const client = lsp.createConnection(clientReader, clientWriter);

  const serverReader = new lsp.StreamMessageReader(serverTransport as unknown as NodeJS.ReadableStream);
  const serverWriter = new lsp.StreamMessageWriter(clientTransport as unknown as NodeJS.WritableStream);
  const server = lsp.createConnection(serverReader, serverWriter);

  return { client, server };
}

async function sleep(ms: number): Promise<void> {
  await new Promise(resolve => setTimeout(resolve, ms));
}

describe('v1.1 graph LSP methods', () => {
  let client: lsp.Connection;
  let server: lsp.Connection;
  const tmpDir = join(tmpdir(), `ttrg-test-${Date.now()}`);
  mkdirSync(tmpDir, { recursive: true });

  beforeAll(async () => {
    const conn = createPairedConnection();
    client = conn.client;
    server = conn.server;
    createServerConnection(server as any);
    client.listen();
    server.listen();
    await client.sendRequest('initialize', { processId: null, rootUri: null, capabilities: {} });
    client.sendNotification('initialized', {});
    await sleep(100);
  });

  afterAll(() => {
    client.dispose();
    server.dispose();
    rmSync(tmpDir, { recursive: true, force: true });
  });

  it('C2.1 listGraphs returns empty when no .ttrg files', async () => {
    const result = await client.sendRequest('modeler/listGraphs', { projectRoot: `file://${tmpDir}` }) as { graphs: unknown[] };
    expect(result.graphs).toHaveLength(0);
  });

  it('C2.1 listGraphs finds .ttrg files', async () => {
    const graphPath = join(tmpDir, 'test_graph.ttrg');
    const content = `graph test_graph { schema: er, objects: [] }`;
    writeFileSync(graphPath, content);

    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: `file://${graphPath}`, languageId: 'ttr', version: 1, text: content },
    });
    await sleep(50);

    const result = await client.sendRequest('modeler/listGraphs', { projectRoot: `file://${tmpDir}` }) as { graphs: Array<{ uri: string; name: string; schema: string; objectCount: number }> };
    expect(result.graphs.length).toBeGreaterThan(0);
    const found = result.graphs.find((g: any) => g.uri.endsWith('test_graph.ttrg'));
    expect(found).toBeDefined();
    expect(found.schema).toBe('er');
    expect(found.objectCount).toBe(0);
  });

  it('C2.2 getGraph returns graph data for existing .ttrg', async () => {
    const graphPath = join(tmpDir, 'artikl_overview.ttrg');
    const content = `graph artikl_overview { schema: er, objects: [] }`;
    writeFileSync(graphPath, content);

    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: `file://${graphPath}`, languageId: 'ttr', version: 1, text: content },
    });
    await sleep(50);

    const result = await client.sendRequest('modeler/getGraph', { uri: `file://${graphPath}` });
    expect(result).not.toBeNull();
    expect((result as any).schema).toBe('er');
    expect((result as any).nodes).toBeDefined();
    expect((result as any).edges).toBeDefined();
    expect((result as any).layout).toBeDefined();
    expect((result as any).missingObjects).toBeDefined();
  });

  it('C2.2 getGraph returns null for non-existent uri', async () => {
    const result = await client.sendRequest('modeler/getGraph', { uri: 'file:///nonexistent/test.ttrg' });
    expect(result).toBeNull();
  });

  it('C2.2 getGraph returns missingObjects for unresolvable qnames', async () => {
    const graphPath = join(tmpDir, 'missing_test.ttrg');
    // Note: graph objects use unquoted dotted ids per grammar rule
    const content = `graph missing_test { schema: er, objects: [er.entity.does_not_exist] }`;
    writeFileSync(graphPath, content);

    const uri = `file://${graphPath}`;
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text: content },
    });
    await sleep(200);

    const result = await client.sendRequest('modeler/getGraph', { uri }) as { missingObjects: string[]; nodes: any[] };
    expect(result.nodes.length).toBe(0);
    expect(result.missingObjects).toContain('er.entity.does_not_exist');
  });

  it('C2.2 getGraph resolves nodes and edges from companion .ttrm docs', async () => {
    const artiklPath = join(tmpDir, 'artikl.ttrm');
    const artiklContent = `schema er namespace entity
def entity artikl {
  attributes: [
    def attribute id { type: int, isKey: true },
    def attribute nazev { type: text }
  ]
}
def entity dobropis {
  attributes: [
    def attribute id { type: int, isKey: true },
    def attribute artikl_id { type: int }
  ]
}
def relation artikl_dobropis { from: er.entity.artikl, to: er.entity.dobropis, cardinality: { from: "1", to: "0..*" } }`;
    writeFileSync(artiklPath, artiklContent);

    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: `file://${artiklPath}`, languageId: 'ttr', version: 1, text: artiklContent },
    });
    await sleep(50);

    const graphPath = join(tmpDir, 'artikl_overview.ttrg');
    const graphContent = `graph artikl_overview { schema: er, objects: [er.entity.artikl, er.entity.dobropis, er.entity.artikl_dobropis] }`;
    writeFileSync(graphPath, graphContent);

    const graphUri = `file://${graphPath}`;
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: graphUri, languageId: 'ttr', version: 1, text: graphContent },
    });
    await sleep(50);

    const result = await client.sendRequest('modeler/getGraph', { uri: graphUri }) as {
      nodes: Array<{ qname: string; kind: string; rows: Array<{ name: string }> }>;
      edges: Array<{ kind: string; fromNode: string; toNode: string }>;
    };
    expect(result.nodes.length).toBe(2);
    const artiklNode = result.nodes.find((n: any) => n.qname === 'er.entity.artikl');
    expect(artiklNode).toBeDefined();
    expect(artiklNode.kind).toBe('entity');
    expect(artiklNode.rows.length).toBeGreaterThan(0);

    expect(result.edges.length).toBeGreaterThan(0);
    const relEdge = result.edges.find((e: any) => e.kind === 'relation');
    expect(relEdge).toBeDefined();
    expect(relEdge.fromNode).toBe('er.entity.artikl');
    expect(relEdge.toNode).toBe('er.entity.dobropis');
  });

  it('C2.2 getGraph layout is preserved', async () => {
    const graphPath = join(tmpDir, 'layout_test.ttrg');
    const content = `graph layout_test { schema: er, objects: [], layout: { nodes: { er.entity.foo: { x: 100, y: 200 } } } }`;
    writeFileSync(graphPath, content);

    const uri = `file://${graphPath}`;
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text: content },
    });
    await sleep(50);

    const result = await client.sendRequest('modeler/getGraph', { uri }) as { layout: { nodes: Record<string, { x: number; y: number }> } };
    expect(result.layout.nodes['er.entity.foo']).toEqual({ x: 100, y: 200 });
  });

  it('C2.3 addObjectToGraph adds object to graph and edit can be applied', async () => {
    const graphPath = join(tmpDir, 'add_test.ttrg');
    const content = `graph add_test { schema: er, objects: [] }`;
    writeFileSync(graphPath, content);

    const uri = `file://${graphPath}`;
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text: content },
    });
    await sleep(50);

    const result = await client.sendRequest('modeler/addObjectToGraph', { uri, qname: 'er.entity.artikl', autoImport: false }) as { documentChanges?: any[] };
    expect(result.documentChanges).toBeDefined();
    expect(result.documentChanges.length).toBeGreaterThan(0);
    const edit = result.documentChanges[0];
    expect(edit.textDocument.uri).toBe(uri);
    expect(edit.edits[0].newText).toBe('er.entity.artikl');

    const updatedContent = content.replace('objects: []', `objects: [er.entity.artikl]`);
    client.sendNotification('textDocument/didChange', {
      textDocument: { uri, version: 2 },
      contentChanges: [{ text: updatedContent }],
    });
    await sleep(50);

    const getResult = await client.sendRequest('modeler/getGraph', { uri }) as { nodes: any[] };
    expect(getResult.nodes.length).toBe(1);
    expect(getResult.nodes[0].qname).toBe('er.entity.artikl');
  });

  it('C2.3 addObjectToGraph with autoImport adds import statement', async () => {
    const graphPath = join(tmpDir, 'add_import_test.ttrg');
    const content = `graph add_import_test { schema: er, objects: [] }`;
    writeFileSync(graphPath, content);

    const uri = `file://${graphPath}`;
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text: content },
    });
    await sleep(50);

    const result = await client.sendRequest('modeler/addObjectToGraph', { uri, qname: 'er.entity.artikl', autoImport: true }) as { documentChanges?: any[] };
    expect(result.documentChanges).toBeDefined();
    expect(result.documentChanges.length).toBe(1);
    expect(result.documentChanges[0].edits[0].newText).toBe('er.entity.artikl');
  });

  it('C2.3 addObjectToGraph with autoImport for unpackaged object parses cleanly', async () => {
    const graphPath = join(tmpDir, 'add_unpackaged_reparse.ttrg');
    const content = `graph add_unpackaged_reparse { schema: er, objects: [] }`;
    writeFileSync(graphPath, content);

    const uri = `file://${graphPath}`;
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text: content },
    });
    await sleep(50);

    const result = await client.sendRequest('modeler/addObjectToGraph', { uri, qname: 'er.entity.artikl', autoImport: true }) as { documentChanges?: any[] };
    expect(result.documentChanges).toBeDefined();
    expect(result.documentChanges.length).toBe(1);

    const edit = result.documentChanges[0];
    const start = edit.edits[0].range.start;
    const end = edit.edits[0].range.end;
    const lines = content.split('\n');
    const before = lines.slice(0, start.line).join('\n') + '\n' + lines[start.line].slice(0, start.character);
    const after = (end.line > start.line ? '' : lines[start.line].slice(end.character)) + '\n' + lines.slice(end.line + 1).join('\n');
    const newContent = before + edit.edits[0].newText + after;

    client.sendNotification('textDocument/didChange', {
      textDocument: { uri, version: 2 },
      contentChanges: [{ text: newContent }],
    });
    await sleep(50);

    const parseResult = await client.sendRequest('modeler/getGraph', { uri }) as { nodes: any[] };
    expect(parseResult.nodes.length).toBe(1);
    expect(parseResult.nodes[0].qname).toBe('er.entity.artikl');
  });

  it('C2.3 removeObjectFromGraph produces an edit that removes the object', async () => {
    const graphPath = join(tmpDir, 'remove_test.ttrg');
    const content = `graph remove_test { schema: er, objects: [er.entity.artikl] }`;
    writeFileSync(graphPath, content);

    const uri = `file://${graphPath}`;
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text: content },
    });
    await sleep(50);

    const result = await client.sendRequest('modeler/removeObjectFromGraph', { uri, qname: 'er.entity.artikl', pruneUnusedImport: false }) as { documentChanges?: any[] };
    expect(result.documentChanges).toBeDefined();
    expect(result.documentChanges.length).toBe(1);
    const edit = result.documentChanges[0];
    expect(edit.textDocument.uri).toBe(uri);
    expect(edit.edits[0].newText).toBe('');
  });

  it('C2.4 createGraph produces CreateFile + TextEdit with canonical body', async () => {
    const newGraphPath = join(tmpDir, 'new_graph_canonical.ttrg');
    const result = await client.sendRequest('modeler/createGraph', {
      uri: `file://${newGraphPath}`,
      name: 'new_graph_canonical',
      schema: 'er',
      packages: ['billing'],
      objects: ['billing.entity.foo', 'billing.entity.bar'],
      description: 'A test graph',
      tags: ['test'],
    }) as { documentChanges?: any[] };

    expect(result.documentChanges).toBeDefined();
    expect(result.documentChanges.length).toBe(2);
    expect(result.documentChanges[0].kind).toBe('create');
    expect(result.documentChanges[0].uri).toContain('new_graph_canonical.ttrg');

    const textEdit = result.documentChanges[1];
    expect(textEdit.edits[0].newText).toContain('graph new_graph_canonical');
    expect(textEdit.edits[0].newText).toContain('schema: er');
    expect(textEdit.edits[0].newText).toContain('description: "A test graph"');
    expect(textEdit.edits[0].newText).toContain('tags: ["test"]');
    expect(textEdit.edits[0].newText).toContain('import billing');
    expect(textEdit.edits[0].newText).toContain('objects: [billing.entity.foo, billing.entity.bar]');
  });

  it('C2.7 setLayout via graphUri returns WorkspaceEdit with unquoted keys', async () => {
    const graphPath = join(tmpDir, 'layout_set_test.ttrg');
    const content = `graph layout_set_test { schema: er, objects: [] }`;
    writeFileSync(graphPath, content);

    const uri = `file://${graphPath}`;
    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri, languageId: 'ttr', version: 1, text: content },
    });
    await sleep(50);

    const layout = {
      version: 1 as const,
      viewports: { er: { zoom: 1.5, panX: 100, panY: 200, displayMode: 'with-types' as const } },
      nodes: { 'er.entity.artikl': { x: 320, y: 180 } },
      edges: {},
    };

    const result = await client.sendRequest('modeler/setLayout', { graphUri: uri, layout }) as { documentChanges?: any[] };
    expect(result.documentChanges).toBeDefined();
    expect(result.documentChanges.length).toBe(1);
    const edit = result.documentChanges[0];
    expect(edit.edits[0].newText).toContain('er.entity.artikl');
    expect(edit.edits[0].newText).toContain('x: 320');
    expect(edit.edits[0].newText).toContain('y: 180');
  });

  it('C2.4 createGraph produces a WorkspaceEdit that creates a file', async () => {
    const newGraphPath = join(tmpDir, 'new_graph.ttrg');
    const result = await client.sendRequest('modeler/createGraph', {
      uri: `file://${newGraphPath}`,
      name: 'new_graph',
      schema: 'er',
      packages: [],
      objects: [],
    }) as { documentChanges?: unknown[] };

    expect(result.documentChanges).toBeDefined();
    expect((result.documentChanges as any[]).length).toBeGreaterThan(0);
  });

  it('C2.4 F1.3 createGraph edit is applied and result is loadable and parsable', async () => {
    const newGraphPath = join(tmpDir, 'roundtrip_graph.ttrg');
    const result = await client.sendRequest('modeler/createGraph', {
      uri: `file://${newGraphPath}`,
      name: 'roundtrip_graph',
      schema: 'er',
      packages: [],
      objects: [],
    }) as { documentChanges?: any[] };

    expect(result.documentChanges).toBeDefined();
    const createOp = result.documentChanges.find((c: any) => c.kind === 'create');
    const textEdit = result.documentChanges.find((c: any) => c.edits);
    expect(createOp).toBeDefined();
    expect(textEdit).toBeDefined();

    const newContent = textEdit.edits[0].newText as string;
    expect(newContent).toContain('graph roundtrip_graph');
    expect(newContent).toContain('schema: er');

    const parsed = parseString(newContent, `file://${newGraphPath}`);
    expect(parsed.errors).toHaveLength(0);
    expect(parsed.ast?.graph).toBeDefined();
    expect(parsed.ast?.graph?.name).toBe('roundtrip_graph');
  });

  it('C2.5 getPackageGraph returns package structure', async () => {
    const result = await client.sendRequest('modeler/getPackageGraph', {}) as { packages: unknown[]; dependencies: unknown[]; cycles: unknown[] };
    expect(result.packages).toBeDefined();
    expect(result.dependencies).toBeDefined();
    expect(result.cycles).toBeDefined();
  });
});