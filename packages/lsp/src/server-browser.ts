// SPDX-License-Identifier: Apache-2.0
import { createServerConnection } from './server.js';
import { createConnection, ProposedFeatures } from 'vscode-languageserver/browser.js';
import { BrowserMessageReader, BrowserMessageWriter } from 'vscode-languageserver-protocol/browser.js';
import type { LayoutFile } from './model-graph.js';

const reader = new BrowserMessageReader(self as unknown as Worker);
const writer = new BrowserMessageWriter(self as unknown as Worker);
const connection = createConnection(ProposedFeatures.all, reader, writer);
createServerConnection(connection, {
  layoutStore: new Map<string, LayoutFile>(),
});
connection.listen();