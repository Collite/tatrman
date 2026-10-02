// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.capability

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.ParamDecl
import org.tatrman.ttrp.ast.ParamDefault
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.graph.model.Container
import org.tatrman.ttrp.graph.model.Store
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * PL-P2.S1 (T6, contracts §7): the executor-capability gate. A program using the F-4 vocabulary
 * (`param`s / `on failure of` / `retries`) compiles against the `tatrman` platform executor and is
 * an ordinary capability error against the `bash` (F-lite) executor — "P3 made executable".
 */
class ExecutorManifestGateSpec :
    StringSpec({
        val loc = SourceLocation.UNKNOWN
        val bash = ClasspathManifestSource().load("bash")!!
        val tatrman = ClasspathManifestSource().load("tatrman")!!

        // A program with a param + an on-failure island that also declares retries.
        val doc =
            TtrpDocument(
                listOf(ParamDecl("run_date", "date", loc, ParamDefault.Builtin("run-date", loc), loc)),
                loc,
            )
        val salvage =
            Container(
                id = "c0",
                label = "salvage",
                location = loc,
                target = "polars",
                memberIds = emptyList(),
                declaredPorts = emptyList(),
                portMapping = emptyMap(),
                onFailureOf = "extract",
                retries = 2,
            )
        val graph = TtrpGraph(emptyMap(), emptyList(), mapOf("c0" to salvage))

        "the bash executor rejects params, on-failure, and retries (one CAP-2xx each)" {
            ExecutorManifestGate.check(doc, graph, listOf(bash)).map { it.id.id } shouldContainExactly
                listOf("TTRP-CAP-201", "TTRP-CAP-202", "TTRP-CAP-203")
        }

        "the tatrman platform executor accepts the whole F-4 vocabulary" {
            ExecutorManifestGate.check(doc, graph, listOf(tatrman)) shouldBe emptyList()
        }

        // ---- AG-P0 S1: the read-only `aip` executor (ai-platform rule engine, AG C-12) ----
        val aip = ClasspathManifestSource().load("aip")!!

        "the aip executor accepts params but rejects on-failure and retries (CAP-202, CAP-203)" {
            ExecutorManifestGate.check(doc, graph, listOf(aip)).map { it.id.id } shouldContainExactly
                listOf("TTRP-CAP-202", "TTRP-CAP-203")
        }

        "a `store` under the aip executor is TTRP-CAP-204; under tatrman it is fine" {
            val store = Store("s0", "store", loc, target = "files.out")
            val g = TtrpGraph(mapOf("s0" to store), emptyList(), emptyMap())
            val noParams = TtrpDocument(emptyList(), loc)
            ExecutorManifestGate.check(noParams, g, listOf(aip)).map { it.id.id } shouldContainExactly
                listOf("TTRP-CAP-204")
            ExecutorManifestGate.check(noParams, g, listOf(tatrman)) shouldBe emptyList()
        }

        "a movement-synthesized `~store` is not an authored write (no CAP-204)" {
            val staged = Store("x0~store", "c__stage", loc, target = "accounts")
            val g = TtrpGraph(mapOf("x0~store" to staged), emptyList(), emptyMap())
            ExecutorManifestGate.check(TtrpDocument(emptyList(), loc), g, listOf(aip)) shouldBe emptyList()
        }

        "bash declares no F-4 capabilities; tatrman declares all three" {
            bash.executorCapability().params shouldBe false
            bash.executorCapability().onFailure shouldBe false
            bash.executorCapability().retries shouldBe false
            tatrman.executorCapability().params shouldBe true
            tatrman.executorCapability().onFailure shouldBe true
            tatrman.executorCapability().retries shouldBe true
        }
    })
