import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const CLI = resolve(__dirname, '../../dist/cli.js');

// Deliberately messy so format() produces a change.
const UNFORMATTED = `schema db namespace dbo
def table users {
columns: [
def column id {    type: int   }
]
}
`;

/** Run the built CLI; returns { status, stdout, stderr }. */
function runCli(args: string[]): { status: number; stdout: string; stderr: string } {
  try {
    const stdout = execFileSync('node', [CLI, ...args], { encoding: 'utf-8' });
    return { status: 0, stdout, stderr: '' };
  } catch (err) {
    const e = err as { status?: number; stdout?: Buffer | string; stderr?: Buffer | string };
    return {
      status: e.status ?? 1,
      stdout: e.stdout?.toString() ?? '',
      stderr: e.stderr?.toString() ?? '',
    };
  }
}

describe('modeler-fmt CLI', () => {
  let dir: string;

  beforeAll(() => {
    dir = mkdtempSync(join(tmpdir(), 'modeler-fmt-cli-'));
  });
  afterAll(() => rmSync(dir, { recursive: true, force: true }));

  it('--check exits 1 on an unformatted file', () => {
    const file = join(dir, 'a.ttr');
    writeFileSync(file, UNFORMATTED);
    const r = runCli([file, '--check']);
    expect(r.status).toBe(1);
  });

  it('--write formats the file, then --check exits 0', () => {
    const file = join(dir, 'b.ttr');
    writeFileSync(file, UNFORMATTED);

    const w = runCli([file, '--write']);
    expect(w.status).toBe(0);
    const after = readFileSync(file, 'utf-8');
    expect(after).not.toBe(UNFORMATTED);

    const c = runCli([file, '--check']);
    expect(c.status).toBe(0);
  });

  it('--write is idempotent (second run changes nothing, exit 0)', () => {
    const file = join(dir, 'c.ttr');
    writeFileSync(file, UNFORMATTED);
    runCli([file, '--write']);
    const first = readFileSync(file, 'utf-8');
    const r = runCli([file, '--write']);
    expect(r.status).toBe(0);
    expect(readFileSync(file, 'utf-8')).toBe(first);
  });

  it('exits 2 on a path that does not exist', () => {
    const r = runCli([join(dir, 'nope.ttr'), '--check']);
    expect(r.status).toBe(2);
  });
});
