// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.framework

import org.tatrman.plan.v1.SchemaCode
import org.tatrman.plan.v1.schemaCodeToToken
import org.apache.calcite.config.Lex
import org.apache.calcite.schema.SchemaPlus
import org.apache.calcite.sql.parser.SqlParser
import org.apache.calcite.tools.FrameworkConfig
import org.apache.calcite.tools.Frameworks
import org.apache.calcite.tools.Planner
import org.apache.calcite.tools.RelBuilder
import org.tatrman.translator.functions.CalciteOperatorTables
import org.tatrman.translator.parser.impl.CalciteExtParserImpl
import org.tatrman.translator.schema.SchemaPlusAdapter

/**
 * Per-query Calcite framework lifecycle holder.
 *
 * Calcite engagement rule #2: a [Planner] is single-use. The planner's internal
 * state (cluster, RelOptUtil caches, validator) cannot be reused across
 * compilations without subtle correctness bugs. Construct a fresh
 * [TranslatorFramework] for every query, call [newPlanner] / [newRelBuilder] at
 * most once per stage, and discard the framework after the query completes.
 *
 * This class is intentionally thin — it is the place where the rest of the
 * codebase obtains a planner so that the rule is enforced by convention.
 */
class TranslatorFramework(
    val model: ModelHandle,
    val schemaCode: SchemaCode = DEFAULT_SCHEMA_CODE,
    val namespace: String = DEFAULT_NAMESPACE,
) {
    val schemaPlusAdapter = SchemaPlusAdapter(model)

    val rootSchema: SchemaPlus =
        Frameworks.createRootSchema(true).also {
            if (model.namespaces(SchemaCode.DB).isNotEmpty()) it.add("db", schemaPlusAdapter.db)
            if (model.namespaces(SchemaCode.ER).isNotEmpty()) it.add("er", schemaPlusAdapter.er)
            if (model.namespaces(SchemaCode.OBJ).isNotEmpty()) it.add("obj", schemaPlusAdapter.obj)
        }

    private val frameworkConfig: FrameworkConfig =
        Frameworks
            .newConfigBuilder()
            // CalciteExtParser (decision D7) — use the generated custom parser so the T-SQL
            // extension productions (COLLATE / DATEADD / (TRY_)CONVERT) parse; `SqlParser.parseQuery`
            // uses the same factory so schema detection stays aligned with the validator.
            .parserConfig(
                SqlParser
                    .config()
                    .withLex(Lex.MYSQL_ANSI)
                    .withParserFactory(CalciteExtParserImpl.FACTORY),
            )
            // Operator resolution: our custom overrides (COLLATE + faithful CONVERT/TRY_CONVERT) and
            // the grounding catalog chain FIRST, then STANDARD + the MS SQL / PostgreSQL library
            // operator tables (loaded wholesale via SqlLibraryOperatorTableFactory) — so the full
            // T-SQL function surface (CONCAT, IIF, ISNULL, LEFT/RIGHT, LEN, DATEADD, …) resolves and
            // validates, while our custom operators win any name collision. See
            // [CalciteOperatorTables.permissiveUnion] for the ordering + the "don't double-chain
            // SqlStdOperatorTable" rationale.
            .operatorTable(CalciteOperatorTables.permissiveUnion)
            .defaultSchema(
                rootSchema
                    .subSchemas()
                    .get(schemaCodeToToken(schemaCode))
                    ?.let { topLevel ->
                        if (namespace.isNotEmpty()) {
                            topLevel.subSchemas().get(namespace) ?: topLevel
                        } else {
                            topLevel
                        }
                    }
                    ?: rootSchema,
            ).build()

    /**
     * Hand back a fresh [Planner] for a single query stage. Callers must NOT
     * reuse the returned instance beyond the stage that constructed it.
     */
    fun newPlanner(): Planner = Frameworks.getPlanner(frameworkConfig)

    /**
     * Hand back a fresh [RelBuilder] anchored to the framework's root schema.
     */
    fun newRelBuilder(): RelBuilder = RelBuilder.create(frameworkConfig)

    companion object {
        val DEFAULT_SCHEMA_CODE = SchemaCode.DB
        const val DEFAULT_NAMESPACE = "dbo"
    }
}
