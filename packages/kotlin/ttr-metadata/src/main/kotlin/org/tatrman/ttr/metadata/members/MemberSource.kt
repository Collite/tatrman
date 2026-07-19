// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.members

import org.tatrman.ttr.md.resolve.QualifiedName

/**
 * A source of published-domain member content (architecture §7). The catalog materializes a
 * [MaterializedMemberSnapshot] by pulling every published domain from a source. Implementations:
 * [DbMemberSource] (`SELECT DISTINCT` over the md2db backing column); a server/agent-backed source
 * lands with the wire protocol (S6-B / S7).
 *
 * A source that cannot currently answer throws [MemberSourceUnavailable]; the degradation ladder in
 * [DegradingMemberCatalog] turns that into a hard error at pass start, or a held-snapshot + stale
 * signal mid-session (GI-19).
 */
interface MemberSource {
    /** The published domains this source serves (a domain opts in via `publish: members`, §1.4). */
    fun publishedDomains(): Set<QualifiedName>

    /** Distinct members of [domain] (order/dup handling is the caller's). Throws when the source is down. */
    fun distinctMembers(domain: QualifiedName): List<String>
}

/** The member source is (currently) unreachable — the [DegradingMemberCatalog] ladder handles it. */
class MemberSourceUnavailable(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
