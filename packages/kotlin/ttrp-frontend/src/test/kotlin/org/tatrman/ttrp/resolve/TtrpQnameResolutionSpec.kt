// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain as stringShouldContain

/**
 * Qname/import resolution with position typing (T1.3.4, D-b). Each syntactic position
 * checks its kind; every miss names both the expected and the found kind.
 */
class TtrpQnameResolutionSpec :
    StringSpec({

        fun check(source: String) = ResolutionFixtures.checker().check(source, "q.ttrp")

        "the canonical hero resolves with zero ERROR diagnostics" {
            check(ResolutionFixtures.program("hero.ttrp")).errors.map { it.render() } shouldBe emptyList()
        }

        "target position: a storage where an engine is expected is TTRP-RES-003" {
            val r = check("container c target files { }")
            val d = r.errors.first { it.id.id == "TTRP-RES-003" }
            d.message stringShouldContain "storage"
        }

        "store position: an engine where a storage is expected is TTRP-MOV-001" {
            val src =
                "uses world \"acme.worlds.dev\"\n" +
                    "container c target polars {\n" +
                    "    s = load(files.sales_2026, schema: sales_csv)\n" +
                    "    s -> store(erp_pg)\n" +
                    "}\n"
            val d = check(src).errors.first { it.id.id == "TTRP-MOV-001" }
            d.message stringShouldContain "engine"
        }

        "a load of an unknown bare name is TTRP-RES-001" {
            check("container c target polars { s = load(no_such_thing) }").errors.map { it.id.id } shouldContain
                "TTRP-RES-001"
        }

        "a dangling wildcard import is TTRP-RES-006" {
            check("import erp.nosuch.*\ncontainer c target polars { }").errors.map { it.id.id } shouldContain
                "TTRP-RES-006"
        }
    })
