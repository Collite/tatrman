// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { renderDataType } from '../model-graph';

describe('renderDataType', () => {
  it("{kind:'simple',name:'int'} → 'int'", () => {
    expect(renderDataType({ kind: 'simple', name: 'int' })).toBe('int');
  });

  it("'{kind:'structured',typeName:'varchar',length:40}' → 'varchar(40)'", () => {
    expect(renderDataType({ kind: 'structured', typeName: 'varchar', length: 40 })).toBe('varchar(40)');
  });

  it("'{kind:'structured',typeName:'decimal',length:10,precision:2}' → 'decimal(10,2)'", () => {
    expect(renderDataType({ kind: 'structured', typeName: 'decimal', length: 10, precision: 2 })).toBe('decimal(10,2)');
  });

  it('undefined → null', () => {
    expect(renderDataType(undefined)).toBe(null);
  });
});