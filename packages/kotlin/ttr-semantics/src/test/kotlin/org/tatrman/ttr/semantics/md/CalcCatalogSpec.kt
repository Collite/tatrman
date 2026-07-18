// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * S1-B2 — the vendored MD calc catalog (D-h). Lookup by token + the cross-repo version drift guard.
 */
class CalcCatalogSpec :
    StringSpec({
        "the v1 time catalog has exactly 11 entries" {
            MdCalcCatalog.entries.size shouldBe 11
        }

        "lookup resolves calc tokens to their domain signatures" {
            val month = MdCalcCatalog.byName("monthOfDate")!!
            month.category shouldBe "extraction"
            month.input shouldBe CatalogShape.Time("instant|date")
            month.output shouldBe CatalogShape.Int(1, 12)
            month.cardinality shouldBe "N:1"

            MdCalcCatalog.byName("truncToMonth")!!.output shouldBe CatalogShape.Time("date")
            MdCalcCatalog.byName("quarterOfMonth")!!.input shouldBe CatalogShape.Int(1, 12)
        }

        "parameterised entries carry their enum param + default" {
            val week = MdCalcCatalog.byName("truncToWeek")!!
            week.params.single().name shouldBe "weekStart"
            week.params.single().values shouldBe listOf("mon", "sun")
            week.params.single().default shouldBe "mon"
        }

        "an unknown token resolves to null" {
            MdCalcCatalog.byName("notACalc").shouldBeNull()
        }

        // Drift guard (S1-B2): this MUST equal the TS `@tatrman/md-catalog` MD_CATALOG_VERSION.
        // If this fails, re-vendor the catalog from packages/md-catalog/src (see MdCalcCatalog KDoc).
        "MD_CATALOG_VERSION is locked to the TS source of truth" {
            MD_CATALOG_VERSION shouldBe "0.1.0"
        }
    })
