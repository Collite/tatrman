// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.airflow3

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.tatrman.ttrp.emit.spi.EmitIsland
import org.tatrman.ttrp.emit.spi.EmitRequest
import org.tatrman.ttrp.emit.spi.EmitTransfer
import org.tatrman.ttrp.emit.spi.OrchestrationGraph
import org.tatrman.ttrp.emit.spi.ProgramMeta
import org.tatrman.ttrp.emit.spi.ResolvedManifest
import org.tatrman.ttrp.emit.spi.TtrEmitPlugin

/**
 * PL-P5.S4.T2 — the NATIVE (standalone, E-3-β) Airflow 3 DAG. One task per island, wave order as task
 * dependencies, F-lite invocation bindings (psql → BashOperator, polars/transfer → PythonOperator), connections
 * from the user's own Airflow Connections — and, the H-8 credential-bounded line, **no platform reference
 * anywhere** in the file (asserted textually).
 */
class Airflow3NativeTest :
    FunSpec({
        // waves [[a,b],[move],[c]] — a/b psql, c polars, `move` a transfer between waves.
        val graph =
            OrchestrationGraph(
                waves = listOf(listOf("a", "b"), listOf("move"), listOf("c")),
                islands =
                    listOf(
                        EmitIsland("a", "erp_pg", "psql", "islands/a.sql"),
                        EmitIsland("b", "erp_pg", "psql", "islands/b.sql"),
                        EmitIsland("c", "polars", "python3", "islands/c.py"),
                    ),
                transfers = listOf(EmitTransfer("b", "c", "staging", "transfers/move.py")),
                connections = listOf("TTR_CONN_ERP_PG"),
                displays = emptyList(),
                connectionByIsland = mapOf("a" to "TTR_CONN_ERP_PG", "b" to "TTR_CONN_ERP_PG"),
            )

        // No executor instance ⇒ the standalone default = native.
        fun request(g: OrchestrationGraph = graph) =
            EmitRequest(
                program = ProgramMeta("hero.ttrp", "acme.worlds.dev", "org.tatrman:ttrp:1.0.0"),
                graph = g,
                islandPayloads = emptyList(),
                transferPayloads = emptyList(),
                executorType = ResolvedManifest(""),
                executorInstance = ResolvedManifest(""),
                manifestJson = "{}",
            )

        val plugin = Airflow3EmitPlugin()
        val dag = String(plugin.emit(request()).files.getValue("dag.py"))

        test("emits exactly one file: dag.py") {
            plugin
                .emit(request())
                .files.keys
                .toList() shouldBe listOf("dag.py")
        }

        test("targetId + spiVersion") {
            plugin.targetId shouldBe "airflow3"
            plugin.spiVersion shouldBe TtrEmitPlugin.SPI_VERSION
        }

        test("Airflow 3 imports + fixed start_date (no timestamp), native dag id") {
            dag shouldContain "from airflow.sdk import DAG"
            dag shouldContain "from airflow.providers.standard.operators.bash import BashOperator"
            dag shouldContain "from airflow.providers.standard.operators.python import PythonOperator"
            dag shouldContain "start_date=datetime(2024, 1, 1)"
            dag shouldContain "dag_id=\"hero\""
            dag shouldContain "\"native\""
        }

        test("psql island → BashOperator with the verbatim F-lite ON_ERROR_STOP invocation") {
            dag shouldContain "a = BashOperator("
            dag shouldContain
                "bash_command='psql \"\$TTR_CONN_ERP_PG\" -v ON_ERROR_STOP=1 --no-psqlrc -f islands/a.sql'"
        }

        test("polars island + transfer → PythonOperator running the island script") {
            dag shouldContain "c = PythonOperator("
            dag shouldContain "op_args=[\"islands/c.py\"]"
            dag shouldContain "move = PythonOperator("
            dag shouldContain "op_args=[\"transfers/move.py\"]"
        }

        test("wave order becomes task dependencies (pairwise edges across adjacent waves)") {
            dag shouldContain "a >> move"
            dag shouldContain "b >> move"
            dag shouldContain "move >> c"
        }

        test("connections come from the user's Airflow Connections — no platform reference (H-8)") {
            dag shouldContain "\"TTR_CONN_ERP_PG\": \"{{ conn.ttr_conn_erp_pg.get_uri() }}\""
            // The credential-bounded line: nothing platform-side in a native DAG.
            dag shouldNotContain "://"
            dag.lowercase() shouldNotContain "door"
            dag shouldNotContain "/v1/runs"
            dag shouldNotContain "BaseHook"
        }

        test("determinism: same request ⇒ byte-identical dag.py (H-6 obligation)") {
            String(plugin.emit(request()).files.getValue("dag.py")) shouldBe dag
        }

        test("the generated DAG is valid Python (py_compile, offline)") {
            AirflowPy.assertCompiles(dag)
        }
    })
