// SPDX-License-Identifier: Apache-2.0
import {
  BrowserMessageReader,
  BrowserMessageWriter,
  createProtocolConnection,
} from 'vscode-languageserver-protocol/browser.js';
import {
  InitializeRequest,
  DidOpenTextDocumentNotification,
  PublishDiagnosticsNotification,
  InitializeParams,
} from 'vscode-languageserver-protocol';

import type { ModelGraph, LayoutFile, SymbolDetail, RenderableSchemaCode, GraphMetadata, GetGraphResponse, PackageGraphResponse } from '@tatrman/lsp';
import type { WorkspaceEdit } from 'vscode-languageserver-types';
import LspWorker from '@tatrman/lsp/browser?worker';

export interface LspClient {
  transportKind: 'node' | 'browser';
  openDocument(uri: string, content: string): Promise<void>;
  setProjectRoot(projectRoot: string): Promise<{ projectRoot: string }>;
  listGraphs(projectRoot: string): Promise<{ graphs: GraphMetadata[] }>;
  getGraph(uri: string): Promise<GetGraphResponse | null>;
  getPackageGraph(): Promise<PackageGraphResponse>;
  getModelGraph(uri: string, schema: RenderableSchemaCode): Promise<ModelGraph>;
  getLayout(uri: string, projectRoot?: string): Promise<LayoutFile>;
  setLayout(uri: string, layout: LayoutFile, projectRoot?: string): Promise<WorkspaceEdit>;
  exportLayout(uri?: string, projectRoot?: string): Promise<LayoutFile>;
  addObjectToGraph(uri: string, qname: string, autoImport: boolean): Promise<WorkspaceEdit>;
  removeObjectFromGraph(uri: string, qname: string, pruneUnusedImport: boolean): Promise<WorkspaceEdit>;
  createGraph(params: { uri: string; name: string; schema: 'db' | 'er' | 'binding' | 'query' | 'cnc'; packages: string[]; objects: string[]; description?: string; tags?: string[] }): Promise<WorkspaceEdit>;
  applyGraphEdit(_params: unknown): Promise<{ ok: false; reason: string }>;
  getSymbolDetail(qname: string): Promise<SymbolDetail | null>;
  listSymbols(options?: { kinds?: string[]; limit?: number }): Promise<Array<{ qname: string; kind: string; name: string; packageName: string | null }>>;
  onDiagnostics(handler: (uri: string, messages: string[]) => void): void;
  dispose(): void;
}

export async function createLspClient(): Promise<LspClient> {
  const worker = new LspWorker();
  const reader = new BrowserMessageReader(worker);
  const writer = new BrowserMessageWriter(worker);
  const connection = createProtocolConnection(reader, writer);
  connection.listen();
  await connection.sendRequest(InitializeRequest.type, {
    processId: null,
    rootUri: null,
    capabilities: {},
  } satisfies InitializeParams);
  const diagnosticHandlers: Array<(uri: string, msgs: string[]) => void> = [];
  connection.onNotification(PublishDiagnosticsNotification.type, (params) => {
    const messages = params.diagnostics.map((d) => d.message);
    for (const h of diagnosticHandlers) h(params.uri, messages);
  });
  return {
    transportKind: 'browser' as const,
    async openDocument(uri, content) {
      await connection.sendNotification(DidOpenTextDocumentNotification.type, {
        textDocument: { uri, languageId: 'ttr', version: 1, text: content },
      });
    },
    async setProjectRoot(projectRoot) {
      return connection.sendRequest('modeler/setProjectRoot', { projectRoot }) as Promise<{ projectRoot: string }>;
    },
    async listGraphs(projectRoot) {
      return connection.sendRequest('modeler/listGraphs', { projectRoot }) as Promise<{ graphs: GraphMetadata[] }>;
    },
    async getGraph(uri) {
      return connection.sendRequest('modeler/getGraph', { uri }) as Promise<GetGraphResponse | null>;
    },
    async getPackageGraph() {
      return connection.sendRequest('modeler/getPackageGraph', {}) as Promise<PackageGraphResponse>;
    },
    async getModelGraph(uri, schema) {
      return connection.sendRequest('modeler/getModelGraph', {
        textDocument: { uri },
        schema,
      }) as Promise<ModelGraph>;
    },
    async getLayout(uri, projectRoot) {
      return connection.sendRequest('modeler/getLayout', { graphUri: uri, projectRoot }) as Promise<LayoutFile>;
    },
    async setLayout(uri, layout, projectRoot) {
      return connection.sendRequest('modeler/setLayout', { graphUri: uri, layout, projectRoot }) as Promise<WorkspaceEdit>;
    },
    async exportLayout(uri, projectRoot) {
      return connection.sendRequest('modeler/exportLayout', { graphUri: uri, projectRoot }) as Promise<LayoutFile>;
    },
    async addObjectToGraph(uri, qname, autoImport) {
      return connection.sendRequest('modeler/addObjectToGraph', { uri, qname, autoImport }) as Promise<WorkspaceEdit>;
    },
    async removeObjectFromGraph(uri, qname, pruneUnusedImport) {
      return connection.sendRequest('modeler/removeObjectFromGraph', { uri, qname, pruneUnusedImport }) as Promise<WorkspaceEdit>;
    },
    async createGraph(params) {
      return connection.sendRequest('modeler/createGraph', params) as Promise<WorkspaceEdit>;
    },
    async applyGraphEdit(_params) {
      return connection.sendRequest('modeler/applyGraphEdit', _params) as Promise<{ ok: false; reason: string }>;
    },
    async getSymbolDetail(qname) {
      return connection.sendRequest('modeler/getSymbolDetail', { qname }) as Promise<SymbolDetail | null>;
    },
    async listSymbols(options) {
      return connection.sendRequest('modeler/listSymbols', options ?? {}) as Promise<Array<{ qname: string; kind: string; name: string; packageName: string | null }>>;
    },
    onDiagnostics(handler) {
      diagnosticHandlers.push(handler);
    },
    dispose() {
      worker.terminate();
    },
  };
}