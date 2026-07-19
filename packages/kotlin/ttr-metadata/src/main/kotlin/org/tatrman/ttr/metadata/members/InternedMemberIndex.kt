// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.members

import org.tatrman.ttr.md.resolve.MemberIndex

/**
 * A [MemberIndex] over an interned, sorted [Array] (architecture §7 "paged + interned"). Members are
 * stored once (via [MemberInterner]), sorted by natural order; [contains] is a binary search and
 * [lookup] a lower-bound scan — both O(log n) to locate. Case-sensitive throughout: members are DATA
 * (D-note), never case-folded, so `contains` is exact.
 */
class InternedMemberIndex private constructor(
    private val sorted: Array<String>,
) : MemberIndex {
    override fun contains(text: String): Boolean = sorted.binarySearch(text) >= 0

    override fun lookup(
        prefix: String,
        limit: Int,
    ): List<String> {
        if (limit <= 0) return emptyList()
        var i = lowerBound(prefix)
        val out = ArrayList<String>(minOf(limit, 16))
        while (i < sorted.size && out.size < limit && sorted[i].startsWith(prefix)) {
            out += sorted[i]
            i++
        }
        return out
    }

    override val count: Long get() = sorted.size.toLong()

    /** First index whose member is >= [key] (natural order — matches [Array.binarySearch]). */
    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        /** Build from raw [members]; de-duplicated, interned through [interner], sorted natural order. */
        fun of(
            members: Iterable<String>,
            interner: MemberInterner = MemberInterner(),
        ): InternedMemberIndex {
            val sorted =
                members
                    .asSequence()
                    .map { interner.intern(it) }
                    .distinct()
                    .sorted()
                    .toList()
                    .toTypedArray()
            return InternedMemberIndex(sorted)
        }
    }
}
