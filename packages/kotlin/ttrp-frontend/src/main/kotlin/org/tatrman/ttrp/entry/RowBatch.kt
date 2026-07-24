// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import org.yaml.snakeyaml.Yaml

/**
 * A **concrete** §5 row-batch instance, parsed for shape-checking against a target table's md shape
 * (FO contracts §5 / demand `ttr-p-row-entry.md` §1). Distinct from [RowBatchInputKind], which is the
 * field-*roster* metamodel; this is one materialized batch the checker (and, at runtime, the door)
 * validates. Values keep a coarse scalar tag ([BatchScalar.Kind]) so `TTRP-EN-001` can flag an
 * md-type-incompatible value without a full value coercion pass (that is the door's runtime job).
 *
 * The wire form is JSON (the FO §5 DTO); YAML is a JSON superset, so we reuse the same snakeyaml
 * loader the validity catalogue uses — no new dependency, and fixtures may be authored in either.
 */
data class RowBatch(
    val batchId: String?,
    val kind: String?,
    val target: BatchTarget,
    val modelVersion: String?,
    val proposals: List<Proposal>,
    val source: BatchSource?,
) {
    /** §5 `target` is a `oneOf`: a base table, or a table-backed entity lowered v1 (FO-32). */
    sealed interface BatchTarget {
        data class Table(
            val qname: String,
        ) : BatchTarget

        data class Entity(
            val qname: String,
            val lowering: String?,
        ) : BatchTarget
    }

    data class Proposal(
        val op: String?,
        val key: Map<String, BatchScalar>?,
        val values: Map<String, BatchScalar>,
        val baseRowVersion: String?,
        val effectiveDate: String?,
        /** 1-based row index within the batch, for messages that name the offending row. */
        val row: Int,
    )

    data class BatchSource(
        val type: String?,
        val ref: String?,
        val pluginId: String?,
        val pluginVersion: String?,
    )

    companion object {
        /** Parse a §5 batch document (JSON or YAML). Structural only — semantics are the checker's job. */
        fun parse(text: String): RowBatch {
            @Suppress("UNCHECKED_CAST")
            val root = Yaml().load<Any?>(text) as? Map<String, Any?> ?: emptyMap()
            return fromMap(root)
        }

        @Suppress("UNCHECKED_CAST")
        private fun fromMap(root: Map<String, Any?>): RowBatch {
            val targetMap = root["target"] as? Map<String, Any?> ?: emptyMap()
            val target =
                when {
                    targetMap.containsKey("table") ->
                        BatchTarget.Table(targetMap["table"]?.toString().orEmpty())
                    else ->
                        BatchTarget.Entity(
                            qname = targetMap["entity"]?.toString().orEmpty(),
                            lowering = targetMap["lowering"]?.toString(),
                        )
                }
            val proposals =
                (root["proposals"] as? List<Any?>).orEmpty().mapIndexed { i, raw ->
                    val p = raw as? Map<String, Any?> ?: emptyMap()
                    Proposal(
                        op = p["op"]?.toString(),
                        key = scalarMapOrNull(p["key"]),
                        values = scalarMapOrNull(p["values"]) ?: emptyMap(),
                        baseRowVersion = p["baseRowVersion"]?.toString(),
                        effectiveDate = p["effectiveDate"]?.toString(),
                        row = i + 1,
                    )
                }
            val sourceMap = root["source"] as? Map<String, Any?>
            return RowBatch(
                batchId = root["batchId"]?.toString(),
                kind = root["kind"]?.toString(),
                target = target,
                modelVersion = root["modelVersion"]?.toString(),
                proposals = proposals,
                source =
                    sourceMap?.let {
                        BatchSource(
                            type = it["type"]?.toString(),
                            ref = it["ref"]?.toString(),
                            pluginId = it["pluginId"]?.toString(),
                            pluginVersion = it["pluginVersion"]?.toString(),
                        )
                    },
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun scalarMapOrNull(raw: Any?): Map<String, BatchScalar>? {
            val m = raw as? Map<String, Any?> ?: return null
            return m.mapValues { (_, v) -> BatchScalar.of(v) }
        }
    }
}

/** A coarse-typed batch value — enough to flag an md-type-incompatible value (`TTRP-EN-001`). */
data class BatchScalar(
    val kind: Kind,
    val raw: Any?,
) {
    enum class Kind { TEXT, NUMBER, BOOL, NULL }

    companion object {
        fun of(v: Any?): BatchScalar =
            when (v) {
                null -> BatchScalar(Kind.NULL, null)
                is Boolean -> BatchScalar(Kind.BOOL, v)
                is Number -> BatchScalar(Kind.NUMBER, v)
                else -> BatchScalar(Kind.TEXT, v.toString())
            }
    }
}
