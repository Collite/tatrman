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

        "bash declares no F-4 capabilities; tatrman declares all three" {
            bash.executorCapability().params shouldBe false
            bash.executorCapability().onFailure shouldBe false
            bash.executorCapability().retries shouldBe false
            tatrman.executorCapability().params shouldBe true
            tatrman.executorCapability().onFailure shouldBe true
            tatrman.executorCapability().retries shouldBe true
        }
    })
