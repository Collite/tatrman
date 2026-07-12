// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
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
 * Diagnostics moved to `@tatrman/lint` (the `Validator` class was removed); the
 * lint runner is exercised by `@tatrman/lint`'s own tests, so this example now
 * covers only the semantics surface (symbols + resolver + reference index).
 */
describe('README worked example', () => {
  it('compiles and produces a resolved reference', () => {
    const result = parseString(
      `model er schema entity
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
    const schemaCode = ast.modelDirective?.modelCode ?? 'db';
    const namespace = ast.modelDirective?.schema ?? '';

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
