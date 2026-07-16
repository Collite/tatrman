// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.copyTo

/**
 * `ttrm/…` write + graph-list methods (T3, TP-5): end-to-end over the real `/ttrm`
 * WebSocket route. These tests WRITE `.ttrg` files (`createGraph`/`addObjectToGraph`/
 * `removeObjectFromGraph`, and `listGraphs`/`getGraph` need a `.ttrg` to find) — unlike
 * the read-only specs, they must NOT run against `MetadataFixtures.erpProjectRoot()`
 * directly (that's the shared, checked-in `ttr-metadata` testFixtures source tree, not
 * a scratch dir). Each test run gets its own disposable copy.
 */
class TtrmWriteMethodsSpec :
    StringSpec({
        val projectCopy = Files.createTempDirectory("ttrm-write-methods-spec")
        copyRecursively(MetadataFixtures.erpProjectRoot(), projectCopy)
        val deps = DesignerServerDeps.forRepo(projectCopy)

        fun withProtocol(block: suspend DefaultClientWebSocketSession.() -> Unit) =
            testApplication {
                application { designerServerModule(deps, watch = false) }
                val client = createClient { install(WebSockets) }
                client.webSocket("/ttrm") { block() }
            }

        "setProjectRoot acknowledges with the fixed repo root (no-op by design, S24)" {
            withProtocol {
                val r = rpc(1, "ttrm/setProjectRoot", buildJsonObject { put("projectRoot", "/anything") }).result()
                r["projectRoot"]!!.jsonPrimitive.content shouldBe deps.repoRoot.toString()
            }
        }

        "applyGraphEdit is a permanent v1 stub, matching the TS server" {
            withProtocol {
                val r = rpc(2, "ttrm/applyGraphEdit").result()
                r["ok"]!!.jsonPrimitive.boolean shouldBe false
                r["reason"]!!.jsonPrimitive.content shouldBe "edit-mode-not-available-in-v1"
            }
        }

        "listGraphs finds a .ttrg file and reports its object/missing counts" {
            withProtocol {
                val realQname =
                    rpc(3, "ttrm/getModelGraph")
                        .result()["nodes"]!!
                        .jsonArray
                        .first()
                        .jsonObject["qname"]!!
                        .jsonPrimitive.content

                val tmp = Files.createTempDirectory(deps.repoRoot, "graphs-")
                val ttrg = tmp.resolve("g.ttrg")
                Files.writeString(ttrg, "graph g {\n  model: db,\n  objects: [$realQname, nonexistent.qname]\n}\n")

                val r = rpc(4, "ttrm/listGraphs").result()
                val listed =
                    r["graphs"]!!.jsonArray.map { it.jsonObject }.firstOrNull {
                        it["name"]!!.jsonPrimitive.content ==
                            "g"
                    }
                requireNotNull(listed)
                listed["objectCount"]!!.jsonPrimitive.content shouldBe "2"
                listed["missingObjectCount"]!!.jsonPrimitive.content shouldBe "1"

                Files.deleteIfExists(ttrg)
                Files.deleteIfExists(tmp)
            }
        }

        "getGraph returns nodes for objects it can resolve and lists the rest as missing" {
            withProtocol {
                val realQname =
                    rpc(5, "ttrm/getModelGraph")
                        .result()["nodes"]!!
                        .jsonArray
                        .first()
                        .jsonObject["qname"]!!
                        .jsonPrimitive.content

                val tmp = Files.createTempDirectory(deps.repoRoot, "graphs-")
                val ttrg = tmp.resolve("g2.ttrg")
                Files.writeString(ttrg, "graph g2 {\n  model: db,\n  objects: [$realQname, nonexistent.qname]\n}\n")

                val r = rpc(6, "ttrm/getGraph", buildJsonObject { put("uri", ttrg.toUri().toString()) }).result()
                r["nodes"]!!.jsonArray.map { it.jsonObject["qname"]!!.jsonPrimitive.content } shouldContain realQname
                r["missingObjects"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContain "nonexistent.qname"

                Files.deleteIfExists(ttrg)
                Files.deleteIfExists(tmp)
            }
        }

        "getGraph on a missing file is not-found" {
            withProtocol {
                val err =
                    rpc(
                        7,
                        "ttrm/getGraph",
                        buildJsonObject {
                            put(
                                "uri",
                                deps.repoRoot
                                    .resolve("nope.ttrg")
                                    .toUri()
                                    .toString(),
                            )
                        },
                    )
                err.errorCode() shouldBe -32001
            }
        }

        "addObjectToGraph then removeObjectFromGraph round-trip on a real file" {
            withProtocol {
                val tmp = Files.createTempDirectory(deps.repoRoot, "graphs-")
                val ttrg = tmp.resolve("g3.ttrg")
                Files.writeString(ttrg, "graph g3 {\n  model: er,\n  objects: []\n}\n")
                val uri = ttrg.toUri().toString()

                val addR =
                    rpc(
                        8,
                        "ttrm/addObjectToGraph",
                        buildJsonObject {
                            put("uri", uri)
                            put("qname", "a.b.c")
                            put("autoImport", false)
                        },
                    ).result()
                addR["ok"]!!.jsonPrimitive.boolean shouldBe true
                addR["objectCount"]!!.jsonPrimitive.content shouldBe "1"
                Files.readString(ttrg) shouldBe "graph g3 {\n  model: er,\n  objects: [a.b.c]\n}\n"

                val removeR =
                    rpc(
                        9,
                        "ttrm/removeObjectFromGraph",
                        buildJsonObject {
                            put("uri", uri)
                            put("qname", "a.b.c")
                            put("pruneUnusedImport", false)
                        },
                    ).result()
                removeR["ok"]!!.jsonPrimitive.boolean shouldBe true
                removeR["objectCount"]!!.jsonPrimitive.content shouldBe "0"

                Files.deleteIfExists(ttrg)
                Files.deleteIfExists(tmp)
            }
        }

        "createGraph writes a brand-new .ttrg file, rejects a non-.ttrg uri, rejects an existing one" {
            withProtocol {
                val tmp = Files.createTempDirectory(deps.repoRoot, "graphs-")
                val uri = tmp.resolve("brand_new.ttrg").toUri().toString()

                val r =
                    rpc(
                        10,
                        "ttrm/createGraph",
                        buildJsonObject {
                            put("uri", uri)
                            put("name", "brand_new")
                            put("schema", "er")
                            put("packages", kotlinx.serialization.json.JsonArray(emptyList()))
                            put("objects", kotlinx.serialization.json.JsonArray(emptyList()))
                        },
                    ).result()
                r["ok"]!!.jsonPrimitive.boolean shouldBe true

                val notTtrg =
                    rpc(
                        11,
                        "ttrm/createGraph",
                        buildJsonObject {
                            put("uri", tmp.resolve("x.txt").toUri().toString())
                            put("name", "x")
                            put("schema", "er")
                        },
                    )
                notTtrg.result()["ok"]!!.jsonPrimitive.boolean shouldBe false

                val alreadyExists =
                    rpc(
                        12,
                        "ttrm/createGraph",
                        buildJsonObject {
                            put("uri", uri)
                            put("name", "brand_new")
                            put("schema", "er")
                        },
                    )
                alreadyExists.errorCode() shouldBe -32602

                Files.deleteIfExists(tmp.resolve("brand_new.ttrg"))
                Files.deleteIfExists(tmp)
            }
        }

        "getSymbolDetail round-trips a qname; listSymbols respects kind + limit filters" {
            withProtocol {
                val realQname =
                    rpc(13, "ttrm/getModelGraph")
                        .result()["nodes"]!!
                        .jsonArray
                        .first()
                        .jsonObject["qname"]!!
                        .jsonPrimitive.content
                val detail = rpc(14, "ttrm/getSymbolDetail", buildJsonObject { put("qname", realQname) }).result()
                detail["qname"]!!.jsonPrimitive.content shouldBe realQname

                val symbols = rpc(15, "ttrm/listSymbols", buildJsonObject { put("limit", 1) }).result()
                symbols["symbols"]!!.jsonArray.size shouldBe 1
            }
        }

        "getPackageGraph lists at least the erp package" {
            withProtocol {
                val r = rpc(16, "ttrm/getPackageGraph").result()
                r["packages"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content } shouldContain "erp"
                r.containsKey("dependencies") shouldBe true
                r.containsKey("cycles") shouldBe true
            }
        }
    })

private fun copyRecursively(
    source: Path,
    target: Path,
) {
    Files.walkFileTree(
        source,
        object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(
                dir: Path,
                attrs: BasicFileAttributes,
            ): FileVisitResult {
                Files.createDirectories(target.resolve(source.relativize(dir)))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(
                file: Path,
                attrs: BasicFileAttributes,
            ): FileVisitResult {
                file.copyTo(target.resolve(source.relativize(file)))
                return FileVisitResult.CONTINUE
            }
        },
    )
}
