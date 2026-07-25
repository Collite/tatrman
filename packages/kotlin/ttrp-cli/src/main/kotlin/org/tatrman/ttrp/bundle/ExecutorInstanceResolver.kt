// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import org.tatrman.ttr.parser.model.PropertyValue
import org.tatrman.ttr.parser.model.TaggedBlockValue
import org.tatrman.ttrp.graph.capability.BoundWorld

/**
 * PL-P5.S4 — resolves the world's executor **instance** entry for an emit target and renders it to TTR-M text for
 * [org.tatrman.ttrp.emit.spi.EmitRequest.executorInstance] (contracts §7/§8). The instance-⊕-type overlay is
 * already applied by ttr-metadata ([org.tatrman.ttr.metadata.world.ResolvedExecutor.manifest]); this picks the
 * entry whose type matches the plugin's `targetId` and serialises its properties as `def executor <name> { … }`.
 *
 * Emit plugins that need world-declared bindings (airflow3: `delegation` / `doorConnection`, E-3-γ) parse this
 * text; bash/kestra ignore it. A target with no declared executor instance (the standalone default) resolves to
 * the empty string — so a standalone airflow3 compile still emits its native binding.
 *
 * Keys are rendered **sorted** (deterministic — the emit result is byte-stable, H-6). Values are rendered in the
 * canonical `.ttrm` scalar/list style the shipped type manifests use.
 */
object ExecutorInstanceResolver {
    /** The executor-instance TTR-M text for [targetId] in [bound], or "" when the world declares no such executor. */
    fun resolve(
        bound: BoundWorld,
        targetId: String,
    ): String {
        val match =
            bound.executors.values.firstOrNull { typeOf(it.executor.type, it.executor.manifest) == targetId }
                ?: return ""
        return render(match.executor.qname.name, match.executor.manifest)
    }

    /** The executor's resolved type: its own `type:`, else the `type` key carried in from the overlaid manifest. */
    private fun typeOf(
        declaredType: String?,
        manifest: Map<String, PropertyValue>,
    ): String? = declaredType ?: (manifest["type"]?.let { scalar(it) })

    /** `def executor <name> { … }` — one sorted `key: value` line per manifest entry. */
    fun render(
        name: String,
        manifest: Map<String, PropertyValue>,
    ): String =
        buildString {
            append("def executor ").append(name).append(" {\n")
            manifest.toSortedMap().forEach { (key, value) ->
                append("    ")
                    .append(key)
                    .append(": ")
                    .append(renderValue(value))
                    .append('\n')
            }
            append("}\n")
        }

    private fun renderValue(value: PropertyValue): String =
        when (value) {
            is PropertyValue.StringValue -> quote(value.raw)
            is PropertyValue.TripleStringValue -> quote(value.raw)
            is PropertyValue.NumberValue -> renderNumber(value.raw)
            is PropertyValue.BoolValue -> value.raw.toString()
            is PropertyValue.NullValue -> "null"
            is PropertyValue.IdValue -> value.parts.joinToString(".")
            is PropertyValue.ListValue -> value.items.joinToString(", ", "[", "]") { renderValue(it) }
            is PropertyValue.ObjectValue ->
                value.entries.toSortedMap().entries.joinToString(", ", "{ ", " }") { (k, v) ->
                    "$k: ${renderValue(v)}"
                }
            is PropertyValue.FunctionCall ->
                value.args.joinToString(", ", "${value.name}(", ")") { renderValue(it) }
            // Embedded language blocks don't occur in an executor-instance entry (scalars/lists/refs only); render
            // the block body as a string so the text stays valid TTR-M rather than dropping data.
            is TaggedBlockValue -> quote(value.value)
        }

    /** The bare scalar text of a value (for `type` matching): unquoted string / dotted id / number / bool. */
    private fun scalar(value: PropertyValue): String? =
        when (value) {
            is PropertyValue.StringValue -> value.raw
            is PropertyValue.TripleStringValue -> value.raw
            is PropertyValue.IdValue -> value.parts.joinToString(".")
            is PropertyValue.NumberValue -> renderNumber(value.raw)
            is PropertyValue.BoolValue -> value.raw.toString()
            is PropertyValue.ListValue, is PropertyValue.ObjectValue, is PropertyValue.FunctionCall,
            is PropertyValue.NullValue, is TaggedBlockValue,
            -> null
        }

    private fun renderNumber(n: Double): String =
        if (n ==
            n.toLong().toDouble()
        ) {
            n.toLong().toString()
        } else {
            n.toString()
        }

    private fun quote(raw: String): String =
        buildString {
            append('"')
            raw.forEach { c ->
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
            append('"')
        }
}
