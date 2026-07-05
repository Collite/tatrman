import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { cpSync, rmSync, readFileSync, readdirSync, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { parseString } from '@modeler/parser';
import * as lsp from 'vscode-languageserver/node';
import { PassThrough } from 'stream';
import { createServerConnection } from '@modeler/lsp/server';

const fixturesRoot = join(__dirname, '../fixtures/migrate-v1');

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

function listTtrFiles(dir: string): string[] {
  const results: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) results.push(...listTtrFiles(full));
    else if (entry.isFile() && entry.name.endsWith('.ttrm')) results.push(full);
  }
  return results;
}

function listTtrgFiles(dir: string): string[] {
  const results: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) results.push(...listTtrgFiles(full));
    else if (entry.isFile() && entry.name.endsWith('.ttrg')) results.push(full);
  }
  return results;
}

const cliPath = join(__dirname, '../../../packages/migrate/dist/cli.js');

describe('migration E2E', () => {
  const workDir = join(tmpdir(), `modeler-migrate-e2e-${Date.now()}`);

  beforeAll(() => {
    cpSync(fixturesRoot, workDir, { recursive: true });
    // A v1 project's view state lives in `.modeler/layout.ttrl`, which the migration
    // converts into per-graph `.ttrg` files. That path is gitignored (build artifact),
    // so the fixture cannot carry it in git — synthesize the ephemeral sidecar here,
    // matching what a real v1 project would have on disk before migrating.
    const modelerDir = join(workDir, '.modeler');
    mkdirSync(modelerDir, { recursive: true });
    const layoutTtrl = [
      '// modeler view-state sidecar (v1) — synthesized by the E2E test (.modeler/ is gitignored)',
      JSON.stringify(
        {
          viewports: { er: { zoom: 1.0, panX: 0, panY: 0, displayMode: 'just-names' } },
          nodes: {},
        },
        null,
        2,
      ),
      '',
    ].join('\n');
    writeFileSync(join(modelerDir, 'layout.ttrl'), layoutTtrl, 'utf-8');
  });

  afterAll(() => {
    rmSync(workDir, { recursive: true, force: true });
  });

  it('--dry-run does not write any files', async () => {
    // Compare file contents before/after — robust against mtime/clock precision.
    const ttrBefore = listTtrFiles(workDir).map(f => [f, readFileSync(f, 'utf-8')] as const);
    execSync(`node "${cliPath}" migrate-to-packages --dry-run "${workDir}"`, { cwd: workDir, stdio: 'pipe' });
    for (const [f, content] of ttrBefore) {
      expect(readFileSync(f, 'utf-8'), `${f} should be unchanged by dry-run`).toBe(content);
    }
    expect(listTtrgFiles(workDir).length, 'no .ttrg files created by dry-run').toBe(0);
    const reportPath = join(workDir, '.modeler', 'migrate-report.json');
    expect(existsSync(reportPath), 'report should not be written on dry-run').toBe(false);
  });

  it('full run produces valid .ttrg files', async () => {
    execSync(`node "${cliPath}" migrate-to-packages "${workDir}"`, { cwd: workDir, stdio: 'pipe', timeout: 10000 });
    const ttrgFiles = listTtrgFiles(workDir);
    expect(ttrgFiles.length, 'at least one .ttrg created').toBeGreaterThan(0);
    for (const ttrgPath of ttrgFiles) {
      const content = readFileSync(ttrgPath, 'utf-8');
      const result = parseString(content, ttrgPath);
      expect(result.errors.filter(e => e.severity === 'error'), `${ttrgPath} parse errors: ${JSON.stringify(result.errors)}`).toHaveLength(0);
      expect(result.ast?.graph).toBeDefined();
    }
  });

  it('.ttrm files parse cleanly under v1.1 grammar', async () => {
    execSync(`node "${cliPath}" migrate-to-packages "${workDir}"`, { cwd: workDir, stdio: 'pipe', timeout: 10000 });
    for (const ttrPath of listTtrFiles(workDir)) {
      const content = readFileSync(ttrPath, 'utf-8');
      const result = parseString(content, ttrPath);
      expect(result.errors.filter(e => e.severity === 'error'), `${ttrPath} errors: ${JSON.stringify(result.errors)}`).toHaveLength(0);
    }
  });

  it('.ttrm files have package declarations after migration', async () => {
    execSync(`node "${cliPath}" migrate-to-packages "${workDir}"`, { cwd: workDir, stdio: 'pipe', timeout: 10000 });
    for (const ttrPath of listTtrFiles(workDir)) {
      const content = readFileSync(ttrPath, 'utf-8');
      expect(content, `${ttrPath} should have package declaration`).toMatch(/^package\s/m);
    }
  });

  it('genuine cross-package reference produces the correct import (real package, top-level def)', async () => {
    execSync(`node "${cliPath}" migrate-to-packages "${workDir}"`, { cwd: workDir, stdio: 'pipe', timeout: 10000 });
    // produkt (billing.products) references artikl (billing.invoicing) via a relation.
    const produkt = readFileSync(join(workDir, 'billing', 'products', 'produkt.ttrm'), 'utf-8');
    expect(produkt, 'produkt.ttrm must import artikl from its real package').toContain(
      'import billing.invoicing.er.entity.artikl',
    );
    // No malformed schema-as-package import.
    expect(produkt).not.toMatch(/^import er\./m);
    // artikl (billing.invoicing) has no cross-package reference, so no spurious import.
    const artikl = readFileSync(join(workDir, 'billing', 'invoicing', 'artikl.ttrm'), 'utf-8');
    expect(artikl.split('\n').filter(l => l.startsWith('import ')), 'artikl.ttrm should have no imports').toHaveLength(0);
  });

  it('.ttrg opens via client.getGraph with missingObjects === []', async () => {
    execSync(`node "${cliPath}" migrate-to-packages "${workDir}"`, { cwd: workDir, stdio: 'pipe', timeout: 10000 });

    const { client, server } = createPairedConnection();
    createServerConnection(server);
    await client.sendRequest('initialize', { processId: null, rootUri: null, capabilities: {} });
    client.sendNotification('initialized', {});

    const erTtrgPath = join(workDir, 'graphs', '_all_er.ttrg');
    if (!readdirSync(join(workDir, 'graphs')).some(e => e.startsWith('_all_er'))) {
      expect.fail('no _all_er.ttrg created');
    }
    const erContent = readFileSync(erTtrgPath, 'utf-8');

    for (const ttrPath of listTtrFiles(workDir)) {
      const content = readFileSync(ttrPath, 'utf-8');
      client.sendNotification('textDocument/didOpen', {
        textDocument: { uri: `file://${ttrPath}`, languageId: 'ttr', version: 1, text: content },
      });
    }

    client.sendNotification('textDocument/didOpen', {
      textDocument: { uri: `file://${erTtrgPath}`, languageId: 'ttrg', version: 1, text: erContent },
    });

    await new Promise(r => setTimeout(r, 300));

    const graphResult = await client.sendRequest('modeler/getGraph', { uri: `file://${erTtrgPath}` }) as any;
    expect(graphResult).toBeDefined();
    expect(graphResult?.missingObjects, `missingObjects should be empty but got: ${JSON.stringify(graphResult?.missingObjects)}`).toHaveLength(0);
  });

  it('cross-references resolve with no unresolved/unimported errors', async () => {
    execSync(`node "${cliPath}" migrate-to-packages "${workDir}"`, { cwd: workDir, stdio: 'pipe', timeout: 10000 });

    const { client, server } = createPairedConnection();
    createServerConnection(server);
    await client.sendRequest('initialize', { processId: null, rootUri: null, capabilities: {} });
    client.sendNotification('initialized', {});

    const errors: lsp.Diagnostic[] = [];
    client.onNotification('textDocument/publishDiagnostics', (params: lsp.PublishDiagnosticsParams) => {
      errors.push(...params.diagnostics.filter(d => d.severity === lsp.DiagnosticSeverity.Error));
    });

    for (const ttrPath of listTtrFiles(workDir)) {
      const content = readFileSync(ttrPath, 'utf-8');
      client.sendNotification('textDocument/didOpen', {
        textDocument: { uri: `file://${ttrPath}`, languageId: 'ttr', version: 1, text: content },
      });
    }

    await new Promise(r => setTimeout(r, 300));

    const unresolvedOrUnimported = errors.filter(
      d => d.code === 'ttr/unresolved-reference' || d.code === 'ttr/unimported-reference'
    );
    expect(unresolvedOrUnimported, `found unresolved references: ${JSON.stringify(unresolvedOrUnimported)}`).toHaveLength(0);
  });
});

