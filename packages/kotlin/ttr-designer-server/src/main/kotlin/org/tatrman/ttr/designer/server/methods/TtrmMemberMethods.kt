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
import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttr.semantics.md.MdModel
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

/**
 * The `ttrm/…` **member-catalog** read handlers (dot-path contracts §7.2): `getMemberDomains` and
 * `getMembers`. Read-only, additive — the `ttrm` `protocolVersion` stays `1` (contracts §4 evolution
 * rule). Member content is served through ttr-metadata's [DbMemberSource] over [DesignerServerDeps
 * .memberDataSource] (the first `DataSource` the server provisions, MD3); the server stays a host
 * (MD5) — snapshot/fingerprint/paging logic all live in the library, this is proto-conversion.
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
        val all = distinctOrUnavailable(source, domain) // sorted, de-duplicated

        val filtered = if (prefix != null) all.filter { it.startsWith(prefix) } else all
        val start =
            if (cursor !=
                null
            ) {
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
private class MdTier(
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
 * Parse every `.ttrm` under [storageRoot] into the MD tier (mirrors the compiler's `MdRepo`: offline,
 * no service). Files that fail to parse contribute no defs (their diagnostics are the model loader's
 * concern, not the member protocol's). An absent/empty tier yields empty maps — the handlers then
 * report no published domains rather than failing.
 */
private fun loadMdTier(storageRoot: Path): MdTier {
    if (!Files.isDirectory(storageRoot)) return MdTier(MdModel.from(emptyList()), MdBindings.from(emptyList()))
    val realRoot = runCatching { storageRoot.toRealPath() }.getOrDefault(storageRoot)
    val files =
        Files.walk(realRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".ttrm") }
                .sorted()
                .collect(Collectors.toList())
        }
    val defs = mutableListOf<Definition>()
    for (file in files) {
        val parsed = TtrLoader.parseString(Files.readString(file), realRoot.relativize(file).toString())
        if (parsed.ok) defs += parsed.definitions
    }
    return MdTier(MdModel.from(defs), MdBindings.from(defs))
}
