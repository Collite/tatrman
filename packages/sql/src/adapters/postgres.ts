import type { ParseTree } from 'antlr4ng';
import {
  Alias_clauseContext,
  ColidContext,
  ColumnrefContext,
  Common_table_exprContext,
  IndirectionContext,
  Indirection_elContext,
  Qualified_nameContext,
  Relation_exprContext,
  Select_with_parensContext,
  Table_refContext,
} from '../generated/postgresql/PostgreSQLParser.js';
import type { SqlColumnRef, SqlRefModel, SqlScope, SqlTableRef } from '../refmodel.js';
import {
  descendants,
  directChildren,
  errorNodes,
  firstChild,
  hasAncestor,
  paramName,
  spanOfCtx,
  spanOfToken,
  stripDelims,
  terminals,
} from './walk.js';

/** Dotted parts of a `colid` + optional `.indirection` chain (tables and columns share this). */
function dottedParts(colid: ColidContext | undefined, ind: IndirectionContext | undefined): string[] {
  const parts: string[] = [];
  if (colid) parts.push(stripDelims(colid.getText()));
  if (ind) {
    for (const el of directChildren(ind, Indirection_elContext)) {
      const t = el.getText().replace(/^\./, '');
      if (t && t !== '*') parts.push(stripDelims(t));
    }
  }
  return parts;
}

const qnameParts = (qn: Qualified_nameContext): string[] =>
  dottedParts(firstChild(qn, ColidContext), firstChild(qn, IndirectionContext));

function aliasOf(tr: Table_refContext): string | undefined {
  const ac = firstChild(tr, Alias_clauseContext);
  const id = ac ? firstChild(ac, ColidContext) : undefined;
  return id ? stripDelims(id.getText()) : undefined;
}

function tableRef(tr: Table_refContext, cteNames: Set<string>): SqlTableRef | null {
  const alias = aliasOf(tr);
  const rel = firstChild(tr, Relation_exprContext);
  if (rel) {
    const qn = firstChild(rel, Qualified_nameContext);
    const name = qn ? qnameParts(qn) : [];
    const isCte = name.length === 1 && cteNames.has(name[0]!);
    return { name, alias, span: spanOfCtx(tr), origin: isCte ? 'cte' : 'base' };
  }
  if (firstChild(tr, Select_with_parensContext)) {
    return { name: [], alias, span: spanOfCtx(tr), origin: 'derived' };
  }
  return null;
}

function columnRef(cr: ColumnrefContext): SqlColumnRef {
  const parts = dottedParts(firstChild(cr, ColidContext), firstChild(cr, IndirectionContext));
  return {
    name: parts[parts.length - 1] ?? cr.getText(),
    qualifier: parts.length > 1 ? parts[parts.length - 2] : undefined,
    span: spanOfCtx(cr),
  };
}

export function extractPostgres(tree: ParseTree): SqlRefModel {
  const ctes = descendants(tree, Common_table_exprContext).map((c) => {
    // The CTE name is `name → colid`; the first colid in pre-order is the name
    // (the subquery's colids come later).
    const id = descendants(c, ColidContext)[0];
    return { name: id ? stripDelims(id.getText()) : '', span: spanOfCtx(c) };
  });
  const cteNames = new Set(ctes.map((c) => c.name));

  const allRefs = descendants(tree, Table_refContext);
  const tables = allRefs
    .map((tr) => tableRef(tr, cteNames))
    .filter((t): t is SqlTableRef => t !== null);

  const columns = descendants(tree, ColumnrefContext).map(columnRef);

  const params = terminals(tree)
    .map((t) => {
      const n = paramName(t.getText());
      return n ? { name: n, span: spanOfToken(t.symbol) } : null;
    })
    .filter((p): p is { name: string; span: SqlColumnRef['span'] } => p !== null);

  const rootScope: SqlScope = {
    tables: allRefs
      .filter((tr) => !hasAncestor(tr, [Select_with_parensContext, Common_table_exprContext]))
      .map((tr) => tableRef(tr, cteNames))
      .filter((t): t is SqlTableRef => t !== null),
  };

  return { tables, columns, ctes, params, rootScope, parseErrors: errorNodes(tree) };
}
