// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.members

import org.tatrman.ttr.md.resolve.MemberCatalog
import org.tatrman.ttr.md.resolve.MemberSnapshot
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * A [MemberCatalog] over a [MemberSource] with the GI-19 degradation ladder (contracts §7.1):
 *  - **source unreachable at pass start** (no snapshot held yet) ⇒ throw [CatalogUnavailable]; the
 *    frontend maps it to a hard error — a connected compile that requires a catalog cannot proceed;
 *  - **source lost mid-session** (a snapshot was held) ⇒ keep serving the held snapshot and emit a
 *    [StaleSnapshot] signal, which the frontend maps to its catalog-lost diagnostic (MD-013 — the
 *    library stays diagnostic-id-free per MD5; ids are the frontend's job).
 *
 * Follows the metadata registry/refresher idiom: an [AtomicReference] holds the last-good snapshot
 * for lock-free reads; `(StaleSnapshot) -> Unit` listeners are notified on degradation exactly as
 * `MetadataRegistry` notifies its listeners, swallowing per-listener failures (`runCatching`).
 * A disconnected compile does not use this class at all — it passes a `null` catalog (R13).
 */
class DegradingMemberCatalog(
    private val source: MemberSource,
) : MemberCatalog {
    private val held = AtomicReference<MemberSnapshot?>(null)
    private val staleListeners = CopyOnWriteArrayList<(StaleSnapshot) -> Unit>()

    /** The last snapshot successfully taken, or `null` before the first successful pass. */
    val heldSnapshot: MemberSnapshot? get() = held.get()

    fun addStaleListener(listener: (StaleSnapshot) -> Unit) {
        staleListeners += listener
    }

    override fun snapshot(asof: Instant): MemberSnapshot {
        val fresh =
            try {
                materialize(asof)
            } catch (e: MemberSourceUnavailable) {
                val prior =
                    held.get()
                        ?: throw CatalogUnavailable("member catalog unavailable at pass start", e)
                val signal =
                    StaleSnapshot(prior.fingerprint, prior.asof, asof, e.message ?: "member source unavailable")
                staleListeners.forEach { it.runCatching { invoke(signal) } }
                return prior
            }
        held.set(fresh)
        return fresh
    }

    private fun materialize(asof: Instant): MemberSnapshot {
        val byDomain = source.publishedDomains().associateWith { source.distinctMembers(it) }
        return MaterializedMemberSnapshot.of(byDomain, asof)
    }
}

/** A connected compile needs the member catalog, but it is unreachable at pass start (GI-19). */
class CatalogUnavailable(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Signal that the member source was lost mid-session and a held snapshot keeps serving (GI-19). The
 * frontend maps it to its catalog-lost diagnostic (MD-013 — held snapshot in use). [heldFingerprint] /
 * [heldAsof] identify the snapshot still in use; [requestedAsof] is the pass that hit the loss;
 * [reason] carries the source's failure text (the reason-string idiom, à la `Binding`).
 */
data class StaleSnapshot(
    val heldFingerprint: String,
    val heldAsof: Instant,
    val requestedAsof: Instant,
    val reason: String,
)
