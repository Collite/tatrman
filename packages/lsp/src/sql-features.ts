import type { SqlRefModel, Span } from '@modeler/sql';
import type { SqlRefHit } from '@modeler/semantics';

const covers = (span: Span, offset: number): boolean =>
  offset >= span.offset && offset < span.offset + span.length;

/**
 * Hit-test a SQL-local char offset against a `SqlRefModel`: return the
 * column/table ref whose span covers it, preferring the **smallest** covering
 * span (columns are tighter than the whole `table_source_item`, so a cursor on
 * `email` in `FROM users u` picks the column, not the table). Returns `undefined`
 * when the offset is on a keyword, literal, whitespace, or `{param}` — callers
 * degrade quietly. Shared by hover (§4.1) and go-to-definition (§4.2).
 */
export function findSqlRefAtOffset(model: SqlRefModel, offset: number): SqlRefHit | undefined {
  let best: SqlRefHit | undefined;
  let bestLen = Infinity;
  for (const ref of model.columns) {
    if (ref.name !== '*' && covers(ref.span, offset) && ref.span.length < bestLen) {
      best = { kind: 'column', ref };
      bestLen = ref.span.length;
    }
  }
  for (const ref of model.tables) {
    if (covers(ref.span, offset) && ref.span.length < bestLen) {
      best = { kind: 'table', ref };
      bestLen = ref.span.length;
    }
  }
  return best;
}
