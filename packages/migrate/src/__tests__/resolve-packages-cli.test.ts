import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, readFileSync, mkdirSync, rmSync, existsSync } from 'node:fs';
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

const entityFile = (pkg: string, e: string) =>
  `package ${pkg}\nschema er namespace entity\ndef entity ${e} { attributes: [def attribute id { type: int }] }\n`;

describe('modeler resolve-packages CLI', () => {
  let root: string;

  beforeEach(() => {
    root = mkdtempSync(join(tmpdir(), 'modeler-rp-cli-'));
    writeFileSync(join(root, 'modeler.toml'), '[project]\nname = "t"\n');
    mkdirSync(join(root, 'a'), { recursive: true });
    writeFileSync(join(root, 'a', 'er.ttrm'), entityFile('a', 'artikl'));
  });
  afterEach(() => rmSync(root, { recursive: true, force: true }));

  it('--out writes a valid artifact and exits 0', () => {
    const out = join(root, 'resolved-packages.json');
    const r = runCli(['resolve-packages', root, '--out', out]);
    expect(r.status).toBe(0);
    const artifact = JSON.parse(readFileSync(out, 'utf-8'));
    expect(artifact.formatVersion).toBe(1);
    expect(artifact.packages.map((p: { canonicalName: string }) => p.canonicalName)).toContain('a');
  });

  it('default output path is <root>/.modeler/resolved-packages.json', () => {
    const r = runCli(['resolve-packages', root]);
    expect(r.status).toBe(0);
    expect(existsSync(join(root, '.modeler', 'resolved-packages.json'))).toBe(true);
  });

  it('exits 2 on an IO error (unwritable --out path)', () => {
    // A regular file masquerading as a parent directory → mkdir/writeFile throws.
    const blocker = join(root, 'blocker');
    writeFileSync(blocker, 'x');
    const r = runCli(['resolve-packages', root, '--out', join(blocker, 'sub', 'rp.json')]);
    expect(r.status).toBe(2);
  });

  it('--check exits 0 when the on-disk artifact is in sync', () => {
    const out = join(root, 'resolved-packages.json');
    expect(runCli(['resolve-packages', root, '--out', out]).status).toBe(0);
    expect(runCli(['resolve-packages', root, '--out', out, '--check']).status).toBe(0);
  });

  it('--check exits non-zero when the model has drifted from the snapshot', () => {
    const out = join(root, 'resolved-packages.json');
    runCli(['resolve-packages', root, '--out', out]);
    // Add a new package/entity → the snapshot is now stale.
    mkdirSync(join(root, 'a', 'b'), { recursive: true });
    writeFileSync(join(root, 'a', 'b', 'er.ttrm'), entityFile('a.b', 'sub'));
    const r = runCli(['resolve-packages', root, '--out', out, '--check']);
    expect(r.status).not.toBe(0);
    // Regenerating brings it back in sync.
    runCli(['resolve-packages', root, '--out', out]);
    expect(runCli(['resolve-packages', root, '--out', out, '--check']).status).toBe(0);
  });

  it('--check exits non-zero when no artifact exists yet', () => {
    const out = join(root, 'missing.json');
    const r = runCli(['resolve-packages', root, '--out', out, '--check']);
    expect(r.status).not.toBe(0);
  });
});
