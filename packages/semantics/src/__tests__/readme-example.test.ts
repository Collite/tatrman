import { describe, it, expect } from 'vitest';
import { parseString } from '@modeler/parser';
import {
  ProjectSymbolTable,
  Resolver,
  ReferenceIndex,
} from '../index.js';

/**
 * Mirrors the worked example in `packages/semantics/README.md`. Fails the build
 * if the snippet ever drifts away from the live public API surface (review-024
 * caught the previous version importing names that didn't exist).
 *
 * Diagnostics moved to `@modeler/lint` (the `Validator` class was removed); the
 * lint runner is exercised by `@modeler/lint`'s own tests, so this example now
 * covers only the semantics surface (symbols + resolver + reference index).
 */
describe('README worked example', () => {
  it('compiles and produces a resolved reference', () => {
    const result = parseString(
      `schema er namespace entity
def entity artikl {
  nameAttribute: id_artiklu,
  attributes: [
    def attribute id_artiklu { type: int, isKey: true },
  ],
}`,
      'artikl.ttrm',
    );
    expect(result.ast).not.toBeNull();
    const ast = result.ast!;
    const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
    const namespace = ast.schemaDirective?.namespace ?? '';

    const table = new ProjectSymbolTable();
    table.upsertDocument('artikl.ttrm', ast, schemaCode, namespace);

    const resolver = new Resolver(table);
    const res = resolver.resolveReference(
      { path: 'id_artiklu', parts: ['id_artiklu'] },
      { schemaCode, namespace, enclosingQname: 'er.entity.artikl' },
    );
    expect(res.resolved).toBe(true);

    const refIndex = new ReferenceIndex();
    refIndex.upsertDocument('artikl.ttrm', ast, schemaCode, namespace, resolver);
    expect(refIndex).toBeDefined();
  });
});
