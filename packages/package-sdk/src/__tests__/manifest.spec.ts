// SPDX-License-Identifier: Apache-2.0
//
// FO-P4.S1.T1 — manifest schema contract (FO contracts §15). Red-first. A valid investment manifest
// passes; the P-3 negative — an agentic plugin declared in the write path — is schema-invalid.

import { describe, expect, it } from 'vitest';
import { validateManifest, type PackageManifest } from '../manifest.js';

// The investment package manifest (contracts §7 shape) — the positive fixture.
const investment: PackageManifest = {
  package: 'investment',
  version: '0.1.0',
  model: './model/',
  canon: './canon/',
  forms: './forms/',
  plugins: [
    { id: 'conseq-distrinfo', type: 'proposal-source', artifact: '@investment/parsers@0.1.0' },
    { id: 'excel-book', type: 'proposal-source', artifact: '@investment/parsers@0.1.0' },
    { id: 'twr', type: 'canon-function', artifact: '@investment/calc@0.1.0', determinism: 'pure' },
  ],
  connectors: [],
  golem: { config: './golem/config.yaml', schemaVersion: 1 },
  reconciliation: './recon.yaml',
};

describe('package-manifest schema (§15)', () => {
  it('accepts a well-formed investment manifest', () => {
    const r = validateManifest(investment);
    expect(r.errors).toEqual([]);
    expect(r.valid).toBe(true);
  });

  it('rejects an agentic plugin in the write path (P-3 boundary)', () => {
    const bad = {
      ...investment,
      plugins: [{ id: 'chatty', type: 'agent', artifact: 'golem://chatty' }],
    };
    const r = validateManifest(bad);
    expect(r.valid).toBe(false);
    // the failure is on the plugin type enum — agentic behaviour is not a manifest plugin.
    expect(r.errors.join(' ')).toMatch(/plugins\/0\/type/);
  });

  it('rejects a non-pure determinism marker on a write-path plugin', () => {
    const bad = {
      ...investment,
      plugins: [{ id: 'twr', type: 'canon-function', artifact: 'x', determinism: 'agentic' }],
    };
    expect(validateManifest(bad).valid).toBe(false);
  });

  it('requires model + canon', () => {
    const noModel: Partial<PackageManifest> = { ...investment };
    delete noModel.model;
    expect(validateManifest(noModel).valid).toBe(false);
  });

  it('requires the golem slot to be versioned (envelope: presence + schemaVersion)', () => {
    const bad = { ...investment, golem: { config: './golem/config.yaml' } };
    expect(validateManifest(bad).valid).toBe(false);
  });

  it('rejects unknown top-level keys (closed manifest)', () => {
    const bad = { ...investment, backdoor: './writes/' };
    expect(validateManifest(bad).valid).toBe(false);
  });

  it('rejects a bad package name (plain domain noun, FO-18)', () => {
    expect(validateManifest({ ...investment, package: 'Investment_Pkg' }).valid).toBe(false);
  });
});
