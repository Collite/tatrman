package org.tatrman.ttr.designer.server.methods

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.tatrman.ttr.designer.server.DesignerServerDeps
import org.tatrman.ttr.designer.server.rpc.JsonRpcDispatcher
import org.tatrman.ttr.designer.server.rpc.RpcCodes
import org.tatrman.ttr.designer.server.rpc.TtrmRpcException
import org.tatrman.ttr.metadata.model.ModelObject
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.schemaCodeToToken
import org.tatrman.ttr.metadata.query.MetadataQuery
import org.tatrman.ttr.metadata.registry.RegistrySnapshot
import org.tatrman.ttr.metadata.search.SearchQuery
import org.tatrman.ttr.metadata.world.ResolvedWorld
import org.tatrman.ttr.metadata.world.WorldResolution
import org.tatrman.ttr.metadata.world.WorldResolver

/**
 * The `ttrm/…` read handlers (contracts §4). Each is proto-conversion + delegation
 * to `MetadataQuery`/`WorldResolver` — no model/query/search/world logic here
 * (MD2/MD5). The library's structured results/failures are mapped to JSON-RPC
 * error objects (`-32000/-32001/-32002`); the library stays id-free.
 */
fun registerTtrmMethods(
    dispatcher: JsonRpcDispatcher,
    deps: DesignerServerDeps,
) {
    fun snapshotOrError(): RegistrySnapshot =
        deps.registry.read()
            ?: throw TtrmRpcException(RpcCodes.MODEL_NOT_LOADED, "model-not-loaded")

    // ---- getStatus — the handshake; answers even before a snapshot exists ----
    dispatcher.register("ttrm/getStatus") { _ ->
        val snap = deps.registry.read()
        buildJsonObject {
            put("protocolVersion", 1)
            put(
                "modelVersion",
                snap
                    ?.model
                    ?.version
                    ?.value
                    ?.let { JsonPrimitive(it) } ?: JsonNull,
            )
            put("loadedAt", snap?.swappedAt?.toString()?.let { JsonPrimitive(it) } ?: JsonNull)
            put("repoRoot", deps.repoRoot.toString())
            put("issues", issuesArray(snap))
        }
    }

    dispatcher.register("ttrm/getModelIndex") { _ ->
        val snap = snapshotOrError()
        val objs = snap.model.objectByQname().values
        buildJsonObject {
            put("modelVersion", snap.model.version.value)
            put("packages", JsonArray(packagesOf(objs).map { JsonPrimitive(it) }))
            put(
                "schemas",
                JsonArray(
                    snap.model.schemas.keys
                        .sorted()
                        .map { JsonPrimitive(it) },
                ),
            )
            put(
                "areas",
                JsonArray(
                    snap.model.areas.keys
                        .sorted()
                        .map { JsonPrimitive(it) },
                ),
            )
            put(
                "counts",
                buildJsonObject {
                    put("objects", objs.size)
                    put("schemas", snap.model.schemas.size)
                    put("areas", snap.model.areas.size)
                },
            )
        }
    }

    dispatcher.register("ttrm/getModelGraph") { params ->
        val snap = snapshotOrError()
        val scope = params["scope"] as? JsonObject
        val pkg = (scope?.get("package") as? JsonPrimitive)?.content
        val schemaTok = (scope?.get("schema") as? JsonPrimitive)?.content
        if (pkg != null && packagesOf(snap.model.objectByQname().values).none { it == pkg }) {
            throw TtrmRpcException(RpcCodes.BAD_SCOPE, "bad-scope", buildJsonObject { put("package", pkg) })
        }
        if (schemaTok != null && !snap.model.schemas.containsKey(schemaTok)) {
            throw TtrmRpcException(RpcCodes.BAD_SCOPE, "bad-scope", buildJsonObject { put("schema", schemaTok) })
        }
        val q = MetadataQuery(snap)
        val pkgObjs =
            if (pkg == null) {
                snap.model
                    .objectByQname()
                    .values
                    .toList()
            } else {
                q
                    .listObjects(
                        MetadataQuery.ObjectFilter(pkg = pkg),
                        MetadataQuery.PageRequest(pageSize = 100_000),
                    ).items
            }
        // Schema scope: narrow to objects whose schema-code token matches the selection.
        // The sidebar/index keys ARE these tokens (model.schemas is token-keyed, and
        // nodeJson exposes `schema = schemaCodeToToken(...)`), so this matches what the UI shows.
        val objs =
            if (schemaTok == null) {
                pkgObjs
            } else {
                pkgObjs.filter { schemaCodeToToken(it.qname.schemaCode) == schemaTok }
            }
        val edgeTypeFilter =
            (params["edgeTypes"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content?.uppercase() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
        val inScope = objs.map { it.internalId }.toSet()
        val graph = q.graph()
        buildJsonObject {
            put("nodes", JsonArray(objs.map { nodeJson(it) }))
            put(
                "edges",
                buildJsonArray {
                    graph.forEachEdge { e ->
                        if (edgeTypeFilter != null && e.type.name !in edgeTypeFilter) return@forEachEdge
                        if (e.source in inScope && e.target in inScope) {
                            val from = graph.byInternalId[e.source] ?: return@forEachEdge
                            val to = graph.byInternalId[e.target] ?: return@forEachEdge
                            add(
                                buildJsonObject {
                                    put("from", fullQname(from.qname))
                                    put("to", fullQname(to.qname))
                                    put("type", e.type.name)
                                },
                            )
                        }
                    }
                },
            )
        }
    }

    dispatcher.register("ttrm/getObject") { params ->
        val snap = snapshotOrError()
        val qnameStr =
            (params["qname"] as? JsonPrimitive)?.content
                ?: throw TtrmRpcException(RpcCodes.INVALID_PARAMS, "invalid-params")
        val obj =
            snap.model
                .objectByQname()
                .values
                .firstOrNull { fullQname(it.qname) == qnameStr }
                ?: throw TtrmRpcException(RpcCodes.NOT_FOUND, "not-found", buildJsonObject { put("qname", qnameStr) })
        buildJsonObject {
            put("object", nodeJson(obj))
            put("sourceLocation", JsonPrimitive(obj.sourceFile))
            put("references", JsonArray(emptyList()))
        }
    }

    dispatcher.register("ttrm/search") { params ->
        val snap = snapshotOrError()
        val query = (params["query"] as? JsonPrimitive)?.content ?: ""
        val algorithm = (params["algorithm"] as? JsonPrimitive)?.content ?: "all"
        val limit = (params["limit"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val hits = MetadataQuery(snap).search(SearchQuery(query = query, algorithm = algorithm, limit = limit))
        buildJsonObject {
            put(
                "hits",
                JsonArray(
                    hits.map {
                        buildJsonObject {
                            put("qname", fullQname(it.ownerQname))
                            put("score", it.score.toDouble())
                            put("matchedField", it.matchedField)
                        }
                    },
                ),
            )
        }
    }

    dispatcher.register("ttrm/refresh") { params ->
        val force = (params["force"] as? JsonPrimitive)?.content?.toBoolean() ?: false
        val results = deps.refresher.refresh(sourceId = "", force = force)
        val inFlight = results.any { it.errorMessage == "refresh_in_flight" }
        buildJsonObject {
            put("outcome", if (inFlight) "in-flight" else "refreshed")
            put(
                "modelVersion",
                deps.registry
                    .read()
                    ?.model
                    ?.version
                    ?.value
                    ?.let { JsonPrimitive(it) } ?: JsonNull,
            )
        }
    }

    dispatcher.register("ttrm/getWorld") { params ->
        val snap = snapshotOrError()
        val resolver = WorldResolver(snap)
        val qnameStr = (params["qname"] as? JsonPrimitive)?.content
        if (qnameStr == null) {
            buildJsonObject {
                put("worlds", JsonArray(resolver.listWorlds().map { JsonPrimitive(fullQname(it)) }))
            }
        } else {
            val wq =
                resolver.listWorlds().firstOrNull { fullQname(it) == qnameStr }
                    ?: throw TtrmRpcException(
                        RpcCodes.NOT_FOUND,
                        "WorldNotFound",
                        buildJsonObject { put("qname", qnameStr) },
                    )
            when (val r = resolver.resolve(wq)) {
                is WorldResolution.Ok -> resolvedWorldJson(r.world)
                is WorldResolution.Failure ->
                    throw TtrmRpcException(RpcCodes.NOT_FOUND, r::class.simpleName ?: "WorldFailure")
            }
        }
    }
}

private fun issuesArray(snap: RegistrySnapshot?): JsonArray =
    JsonArray(
        (snap?.warnings ?: emptyList()).map {
            buildJsonObject {
                put("message", it.message)
                put("file", it.file)
            }
        },
    )

/** Full package-qualified qname string: `<package>.<schemaToken>.<namespace>.<name>` (empty slots dropped). */
private fun fullQname(q: QualifiedName): String =
    buildList {
        if (q.`package`.isNotEmpty()) add(q.`package`)
        schemaCodeToToken(q.schemaCode).takeIf { it.isNotEmpty() }?.let { add(it) }
        if (q.namespace.isNotEmpty()) add(q.namespace)
        add(q.name)
    }.joinToString(".")

private fun nodeJson(o: ModelObject): JsonObject =
    buildJsonObject {
        put("qname", fullQname(o.qname))
        put("kind", o.kind)
        put("label", o.qname.name)
        put("schema", schemaCodeToToken(o.qname.schemaCode))
        put("pkg", o.qname.`package`)
    }

/** Packages derived from object source files (`…/models/<pkg-path>/<file>` → dotted pkg). */
private fun packagesOf(objs: Collection<ModelObject>): List<String> =
    objs
        .mapNotNull { o ->
            val sf = o.sourceFile.replace('\\', '/')
            val afterModels = sf.substringAfterLast("/models/", "")
            if (afterModels.isEmpty()) return@mapNotNull null
            afterModels.substringBeforeLast('/', "").replace('/', '.').ifEmpty { null }
        }.distinct()
        .sorted()

private fun resolvedWorldJson(w: ResolvedWorld): JsonObject =
    buildJsonObject {
        put("qname", fullQname(w.qname))
        put("fingerprint", w.fingerprint)
        put("engines", JsonArray(w.engines.map { JsonPrimitive(it.qname.name) }))
        put("executors", JsonArray(w.executors.map { JsonPrimitive(it.qname.name) }))
        put("storages", JsonArray(w.storages.map { JsonPrimitive(it.qname.name) }))
        put(
            "staging",
            w.staging
                ?.qname
                ?.name
                ?.let { JsonPrimitive(it) } ?: JsonNull,
        )
    }
