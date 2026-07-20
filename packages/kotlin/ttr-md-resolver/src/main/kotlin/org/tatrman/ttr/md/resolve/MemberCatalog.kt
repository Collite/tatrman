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
    /**
     * One immutable snapshot per compile pass, taken at [asof]. The GI-19 degradation ladder
     * (§7.1) surfaces here: a catalog unreachable **at pass start** with no held snapshot throws
     * [CatalogUnavailable] (the frontend maps it to a hard error); a catalog lost **mid-session**
     * serves the held snapshot instead and invokes [onStale] with the signal (the frontend maps it to
     * `TTRP-MD-013`). [onStale] is per-call, so it feeds exactly the pass that hit the loss.
     */
    fun snapshot(
        asof: Instant,
        onStale: (StaleSnapshot) -> Unit = {},
    ): MemberSnapshot
}

/** A connected compile needs the member catalog, but it is unreachable at pass start (GI-19 hard error). */
class CatalogUnavailable(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Signal that the member source was lost mid-session and a held snapshot keeps serving (GI-19). The
 * frontend maps it to `TTRP-MD-013` (held snapshot in use, stale). [heldFingerprint] / [heldAsof]
 * identify the snapshot still in use; [requestedAsof] is the pass that hit the loss; [reason] carries
 * the source's failure text. Lives here (the interface home) so the frontend maps it without a
 * dependency on the `ttr-metadata` implementation.
 */
data class StaleSnapshot(
    val heldFingerprint: String,
    val heldAsof: Instant,
    val requestedAsof: Instant,
    val reason: String,
)

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
