// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.members

import org.tatrman.ttr.md.resolve.MemberIndex
import org.tatrman.ttr.md.resolve.MemberSnapshot
import org.tatrman.ttr.md.resolve.QualifiedName
import java.time.Instant

/**
 * An immutable, in-memory [MemberSnapshot] (contracts §7.1): one per compile pass. Holds an
 * [InternedMemberIndex] per **published** domain; [domains] returns exactly those keys and
 * [members] returns `null` for any unpublished domain. [fingerprint] is the content hash
 * ([MemberFingerprint], asof-independent) and [asof] is the carried instant recorded in the manifest.
 *
 * Interning is shared across every domain's index, so a member value common to two domains is one
 * object in memory (architecture §7 large-dimension mitigation).
 */
class MaterializedMemberSnapshot private constructor(
    override val asof: Instant,
    override val fingerprint: String,
    private val byDomain: Map<QualifiedName, InternedMemberIndex>,
) : MemberSnapshot {
    override fun domains(): Set<QualifiedName> = byDomain.keys

    override fun members(domain: QualifiedName): MemberIndex? = byDomain[domain]

    companion object {
        /** Build a snapshot from `published-domain → members`, taken at [asof]. */
        fun of(
            byDomain: Map<QualifiedName, List<String>>,
            asof: Instant,
        ): MaterializedMemberSnapshot {
            val interner = MemberInterner()
            val indexes =
                byDomain.entries
                    .sortedBy { it.key }
                    .associate { (domain, members) -> domain to InternedMemberIndex.of(members, interner) }
            return MaterializedMemberSnapshot(asof, MemberFingerprint.of(byDomain), indexes)
        }
    }
}
