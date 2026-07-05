package org.tatrman.ttr.metadata.world

import org.tatrman.ttr.parser.model.PropertyValue
import java.security.MessageDigest

/**
 * Semantic world fingerprint (contracts §5, F-f-ii). One implementation shared by
 * the compiler and the conformance harness. Canonicalization: the resolved
 * (post-overlay) world → canonical JSON (keys sorted at every level, qname-sorted
 * arrays, defaults elided, source locations & doc-comments excluded) → sha256,
 * spelled `sha256:<hex>`. The world qname travels in clear beside the hash (the
 * TTR-P bundle's `{qname, fingerprint}` pair) — it is NOT part of the hashed form.
 *
 * Hand-rolled deterministic JSON (RFC 8785 / JCS in spirit) so no serialization
 * dependency rides the core artifact (MD3 / architecture §2.1). World property
 * values are strings/ints/bools/lists/objects only.
 */
object WorldFingerprint {
    fun of(world: ResolvedWorld): String {
        val bytes = canonicalForm(world).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }

    /** The canonical JSON string that gets hashed (exposed for the conformance harness). */
    fun canonicalForm(world: ResolvedWorld): String {
        val root =
            sortedMapOf<String, Any?>(
                "engines" to
                    world.engines
                        .sortedBy {
                            it.qname.dotted()
                        }.map { enginePart(it.qname.name, it.type, it.version, it.extendsRef, it.manifest) },
                "executors" to
                    world.executors
                        .sortedBy {
                            it.qname.dotted()
                        }.map { enginePart(it.qname.name, it.type, it.version, it.extendsRef, it.manifest) },
                "storages" to world.storages.sortedBy { it.qname.dotted() }.map { storage(it) },
            ).apply {
                world.staging?.let { this["staging"] = it.qname.name }
            }
        val sb = StringBuilder()
        emit(pruneEmpty(root), sb)
        return sb.toString()
    }

    private fun enginePart(
        name: String,
        type: String?,
        version: String?,
        extendsRef: String?,
        manifest: Map<String, PropertyValue>,
    ): Map<String, Any?> =
        sortedMapOf(
            "name" to name,
            "type" to type,
            "version" to version,
            "extends" to extendsRef,
            "manifest" to canonManifest(manifest),
        )

    private fun storage(s: ResolvedStorage): Map<String, Any?> =
        sortedMapOf(
            "name" to s.qname.name,
            "type" to s.type,
            "via" to s.via,
            "hosts" to s.hosts.sorted(),
            "staging" to if (s.staging) true else null,
            "extends" to s.extendsRef,
            "schemas" to
                s.schemas.sortedBy { it.qname.dotted() }.map {
                    sortedMapOf<String, Any?>("name" to it.qname.name, "fields" to it.fields.toSortedMap())
                },
            "manifest" to canonManifest(s.manifest),
        )

    private fun canonManifest(m: Map<String, PropertyValue>): Map<String, Any?> =
        m.entries.associate { it.key to canonValue(it.value) }.toSortedMap()

    private fun canonValue(v: PropertyValue): Any? =
        when (v) {
            is PropertyValue.StringValue -> v.raw
            is PropertyValue.TripleStringValue -> v.raw
            is PropertyValue.NumberValue -> v.raw
            is PropertyValue.BoolValue -> v.raw
            is PropertyValue.NullValue -> null
            is PropertyValue.IdValue -> v.ref.path
            is PropertyValue.ListValue -> v.items.map { canonValue(it) }
            is PropertyValue.ObjectValue -> v.entries.mapValues { canonValue(it.value) }.toSortedMap()
            is PropertyValue.FunctionCall ->
                mapOf(
                    "fn" to v.name,
                    "args" to v.args.map { canonValue(it) },
                ).toSortedMap()
            else -> v.toString()
        }

    /** Elide null values and empty collections/maps recursively (defaults-elided rule). */
    @Suppress("UNCHECKED_CAST")
    private fun pruneEmpty(value: Any?): Any? =
        when (value) {
            null -> null
            is Map<*, *> -> {
                val out = sortedMapOf<String, Any?>()
                for ((k, raw) in value) {
                    val pruned = pruneEmpty(raw)
                    if (pruned != null &&
                        !(pruned is Collection<*> && pruned.isEmpty()) &&
                        !(pruned is Map<*, *> && pruned.isEmpty())
                    ) {
                        out[k as String] = pruned
                    }
                }
                out
            }
            is List<*> -> value.map { pruneEmpty(it) }
            else -> value
        }

    private fun emit(
        value: Any?,
        sb: StringBuilder,
    ) {
        when (value) {
            null -> sb.append("null")
            is String -> sb.append(quote(value))
            is Boolean -> sb.append(value.toString())
            is Int, is Long -> sb.append(value.toString())
            is Double ->
                sb.append(
                    if (value ==
                        value.toLong().toDouble()
                    ) {
                        value.toLong().toString()
                    } else {
                        value.toString()
                    },
                )
            is Map<*, *> -> {
                sb.append('{')
                value.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) sb.append(',')
                    sb.append(quote(k.toString())).append(':')
                    emit(v, sb)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) sb.append(',')
                    emit(v, sb)
                }
                sb.append(']')
            }
            else -> sb.append(quote(value.toString()))
        }
    }

    private fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2).append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
