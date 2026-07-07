// The contract the TTR-P VS Code extension client relies on, exercised over the real
// Kotlin server + real stdio. Run: `./gradlew :packages:kotlin:ttrp-lsp:installDist &&
// pnpm --filter @tatrman/integration-tests test -- ttrp-lsp-stdio`.

import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { SERVER_BIN, startTtrpServer, type TtrpServer } from './ttrp-harness.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const brokenHeroPath = path.resolve(
  here,
  '../../../packages/kotlin/ttrp-lsp/src/test/resources/fixtures/hero-broken.ttrp',
);

// Gated on the launcher being built: `pnpm -r test` (the pure-Node CI job) has no
// installDist artifact and SKIPS this suite (visible in the vitest output — not a
// silent pass); the dedicated CI job with Java runs installDist first, so it always
// executes there. Locally: `./gradlew :packages:kotlin:ttrp-lsp:installDist` then this.
const serverBuilt = existsSync(SERVER_BIN);
if (!serverBuilt) {
  // eslint-disable-next-line no-console
  console.warn(
    `[ttrp-lsp-stdio] SKIPPED — launcher not built at ${SERVER_BIN}. ` +
      'Run: ./gradlew :packages:kotlin:ttrp-lsp:installDist',
  );
}

describe.runIf(serverBuilt)('TTR-P LSP over stdio (real Kotlin server)', () => {
  let server: TtrpServer;

  beforeAll(() => {
    server = startTtrpServer();
  });

  afterAll(async () => {
    await server?.stop();
  });

  it('initialize advertises incremental sync + hover + rename + formatting', async () => {
    const result: any = await server.conn.sendRequest('initialize', {
      processId: process.pid,
      rootUri: null,
      capabilities: {},
    });
    await server.conn.sendNotification('initialized', {});
    const caps = result.capabilities;
    expect(caps.textDocumentSync).toBe(2); // Incremental
    expect(caps.hoverProvider).toBe(true);
    expect(caps.renameProvider?.prepareProvider).toBe(true);
    expect(caps.documentFormattingProvider).toBe(true);
  });

  it('didOpen a broken hero publishes TTRP-EQ-001', async () => {
    const text = readFileSync(brokenHeroPath, 'utf8');
    const uri = pathToFileURL(brokenHeroPath).toString();
    const diagnostics: any[] = await new Promise((resolve) => {
      const disposable = server.conn.onNotification('textDocument/publishDiagnostics', (params: any) => {
        if (params.uri === uri) {
          disposable.dispose();
          resolve(params.diagnostics);
        }
      });
      void server.conn.sendNotification('textDocument/didOpen', {
        textDocument: { uri, languageId: 'ttrp', version: 1, text },
      });
    });
    expect(diagnostics.map((d) => d.code)).toContain('TTRP-EQ-001');
  });

  it('ttrp/validate round-trips a raw custom request', async () => {
    const text = readFileSync(brokenHeroPath, 'utf8');
    const result: any = await server.conn.sendRequest('ttrp/validate', {
      source: text,
      uri: pathToFileURL(brokenHeroPath).toString(),
    });
    expect(result.diagnostics.map((d: any) => d.code)).toContain('TTRP-EQ-001');
  });

  it('ttrp/transpile with a stale version fails with ContentModified (-32801)', async () => {
    const uri = pathToFileURL(brokenHeroPath).toString();
    await expect(server.conn.sendRequest('ttrp/transpile', { uri, version: 999 })).rejects.toMatchObject({
      code: -32801,
    });
  });

  it('shutdown/exit terminates the process (no zombie JVM)', async () => {
    const local = startTtrpServer();
    await local.conn.sendRequest('initialize', { processId: process.pid, rootUri: null, capabilities: {} });
    await local.conn.sendRequest('shutdown', null);
    await local.conn.sendNotification('exit', null);
    const exited = await new Promise<boolean>((resolve) => {
      const timer = setTimeout(() => resolve(false), 5000);
      local.proc.on('exit', () => {
        clearTimeout(timer);
        resolve(true);
      });
    });
    expect(exited).toBe(true);
  });
});
