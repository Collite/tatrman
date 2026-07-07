package org.tatrman.ttrp.emit.sql

import org.tatrman.plan.v1.QualifiedName
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.translator.framework.EntityMapping
import org.tatrman.translator.framework.ModelAttribute
import org.tatrman.translator.framework.ModelColumn
import org.tatrman.translator.framework.ModelEntity
import org.tatrman.translator.framework.ModelForeignKey
import org.tatrman.translator.framework.ModelHandle
import org.tatrman.translator.framework.ModelRelation
import org.tatrman.translator.framework.ModelSavedQuery
import org.tatrman.translator.framework.ModelTable
import org.tatrman.translator.framework.SavedQueryBody

/**
 * A [ModelHandle] over a fixed set of physical DB tables — the only surface the
 * translator needs to type-check and unparse a purely relational island. TTR-P has
 * already resolved er→db (E-d) and normalized the graph, so the ER side of the SPI
 * is empty here: emit only ever hands the translator physical [org.tatrman.plan.v1.TableScanNode]
 * leaves referencing these tables.
 *
 * Two kinds of table live here: real base tables (a [org.tatrman.ttrp.graph.model.Load]'s
 * backing storage table) and synthetic CTE pseudo-tables (one per emitted island node,
 * so the translator can resolve `FROM <cte-name>` when we drive it node-by-node —
 * [CtePlanner]). All are registered under the same [namespace] so unparsed `FROM`
 * clauses stay bare.
 */
class IslandModelHandle(
    tables: List<ModelTable>,
    private val namespace: String = DEFAULT_NAMESPACE,
) : ModelHandle {
    private val byNamespace: Map<String, Map<QualifiedName, ModelTable>> =
        tables
            .groupBy { it.qname.namespace }
            .mapValues { (_, ts) -> ts.associateBy { it.qname } }
    private val columnsByQname: Map<QualifiedName, List<ModelColumn>> =
        tables.associate { it.qname to it.columns }

    override fun tables(
        schemaCode: SchemaCode,
        namespace: String,
    ): Map<QualifiedName, ModelTable> =
        if (schemaCode == SchemaCode.DB) byNamespace[namespace].orEmpty() else emptyMap()

    override fun columns(tableQname: QualifiedName): List<ModelColumn> = columnsByQname[tableQname].orEmpty()

    override fun namespaces(schemaCode: SchemaCode): Set<String> =
        if (schemaCode == SchemaCode.DB) byNamespace.keys else emptySet()

    override fun foreignKeys(): List<ModelForeignKey> = emptyList()

    override fun entities(
        schemaCode: SchemaCode,
        namespace: String,
    ): Map<QualifiedName, ModelEntity> = emptyMap()

    override fun attributes(entityQname: QualifiedName): List<ModelAttribute> = emptyList()

    override fun relations(): List<ModelRelation> = emptyList()

    override fun entityMapping(entityQname: QualifiedName): EntityMapping? = null

    override fun savedQueries(
        schemaCode: SchemaCode,
        namespace: String,
    ): Map<QualifiedName, ModelSavedQuery> = emptyMap()

    override fun savedQueryBody(queryQname: QualifiedName): SavedQueryBody =
        throw IllegalStateException("IslandModelHandle carries no saved queries")

    override fun currentVersion(): String = "ttrp-emit"

    companion object {
        /** The namespace all emit tables register under; matches the framework default. */
        const val DEFAULT_NAMESPACE = "dbo"
    }
}
