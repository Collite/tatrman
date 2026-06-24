import type { ProjectFile } from './helpers.js';

/**
 * Corpus covering every emittable diagnostic class. Used by the golden parity
 * test, which freezes the old Validator's output as a committed snapshot
 * (`golden-snapshot.json`) so the regression net survives `Validator`'s deletion.
 */
export const CORPUS: ProjectFile[] = [
  { uri: '/proj/pkg/structure.ttrm', src: `package pkg
schema db namespace dbo
def table empty { description: "x" }
def column lonely { description: "x" }` },
  { uri: '/proj/pkg/entity.ttrm', src: `package pkg
schema er namespace ent
def entity empty { description: "x" }
def entity withAttrs {
  attributes: [def attribute id { type: int }]
  nameAttribute: ghost
}` },
  { uri: '/proj/pkg/table.ttrm', src: `package pkg
schema db namespace dbo
def table orders {
  columns: [def column id { type: int }]
  primaryKey: ["missing"]
  search: { fuzzy: true }
}` },
  { uri: '/proj/pkg/refs.ttrm', src: `package pkg
schema er namespace ent
def relation r {
  from: ghost_a
  to: ghost_b
}` },
  { uri: '/proj/pkg/imports.ttrm', src: `package pkg
import other.db.dbo.thing
import other.db.dbo.thing
import nowhere.*
schema db namespace dbo
def table t { columns: [def column id { type: int }] }` },
  { uri: '/proj/sub/mismatch.ttrm', src: `package totally.wrong
schema db namespace dbo
def table t { columns: [def column id { type: int }] }` },
  { uri: '/proj/sub/missing.ttrm', src: `schema db namespace dbo
def table t2 { columns: [def column id { type: int }] }` },
  { uri: '/proj/mygraph.ttrg', src: `graph wrongname {
  objects: []
}` },
  { uri: '/proj/pkg/dupA.ttrm', src: `package pkg
schema db namespace dbo
def table dup { columns: [def column id { type: int }] }` },
  { uri: '/proj/pkg/dupB.ttrm', src: `package pkg
schema db namespace dbo
def table dup { columns: [def column id { type: int }] }` },
];

export const PROJECT_ROOT = '/proj';

/** Stable comparison key for a diagnostic (code+severity+exact source+message). */
export function diagKey(d: {
  code: string;
  severity: string;
  source: { file: string; line: number; column: number; endLine: number; endColumn: number };
  message: string;
}): string {
  const s = d.source;
  return [d.code, d.severity, s.file, s.line, s.column, s.endLine, s.endColumn, d.message].join('');
}