describe('migration ambiguous references', () => {
  const ambFixture = join(__dirname, '../fixtures/migrate-ambiguous');
  const ambWorkDir = join(tmpdir(), `modeler-migrate-amb-${Date.now()}`);

  beforeAll(() => {
    cpSync(ambFixture, ambWorkDir, { recursive: true });
  });
  afterAll(() => {
    rmSync(ambWorkDir, { recursive: true, force: true });
  });

  it('exits 1 and records the ambiguous reference when a bare name is exported by 2+ packages', () => {
    let status = 0;
    try {
      execSync(`node "${cliPath}" migrate-to-packages "${ambWorkDir}"`, { cwd: ambWorkDir, stdio: 'pipe' });
    } catch (err) {
      status = (err as { status?: number }).status ?? -1;
    }
    expect(status, 'CLI should exit with code 1 on ambiguous references').toBe(1);

    const report = JSON.parse(readFileSync(join(ambWorkDir, '.modeler', 'migrate-report.json'), 'utf-8'));
    expect(report.ambiguousReferences.length, 'report should list the ambiguous reference').toBeGreaterThan(0);
    const amb = report.ambiguousReferences[0];
    expect(amb.ref).toContain('shared');
    // Candidates name the conflicting packages.
    expect(amb.candidates.some((c: string) => c.startsWith('b.'))).toBe(true);
    expect(amb.candidates.some((c: string) => c.startsWith('c.'))).toBe(true);
  });
});