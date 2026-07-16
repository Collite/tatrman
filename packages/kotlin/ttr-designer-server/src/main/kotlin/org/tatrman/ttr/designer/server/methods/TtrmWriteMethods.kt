// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server.methods

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.tatrman.ttr.designer.server.DesignerServerDeps
import org.tatrman.ttr.designer.server.graph.TtrgGraphFile
import org.tatrman.ttr.designer.server.rpc.JsonRpcDispatcher
import org.tatrman.ttr.designer.server.rpc.RpcCodes
import org.tatrman.ttr.designer.server.rpc.TtrmRpcException
import org.tatrman.ttr.metadata.query.MetadataQuery
import org.tatrman.ttr.metadata.registry.RegistrySnapshot
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * `ttrm/…` write + graph-list methods (T3, TP-5) — the Kotlin port of TS
 * `packages/lsp/src/server.ts`'s `modeler/…` handlers (`listGraphs`/`getGraph`/
 * `getPackageGraph`/`addObjectToGraph`/`removeObjectFromGraph`/`createGraph`/
 * `applyGraphEdit`/`getSymbolDetail`/`listSymbols`/`setProjectRoot`), applying
 * T3.1's ratified edit-application model: this process writes files to disk
 * itself and returns a plain ack — no `WorkspaceEdit` wire type, no remote-host
 * apply step. `ttrm/setLayout` is T1's `TtrmLayoutService`, not duplicated here.
 *
 * `.ttrg` file access goes through [TtrgGraphFile] (plain text, matching the
 * TS `@tatrman/edit` precedent — see its header comment) — there is no Kotlin
 * `.ttrg` AST parser and this doesn't invent one.
 */
