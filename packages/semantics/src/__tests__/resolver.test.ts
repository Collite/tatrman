import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { Resolver } from '../resolver.js';

function tableWith(uri: string, src: string) {
  const ast = parseString(src, uri).ast!;
  const schemaCode = ast.modelDirective?.modelCode ?? 'db';
  const namespace = ast.modelDirective?.schema ?? '';
  const table = new ProjectSymbolTable();
  table.upsertDocument(uri, ast, schemaCode, namespace);
  return { table, schemaCode, namespace };
}

describe('Resolver.resolveReference (dotted)', () => {
  it('resolves a fully-qualified dotted reference', () => {
    const { table } = tableWith(
      'er.ttrm',
      `model er schema entity
       def entity artikl { attributes: [def attribute id { type: int }] }`
    );
    const resolver = new Resolver(table);
    const res = resolver.resolveReference(
      { path: 'er.entity.artikl', parts: ['er', 'entity', 'artikl'] },
      { schemaCode: 'er', namespace: 'entity' }
    );
    expect(res.resolved).toBe(true);
    if (res.resolved) {
      expect(res.symbol.qname).toBe('er.entity.artikl');
    }
  });

  it('returns not-found with the tried qnames populated', () => {
    const { table } = tableWith(
      'er.ttrm',
      `model er schema entity
       def entity artikl { attributes: [def attribute id { type: int }] }`
    );
    const resolver = new Resolver(table);
    const res = resolver.resolveReference(
      { path: 'er.entity.nonexistent', parts: ['er', 'entity', 'nonexistent'] },
      { schemaCode: 'er', namespace: 'entity' }
    );
    expect(res.resolved).toBe(false);
    if (!res.resolved) {
      expect(res.tried.length).toBeGreaterThan(0);
      expect(res.tried[0].candidate).toContain('nonexistent');
    }
  });

  it('resolves a bare id via the context schema/namespace', () => {
    const { table, schemaCode, namespace } = tableWith(
      'er.ttrm',
      `model er schema entity
       def entity artikl { attributes: [def attribute id { type: int }] }`
    );
    const resolver = new Resolver(table);
    const res = resolver.resolveReference(
      { path: 'artikl', parts: ['artikl'] },
      { schemaCode, namespace }
    );
    expect(res.resolved).toBe(true);
    if (res.resolved) expect(res.symbol.qname).toBe('er.entity.artikl');
  });
});

describe('Resolver.resolveBareId', () => {
  it('resolves an attribute name through the enclosing entity scope', () => {
    const { table } = tableWith(
      'er.ttrm',
      `model er schema entity
       def entity artikl { attributes: [def attribute nazev { type: string }] }`
    );
    const resolver = new Resolver(table);
    const res = resolver.resolveBareId('nazev', {
      schemaCode: 'er',
      namespace: 'entity',
      enclosing: { kind: 'entity', qname: 'er.entity.artikl' },
    });
    expect(res.resolved).toBe(true);
    if (res.resolved) expect(res.symbol.qname).toBe('er.entity.artikl.nazev');
  });

  // TODO(B3): Stock cnc qname doubled to cnc.cnc.role.* in B2; resolver
  // resolveBareId still looks for cnc.role.* (line 95). B3 updates the resolver's
  // stock-cnc fallback to match the doubled form.
  it('falls through to stock cnc.cnc.role.<name> when the bare id matches one', () => {
    const stock = parseString(
      `model cnc schema role
       def role fact { description: "fact" }`,
      'stock://cnc-roles.ttrm'
    ).ast!;
    const table = new ProjectSymbolTable();
    table.upsertDocument('stock://cnc-roles.ttrm', stock, 'cnc', 'role');

    const resolver = new Resolver(table);
    const res = resolver.resolveBareId('fact', { schemaCode: 'er', namespace: 'entity' });
    expect(res.resolved).toBe(true);
    if (res.resolved) expect(res.symbol.qname).toBe('cnc.role.fact');
  });

  it('returns not-found when the bare id resolves nowhere', () => {
    const table = new ProjectSymbolTable();
    const resolver = new Resolver(table);
    const res = resolver.resolveBareId('does_not_exist', {
      schemaCode: 'er',
      namespace: 'entity',
    });
    expect(res.resolved).toBe(false);
  });
});
