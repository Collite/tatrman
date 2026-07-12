// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';
import type { DrillMapDef } from '../ast.js';

describe('grammar v2.2 — drill_map', () => {
  it('parses a full drill_map block', () => {
    const r = parseString(
      [
        'package ucetnictvi',
        'model query schema drill',
        '',
        'def drill_map agg_strediska_na_doklad {',
        '  from: query.query.ucetni_zapisy_agregace_strediska,',
        '  to:   query.query.ucetni_doklad_detail,',
        '  args: { id_ucetniho_zapisu: "IDUCETZAP" },',
        '  display: { cs: "Detail dokladu", en: "Document detail" },',
        '  override: true,',
        '}',
        '',
      ].join('\n'),
    );

    expect(r.errors).toEqual([]);
    expect(r.ast?.definitions).toHaveLength(1);
    const def = r.ast!.definitions[0] as DrillMapDef;
    expect(def.kind).toBe('drillMap');
    expect(def.name).toBe('agg_strediska_na_doklad');
    expect(def.from?.path).toBe('query.query.ucetni_zapisy_agregace_strediska');
    expect(def.to?.path).toBe('query.query.ucetni_doklad_detail');
    expect(def.args).toHaveLength(1);
    expect(def.args[0].name).toBe('id_ucetniho_zapisu');
    expect(def.args[0].value.value).toBe('IDUCETZAP');
    expect(def.display?.entries['cs']).toBe('Detail dokladu');
    expect(def.display?.entries['en']).toBe('Document detail');
    expect(def.overrideAuto).toBe(true);
  });

  it('parses a minimal drill_map (no display, no override)', () => {
    const r = parseString(
      [
        'package ucetnictvi',
        'model query schema drill',
        '',
        'def drill_map agg_uctu_na_doklad {',
        '  from: query.query.ucetni_zapisy_agregace_uctu,',
        '  to:   query.query.ucetni_doklad_detail,',
        '  args: { id_ucetniho_zapisu: "IDUCETZAP" },',
        '}',
        '',
      ].join('\n'),
    );

    expect(r.errors).toEqual([]);
    const def = r.ast!.definitions[0] as DrillMapDef;
    expect(def.display).toBeUndefined();
    expect(def.overrideAuto).toBeUndefined();
  });

  it('rejects an unknown property key inside the drill_map block', () => {
    // `frmo:` typo — expected to be a parse error since the block grammar only
    // accepts the listed drillMapProperty alternatives.
    const r = parseString(
      [
        'package ucetnictvi',
        'model query schema drill',
        '',
        'def drill_map x {',
        '  frmo: query.query.a,',
        '  to:   query.query.b,',
        '  args: { p: "C" },',
        '}',
        '',
      ].join('\n'),
    );

    expect(r.errors.length).toBeGreaterThan(0);
  });

  it('accepts an empty args block', () => {
    // Validation of "args keys must be parameters on `to`" is enforced by the
    // metadata-side validator (ai-platform), not the parser.
    const r = parseString(
      [
        'package ucetnictvi',
        'model query schema drill',
        '',
        'def drill_map x {',
        '  from: query.query.a,',
        '  to:   query.query.b,',
        '  args: {},',
        '}',
        '',
      ].join('\n'),
    );

    expect(r.errors).toEqual([]);
    const def = r.ast!.definitions[0] as DrillMapDef;
    expect(def.args).toHaveLength(0);
  });
});
