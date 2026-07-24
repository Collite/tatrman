// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.members

import java.security.MessageDigest

/**
 * Content fingerprint of a member snapshot (contracts §7.1): `"sha256:<hex>"` over a canonical JSON
 * of `published-domain → sorted, de-duplicated members`. Mirrors
 * [org.tatrman.ttr.metadata.world.WorldFingerprint]'s hand-rolled deterministic JSON so no
 * serialization dependency rides the light metadata core (MD3 / architecture §2.1), and is spelled
 * the same `sha256:<hex>` beside the world fingerprint in the bundle manifest.
 *
 * Content-only by design: the `asof` instant is carried on the snapshot but **not** hashed, so two
 * compile passes at different clocks over identical members agree (decision 13 records `asof` and
 * `fingerprint` as separate manifest facts). Published domains with no members are kept (rendered as
 * `[]`) so the published-domain *set* — not just member content — is part of the fingerprint.
 */
object MemberFingerprint {
    fun of(byDomain: Map<String, List<String>>): String {
        val bytes = canonicalForm(byDomain).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }

    /** The canonical JSON string that gets hashed (exposed to mirror WorldFingerprint's harness seam). */
    fun canonicalForm(byDomain: Map<String, List<String>>): String {
        // Domain keys sorted; each domain's members sorted + de-duplicated. A LinkedHashMap keeps the
        // sorted key order for [emit]. Members are DATA — no case folding (case-sensitive members).
        val root =
            byDomain.entries
                .sortedBy { it.key }
                .associate { (domain, members) -> domain to members.distinct().sorted() }
        val sb = StringBuilder()
        emit(root, sb)
        return sb.toString()
    }

    private fun emit(
        value: Any?,
        sb: StringBuilder,
    ) {
        when (value) {
            null -> sb.append("null")
            is String -> sb.append(quote(value))
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
