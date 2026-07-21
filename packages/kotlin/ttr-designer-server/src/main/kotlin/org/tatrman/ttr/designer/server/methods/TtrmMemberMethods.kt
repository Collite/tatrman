// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server.methods

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.tatrman.ttr.designer.server.DesignerServerDeps
import org.tatrman.ttr.designer.server.rpc.JsonRpcDispatcher
import org.tatrman.ttr.designer.server.rpc.RpcCodes
import org.tatrman.ttr.designer.server.rpc.TtrmRpcException
import org.tatrman.ttr.metadata.members.DbMemberSource
import org.tatrman.ttr.metadata.members.MemberFingerprint
import org.tatrman.ttr.metadata.members.MemberSource
import org.tatrman.ttr.metadata.members.MemberSourceUnavailable
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.semantics.md.AttrBinding
import org.tatrman.ttr.semantics.md.BindingShape
import org.tatrman.ttr.semantics.md.CubeletBinding
import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttr.semantics.md.MdModel
import org.tatrman.ttr.semantics.md.MeasureBinding
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import javax.sql.DataSource

/**
 * The `ttrm/…` **member-catalog** read handlers (dot-path contracts §7.2): `getMemberDomains`,
 * `getMembers`, and `getMaterializationStatus`. Read-only, additive — the `ttrm` `protocolVersion`
 * stays `1` (contracts §4 evolution rule). Member content is served through ttr-metadata's
 * [DbMemberSource] over [DesignerServerDeps.memberDataSource] (the first `DataSource` the server
 * provisions, MD3); the server stays a host (MD5) — snapshot/fingerprint/paging logic all live in the
 * library, this is proto-conversion. `getMaterializationStatus` probes the same DataSource's catalog.
 *
 * The MD tier (which domains publish members, their md2db backing columns, and the dimension/attribute
 * a domain sits under) is not on ttr-metadata's ER/DB `Model`, so it is parsed from the `.ttrm` files
 * under [DesignerServerDeps.storageRoot] on demand — the same offline load as the compiler's `MdRepo`.
 */
