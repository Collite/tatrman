// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve.fixtures

import org.tatrman.ttr.md.resolve.MemberIndex
import org.tatrman.ttr.md.resolve.MemberSnapshot
import java.security.MessageDigest
import java.time.Instant

/**
 * An in-memory [MemberSnapshot] for the resolver test/golden suites (S2-A2). Each domain maps to a
 * sorted, de-duplicated member list; the [fingerprint] is a sha256 over the content so goldens that
 * pin a fingerprint stay stable. The real server-backed catalog is S6.
 *
 * Note: which domains this exposes is a fixture choice, not the model's `publish: members` flag —
 * the tests seed numeric/temporal domains (years, months) alongside the published `Name` so INT
 * member candidacy (R5f) can be exercised.
 */
class InMemoryMemberSnapshot(
    private val byDomain: Map<String, List<String>>,
    override val asof: Instant = Instant.EPOCH,
) : MemberSnapshot {
    private val sorted: Map<String, List<String>> =
        byDomain.mapValues { (_, v) -> v.distinct().sorted() }

    override val fingerprint: String = "sha256:" + sha256(sorted)

    override fun domains(): Set<String> = sorted.keys

    override fun members(domain: String): MemberIndex? = sorted[domain]?.let { InMemoryMemberIndex(it) }

    companion object {
        private fun sha256(content: Map<String, List<String>>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            for ((domain, members) in content.toSortedMap()) {
                digest.update(domain.toByteArray())
                digest.update(0)
                for (m in members) {
                    digest.update(m.toByteArray())
                    digest.update(0)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

private class InMemoryMemberIndex(
    private val members: List<String>,
) : MemberIndex {
    private val set = members.toHashSet()

    override fun contains(text: String): Boolean = text in set

    override fun lookup(
        prefix: String,
        limit: Int,
    ): List<String> = members.filter { it.startsWith(prefix) }.take(limit)

    override val count: Long = members.size.toLong()
}
