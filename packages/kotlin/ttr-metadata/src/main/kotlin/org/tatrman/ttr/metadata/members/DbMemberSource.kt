// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.members

import org.tatrman.ttr.md.resolve.QualifiedName
import org.tatrman.ttr.semantics.md.DomainBinding
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/**
 * A [MemberSource] backed by a JDBC [DataSource]: for each published domain, `SELECT DISTINCT` the
 * md2db backing column ([DomainBinding.column]) from its table ([DomainBinding.table]), with **keyset
 * paging** (default [DEFAULT_PAGE_SIZE]/page) so an unbounded dimension never lands in one round-trip
 * (architecture §7 risk row). Members are cached per domain; invalidation is refresh-driven
 * ([invalidate]), not TTL — a metadata refresh clears the cache, matching the registry idiom.
 *
 * ttr-metadata carries no connection provisioning of its own (light artifact, MD3): the caller
 * supplies the [DataSource] (designer-server wires it in S6-B). Table/column are rendered as quoted
 * identifiers off the binding; the table ref's last dotted segment is the physical table (public
 * schema), matching the S4-A read emitter's bare rendering of `db.dbo.<t>` → `"<t>"`. Where the
 * physical table actually sits (search_path / catalog) is the DataSource owner's concern.
 *
 * Any [SQLException] (a dropped connection, a vanished table) surfaces as [MemberSourceUnavailable],
 * which the [DegradingMemberCatalog] ladder turns into a hard error or a held-snapshot + stale signal.
 */
class DbMemberSource(
    private val dataSource: DataSource,
    domainBindings: Collection<DomainBinding>,
    private val published: Set<QualifiedName>,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) : MemberSource {
    init {
        require(pageSize > 0) { "pageSize must be positive: $pageSize" }
    }

    private val bindingByDomain: Map<String, DomainBinding> = domainBindings.associateBy { it.domain }
    private val cache = ConcurrentHashMap<QualifiedName, List<String>>()

    /** Published domains that actually have an md2db binding to read from. */
    override fun publishedDomains(): Set<QualifiedName> = published.filterTo(mutableSetOf()) { it in bindingByDomain }

    override fun distinctMembers(domain: QualifiedName): List<String> {
        require(domain in published) { "domain not published: $domain" }
        val binding =
            bindingByDomain[domain]
                ?: throw MemberSourceUnavailable("no md2db binding for published domain $domain")
        return cache.getOrPut(domain) { readDistinct(binding) }
    }

    /** Drop cached members (a metadata refresh invalidates; there is no TTL). */
    fun invalidate() {
        cache.clear()
    }

    private fun readDistinct(binding: DomainBinding): List<String> {
        val table = quoteIdent(binding.table.substringAfterLast('.'))
        val column = quoteIdent(binding.column)
        // Keyset paging: page 1 has no lower bound; each subsequent page advances past the last
        // value seen (`WHERE col > ?`). NULLs are excluded — a null is not a coordinate value. The
        // page limit is an int we own (validated positive), inlined for cross-dialect portability.
        val base = "SELECT DISTINCT $column FROM $table WHERE $column IS NOT NULL"
        val firstPage = "$base ORDER BY $column ASC LIMIT $pageSize"
        val nextPage = "$base AND $column > ? ORDER BY $column ASC LIMIT $pageSize"

        return try {
            dataSource.connection.use { conn ->
                val out = ArrayList<String>()
                var last: String? = null
                while (true) {
                    val page = ArrayList<String>(minOf(pageSize, 1024))
                    conn.prepareStatement(if (last == null) firstPage else nextPage).use { ps ->
                        if (last != null) ps.setString(1, last)
                        ps.executeQuery().use { rs ->
                            while (rs.next()) page += rs.getString(1)
                        }
                    }
                    out += page
                    if (page.size < pageSize) break
                    last = page.last()
                }
                out
            }
        } catch (e: SQLException) {
            throw MemberSourceUnavailable("member read failed for ${binding.domain}: ${e.message}", e)
        }
    }

    private fun quoteIdent(ident: String): String = "\"" + ident.replace("\"", "\"\"") + "\""

    companion object {
        const val DEFAULT_PAGE_SIZE = 10_000
    }
}
