// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File

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

        // Drift guard (S1-B2): the vendored version MUST equal the TS `@tatrman/md-catalog`
        // source of truth — read the constant straight out of `md-catalog/src/index.ts` (both
        // packages live in this monorepo) rather than a hardcoded literal, so a TS bump fails
        // loudly here. If this fails, re-vendor the catalog from packages/md-catalog/src (see
        // MdCalcCatalog KDoc); the S1-B4 generation script will replace the hand-vendoring.
        "MD_CATALOG_VERSION is locked to the TS source of truth" {
            MD_CATALOG_VERSION shouldBe readTsCatalogVersion()
        }

        // Content drift guard (review-071 T-P4): the version string alone cannot catch entry-content
        // skew — a TS edit that renames an entry or rewrites a `semantics` string WITHOUT bumping the
        // version used to go green (five semantics strings had already drifted once). Cross-check the
        // (name → semantics) map straight out of the TS `catalog.ts` source so any such edit fails here.
        "the vendored (name → semantics) map matches the TS catalog.ts source" {
            MdCalcCatalog.entries.associate { it.name to it.semantics } shouldBe readTsCatalogSemantics()
        }
    })

/** Extract each entry's `name → semantics` from the TS `catalog.ts` (an entry has a `category:`; params do not). */
private fun readTsCatalogSemantics(): Map<String, String> {
    val catalog = locateRepoFile("packages/md-catalog/src/catalog.ts").readText()
    val entry = Regex("""name:\s*'([^']+)',\s*[\r\n]+\s*category:[\s\S]*?semantics:\s*'([^']+)'""")
    return entry.findAll(catalog).associate { it.groupValues[1] to it.groupValues[2] }
}

/** Read `MD_CATALOG_VERSION` out of the TS `@tatrman/md-catalog` source (the cross-repo sync key). */
private fun readTsCatalogVersion(): String {
    val index = locateRepoFile("packages/md-catalog/src/index.ts")
    val match =
        Regex("""MD_CATALOG_VERSION\s*=\s*['"]([^'"]+)['"]""").find(index.readText())
            ?: error("MD_CATALOG_VERSION not found in ${index.path}")
    return match.groupValues[1]
}

/** Walk up from the test working dir until [relative] is found under a repo ancestor. */
private fun locateRepoFile(relative: String): File {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
        val candidate = File(dir, relative)
        if (candidate.isFile) return candidate
        dir = dir.parentFile
    }
    error("could not locate $relative from ${System.getProperty("user.dir")}")
}
