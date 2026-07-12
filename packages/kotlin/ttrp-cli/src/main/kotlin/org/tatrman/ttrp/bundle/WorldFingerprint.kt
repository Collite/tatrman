// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import org.tatrman.ttr.metadata.world.ResolvedEngine
import org.tatrman.ttr.metadata.world.ResolvedExecutor
import org.tatrman.ttr.metadata.world.ResolvedStorage
import org.tatrman.ttr.metadata.world.ResolvedWorld
import org.tatrman.ttr.parser.model.PropertyValue
import java.security.MessageDigest

/**
 * The semantic world fingerprint (F-f-ii β) — a comment/formatting-immune hash of the *resolved*
 * world model. Two worlds are fingerprint-equal iff they mean the same. Canonicalization (the
 * mini-spec of record until promoted into contracts):
 *
 *  1. Input = the resolved world (post `extends`-overlay), never source text.
 *  2. Canonical document: `{qname, engines[], executors[], storages[]}`, each list sorted by name.
 *  3. Each entry carries `name`, `type`, `version`, and its capability/manifest content as sorted
 *     key→value pairs; storages add `staging` (bool), `via`, sorted `hosts`, and sorted schemas.
 *     Credentials never appear (the world doc is secret-free — connections are named, creds via env).
 *  4. Excluded: trivia/comments, source locations, doc order, formatting.
 *  5. Compact separators, keys sorted at every level, UTF-8, `sha256`, rendered `sha256:<hex>`.
 */
object WorldFingerprint {
    fun of(world: ResolvedWorld): String {
        val doc =
            obj(
                "qname" to str(qnameOf(world)),
                "engines" to arr(world.engines.sortedBy { it.qname.name }.map { engine(it) }),
                "executors" to arr(world.executors.sortedBy { it.qname.name }.map { executor(it) }),
                "storages" to arr(world.storages.sortedBy { it.qname.name }.map { storage(it) }),
            )
        val digest = MessageDigest.getInstance("SHA-256").digest(doc.toByteArray(Charsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }

    private fun engine(e: ResolvedEngine): String =
        obj(
            "name" to str(e.qname.name),
            "type" to str(e.type ?: ""),
            "version" to str(e.version ?: ""),
            "manifest" to manifest(e.manifest),
        )

    private fun executor(e: ResolvedExecutor): String =
        obj(
            "name" to str(e.qname.name),
            "type" to str(e.type ?: ""),
            "version" to str(e.version ?: ""),
            "manifest" to manifest(e.manifest),
        )

    private fun storage(s: ResolvedStorage): String =
        obj(
            "name" to str(s.qname.name),
            "type" to str(s.type ?: ""),
            "staging" to s.staging.toString(),
            "via" to str(s.via ?: ""),
            "hosts" to arr(s.hosts.sorted().map { str(it) }),
            "schemas" to
                arr(
                    s.schemas.sortedBy { it.qname.name }.map { sch ->
                        obj(
                            "name" to str(sch.qname.name),
                            "fields" to
                                obj(
                                    *sch.fields.entries
                                        .sortedBy { it.key }
                                        .map {
                                            it.key to
                                                str(
                                                    it.value,
                                                )
                                        }.toTypedArray(),
                                ),
                        )
                    },
                ),
            "manifest" to manifest(s.manifest),
        )

    private fun manifest(m: Map<String, PropertyValue>): String =
        obj(
            *m.entries
                .sortedBy { it.key }
                .map { it.key to render(it.value) }
                .toTypedArray(),
        )

    /** Source-immune canonical rendering of a [PropertyValue]. */
    private fun render(v: PropertyValue): String =
        when (v) {
            is PropertyValue.StringValue -> str(v.raw)
            is PropertyValue.TripleStringValue -> str(v.raw)
            is PropertyValue.NumberValue -> number(v.raw)
            is PropertyValue.BoolValue -> v.raw.toString()
            is PropertyValue.NullValue -> "null"
            is PropertyValue.IdValue -> str(v.parts.joinToString("."))
            is PropertyValue.ListValue -> arr(v.items.map { render(it) })
            is PropertyValue.ObjectValue ->
                obj(
                    *v.entries.entries
                        .sortedBy { it.key }
                        .map { it.key to render(it.value) }
                        .toTypedArray(),
                )
            is PropertyValue.FunctionCall -> "${v.name}(${v.args.joinToString(",") { render(it) }})"
            is org.tatrman.ttr.parser.model.TaggedBlockValue -> str(v.value)
            else -> str(v.toString())
        }

    private fun number(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun str(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun arr(items: List<String>): String = items.joinToString(",", "[", "]")

    private fun obj(vararg pairs: Pair<String, String>): String =
        pairs.sortedBy { it.first }.joinToString(",", "{", "}") { "${str(it.first)}:${it.second}" }

    private fun qnameOf(world: ResolvedWorld): String =
        world.qname.let { q ->
            val prefix = q.`package`.ifBlank { q.namespace }
            if (prefix.isBlank()) q.name else "$prefix.${q.name}"
        }
}