fun registerTtrmMemberMethods(
    dispatcher: JsonRpcDispatcher,
    deps: DesignerServerDeps,
) {
    fun dataSourceOrError() =
        deps.memberDataSource
            ?: throw TtrmRpcException(RpcCodes.MODEL_NOT_LOADED, "member-db-not-configured")

    // ---- ttrm/getMemberDomains → { domains: [{ qname, attribute, dimension, count, fingerprint }] } ----
    dispatcher.register("ttrm/getMemberDomains") { _ ->
        val ds = dataSourceOrError()
        val tier = loadMdTier(deps.storageRoot)
        val source = DbMemberSource(ds, tier.bindings.domains.values, tier.publishedDomains)
        buildJsonObject {
            put(
                "domains",
                buildJsonArray {
                    for (domain in tier.publishedDomains.sorted()) {
                        if (domain !in tier.bindings.domains) continue // published but unbound ⇒ not servable
                        val members = distinctOrUnavailable(source, domain)
                        val placement = tier.attributeFor(domain)
                        add(
                            buildJsonObject {
                                put("qname", placement?.domainRef ?: domain)
                                put("attribute", placement?.attributePath?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("dimension", placement?.dimension?.let { JsonPrimitive(it) } ?: JsonNull)
                                put("count", members.size)
                                put("fingerprint", domainFingerprint(domain, members))
                            },
                        )
                    }
                },
            )
        }
    }

    // ---- ttrm/getMembers { domain, prefix?, cursor?, limit? } → { members, nextCursor?, fingerprint } ----
    dispatcher.register("ttrm/getMembers") { params ->
        val ds = dataSourceOrError()
        val qname = requireParam(params, "domain")
        val prefix = (params["prefix"] as? JsonPrimitive)?.content
        val cursor = (params["cursor"] as? JsonPrimitive)?.content
        val limit = ((params["limit"] as? JsonPrimitive)?.content?.toIntOrNull() ?: MAX_LIMIT).coerceIn(1, MAX_LIMIT)

        val tier = loadMdTier(deps.storageRoot)
        // The wire qname is the domain's qualified ref (e.g. `md.Name`); the source is keyed by the
        // simple domain name. An unpublished/unknown domain is the established not-found shape (§7.2).
        val domain = qname.substringAfterLast('.')
        if (domain !in tier.publishedDomains || domain !in tier.bindings.domains) {
            throw TtrmRpcException(RpcCodes.NOT_FOUND, "not-found", buildJsonObject { put("domain", qname) })
        }
        val source = DbMemberSource(ds, tier.bindings.domains.values, tier.publishedDomains)
        // T-R1-3: `readDistinct` returns rows in DB `ORDER BY` (collation) order, but the wire cursor is
        // advanced with Kotlin's `>` (Java/UTF-16 order). On a locale-collated PG the two disagree
        // (`"apple" < "Banana"` in the DB but `'a' > 'B'` in Java), so the cursor could regress and the
        // pager loop forever, or skip members. Re-sort in Java natural order here so the `>` compare and
        // the page order agree — the same order `MemberFingerprint` / `InternedMemberIndex` already use.
        val all = distinctOrUnavailable(source, domain).sorted()

        val filtered = if (prefix != null) all.filter { it.startsWith(prefix) } else all
        val start =
            if (cursor != null) {
                filtered.indexOfFirst { it > cursor }.let { if (it < 0) filtered.size else it }
            } else {
                0
            }
        val page = filtered.drop(start).take(limit)
        val more = start + limit < filtered.size
        buildJsonObject {
            put("members", buildJsonArray { page.forEach { add(JsonPrimitive(it)) } })
            put("nextCursor", if (more && page.isNotEmpty()) JsonPrimitive(page.last()) else JsonNull)
            // Fingerprint spans the domain's full member set — stable across pages and prefix filters.
            put("fingerprint", domainFingerprint(domain, all))
        }
    }

    // ---- ttrm/getMaterializationStatus { cubelet? } → { statuses:[{cubelet,table,status,detail?}] } ----
    // The dbt-ish loop (D22, contracts §7.2): compare each bound cubelet's declared fact-table columns
    // against the live DB catalog (information_schema). `materialized` = table present with every bound
    // column; `declared-only` = table absent (a freshly generated `.ttrm` before its first run);
    // `drifted` = table present but a bound column is missing (+ detail). Read-only; same DataSource.
    dispatcher.register("ttrm/getMaterializationStatus") { params ->
        val ds = dataSourceOrError()
        val tier = loadMdTier(deps.storageRoot)
        val only = (params["cubelet"] as? JsonPrimitive)?.content
        val cubelets =
            tier.bindings.cubelets.values
                .filter { only == null || it.cubelet == only }
        buildJsonObject {
            put(
                "statuses",
                buildJsonArray {
                    for (cb in cubelets.sortedBy { it.cubelet }) {
                        val present = tableColumns(ds, cb.table.substringAfterLast('.')) // null ⇒ table absent
                        val missing = present?.let { (expectedFactColumns(cb) - it).sorted() }
                        val status =
                            when {
                                present == null -> "declared-only"
                                missing!!.isEmpty() -> "materialized"
                                else -> "drifted"
                            }
                        add(
                            buildJsonObject {
                                put("cubelet", cb.cubelet)
                                put("table", cb.table)
                                put("status", status)
                                if (status == "drifted") put("detail", "missing columns: ${missing!!.joinToString()}")
                            },
                        )
                    }
                },
            )
        }
    }
}

/** The fact-table columns a wide/long cubelet binding expects (grain attr columns + measure/value cols). */
private fun expectedFactColumns(cb: CubeletBinding): Set<String> {
    val cols = mutableSetOf<String>()
    // Grain/drill attribute columns on the fact table; Hop drills live on a map table, not here.
    cb.attributes.values.forEach { if (it is AttrBinding.Column) cols += it.column }
    when (val s = cb.shape) {
        is BindingShape.Wide -> cb.measures.values.forEach { if (it is MeasureBinding.Column) cols += it.column }
        is BindingShape.Long -> {
            cols += s.codeColumn
            cols += s.valueColumn
        }
    }
    return cols
}

/** Column names of [table] via `information_schema` (case-sensitive), or null when the table is absent. */
private fun tableColumns(
    dataSource: DataSource,
    table: String,
): Set<String>? =
    try {
        dataSource.connection.use { c ->
            c.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_name = ?").use { ps ->
                ps.setString(1, table)
                ps.executeQuery().use { rs ->
                    val cols = mutableSetOf<String>()
                    while (rs.next()) cols += rs.getString(1)
                    cols.ifEmpty { null } // a real table always has ≥1 column ⇒ empty means absent
                }
            }
        }
    } catch (e: SQLException) {
        throw TtrmRpcException(
            RpcCodes.INTERNAL,
            "materialization-probe-failed",
            buildJsonObject { put("table", table) },
        )
    }