fun registerTtrmWriteMethods(
    dispatcher: JsonRpcDispatcher,
    deps: DesignerServerDeps,
) {
    fun snapshotOrError(): RegistrySnapshot =
        deps.registry.read()
            ?: throw TtrmRpcException(RpcCodes.MODEL_NOT_LOADED, "model-not-loaded")

    fun uriToPath(uri: String): Path =
        runCatching { Path.of(URI(uri)) }.getOrNull() ?: runCatching { Path.of(uri) }.getOrNull()
            ?: throw TtrmRpcException(RpcCodes.INVALID_PARAMS, "invalid-params", buildJsonObject { put("uri", uri) })

    fun requireParam(
        params: JsonObject,
        name: String,
    ): String =
        (params[name] as? JsonPrimitive)?.content
            ?: throw TtrmRpcException(RpcCodes.INVALID_PARAMS, "invalid-params", buildJsonObject { put("param", name) })

    // ---- setProjectRoot — the JVM process is bound to one --repo at startup (S24);
    // unlike the browser Worker (which starts rootless), there is no "set root after
    // the fact" concept here. Kept as a method (client parity) but it's an
    // acknowledge-only no-op, not a stub-because-unfinished. ----
    dispatcher.register("ttrm/setProjectRoot") { _ ->
        buildJsonObject { put("projectRoot", deps.repoRoot.toString()) }
    }

    dispatcher.register("ttrm/applyGraphEdit") { _ ->
        buildJsonObject {
            put("ok", false)
            put("reason", "edit-mode-not-available-in-v1")
        }
    }

    dispatcher.register("ttrm/listGraphs") { _ ->
        val snap = deps.registry.read()
        val known =
            snap
                ?.model
                ?.objectByQname()
                ?.values
                ?.mapTo(mutableSetOf()) { fullQname(it.qname) } ?: emptySet()
        val graphs = mutableListOf<JsonObject>()
        walkTtrg(deps.repoRoot) { path, content ->
            val header = TtrgGraphFile.parseHeader(content) ?: return@walkTtrg
            val objects = TtrgGraphFile.parseObjects(content)
            graphs +=
                buildJsonObject {
                    put("uri", path.toUri().toString())
                    put("name", header.name)
                    put("schema", header.schema ?: "er")
                    put("objectCount", objects.size)
                    put("missingObjectCount", objects.count { it !in known })
                }
        }
        buildJsonObject { put("graphs", JsonArray(graphs)) }
    }

    dispatcher.register("ttrm/getGraph") { params ->
        val uri = requireParam(params, "uri")
        val path = uriToPath(uri)
        if (!Files.isRegularFile(path)) {
            throw TtrmRpcException(
                RpcCodes.NOT_FOUND,
                "not-found",
                buildJsonObject {
                    put("uri", uri)
                },
            )
        }
        val content = Files.readString(path)
        val header =
            TtrgGraphFile.parseHeader(content)
                ?: throw TtrmRpcException(
                    RpcCodes.INVALID_PARAMS,
                    "not-a-graph-file",
                    buildJsonObject { put("uri", uri) },
                )
        val objects = TtrgGraphFile.parseObjects(content)

        val snap = snapshotOrError()
        val byQname =
            snap.model
                .objectByQname()
                .values
                .associateBy { fullQname(it.qname) }
        val missing = objects.filter { it !in byQname }
        val nodeObjs = objects.mapNotNull { byQname[it] }
        val inScope = nodeObjs.map { it.internalId }.toSet()
        val graph = MetadataQuery(snap).graph()

        buildJsonObject {
            put("schema", header.schema ?: "er")
            put("nodes", JsonArray(nodeObjs.map { nodeJson(it) }))
            put(
                "edges",
                JsonArray(
                    buildList {
                        graph.forEachEdge { e ->
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
                ),
            )
            put("missingObjects", JsonArray(missing.map { JsonPrimitive(it) }))
        }
    }

    dispatcher.register("ttrm/getPackageGraph") { _ ->
        val snap = snapshotOrError()
        val objs = snap.model.objectByQname().values
        val byInternalId = objs.associateBy { it.internalId }
        val graph = MetadataQuery(snap).graph()

        val depCounts = LinkedHashMap<Pair<String, String>, MutableSet<String>>()
        graph.forEachEdge { e ->
            val from = byInternalId[e.source] ?: return@forEachEdge
            val to = byInternalId[e.target] ?: return@forEachEdge
            val fp = packageOf(from)
            val tp = packageOf(to)
            if (fp == null || tp == null || fp == tp) return@forEachEdge
            depCounts.getOrPut(fp to tp) { mutableSetOf() }.add(fullQname(from.qname))
        }

        val packages = objs.mapNotNull { packageOf(it) }.distinct().sorted()
        val dependencies =
            depCounts.entries.map { (edge, citedBy) ->
                buildJsonObject {
                    put("from", edge.first)
                    put("to", edge.second)
                    put("citedBy", JsonArray(citedBy.sorted().map { JsonPrimitive(it) }))
                }
            }

        buildJsonObject {
            put("packages", JsonArray(packages.map { name -> buildJsonObject { put("name", name) } }))
            put("dependencies", JsonArray(dependencies))
            put(
                "cycles",
                JsonArray(
                    findCycles(packages, depCounts.keys).map { cyc ->
                        JsonArray(cyc.map { JsonPrimitive(it) })
                    },
                ),
            )
        }
    }

    dispatcher.register("ttrm/addObjectToGraph") { params ->
        val uri = requireParam(params, "uri")
        val qname = requireParam(params, "qname")
        val autoImport = (params["autoImport"] as? JsonPrimitive)?.boolean ?: false
        val path = uriToPath(uri)
        if (!Files.isRegularFile(path)) {
            throw TtrmRpcException(
                RpcCodes.NOT_FOUND,
                "not-found",
                buildJsonObject {
                    put("uri", uri)
                },
            )
        }
        val content = Files.readString(path)

        var packageToImport: String? = null
        if (autoImport) {
            val fromQname = qname.substringBefore('.', missingDelimiterValue = "").ifEmpty { null }
            val schemaCodes = setOf("db", "er", "binding", "query", "cnc")
            if (fromQname != null && fromQname !in schemaCodes) packageToImport = fromQname
        }

        val updated =
            TtrgGraphFile.addObject(content, qname, packageToImport)
                ?: throw TtrmRpcException(RpcCodes.INTERNAL, "no-objects-list", buildJsonObject { put("uri", uri) })
        Files.writeString(path, updated)
        buildJsonObject {
            put("ok", true)
            put("objectCount", TtrgGraphFile.parseObjects(updated).size)
        }
    }

    dispatcher.register("ttrm/removeObjectFromGraph") { params ->
        val uri = requireParam(params, "uri")
        val qname = requireParam(params, "qname")
        val pruneUnusedImport = (params["pruneUnusedImport"] as? JsonPrimitive)?.boolean ?: false
        val path = uriToPath(uri)
        if (!Files.isRegularFile(path)) {
            throw TtrmRpcException(
                RpcCodes.NOT_FOUND,
                "not-found",
                buildJsonObject {
                    put("uri", uri)
                },
            )
        }
        val content = Files.readString(path)

        val updated = TtrgGraphFile.removeObject(content, qname, pruneUnusedImport)
        if (updated == null) {
            buildJsonObject { put("ok", false) }
        } else {
            Files.writeString(path, updated)
            buildJsonObject {
                put("ok", true)
                put("objectCount", TtrgGraphFile.parseObjects(updated).size)
            }
        }
    }

    dispatcher.register("ttrm/createGraph") { params ->
        val uri = requireParam(params, "uri")
        if (!uri.endsWith(".ttrg")) {
            buildJsonObject {
                put("ok", false)
                put("reason", "uri-must-end-with-ttrg")
            }
        } else {
            val path = uriToPath(uri)
            if (Files.exists(path)) {
                throw TtrmRpcException(
                    RpcCodes.INVALID_PARAMS,
                    "already-exists",
                    buildJsonObject {
                        put("uri", uri)
                    },
                )
            }
            val name = requireParam(params, "name")
            val schema = requireParam(params, "schema")
            val packages =
                (params["packages"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
            val objects =
                (params["objects"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
            val description = (params["description"] as? JsonPrimitive)?.content
            val tags = (params["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
            val content =
                TtrgGraphFile.createContent(
                    TtrgGraphFile.CreateGraphParams(name, schema, packages, objects, description, tags),
                )
            Files.createDirectories(path.parent)
            Files.writeString(path, content)
            buildJsonObject {
                put("ok", true)
                put("uri", uri)
            }
        }
    }

    dispatcher.register("ttrm/getSymbolDetail") { params ->
        val snap = snapshotOrError()
        val qnameStr = requireParam(params, "qname")
        val obj =
            snap.model
                .objectByQname()
                .values
                .firstOrNull { fullQname(it.qname) == qnameStr }
                ?: throw TtrmRpcException(RpcCodes.NOT_FOUND, "not-found", buildJsonObject { put("qname", qnameStr) })
        buildJsonObject {
            put("qname", fullQname(obj.qname))
            put("kind", obj.kind)
            put("name", obj.qname.name)
            put("sourceUri", obj.sourceFile)
            // References/perKindData rows: not ported (TS-parser-specific rendering,
            // C1-f arc scope call — see plan.md T3). `ttrm/getObject`'s `references`
            // is `[]` for the same reason (contracts.md §v1.3(e)), so this matches
            // the existing precedent rather than introducing a new gap.
            put("referencedBy", JsonArray(emptyList()))
        }
    }

    dispatcher.register("ttrm/listSymbols") { params ->
        val snap = snapshotOrError()
        val limit = (params["limit"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 500
        val kinds = (params["kinds"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet()
        val symbols =
            snap.model
                .objectByQname()
                .values
                .asSequence()
                .filter { kinds == null || it.kind in kinds }
                .take(limit)
                .map { o ->
                    buildJsonObject {
                        put("qname", fullQname(o.qname))
                        put("kind", o.kind)
                        put("name", o.qname.name)
                        put(
                            "packageName",
                            o.qname.`package`
                                .ifEmpty { null }
                                ?.let { JsonPrimitive(it) }
                                ?: kotlinx.serialization.json.JsonNull,
                        )
                    }
                }.toList()
        buildJsonObject { put("symbols", JsonArray(symbols)) }
    }
}

/** Recursively walk [root] for `.ttrg` files, skipping VCS/build/tooling dirs, calling [onFile] with (path, text). */
private fun walkTtrg(
    root: Path,
    onFile: (Path, String) -> Unit,
) {
    if (!Files.isDirectory(root)) return
    val skip = setOf(".modeler", "node_modules", ".git", "build", "dist")
    Files.newDirectoryStream(root).use { stream ->
        for (entry in stream) {
            val name = entry.fileName.toString()
            if (Files.isDirectory(entry)) {
                if (name in skip) continue
                walkTtrg(entry, onFile)
            } else if (name.endsWith(".ttrg")) {
                onFile(entry, Files.readString(entry))
            }
        }
    }
}

/** Simple DFS cycle enumeration over the package dependency graph (small N — no need for Tarjan's). */
private fun findCycles(
    nodes: List<String>,
    edges: Set<Pair<String, String>>,
): List<List<String>> {
    val adjacency = edges.groupBy({ it.first }, { it.second })
    val cycles = mutableListOf<List<String>>()
    val seenCycleKeys = mutableSetOf<Set<String>>()

    fun dfs(
        start: String,
        current: String,
        path: MutableList<String>,
        onPath: MutableSet<String>,
    ) {
        for (next in adjacency[current].orEmpty()) {
            if (next == start && path.size > 1) {
                val key = path.toSet()
                if (seenCycleKeys.add(key)) cycles += path.toList()
            } else if (next !in onPath) {
                onPath.add(next)
                path.add(next)
                dfs(start, next, path, onPath)
                path.removeAt(path.size - 1)
                onPath.remove(next)
            }
        }
    }

    for (n in nodes) dfs(n, n, mutableListOf(n), mutableSetOf(n))
    return cycles
}
