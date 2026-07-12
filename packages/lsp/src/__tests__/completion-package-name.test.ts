// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '../server.js';

function createPairedConnection(): { client: lsp.Connection; server: lsp.Connection } {
  const clientTransport = new PassThrough({ objectMode: true });
  const serverTransport = new PassThrough({ objectMode: true });

  const clientReader = new lsp.StreamMessageReader(clientTransport as unknown as NodeJS.ReadableStream);
  const clientWriter = new lsp.StreamMessageWriter(serverTransport as unknown as NodeJS.WritableStream);
  const client = lsp.createConnection(clientReader, clientWriter) as lsp.Connection;

  const serverReader = new lsp.StreamMessageReader(serverTransport as unknown as NodeJS.ReadableStream);
  const serverWriter = new lsp.StreamMessageWriter(clientTransport as unknown as NodeJS.WritableStream);
  const server = lsp.createConnection(serverReader, serverWriter) as lsp.Connection;

  client.listen();
  server.listen();

  return { client, server };
}

async function sleep(ms: number): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms));
}

describe('completion-package-name', () => {
  let clientConnection: lsp.Connection;
  let serverConnection: lsp.Connection;

  beforeEach(async () => {
    const { client, server } = createPairedConnection();
    clientConnection = client;
    serverConnection = server;
    createServerConnection(serverConnection);
  });

  afterEach(() => {
    clientConnection.dispose();
    serverConnection.dispose();
  });

  it('returns billing.invoicing as top suggestion for file at billing/invoicing/', async () => {
    await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: 'file:///',
      capabilities: {},
    });
    clientConnection.sendNotification('initialized', {});

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///billing/invoicing/test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `package <CURSOR>`,
      },
    });

    await sleep(50);

    const result = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: 'file:///billing/invoicing/test.ttrm' },
      position: { line: 0, character: 8 },
      context: { triggerKind: 1 },
    }) as { isIncomplete: boolean; items: unknown[] };

    expect(result.items.length).toBeGreaterThan(0);
    const top = result.items[0] as { label: string; detail: string };
    expect(top.label).toBe('billing.invoicing');
    expect(top.detail).toBe('(inferred from path)');
  });

  it('returns all project packages on import with no prefix', async () => {
    await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: 'file:///',
      capabilities: {},
    });
    clientConnection.sendNotification('initialized', {});

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///com/foo/test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `package com.foo\n\ndef entity foo {}`,
      },
    });
    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///com/bar/test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `package com.bar\n\ndef entity bar {}`,
      },
    });
    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///org/baz/test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `package org.baz\n\ndef entity baz {}`,
      },
    });

    await sleep(50);

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `import <CURSOR>`,
      },
    });

    await sleep(50);

    const result = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: 'file:///test.ttrm' },
      position: { line: 0, character: 7 },
      context: { triggerKind: 1 },
    }) as { isIncomplete: boolean; items: unknown[] };

    const labels = (result.items as Array<{ label: string }>).map((i) => i.label);
    expect(labels).toContain('com.foo');
    expect(labels).toContain('com.bar');
    expect(labels).toContain('org.baz');
  });

  it('filters import suggestions by partial prefix', async () => {
    await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: 'file:///',
      capabilities: {},
    });
    clientConnection.sendNotification('initialized', {});

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///com/foo/test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `package com.foo\n\ndef entity foo {}`,
      },
    });
    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///com/bar/test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `package com.bar\n\ndef entity bar {}`,
      },
    });
    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///org/baz/test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `package org.baz\n\ndef entity baz {}`,
      },
    });

    await sleep(50);

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `import com.<CURSOR>`,
      },
    });

    await sleep(50);

    const result = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: 'file:///test.ttrm' },
      position: { line: 0, character: 11 },
      context: { triggerKind: 1 },
    }) as { isIncomplete: boolean; items: unknown[] };

    const labels = (result.items as Array<{ label: string }>).map((i) => i.label);
    expect(labels.every((l) => l.startsWith('com.'))).toBe(true);
    expect(labels).not.toContain('org.baz');
  });

  it('import detail shows child-symbol counts', async () => {
    await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: 'file:///',
      capabilities: {},
    });
    clientConnection.sendNotification('initialized', {});

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///com/foo/test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `package com.foo\n\ndef entity foo {}\ndef entity bar {}`,
      },
    });

    await sleep(50);

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `import <CURSOR>`,
      },
    });

    await sleep(50);

    const result = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: 'file:///test.ttrm' },
      position: { line: 0, character: 7 },
      context: { triggerKind: 1 },
    }) as { isIncomplete: boolean; items: unknown[] };

    const fooItem = (result.items as Array<{ label: string; detail: string }>).find(
      (i) => i.label === 'com.foo'
    );
    expect(fooItem).toBeDefined();
    expect(fooItem!.detail).toMatch(/symbol/);
  });

  it('import does not list the empty root package as a blank-label item', async () => {
    await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: 'file:///',
      capabilities: {},
    });
    clientConnection.sendNotification('initialized', {});

    // A package-less root file (lives in the default package '').
    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///root.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `def entity loose {}`,
      },
    });
    // A real package, so there's at least one valid suggestion too.
    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///com/foo/test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `package com.foo\n\ndef entity foo {}`,
      },
    });

    await sleep(50);

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///importer.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `import <CURSOR>`,
      },
    });

    await sleep(50);

    const result = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: 'file:///importer.ttrm' },
      position: { line: 0, character: 7 },
      context: { triggerKind: 1 },
    }) as { isIncomplete: boolean; items: unknown[] };

    const labels = (result.items as Array<{ label: string }>).map((i) => i.label);
    expect(labels).not.toContain('');
    expect(labels.every((l) => l.length > 0)).toBe(true);
    expect(labels).toContain('com.foo');
  });

  it('does not return package suggestions outside package/import context', async () => {
    await clientConnection.sendRequest('initialize', {
      processId: null,
      rootUri: null,
      capabilities: {},
    });
    clientConnection.sendNotification('initialized', {});

    clientConnection.sendNotification('textDocument/didOpen', {
      textDocument: {
        uri: 'file:///test.ttrm',
        languageId: 'ttr',
        version: 1,
        text: `def entity foo {
  desc
}`,
      },
    });

    await sleep(50);

    const result = await clientConnection.sendRequest('textDocument/completion', {
      textDocument: { uri: 'file:///test.ttrm' },
      position: { line: 1, character: 4 },
      context: { triggerKind: 1 },
    }) as { isIncomplete: boolean; items: unknown[] };

    const labels = (result.items as Array<{ label: string }>).map((i) => i.label);
    expect(labels).not.toContain('billing');
    expect(labels).not.toContain('com');
  });
});