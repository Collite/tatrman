// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.functions

import org.apache.calcite.sql.SqlOperatorTable
import org.apache.calcite.sql.`fun`.SqlLibraryOperators
import org.apache.calcite.sql.util.SqlOperatorTables

/**
 * The calcite-ext extension operator surface backing the extended parser's T-SQL productions
 * (COLLATE / DATEADD / DATEDIFF / DATEPART / DATE_PART / (TRY_)CONVERT — the `CalciteExtParserImpl`
 * generated from `src/main/codegen/`). Chained **additively** after `SqlStdOperatorTable ⊕
 * [PlatformOperators]` in [org.tatrman.translator.framework.TranslatorFramework], so a parsed
 * extension node also *validates* without disturbing the existing operator-resolution surface
 * (decision D7 / CalciteExtParser port — the additive subset, not ai-platform's `permissiveUnion`).
 *
 * Grows per phase: CEP-P0 registers COLLATE; CEP-P1 adds the DATEADD family (Calcite
 * `SqlLibraryOperators`); CEP-P2 adds CONVERT / TRY_CONVERT ([ConvertOperators.table]).
 */
object ExtOperators {
    val OPERATOR_TABLE: SqlOperatorTable =
        SqlOperatorTables.of(
            // CEP-P0 — postfix COLLATE.
            SqlCollateOperator,
            // CEP-P1 — the T-SQL datetime family; these are Calcite built-ins (SqlLibraryOperators),
            // registered here so the parser's DATEADD/DATEDIFF/DATEPART/DATE_PART productions validate.
            SqlLibraryOperators.DATEADD,
            SqlLibraryOperators.DATEDIFF,
            SqlLibraryOperators.DATEPART,
            SqlLibraryOperators.DATE_PART,
        )
}
