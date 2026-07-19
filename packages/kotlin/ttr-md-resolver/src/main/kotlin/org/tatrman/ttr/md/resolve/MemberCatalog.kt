// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.resolve

import java.time.Instant

/**
 * The member-catalog contract (§7.1). Backings: server-connected (S6) or serverless/null. A null
 * catalog (or a null snapshot) means **disconnected mode** — R13 rules apply. The library
 * implementation and the `ttrm/…` wire protocol land in S6 (ttr-metadata); S2 ships only the
 * interfaces and an in-memory test fixture (`InMemoryMemberSnapshot`).
 */
interface MemberCatalog {
    /** One immutable snapshot per compile pass, taken at [asof]. */
    fun snapshot(asof: Instant): MemberSnapshot
}

/** An immutable view of the published member content at one instant. */
interface MemberSnapshot {
    /** Content fingerprint `"sha256:<hex>"` — recorded in the bundle manifest for reproducibility. */
    val fingerprint: String

    val asof: Instant

    /** Published domains only (a domain opts in via `publish: members`, §1.4). */
    fun domains(): Set<QualifiedName>

    /** The member index for a published [domain], or null if the domain is not published. */
    fun members(domain: QualifiedName): MemberIndex?
}

/** A paged, interned index of one domain's members (architecture §7). */
interface MemberIndex {
    fun contains(text: String): Boolean

    /** Up to [limit] members with the given [prefix], for completion/paging. */
    fun lookup(
        prefix: String,
        limit: Int,
    ): List<String>

    val count: Long
}
