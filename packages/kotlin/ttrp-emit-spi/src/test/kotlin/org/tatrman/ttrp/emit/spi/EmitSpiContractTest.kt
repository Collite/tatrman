// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.spi

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.TreeMap

/**
 * PL-P5.S1.T1 — the §8 emit-SPI surface, pinned. The request carries the graph + VERBATIM sha256-pinned
 * payloads + resolved type/instance manifests + finished manifestJson; the result's files are a SortedMap
 * (deterministic iteration by construction); a plugin returning a core-owned path is a structured error.
 */
class EmitSpiContractTest :
    StringSpec({

        fun request(): EmitRequest {
            val islandBytes = "select 1;\n".toByteArray()
            return EmitRequest(
                program =
                    ProgramMeta(
                        name = "hero.ttrp",
                        qname = "acme.worlds.dev",
                        toolchain = "org.tatrman:ttrp:0.0.0",
                    ),
                graph =
                    OrchestrationGraph(
                        waves = listOf(listOf("crunch")),
                        islands =
                            listOf(
                                EmitIsland(
                                    name = "crunch",
                                    engine = "erp_pg",
                                    invocation = "psql",
                                    file = "islands/crunch.sql",
                                ),
                            ),
                        transfers = emptyList(),
                        connections = listOf("TTR_CONN_ERP_PG"),
                        displays = emptyList(),
                        connectionByIsland = mapOf("crunch" to "TTR_CONN_ERP_PG"),
                    ),
                islandPayloads =
                    listOf(
                        IslandPayload(
                            name = "crunch",
                            relPath = "islands/crunch.sql",
                            bytes = islandBytes,
                            sha256 = "sha256:deadbeef",
                        ),
                    ),
                transferPayloads = emptyList(),
                executorType = ResolvedManifest("def executor bash { type: bash }\n"),
                executorInstance = ResolvedManifest(""),
                manifestJson = """{"program":"hero.ttrp"}""",
            )
        }

        "EmitRequest carries graph + verbatim sha256-pinned payloads + both manifests + manifestJson" {
            val r = request()
            r.graph.waves shouldBe listOf(listOf("crunch"))
            // island payloads are handed in finished — bytes + the sha256 the core recorded.
            val p = r.islandPayloads.single()
            p.relPath shouldBe "islands/crunch.sql"
            String(p.bytes) shouldBe "select 1;\n"
            p.sha256 shouldBe "sha256:deadbeef"
            r.executorType.text shouldContain "executor bash"
            r.manifestJson shouldContain "hero.ttrp"
        }

        "EmitResult.files is a SortedMap — iteration order is deterministic by construction" {
            // Insert out of order; iteration must still be sorted.
            val files = TreeMap<String, ByteArray>()
            files["run.sh"] = ByteArray(0)
            files["aux/z.txt"] = ByteArray(0)
            files["aux/a.txt"] = ByteArray(0)
            EmitResult(files).files.keys.toList() shouldBe listOf("aux/a.txt", "aux/z.txt", "run.sh")
        }

        "a plugin returning a core-owned path is a structured error" {
            for (bad in listOf(
                "manifest.json",
                "islands/crunch.sql",
                "transfers/x.py",
                "schemas/y.json",
                "hero.compile-record.json",
            )) {
                val files = TreeMap<String, ByteArray>()
                files["run.sh"] = ByteArray(0)
                files[bad] = ByteArray(0)
                val ex = shouldThrow<EmitContractException> { CoreOwnedPaths.check(EmitResult(files)) }
                ex.message!! shouldContain bad
            }
            // the launcher itself is plugin territory — never a violation.
            val ok = TreeMap<String, ByteArray>()
            ok["run.sh"] = ByteArray(0)
            CoreOwnedPaths.check(EmitResult(ok)) // does not throw
        }

        "the fake plugin is a pure function of the request (determinism-kit ready)" {
            val plugin = RecordingFakePlugin()
            plugin.spiVersion shouldBe TtrEmitPlugin.SPI_VERSION
            val a = plugin.emit(request()).files
            val b = plugin.emit(request()).files
            a.keys shouldBe b.keys
            a.keys.forEach { String(a.getValue(it)) shouldBe String(b.getValue(it)) }
            plugin.lastRequest?.program?.name shouldBe "hero.ttrp"
        }
    })
