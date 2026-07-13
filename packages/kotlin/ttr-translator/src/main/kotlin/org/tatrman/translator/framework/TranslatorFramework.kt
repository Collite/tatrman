// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.framework

import org.tatrman.plan.v1.SchemaCode
import org.tatrman.plan.v1.schemaCodeToToken
import org.apache.calcite.config.Lex
import org.apache.calcite.schema.SchemaPlus
import org.apache.calcite.sql.`fun`.SqlStdOperatorTable
import org.apache.calcite.sql.parser.SqlParser
import org.apache.calcite.sql.util.SqlOperatorTables
import org.apache.calcite.tools.FrameworkConfig
import org.apache.calcite.tools.Frameworks
import org.apache.calcite.tools.Planner
import org.apache.calcite.tools.RelBuilder
import org.tatrman.translator.functions.PlatformOperators
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
            .parserConfig(SqlParser.config().withLex(Lex.MYSQL_ANSI))
            // RG-P3 — chain the platform grounding operators (period_start/period_end/
            // geo_distance_m) after the Calcite standard table so grounding recipe SQL
            // (`period_start({p})`) resolves + validates. Additive: the standard operator surface
            // (the previous default) is preserved as the first table in the chain.
            .operatorTable(
                SqlOperatorTables.chain(
                    SqlStdOperatorTable.instance(),
                    PlatformOperators.OPERATOR_TABLE,
                ),
            ).defaultSchema(
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
