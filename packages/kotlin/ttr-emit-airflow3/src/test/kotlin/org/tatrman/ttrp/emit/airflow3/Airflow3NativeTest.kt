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

        test("SSA-labelled islands (~n, name#k) emit Airflow-legal, unique task ids — task_id validity") {
            // An anonymous (`~0`) and an SSA-versioned (`a#2`) island, plus a real `a_2` island that collides
            // with a#2's fold. Airflow's task-id key regex forbids `~` and `#`, so the raw labels would make the
            // DAG fail to load; the ids must fold and de-collide deterministically.
            val ssa =
                OrchestrationGraph(
                    waves = listOf(listOf("~0", "a#2"), listOf("a_2")),
                    islands =
                        listOf(
                            EmitIsland("~0", "erp_pg", "psql", "islands/~0.sql"),
                            EmitIsland("a#2", "erp_pg", "psql", "islands/a#2.sql"),
                            EmitIsland("a_2", "polars", "python3", "islands/a_2.py"),
                        ),
                    transfers = emptyList(),
                    connections = listOf("TTR_CONN_ERP_PG"),
                    displays = emptyList(),
                    connectionByIsland = mapOf("~0" to "TTR_CONN_ERP_PG", "a#2" to "TTR_CONN_ERP_PG"),
                )
            val out = String(plugin.emit(request(ssa)).files.getValue("dag.py"))

            // No task_id may contain a `~` or `#` — Airflow rejects such a key at DAG parse.
            Regex("task_id=\"[^\"]*[~#][^\"]*\"").containsMatchIn(out) shouldBe false
            // ~0 → _0 ; a#2 → a_2 ; a_2 collides with a#2's fold → de-collided to a_2_2 (deterministic, wave order).
            out shouldContain "task_id=\"_0\""
            out shouldContain "task_id=\"a_2\""
            out shouldContain "task_id=\"a_2_2\""
            // task_id == operator variable, so the wave edges bind to the real operators.
            out shouldContain "_0 >> a_2_2"
            out shouldContain "a_2 >> a_2_2"
            // The psql -f path still points at the real on-disk island file (raw SSA name).
            out shouldContain "-f islands/~0.sql"
            // …and the whole DAG is still valid Python.
            AirflowPy.assertCompiles(out)
        }
    })
