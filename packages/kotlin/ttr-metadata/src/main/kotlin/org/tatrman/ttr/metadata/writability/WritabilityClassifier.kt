// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.writability

import org.tatrman.ttr.metadata.model.AttributeMappingTarget
import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttr.metadata.model.Entity
import org.tatrman.ttr.metadata.model.Er2DbAttributeMapping
import org.tatrman.ttr.metadata.model.Er2DbEntityMapping
import org.tatrman.ttr.metadata.model.MappingTarget
import org.tatrman.ttr.metadata.model.Model

/**
 * EN-P1.2 — the writability classifier (FO contracts §7 / demand `ttrm-md-declarations.md` §3). Per
 * model version, every ER entity gets a §7 verdict: writable (rung-v1) with its base-table lowering, or
 * not-writable with a `whyNot` code.
 *
 * **Rung-v1 detection (this wave, FO-32):** an entity is writable iff it is backed by a **single base
 * DB table** via an **identity binding** whose **key attributes map onto that table's primary key**.
 * Everything else is not-writable with the most specific derivable reason. The verdict carries `rung`
 * so rung-v2 (filter-only predicate inversion) slots in without a payload change.
 *
 * Pure function of the resolved [Model] — same model ⇒ byte-identical payload (entities sorted by
 * qname; no map-iteration leakage). See [WritabilityPayload].
 */
object WritabilityClassifier {
    fun classify(model: Model): WritabilityPayload {
        val entities =
            model
                .objectByQname()
                .values
                .filterIsInstance<Entity>()
                .sortedBy { it.qname.dotted() }
        val entMappings = model.mappings.filterIsInstance<Er2DbEntityMapping>().associateBy { it.entity }
        val attrMappings = model.mappings.filterIsInstance<Er2DbAttributeMapping>().associateBy { it.attribute }
        val objects = model.objectByQname()
        val verdicts = entities.map { classifyEntity(it, objects, entMappings, attrMappings) }
        return WritabilityPayload(modelVersion = model.version.value, entities = verdicts)
    }

    private fun bare(dottedColumn: String): String = dottedColumn.substringAfterLast('.')

    private fun table(dottedColumn: String): String = dottedColumn.substringBeforeLast('.', "")

    private fun classifyEntity(
        e: Entity,
        objects: Map<org.tatrman.ttr.metadata.model.QualifiedName, org.tatrman.ttr.metadata.model.ModelObject>,
        entMappings: Map<org.tatrman.ttr.metadata.model.QualifiedName, Er2DbEntityMapping>,
        attrMappings: Map<org.tatrman.ttr.metadata.model.QualifiedName, Er2DbAttributeMapping>,
    ): EntityWritability {
        val q = e.qname.dotted()

        // An aggregation-derived attribute makes the whole entity non-invertible.
        if (e.attributes.any { it.aggregated }) {
            return notWritable(q, WhyNotCode.AGGREGATION, "entity '$q' has aggregation-derived attributes", null)
        }

        val em =
            entMappings[e.qname]
                ?: return notWritable(
                    q,
                    WhyNotCode.NO_DECLARED_WRITEBACK,
                    "entity '$q' declares no db binding",
                    "rung-v3",
                )

        val target = em.target
        if (target !is MappingTarget.Table) {
            val kind = if (target is MappingTarget.View) "view" else "query"
            return notWritable(
                q,
                WhyNotCode.NON_KEY_PRESERVED_JOIN,
                "entity '$q' is backed by a $kind, not a single base table",
                "rung-v2",
            )
        }
        val tableName = target.qname.name

        // Resolve each attribute's target column; a computed expression or a foreign-table column
        // breaks identity binding.
        val binding = LinkedHashMap<String, String>()
        for (attr in e.attributes) {
            val attrName = attr.qname.name.substringAfterLast('.')
            when (val at = attrMappings[attr.qname]?.target) {
                is AttributeMappingTarget.Expression ->
                    return notWritable(
                        q,
                        WhyNotCode.COMPUTED_COLUMN,
                        "attribute '$q.$attrName' is a computed expression",
                        null,
                    )
                is AttributeMappingTarget.Column -> {
                    val col = at.qname.name // "TABLE.COLUMN"
                    val colTable = table(col)
                    if (colTable.isNotEmpty() && colTable != tableName) {
                        return notWritable(
                            q,
                            WhyNotCode.NON_KEY_PRESERVED_JOIN,
                            "attribute '$q.$attrName' maps to a column of table '$colTable', not the base table '$tableName'",
                            "rung-v2",
                        )
                    }
                    binding[attrName] = bare(col)
                }
                null -> binding[attrName] = attrName // identity-by-name fallback
            }
        }

        // Key preservation: the entity's key attributes must map exactly onto the base table's PK. A base
        // table that does not resolve, or declares no primary key, has no key to preserve — there is no
        // basis for keyed writeback, so it is NOT writable (§7 fail-closed — this gate must never fail open).
        val dbTable = objects[target.qname] as? DbTable
        if (dbTable == null) {
            return notWritable(
                q,
                WhyNotCode.NON_KEY_PRESERVED_JOIN,
                "entity '$q' base table '$tableName' does not resolve to a keyed table",
                null,
            )
        }
        if (dbTable.primaryKey.isEmpty()) {
            return notWritable(
                q,
                WhyNotCode.NON_KEY_PRESERVED_JOIN,
                "entity '$q' base table '$tableName' declares no primary key — no keyed writeback is possible",
                null,
            )
        }
        val keyCols =
            e.attributes
                .filter { it.isKey }
                .mapNotNull { binding[it.qname.name.substringAfterLast('.')] }
                .toSet()
        if (keyCols != dbTable.primaryKey.toSet()) {
            return notWritable(
                q,
                WhyNotCode.NON_KEY_PRESERVED_JOIN,
                "entity '$q' key columns $keyCols do not match the base table primary key ${dbTable.primaryKey}",
                "rung-v2",
            )
        }

        return EntityWritability.Writable(q, "v1", Lowering(baseTable = tableName, binding = binding))
    }

    private fun notWritable(
        qname: String,
        code: WhyNotCode,
        detail: String,
        unlockedBy: String?,
    ): EntityWritability.NotWritable = EntityWritability.NotWritable(qname, WhyNot(code, detail, unlockedBy))
}
