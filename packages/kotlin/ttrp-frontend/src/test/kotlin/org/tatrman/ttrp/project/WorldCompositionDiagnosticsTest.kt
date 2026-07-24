// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.project

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.metadata.world.CompositionResult
import org.tatrman.ttr.metadata.world.Contradiction

/** PL-P1.S4 — the composition contradiction (ttr-metadata, id-free) surfaces as TTRP-LCK-004 (closes S2.T6). */
class WorldCompositionDiagnosticsTest :
    FunSpec({
        test("a K composition contradiction maps to TTRP-LCK-004 naming the member + field") {
            val contradiction =
                Contradiction(
                    member = QualifiedName(SchemaCode.WORLD, "dev", "pg", "acme"),
                    field = "type",
                    platformValue = "postgres-16",
                    projectValue = "mysql-8",
                )
            val d = WorldCompositionDiagnostics.lck004(contradiction)
            d.id.id shouldBe "TTRP-LCK-004"
            d.message shouldContain "pg"
            d.message shouldContain "postgres-16"
            d.message shouldContain "mysql-8"
        }

        test("all conflicts in a failed composition become LCK-004 diagnostics") {
            val result =
                CompositionResult.Contradiction(
                    listOf(
                        Contradiction(
                            QualifiedName(SchemaCode.WORLD, "dev", "pg", "acme"),
                            "type",
                            "postgres-16",
                            "mysql-8",
                        ),
                        Contradiction(
                            QualifiedName(SchemaCode.WORLD, "dev", "warehouse", "acme"),
                            "version",
                            "1.0",
                            "2.0",
                        ),
                    ),
                )
            val ds = WorldCompositionDiagnostics.lck004(result)
            ds.map { it.id.id }.toSet() shouldBe setOf("TTRP-LCK-004")
            ds.size shouldBe 2
        }
    })
