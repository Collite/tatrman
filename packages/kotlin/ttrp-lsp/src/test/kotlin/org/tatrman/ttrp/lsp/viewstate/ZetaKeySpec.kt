// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp.viewstate

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe

/**
 * ζ key derivation from the authored hero graph (T5.2.3): container-qualified SSA keys,
 * the orchestration canvas keyed by container names + program leaves, and per-name chain
 * lengths (the orphaning discriminator).
 */
class ZetaKeySpec :
    StringSpec({

        val graph = ViewStateFixtures.heroGraph()

        "canvasKeys has the program canvas plus one per container" {
            val canvases = ZetaKeys.canvasKeys(graph)
            canvases shouldContainKey ZetaKeys.PROGRAM_CANVAS
            canvases shouldContainKey "acc_prep"
            canvases shouldContainKey "crunch"
        }

        "the program canvas lists the containers as ζ keys" {
            val program = ZetaKeys.canvasKeys(graph).getValue(ZetaKeys.PROGRAM_CANVAS)
            program.keys shouldContain "acc_prep"
            program.keys shouldContain "crunch"
        }

        "a reassigned variable is a container-qualified SSA key (crunch/sales#2)" {
            val crunch = ZetaKeys.canvasKeys(graph).getValue("crunch")
            crunch.keys shouldContain "crunch/sales#1"
            crunch.keys shouldContain "crunch/sales#2"
        }

        "chain length of a reassigned name is its SSA count" {
            val crunch = ZetaKeys.canvasKeys(graph).getValue("crunch")
            val chains = ZetaKeys.chainLengths(crunch.keys)
            chains["crunch/sales"] shouldBe 2
        }
    })
