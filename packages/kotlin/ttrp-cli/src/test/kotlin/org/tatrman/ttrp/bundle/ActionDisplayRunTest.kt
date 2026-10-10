// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.project.TtrpManifestReader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Display fixture programs built for the bash-launched `shop.worlds.local` world and RUN: the Polars island
 * script executes under `python3` against the fixture's `files/orders.csv` (provisioned into the bundle), and
 * the Arrow files it writes are read back — what the engine produced, not the script text.
 *
 * Needs `python3` with `polars` + `pyarrow` (the bash executor's package list; CI's ttrp-conform job installs
 * them). Without them each test prints a SKIP line and passes — the offline bundle tests stay the gate there.
 */
class ActionDisplayRunTest :
    FunSpec({
        val project = ActionDisplayBundleTest.locate("packages/kotlin/ttrp-frontend/src/test/resources/display")
        val manifest = TtrpManifestReader.resolve(project).manifest

        fun build(
            program: String,
            source: String = Files.readString(project.resolve("programs/$program")),
            target: String? = null,
        ) = BundleAssembler("1.0.0").build(
            source = source,
            fileName = program,
            pipelineManifest = manifest,
            modelsRoot = manifest.modelsRoot(),
            outDir = Files.createTempDirectory("ttrp-action-run"),
            targetOverrides = target?.let { mapOf("review" to it) } ?: emptyMap(),
        )

        fun skip(): Boolean {
            val why = IslandRun.polarsMissing() ?: return false
            System.err.println("SKIP: $why")
            return true
        }

        fun runIsland(r: BundleAssembler.BundleResult): Path {
            val island = r.manifest.islands.single()
            val run = IslandRun.run(r.dir, island.file)
            withClue("${island.file}\n${Files.readString(r.dir.resolve(island.file))}\n---\n${run.output}") {
                run.exitCode shouldBe 0
            }
            return r.dir
        }

        test("a Polars CSV load reads a decimal field at a declared precision/scale — the island runs") {
            if (skip()) return@test
            val r = build("notify_local.ttrp")
            val py =
                Files.readString(
                    r.dir.resolve(
                        r.manifest.islands
                            .single()
                            .file,
                    ),
                )
            py shouldContain "\"amount\": pl.Decimal(19, 2)"
            val dir = runIsland(r)
            val late = IslandRun.read(dir, listOf("out/notify.arrow")).getValue("out/notify.arrow")
            late.fields.first { it.first == "amount" }.second shouldBe "decimal128(19, 2)"
            late.rows.map { it["amount"] } shouldBe listOf("120.50", "1500.25")
        }

        // `… -> late` inside the body writes the chain's value to the OUT port `late`. The graph used to leave the
        // chain dangling and map the port to the body's last assigned value — the island wrote the unfiltered head.
        val chainToPort =
            """
            uses world "shop.worlds.local"

            container review(out late) target pl {
                o = load(files.orders, schema: orders_csv)
                o -> filter(status = 1) -> select(order_id, email) -> late
            }
            review.late -> display(late)
            """.trimIndent() + "\n"

        test("chain-to-port, ordinary display: the Polars island writes the chain's filtered frame") {
            if (skip()) return@test
            val r = build("chain_port.ttrp", source = chainToPort)
            val out = IslandRun.read(runIsland(r), listOf("out/late.arrow")).getValue("out/late.arrow")
            out.fields.map { it.first } shouldBe listOf("order_id", "email")
            out.rows.map { it["order_id"] } shouldBe listOf("1", "3")
        }

        test("chain-to-port on Postgres and sql-text: the port statement is the chain's (filtered, selected)") {
            val pg = build("chain_port.ttrp", source = chainToPort, target = "pg")
            val py =
                Files.readString(
                    pg.dir.resolve(
                        pg.manifest.islands
                            .single()
                            .file,
                    ),
                )
            py shouldContain "WHERE"
            py shouldContain "\"status\" = 1"
            val host =
                build(
                    "chain_port_host.ttrp",
                    source =
                        """
                        uses world "shop.worlds.host"
                        import shop.orders.*

                        container review(out late) target erp {
                            o = load(orders)
                            o -> filter(status = 1) -> select(order_id, amount) -> late
                        }
                        review.late -> display(late)
                        """.trimIndent() + "\n",
                )
            val out =
                host.manifest.islands
                    .single()
                    .outputs!!
                    .single()
            out.columns.map { it.name } shouldBe listOf("order_id", "amount")
            Files.readString(host.dir.resolve(out.file)) shouldContain "WHERE"
        }
    })
