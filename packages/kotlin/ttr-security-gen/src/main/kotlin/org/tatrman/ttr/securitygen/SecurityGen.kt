// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.securitygen

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.tatrman.ttr.parser.model.SecurityBlock
import org.tatrman.ttr.parser.model.SecurityStatement

/** The `# GENERATED — do not edit` marker every emitted file carries (S3.T3). */
const val GENERATED_MARKER: String = "GENERATED — do not edit"

/**
 * The output of a generation pass: one Rego fragment per object with allow rules,
 * plus one aggregated `data.json`. All content is deterministic — same blocks ⇒
 * same bytes (S3.T3), no timestamps, everything sorted.
 */
data class GeneratedPolicy(
    /** `<sanitized>.rego` → file content, sorted by filename. */
    val regoFiles: Map<String, String>,
    /** the single `data.json` content. */
    val dataJson: String,
) {
    /** Every file to write (rego fragments + `data.json`), filename → content, sorted. */
    fun files(): Map<String, String> = (regoFiles + ("data.json" to dataJson)).toSortedMap()
}

/**
 * PL-P4.S3 (H-1) — the one-way, deterministic `security { }` → Rego generator.
 *
 * Invariants (contracts §11):
 *  - **grants only grant**: every emitted rule is `allow`; no generated fragment
 *    can ever contain a deny — deny-overrides composition is Perun's job (§19).
 *  - **classifications land as data, roles verbatim** (HQ-1): `classify`/`mask`/
 *    `own` metadata goes to `data.json`; grant/owner role tokens are copied
 *    verbatim into the Rego (classification→role mapping is org policy data,
 *    resolved at Perun's build — never baked into a fragment).
 *  - **fingerprint-neutral, one-way**: reads blocks, writes policy; never touches
 *    the model or its fingerprint.
 */
object SecurityGen {
    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    /** Per-object accumulator (keyed by sanitized token). */
    private class Obj(
        val sanitized: String,
    ) {
        val objectRefs = sortedSetOf<String>() // raw refs that mapped here (collision guard)
        var owner: String? = null
        val grants = sortedSetOf<Pair<String, String>>(compareBy({ it.first }, { it.second })) // privilege, grantee
        var classification: String? = null
        var masked = false
    }

    fun generate(blocks: List<SecurityBlock>): GeneratedPolicy {
        val byToken = linkedMapOf<String, Obj>()

        fun objFor(ref: String): Obj {
            val token = sanitizeQname(ref)
            val o = byToken.getOrPut(token) { Obj(token) }
            o.objectRefs += ref
            // Fail closed on a mangle collision (two distinct refs → one token): a
            // silent merge would cross access boundaries. Mirrors IQ-2 (§12).
            check(o.objectRefs.size == 1) {
                "security-gen: qname collision — refs ${o.objectRefs} both sanitize to '$token'; rename one in the model"
            }
            return o
        }

        for (block in blocks) {
            for (stmt in block.statements) {
                when (stmt) {
                    is SecurityStatement.Own -> objFor(stmt.objectRef).owner = stmt.owner
                    is SecurityStatement.Classify -> objFor(stmt.objectRef).classification = stmt.classification
                    is SecurityStatement.Grant -> objFor(stmt.objectRef).grants += (stmt.privilege to stmt.grantee)
                    is SecurityStatement.Mask -> objFor(stmt.objectRef).masked = true
                }
            }
        }

        val rego = sortedMapOf<String, String>()
        for (o in byToken.values.sortedBy { it.sanitized }) {
            renderRego(o)?.let { rego["${o.sanitized}.rego"] = it }
        }
        return GeneratedPolicy(regoFiles = rego, dataJson = renderData(byToken.values.sortedBy { it.sanitized }))
    }

    /** A fragment is emitted only when the object has ALLOW rules (owner or grants). */
    private fun renderRego(o: Obj): String? {
        val rules = mutableListOf<String>()
        o.owner?.let { owner ->
            rules +=
                "# own ${o.objectRefs.first()}: $owner  (owner has full access)\nallow if input.role == ${quote(owner)}"
        }
        for ((privilege, grantee) in o.grants) {
            rules +=
                "# grant $privilege on ${o.objectRefs.first()} to $grantee\n" +
                "allow if {\n\tinput.action == ${quote(privilege)}\n\tinput.role == ${quote(grantee)}\n}"
        }
        if (rules.isEmpty()) return null
        return buildString {
            append("# $GENERATED_MARKER\n")
            append("#\n")
            append("# Object: ${o.objectRefs.first()}\n")
            append("# Source: TTR-M `security { }` blocks (PL-P4.S3, H-1). Regenerate with `ttr security-gen`.\n")
            append("# Grants only ALLOW — deny-overrides composition is applied by Perun at bundle build (§19).\n")
            append("package tatrman.generated.${o.sanitized}\n\n")
            append("import rego.v1\n\n")
            append(rules.joinToString("\n\n"))
            append("\n")
        }
    }

    /** The aggregated `data.json`: classifications, masks and owners, keyed by sanitized token. */
    private fun renderData(objects: List<Obj>): String {
        val generated =
            buildJsonObject {
                for (o in objects) {
                    val fields = sortedMapOf<String, JsonPrimitive>()
                    fields["objectRef"] = JsonPrimitive(o.objectRefs.first())
                    o.owner?.let { fields["owner"] = JsonPrimitive(it) }
                    o.classification?.let { fields["classification"] = JsonPrimitive(it) }
                    if (o.masked) fields["masked"] = JsonPrimitive(true)
                    put(o.sanitized, JsonObject(fields))
                }
            }
        val root =
            buildJsonObject {
                // JsonObject preserves insertion order; keys inserted in sorted order.
                put("_generated", GENERATED_MARKER)
                put("tatrman", buildJsonObject { put("generated", generated) })
            }
        return json.encodeToString(JsonObject.serializer(), root) + "\n"
    }

    private fun quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
