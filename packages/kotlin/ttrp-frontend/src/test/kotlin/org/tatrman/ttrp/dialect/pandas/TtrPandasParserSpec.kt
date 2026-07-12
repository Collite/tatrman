// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.pandas

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.FragmentBody

/** The accept corpus parses + decomposes with no TTR-pandas reject/syntax diagnostic. */
class TtrPandasParserSpec :
    StringSpec({
        val accept = listOf("hero-crunch-pandas.ttrp", "ssa-rebind.ttrp", "eq-synonym.ttrp", "roster-full.ttrp")

        for (f in accept) {
            "accept/$f parses and decomposes with no TTRP-PD diagnostic" {
                val result = PandasCorpus.parseResult("accept/$f")
                result.diagnostics.filter { it.id.id.startsWith("TTRP-PD-") }.map { it.id.id } shouldBe emptyList()
                result.document.statements
                    .filterIsInstance<ContainerDecl>()
                    .map { it.body }
                    .filterIsInstance<FragmentBody>()
                    .filter { it.tag == "pandas" }
                    .all { it.decomposition != null } shouldBe true
            }
        }
    })
