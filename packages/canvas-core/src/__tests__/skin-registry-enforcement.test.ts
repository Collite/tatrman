// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { SkinRegistry, SkinRegistrationError } from '../registry.js';
import type { AnchorDeclaration, NodeSize } from '../contract.js';
import { fakeSkin } from './fake-skin.js';

describe('SkinRegistry enforcement (contracts §1.1 — P-3 by construction)', () => {
  it('(a) claiming a never-claimable slot is rejected with DS-SKIN-001', () => {
    const reg = new SkinRegistry();
    // cast to bypass the type (`claims` only allows status/diagnostics) — runtime must still reject
    const bad = fakeSkin({ id: 'bad.claim', claims: { selection: true } as unknown as { status?: true } });
    expect(() => reg.register(bad)).toThrowError(SkinRegistrationError);
    try {
      reg.register(fakeSkin({ id: 'bad.claim2', claims: { selection: true } as unknown as { status?: true } }));
    } catch (e) {
      expect((e as SkinRegistrationError).code).toBe('DS-SKIN-001');
    }
  });

  it('(b) omitting the chrome anchor is rejected with DS-SKIN-003', () => {
    const reg = new SkinRegistry();
    const noChrome = fakeSkin({
      id: 'no.chrome',
      declareAnchors: (s: NodeSize): AnchorDeclaration =>
        ({ status: { x: s.width, y: 0, align: 'tr' }, diagnostics: { x: 0, y: 0, align: 'tl' } } as unknown as AnchorDeclaration),
    });
    expect(() => reg.register(noChrome)).toThrowError(/DS-SKIN-003/);
  });

  it('(b) omitting the status anchor while NOT claiming status is DS-SKIN-003', () => {
    const reg = new SkinRegistry();
    const noStatus = fakeSkin({
      id: 'no.status',
      declareAnchors: (): AnchorDeclaration => ({ chrome: { x: 0, y: 0, align: 'tl' }, diagnostics: { x: 0, y: 0, align: 'tl' } }),
    });
    expect(() => reg.register(noStatus)).toThrowError(/DS-SKIN-003/);
  });

  it('(b) a skin that CLAIMS status need not declare a status anchor', () => {
    const reg = new SkinRegistry();
    const claimsStatus = fakeSkin({
      id: 'claims.status',
      claims: { status: true },
      declareAnchors: (): AnchorDeclaration => ({ chrome: { x: 0, y: 0, align: 'tl' }, diagnostics: { x: 0, y: 0, align: 'tl' } }),
    });
    expect(() => reg.register(claimsStatus)).not.toThrow();
  });

  it('(c) a duplicate id is rejected', () => {
    const reg = new SkinRegistry();
    reg.register(fakeSkin({ id: 'dup' }));
    expect(() => reg.register(fakeSkin({ id: 'dup' }))).toThrowError(/duplicate-id/);
  });

  it('(d) resolve() of an unknown id returns undefined (the caller\'s DS-SKIN-002 path)', () => {
    const reg = new SkinRegistry();
    expect(reg.resolve('nope')).toBeUndefined();
  });
});

describe('SkinRegistry rosters + defaults (E-3a, contracts §1.4)', () => {
  function stub(id: string, face: 'processing' | 'modeling', kind?: 'db' | 'er' | 'md' | 'cnc') {
    return fakeSkin({ id, face, modelKind: kind, displayName: id });
  }

  it('roster(processing) preserves registration order [stage, script]', () => {
    const reg = new SkinRegistry();
    reg.register(stub('stage', 'processing'));
    reg.register(stub('script', 'processing'));
    reg.register(stub('er.crow', 'modeling', 'er'));
    expect(reg.roster('processing').map((s) => s.id)).toEqual(['stage', 'script']);
  });

  it('roster(modeling, kind) returns only that kind', () => {
    const reg = new SkinRegistry();
    reg.register(stub('md.star-glyph', 'modeling', 'md'));
    reg.register(stub('md.er-dialect', 'modeling', 'md'));
    reg.register(stub('cnc.bubbles', 'modeling', 'cnc'));
    expect(reg.roster('modeling', 'md').map((s) => s.id)).toEqual(['md.star-glyph', 'md.er-dialect']);
    expect(reg.roster('modeling', 'cnc').map((s) => s.id)).toEqual(['cnc.bubbles']);
  });

  it('defaultSkin returns the pinned E-3a defaults', () => {
    const reg = new SkinRegistry();
    expect(reg.defaultSkin('processing')).toBe('stage');
    expect(reg.defaultSkin('modeling', 'er')).toBe('er.crow');
    expect(reg.defaultSkin('modeling', 'md')).toBe('md.star-glyph');
    expect(reg.defaultSkin('modeling', 'cnc')).toBe('cnc.bubbles');
    expect(reg.defaultSkin('modeling', 'db')).toBe('db.table-classic');
  });
});
