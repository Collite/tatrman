// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.airflow3

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.tatrman.ttrp.emit.spi.EmitIsland
import org.tatrman.ttrp.emit.spi.EmitRequest
import org.tatrman.ttrp.emit.spi.OrchestrationGraph
import org.tatrman.ttrp.emit.spi.ProgramMeta
import org.tatrman.ttrp.emit.spi.ResolvedManifest

/**
 * PL-P5.S4.T1 — the DOOR-CALLING (platform, E-3-α-1) Airflow 3 DAG. A single whole-program task obtains a
 * client-credentials token from the platform IdP token endpoint (from the connection's `token_url` — NOT
 * Airflow's own /auth/token), `POST`s the program door `/v1/runs`, polls `/v1/runs/{id}`, and cancels on kill.
 * Door base URL + IdP token URL + principal client-id all come from ONE Airflow Connection ref named by the
 * world instance entry — never inline (B-4).
 */
class Airflow3DoorCallingTest :
    FunSpec({
        val graph =
            OrchestrationGraph(
                waves = listOf(listOf("a")),
                islands = listOf(EmitIsland("a", "erp_pg", "psql", "islands/a.sql")),
                transfers = emptyList(),
                connections = listOf("TTR_CONN_ERP_PG"),
                displays = emptyList(),
                connectionByIsland = mapOf("a" to "TTR_CONN_ERP_PG"),
            )

        // A platform-world executor instance overlay: door-calling + the single Airflow Connection ref.
        val doorInstance =
            """
            def executor airflow3_prod {
                delegation: "door-calling"
                doorConnection: "airflow_conn_tatrman_door"
            }
            """.trimIndent()

        fun request(instance: String) =
            EmitRequest(
                program = ProgramMeta("hero.ttrp", "acme.worlds.dev", "org.tatrman:ttrp:1.0.0"),
                graph = graph,
                islandPayloads = emptyList(),
                transferPayloads = emptyList(),
                executorType = ResolvedManifest(""),
                executorInstance = ResolvedManifest(instance),
                manifestJson = "{}",
            )

        val plugin = Airflow3EmitPlugin()
        val dag = String(plugin.emit(request(doorInstance)).files.getValue("dag.py"))

        test("a single whole-program door task (no per-island tasks)") {
            dag shouldContain "class TtrDoorRunOperator(BaseOperator):"
            dag shouldContain "run = TtrDoorRunOperator("
            dag shouldContain "task_id=\"run_hero\""
            dag shouldContain "\"door-calling\""
            // The whole program is delegated — no per-island operators appear.
            dag shouldNotContain "BashOperator"
            dag shouldNotContain "islands/a.sql"
        }

        test("obtains a client-credentials token from the platform IdP token endpoint (not Airflow /auth/token)") {
            dag shouldContain "\"grant_type\": \"client_credentials\""
            dag shouldContain "extra[\"token_url\"]"
            dag shouldNotContain "/auth/token"
        }

        test("POSTs /v1/runs and polls /v1/runs/{id} to a terminal state") {
            dag shouldContain "/v1/runs"
            dag shouldContain "[\"runId\"]"
            dag shouldContain "[\"state\"]"
            dag shouldContain "\"succeeded\""
        }

        test("cancel propagates on task kill → POST /v1/runs/{id}/cancel") {
            dag shouldContain "def on_kill(self):"
            dag shouldContain "/v1/runs/\" + self._run_id + \"/cancel"
        }

        test("door URL + token URL + client-id all come from ONE Connection ref — never inline (B-4)") {
            dag shouldContain "conn_id=\"airflow_conn_tatrman_door\""
            dag shouldContain "BaseHook.get_connection(self.conn_id)"
            dag shouldContain "conn.host"
            dag shouldContain "conn.login"
            dag shouldContain "conn.password"
            // No door URL or secret material is written into the DAG file itself.
            dag shouldNotContain "://"
        }

        test("determinism: same request ⇒ byte-identical dag.py (H-6 obligation)") {
            String(plugin.emit(request(doorInstance)).files.getValue("dag.py")) shouldBe dag
        }

        test("the generated DAG is valid Python (py_compile, offline)") {
            AirflowPy.assertCompiles(dag)
        }

        test("a program name containing a placeholder token is not re-substituted (single-pass render)") {
            // dag id `p__CONN_ID__` embeds a template placeholder. An ordered `.replace` chain would let the
            // later `__CONN_ID__` pass rewrite it; the single-pass render must leave the inserted value intact.
            val evil =
                EmitRequest(
                    program = ProgramMeta("p__CONN_ID__.ttrp", "acme.worlds.dev", "org.tatrman:ttrp:1.0.0"),
                    graph = graph,
                    islandPayloads = emptyList(),
                    transferPayloads = emptyList(),
                    executorType = ResolvedManifest(""),
                    executorInstance = ResolvedManifest(doorInstance),
                    manifestJson = "{}",
                )
            val out = String(plugin.emit(evil).files.getValue("dag.py"))
            out shouldContain "dag_id=\"p__CONN_ID__\""
            out shouldContain "task_id=\"run_p__CONN_ID__\""
            // …and the real connection ref still lands in its own slot, uncorrupted.
            out shouldContain "conn_id=\"airflow_conn_tatrman_door\""
            AirflowPy.assertCompiles(out)
        }

        test("the door poll is bounded — a non-terminal run fails the task instead of hanging forever") {
            dag shouldContain "poll_timeout = 21600"
            dag shouldContain "time.monotonic() >= deadline"
            dag shouldContain "raise TimeoutError("
        }

        test("a platform world declaring door-calling but NO doorConnection is refused (P3-explicit)") {
            val bad =
                """
                def executor airflow3_prod {
                    delegation: "door-calling"
                }
                """.trimIndent()
            val ex = shouldThrow<IllegalStateException> { plugin.emit(request(bad)) }
            ex.message!! shouldContain "doorConnection"
        }
    })
