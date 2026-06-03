import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import {
  ProjectSymbolTable,
  Resolver,
  Validator,
  ReferenceIndex,
  resolveManifest,
} from '../index.js';

/**
 * Mirrors the worked example in `packages/semantics/README.md`. Fails the build
 * if the snippet ever drifts away from the live public API surface (review-024
 * caught the previous version importing names that didn't exist).
 */
describe('README worked example', () => {
  it('compiles and produces a resolved reference + zero diagnostics', () => {
    const result = parseString(
      `schema er namespace entity
def entity artikl {
  nameAttribute: id_artiklu,
  attributes: [
    def attribute id_artiklu { type: int, isKey: true },
  ],
}`,
      'artikl.ttr',
    );
    expect(result.ast).not.toBeNull();
    const ast = result.ast!;
    const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
    const namespace = ast.schemaDirective?.namespace ?? '';

    const table = new ProjectSymbolTable();
    table.upsertDocument('artikl.ttr', ast, schemaCode, namespace);

    const resolver = new Resolver(table);
    const res = resolver.resolveReference(
      { path: 'id_artiklu', parts: ['id_artiklu'] },
      { schemaCode, namespace, enclosingQname: 'er.entity.artikl' },
    );
    expect(res.resolved).toBe(true);

    const refIndex = new ReferenceIndex();
    refIndex.upsertDocument('artikl.ttr', ast, schemaCode, namespace, resolver);

    const manifest = resolveManifest(undefined, '.');
    const validator = new Validator(table, resolver, manifest);
    const diags = [
      ...validator.validateDocument('artikl.ttr', ast),
      ...validator.validateReferences('artikl.ttr', ast),
    ];
    // Sanity: nothing structurally wrong with the worked example's TTR.
    expect(diags.filter((d) => d.severity === 'error')).toHaveLength(0);
  });
});
