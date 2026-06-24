import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';

describe('inline mapping grammar — all surface forms', () => {
  function errors(src: string): string[] {
    const r = parseString(src);
    return r.errors.map((e) => e.message);
  }

  it('entity full — forms (a), (b), (c) with entity target shorthand', () => {
    const src = `schema er namespace entity
def entity artikl {
  mapping: {
    target: { table: db.dbo.QZBOZI_DF },
    columns: {
      id_artiklu:    IDZBOZI,
      kód_artiklu:   { target: KOD_ZBOZI },
      název_artiklu: { target: { column: NAZEV_ZBOZI } }
    }
  },
  attributes: [ def attribute id_artiklu { type: int, isKey: true } ]
}`;
    expect(errors(src), 'entity full a+b+c must parse').toHaveLength(0);
  });

  it('attribute bare-id — mapping: <bareId>', () => {
    const src = `schema er namespace entity
def entity a { attributes: [
  def attribute id_produktu { type: int, mapping: IDSKUPZBOZI }
] }`;
    expect(errors(src), 'attribute bare-id must parse').toHaveLength(0);
  });

  it('attribute full — mapping: { target: { column: ... } }', () => {
    const src = `schema er namespace entity
def entity a { attributes: [
  def attribute název { type: text, mapping: { target: { column: NAZEV_ZBOZI } } }
] }`;
    expect(errors(src), 'attribute full must parse').toHaveLength(0);
  });

  it('relation bare-fk — mapping: <bareId>', () => {
    const src = `schema er namespace entity
def entity a {}
def entity b {}
def relation r { from: er.entity.a, to: er.entity.b, mapping: db.dbo.fk_artikl_produkt }`;
    expect(errors(src), 'relation bare-fk must parse').toHaveLength(0);
  });

  it('relation full — mapping: { fk: ... }', () => {
    const src = `schema er namespace entity
def entity a {}
def entity b {}
def relation r { from: er.entity.a, to: er.entity.b, mapping: { fk: db.dbo.fk_artikl_produkt } }`;
    expect(errors(src), 'relation full must parse').toHaveLength(0);
  });

  it('target: shorthand in explicit er2db_attribute', () => {
    const src = `schema binding namespace m
def er2db_attribute foo { target: KOD_ZBOZI }`;
    expect(errors(src), 'target shorthand in er2db_attribute must parse').toHaveLength(0);
  });
});