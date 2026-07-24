// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.ParamDecl
import org.tatrman.ttrp.ast.ParamDefault
import org.tatrman.ttrp.diagnostics.Severity

/**
 * PL-P2.S1 (F-4-i runtime params, F-4-iv on-failure islands, F-4-ii retries). The
 * grammar/AST surface: params parse and type/default correctly; container on-failure
 * + retries attributes reach the AST; `on` stays usable as the `join(on: …)` arg name
 * (soft keyword); FF (`finishes with`) is still a compile error (CTL-001 regression).
 */
class TtrpParamsOnFailureSpec :
    StringSpec({
        fun errors(src: String) =
            TtrpParser.parseString(src, "s.ttrp").diagnostics.filter { it.severity == Severity.ERROR }

        "the golden params fixture parses with zero errors and yields three params + an on-failure island" {
            val doc = TtrpParser.parseString(Fixtures.golden("params.ttrp"), "params.ttrp").document

            val params = doc.statements.filterIsInstance<ParamDecl>()
            params.map { it.name } shouldContainExactly listOf("run_date", "region", "max_rows")

            val runDate = params.single { it.name == "run_date" }
            runDate.type shouldBe "date"
            runDate.required shouldBe false
            (runDate.default as ParamDefault.Builtin).name shouldBe "run-date"

            val region = params.single { it.name == "region" }
            (region.default as ParamDefault.Literal).text shouldBe "\"CZ\""

            val maxRows = params.single { it.name == "max_rows" }
            maxRows.required shouldBe true
            maxRows.default shouldBe null

            val containers = doc.statements.filterIsInstance<ContainerDecl>()
            containers.single { it.name == "extract" }.retries shouldBe 2

            val salvage = containers.single { it.name == "salvage" }
            salvage.onFailureOf shouldBe "extract"
            salvage.onFailureAbsorbs shouldBe false
            salvage.onFailureOfLocation.shouldNotBeNull()
        }

        "a required param (no default) parses" {
            errors("param cutoff: datetime\n") shouldBe emptyList()
        }

        "`on` remains usable as the join arg name (soft keyword)" {
            val src =
                """
                container c(in l, in r, out o) target polars {
                    o = join(left: l, right: r, on: l.k = r.k)
                }
                """.trimIndent()
            // The `on:` named arg must still parse — no syntax error introduced by the `on failure of` keyword.
            errors(src).map { it.id.id } shouldBe emptyList()
        }

        "`finishes with` (FF) is still a compile error — CTL-001 regression" {
            errors("a finishes with b\n").map { it.id.id } shouldBe listOf("TTRP-CTL-001")
        }
    })
