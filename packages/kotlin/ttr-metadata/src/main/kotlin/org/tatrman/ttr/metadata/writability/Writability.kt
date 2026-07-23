// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.writability

/*
 * EN-P1.2 — the writability classifier output (FO contracts §7 / demand ttrm-md-declarations.md §3).
 * The typed result the classifier produces per model version; the served JSON payload
 * (WritabilityPayload.toJson) is byte-compatible with the platform's writability.schema.json (the
 * FO-P2 wire truth). whyNot is a product surface (rendered in Data Entry), not just a diagnostic.
 *
 * Rung-v1 detection this wave: a table-backed identity binding ⇒ writable. The verdict carries `rung`
 * so rung-v2 (filter-only predicate inversion) slots in without a payload change (FO-32).
 */

/** Why an entity is not writable (§7 `whyNot.code`). Stable strings — a product surface. */
enum class WhyNotCode {
    AGGREGATION,
    NON_KEY_PRESERVED_JOIN,
    COMPUTED_COLUMN,
    NO_DECLARED_WRITEBACK,
}

/** The §7 `whyNot` object: a stable [code], a human-renderable [detail], and the rung that unlocks it. */
data class WhyNot(
    val code: WhyNotCode,
    val detail: String,
    val unlockedBy: String? = null,
)

/** The §7 `lowering` for a writable entity: the base table + the attribute→column binding (identity v1). */
data class Lowering(
    val baseTable: String,
    val binding: Map<String, String> = emptyMap(),
)

/** One entity's §7 verdict — the `oneOf` of the schema: writable-with-lowering, or not-with-whyNot. */
sealed interface EntityWritability {
    val qname: String

    data class Writable(
        override val qname: String,
        val rung: String,
        val lowering: Lowering,
    ) : EntityWritability

    data class NotWritable(
        override val qname: String,
        val whyNot: WhyNot,
    ) : EntityWritability
}

/**
 * The §7 payload: the model-version fingerprint + one verdict per entity. `modelVersion` must equal
 * the fingerprint md-metadata serving stamps (one fingerprint, two payloads). Entity order is pinned
 * (by qname) so the serialized payload is byte-deterministic across runs (EN-P1.2 T6).
 */
data class WritabilityPayload(
    val modelVersion: String,
    val entities: List<EntityWritability>,
) {
    /** Serialize to the §7 JSON (byte-compatible with `writability.schema.json`). Dep-free + stable. */
    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{\"modelVersion\":").append(str(modelVersion)).append(",\"entities\":[")
        entities.forEachIndexed { i, e ->
            if (i > 0) sb.append(',')
            when (e) {
                is EntityWritability.Writable -> {
                    sb.append("{\"qname\":").append(str(e.qname))
                    sb.append(",\"writable\":true,\"rung\":").append(str(e.rung))
                    sb.append(",\"lowering\":{\"baseTable\":").append(str(e.lowering.baseTable))
                    if (e.lowering.binding.isNotEmpty()) {
                        sb.append(",\"binding\":{")
                        e.lowering.binding.entries.forEachIndexed { j, (k, v) ->
                            if (j > 0) sb.append(',')
                            sb.append(str(k)).append(':').append(str(v))
                        }
                        sb.append('}')
                    }
                    sb.append("}}")
                }
                is EntityWritability.NotWritable -> {
                    sb.append("{\"qname\":").append(str(e.qname))
                    sb.append(",\"writable\":false,\"whyNot\":{\"code\":").append(str(e.whyNot.code.name))
                    sb.append(",\"detail\":").append(str(e.whyNot.detail))
                    val unlocked = e.whyNot.unlockedBy
                    sb.append(",\"unlockedBy\":").append(if (unlocked == null) "null" else str(unlocked))
                    sb.append("}}")
                }
            }
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun str(s: String): String {
        val b = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> b.append("\\\"")
                '\\' -> b.append("\\\\")
                '\n' -> b.append("\\n")
                '\r' -> b.append("\\r")
                '\t' -> b.append("\\t")
                else -> if (c < ' ') b.append("\\u%04x".format(c.code)) else b.append(c)
            }
        }
        return b.append('"').toString()
    }
}
