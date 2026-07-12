// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, readFileSync, mkdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const CLI = resolve(__dirname, '../../dist/cli.js');

function runCli(args: string[]): { status: number; stdout: string; stderr: string } {
  try {
    const stdout = execFileSync('node', [CLI, ...args], { encoding: 'utf-8' });
    return { status: 0, stdout, stderr: '' };
  } catch (err) {
    const e = err as { status?: number; stdout?: Buffer | string; stderr?: Buffer | string };
    return { status: e.status ?? 1, stdout: e.stdout?.toString() ?? '', stderr: e.stderr?.toString() ?? '' };
  }
}

describe('modeler-lint CLI', () => {
  let root: string;

  beforeAll(() => {
    root = mkdtempSync(join(tmpdir(), 'modeler-lint-cli-'));
    writeFileSync(join(root, 'modeler.toml'), `[project]\nname = "t"\n`);
    mkdirSync(join(root, 'app'), { recursive: true });
    writeFileSync(
      join(root, 'app', 'main.ttrm'),
      `package app\nimport other.db.dbo.thing\nmodel db schema dbo\ndef table t { columns: [def column id { type: int }] }\n`
    );
  });
  afterAll(() => rmSync(root, { recursive: true, force: true }));

  it('reports the unused import (exit 1 with fail-on warning)', () => {
    const r = runCli([root, '--fail-on', 'warning']);
    expect(r.status).toBe(1);
    expect(r.stdout).toContain('unused-import');
  });

  it('--fix removes the unused import and then exits 0', () => {
    const r = runCli([root, '--fix']);
    const after = readFileSync(join(root, 'app', 'main.ttrm'), 'utf-8');
    expect(after).not.toContain('import other.db.dbo.thing');
    // default fail-on is error; no errors remain → exit 0
    expect(r.status).toBe(0);
  });

  it('--explain prints the rule docs and exits 0', () => {
    const r = runCli(['--explain', 'unused-import', root]);
    expect(r.status).toBe(0);
    expect(r.stdout).toContain('unused-import');
    expect(r.stdout.toLowerCase()).toContain('import');
  });

  it('--format json emits a stable shape', () => {
    // Re-add an unused import so there is something to report.
    writeFileSync(
      join(root, 'app', 'main.ttrm'),
      `package app\nimport other.db.dbo.thing\nmodel db schema dbo\ndef table t { columns: [def column id { type: int }] }\n`
    );
    const r = runCli([root, '--format', 'json', '--fail-on', 'none']);
    expect(r.status).toBe(0);
    const parsed = JSON.parse(r.stdout) as Array<{ ruleId: string; code: string; range: unknown }>;
    expect(parsed.some((d) => d.ruleId === 'unused-import' && d.code === 'ttr/unused-import')).toBe(true);
    expect(parsed[0]).toHaveProperty('range');
  });
});
