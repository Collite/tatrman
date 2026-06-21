import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import { ProjectSymbolTable } from '../project-symbols.js';
import { Resolver } from '../resolver.js';
import { effectivePackage } from '../derivation.js';
import type { PackagesConfig } from '../manifest.js';

const ROOT_DIR = '/proj';
const cfg: PackagesConfig = { root: 'cz.dfpartner', layout: 'flexible' };

const ENTITY = 'def entity x { attributes: [def attribute id { type: int }] }';

function project(uri: string, src: string) {
  const symbols = new ProjectSymbolTable();
  const ast = parseString(src, uri).ast!;
  const pkg = effectivePackage(ast, uri, ROOT_DIR, cfg);
  symbols.upsertDocument(
    uri,
    ast,
    ast.schemaDirective?.schemaCode ?? '',
    ast.schemaDirective?.namespace ?? '',
    pkg
  );
  return { symbols, resolver: new Resolver(symbols, cfg.root), pkg };
}

const ctx = { schemaCode: 'er', namespace: 'entity' };
const resolve = (r: Resolver, path: string) =>
  r.resolveReference({ path, parts: path.split('.') }, ctx);

describe('PD1.4 — root elision in the resolver', () => {
  it('declared package elides root: both prefixed and bare references resolve to the same symbol', () => {
    // File declares `package a.b` (omits the root); canonical qname omits root.
    const { resolver, pkg } = project('file:///proj/a/b/er.ttr', `package a.b\nschema er namespace entity\n${ENTITY}`);
    expect(pkg).toBe('a.b');

    const bare = resolve(resolver, 'a.b.er.entity.x');
    const prefixed = resolve(resolver, 'cz.dfpartner.a.b.er.entity.x');

    expect(bare.resolved).toBe(true);
    expect(prefixed.resolved).toBe(true);
    if (bare.resolved && prefixed.resolved) {
      expect(bare.symbol.qname).toBe('a.b.er.entity.x');
      expect(prefixed.symbol.qname).toBe(bare.symbol.qname);
    }
  });

  it('derived package carries root: both prefixed and bare references resolve to the same symbol', () => {
    // No declaration → effective package is root-prefixed; canonical qname carries root.
    const { resolver, pkg } = project('file:///proj/a/b/er.ttr', `schema er namespace entity\n${ENTITY}`);
    expect(pkg).toBe('cz.dfpartner.a.b');

    const bare = resolve(resolver, 'a.b.er.entity.x');
    const prefixed = resolve(resolver, 'cz.dfpartner.a.b.er.entity.x');

    expect(bare.resolved).toBe(true);
    expect(prefixed.resolved).toBe(true);
    if (bare.resolved && prefixed.resolved) {
      expect(prefixed.symbol.qname).toBe('cz.dfpartner.a.b.er.entity.x');
      expect(bare.symbol.qname).toBe(prefixed.symbol.qname);
    }
  });

  it('with no root configured, references resolve unchanged', () => {
    const symbols = new ProjectSymbolTable();
    const uri = 'file:///proj/a/b/er.ttr';
    const ast = parseString(`package a.b\nschema er namespace entity\n${ENTITY}`, uri).ast!;
    symbols.upsertDocument(uri, ast, 'er', 'entity', 'a.b');
    const resolver = new Resolver(symbols); // no root

    const r = resolve(resolver, 'a.b.er.entity.x');
    expect(r.resolved).toBe(true);
  });
});
