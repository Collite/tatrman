import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parseString } from '@modeler/parser';
import { validateWorldDocument } from '../world-validate.js';

// v4.1 — world-model warning validators (ttr-metadata M0). Mirrors the Kotlin
// WorldSemanticsSpec case-for-case. Hard errors stay in M2's WorldResolver (MD5).
const FIXTURES = join(__dirname, '../../../../tests/conformance/fixtures');

function validate(src: string, known?: Set<string>) {
  const { ast, errors } = parseString(src, 'file:///w/w.ttrm');
  expect(errors.length).toBe(0);
  return validateWorldDocument(ast!, known);
}

describe('v4.1 — world validators', () => {
  it('golden fixture produces zero world diagnostics', () => {
    const src = readFileSync(join(FIXTURES, '57-world.ttrm'), 'utf-8');
    const diags = validate(src, new Set(['erp']));
    expect(diags).toEqual([]);
  });

  it('two staging storages → one world/duplicate-staging warning', () => {
    const src = `package acme.worlds
model world
def world dev {
  def storage a { type: local_dir, staging: true }
  def storage b { type: local_dir, staging: true }
}`;
    const diags = validate(src);
    const staging = diags.filter((d) => d.code === 'world/duplicate-staging');
    expect(staging).toHaveLength(1);
    expect(staging[0].severity).toBe('warning');
    expect(staging[0].message).toContain('a');
    expect(staging[0].message).toContain('b');
  });

  it('hosts naming an unknown package → world/hosts-unknown-package', () => {
    const src = `package acme.worlds
model world
def world dev {
  def storage s { type: postgres, hosts: [nosuch] }
}`;
    const diags = validate(src, new Set(['erp']));
    const hosts = diags.filter((d) => d.code === 'world/hosts-unknown-package');
    expect(hosts).toHaveLength(1);
    expect(hosts[0].message).toContain('nosuch');
  });

  it('a def world in a non-model-world file → world/wrong-model-kind', () => {
    const src = `package acme.worlds
model db
def world dev { }`;
    const diags = validate(src);
    expect(diags.some((d) => d.code === 'world/wrong-model-kind')).toBe(true);
  });

  it('a non-world def in a model world file → world/wrong-model-kind', () => {
    const src = `package acme.worlds
model world
def table t { }`;
    const diags = validate(src);
    expect(diags.some((d) => d.code === 'world/wrong-model-kind')).toBe(true);
  });
});
