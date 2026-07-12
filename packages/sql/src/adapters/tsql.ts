// SPDX-License-Identifier: Apache-2.0
import type { ParseTree } from 'antlr4ng';
import {
  As_table_aliasContext,
  Common_table_expressionContext,
  Derived_tableContext,
  Full_column_nameContext,
  Full_table_nameContext,
  Id_Context,
  Query_specificationContext,
  Table_source_itemContext,
} from '../generated/tsql/TSqlParser.js';
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

const nameParts = (ftn: Full_table_nameContext): string[] =>
  directChildren(ftn, Id_Context).map((id) => stripDelims(id.getText()));

function aliasOf(tsi: Table_source_itemContext): string | undefined {
  const ata = firstChild(tsi, As_table_aliasContext);
  if (!ata) return undefined;
  const id = descendants(ata, Id_Context)[0];
  return id ? stripDelims(id.getText()) : undefined;
}

function tableRef(tsi: Table_source_itemContext, cteNames: Set<string>): SqlTableRef | null {
  const alias = aliasOf(tsi);
  const ftn = firstChild(tsi, Full_table_nameContext);
  if (ftn) {
    const name = nameParts(ftn);
    const isCte = name.length === 1 && cteNames.has(name[0]!);
    return { name, alias, span: spanOfCtx(tsi), origin: isCte ? 'cte' : 'base' };
  }
  if (firstChild(tsi, Derived_tableContext)) {
    return { name: [], alias, span: spanOfCtx(tsi), origin: 'derived' };
  }
  return null;
}

function columnRef(fcn: Full_column_nameContext): SqlColumnRef {
  // fcn = [Full_table_name(qualifier)? '.'] Id_(column). The qualifier's own id_s
  // are nested under Full_table_name, so the column id is the last DIRECT id_.
  const ids = directChildren(fcn, Id_Context);
  const colId = ids[ids.length - 1];
  const name = colId ? stripDelims(colId.getText()) : fcn.getText();
  const qualFtn = firstChild(fcn, Full_table_nameContext);
  const qualParts = qualFtn ? nameParts(qualFtn) : [];
  return { name, qualifier: qualParts[qualParts.length - 1], span: spanOfCtx(fcn) };
}

function nearestQuery(node: ParseTree): Query_specificationContext | undefined {
  let p = (node as Query_specificationContext).parent;
  while (p) {
    if (p instanceof Query_specificationContext) return p;
    p = p.parent;
  }
  return undefined;
}

export function extractTsql(tree: ParseTree): SqlRefModel {
  const ctes = descendants(tree, Common_table_expressionContext).map((c) => {
    const id = descendants(c, Id_Context)[0];
    return { name: id ? stripDelims(id.getText()) : '', span: spanOfCtx(c) };
  });
  const cteNames = new Set(ctes.map((c) => c.name));

  const allItems = descendants(tree, Table_source_itemContext);
  const tables = allItems
    .map((tsi) => tableRef(tsi, cteNames))
    .filter((t): t is SqlTableRef => t !== null);

  const columns = descendants(tree, Full_column_nameContext).map(columnRef);

  const params = terminals(tree)
    .map((t) => {
      const n = paramName(t.getText());
      return n ? { name: n, span: spanOfToken(t.symbol) } : null;
    })
    .filter((p): p is { name: string; span: SqlColumnRef['span'] } => p !== null);

  const outer = descendants(tree, Query_specificationContext).find(
    (qs) => !hasAncestor(qs, [Common_table_expressionContext, Derived_tableContext]),
  );
  const rootScope: SqlScope = {
    tables: allItems
      .filter((tsi) => nearestQuery(tsi) === outer)
      .map((tsi) => tableRef(tsi, cteNames))
      .filter((t): t is SqlTableRef => t !== null),
  };

  return { tables, columns, ctes, params, rootScope, parseErrors: errorNodes(tree) };
}