/** A per-domain content fingerprint (`sha256:<hex>`), reusing the library's snapshot fingerprint. */
private fun domainFingerprint(
    domain: String,
    members: List<String>,
): String = MemberFingerprint.of(mapOf(domain to members))

/** Read a domain's distinct members, mapping a source outage to the JSON-RPC internal-error shape. */
private fun distinctOrUnavailable(
    source: MemberSource,
    domain: String,
): List<String> =
    try {
        source.distinctMembers(domain)
    } catch (e: MemberSourceUnavailable) {
        throw TtrmRpcException(
            RpcCodes.INTERNAL,
            "member-source-unavailable",
            buildJsonObject { put("domain", domain) },
        )
    }

private const val MAX_LIMIT = 10_000

/** The MD tier parsed from the repo's `.ttrm` files: the model + its md2db bindings. */
internal class MdTier(
    val model: MdModel,
    val bindings: MdBindings,
) {
    /** Published domains (opted in via `publish: members`), by simple name. */
    val publishedDomains: Set<String> = model.domains.filterValues { it.publishMembers }.keys

    /** Where a domain sits: the `Dimension.attribute` that ranges over it, and its authored qname. */
    fun attributeFor(domain: String): Placement? {
        val entry =
            model.attributes.entries.firstOrNull { (_, a) -> model.underlyingDomain(a.domainRef ?: "") == domain }
                ?: return null
        return Placement(entry.key, entry.value.dimension, entry.value.domainRef)
    }

    class Placement(
        val attributePath: String,
        val dimension: String,
        val domainRef: String?,
    )
}

/**
 * Load the MD tier for [storageRoot] (model + md2db bindings parsed from its `.ttrm` files, mirroring
 * the compiler's `MdRepo`), reusing a cached parse when nothing under it has changed (T-C5). Every
 * `ttrm/…` RPC used to re-parse the whole model tree; now a cheap **stat-based fingerprint** (each
 * `.ttrm`'s relative path + size + mtime) gates the reuse, so a model edit / add / remove busts the
 * cache automatically — no explicit refresh call needed — and an unchanged repeated call is O(stat),
 * not O(parse). The DataSource-backed member reads stay per-request (fresh DB data); only the model
 * parse is cached. An absent/empty tier yields empty maps (the handlers then report no published domains).
 */
internal fun loadMdTier(storageRoot: Path): MdTier = MdTierCache.get(storageRoot)

private object MdTierCache {
    private class Entry(
        val fingerprint: String,
        val tier: MdTier,
    )

    private val cache = ConcurrentHashMap<Path, Entry>()

    fun get(storageRoot: Path): MdTier {
        if (!Files.isDirectory(storageRoot)) return MdTier(MdModel.from(emptyList()), MdBindings.from(emptyList()))
        val realRoot = runCatching { storageRoot.toRealPath() }.getOrDefault(storageRoot)
        val fp = fingerprint(realRoot)
        cache[realRoot]?.let { if (it.fingerprint == fp) return it.tier }
        val tier = parse(realRoot)
        cache[realRoot] = Entry(fp, tier)
        return tier
    }

    /** A digest over every `.ttrm`'s (relative path, size, mtime) — changes on any add/remove/edit. */
    private fun fingerprint(realRoot: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (f in ttrmFiles(realRoot)) {
            digest.update(realRoot.relativize(f).toString().toByteArray())
            digest.update(0)
            digest.update(Files.size(f).toString().toByteArray())
            digest.update(0)
            digest.update(
                Files
                    .getLastModifiedTime(f)
                    .toMillis()
                    .toString()
                    .toByteArray(),
            )
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun parse(realRoot: Path): MdTier {
        val defs = mutableListOf<Definition>()
        for (file in ttrmFiles(realRoot)) {
            val parsed = TtrLoader.parseString(Files.readString(file), realRoot.relativize(file).toString())
            if (parsed.ok) defs += parsed.definitions
        }
        return MdTier(MdModel.from(defs), MdBindings.from(defs))
    }

    private fun ttrmFiles(realRoot: Path): List<Path> =
        Files.walk(realRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".ttrm") }
                .sorted()
                .collect(Collectors.toList())
        }
}
