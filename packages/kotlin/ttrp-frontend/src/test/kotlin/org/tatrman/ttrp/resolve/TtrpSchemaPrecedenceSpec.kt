// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * Declared-schema handling (T1.3.6, D-c): precedence inline > program > world,
 * same-level conflict = error, S23 type vocabulary. The hero's expressions now
 * typecheck against RESOLVED schemas — no hand-fed maps.
 */
class TtrpSchemaPrecedenceSpec :
    StringSpec({

        fun ids(source: String) =
            ResolutionFixtures
                .checker()
                .check(source, "sch.ttrp")
                .errors
                .map { it.id.id }

        fun body(
            programSchema: String,
            loadArgs: String,
            pred: String,
        ) = programSchema +
            "container c target polars {\n" +
            "    s = load($loadArgs)\n" +
            "    s = filter(s, $pred)\n" +
            "}\n"

        "world tier: a world-declared schema column is in scope" {
            ids(body("", "files.sales_2026, schema: sales_csv", "region is not null")) shouldBe emptyList()
        }

        "program beats world: the program def schema wins over the same-named world schema" {
            val prog = "def schema sales_csv { customer: string, flag: boolean }\n"
            // `flag` is program-only → in scope; `region` is world-only → now out of scope (program won).
            ids(body(prog, "files.sales_2026, schema: sales_csv", "flag is not null")) shouldBe emptyList()
            ids(body(prog, "files.sales_2026, schema: sales_csv", "region is not null")) shouldContain "TTRP-EXP-001"
        }

        "inline beats program: an inline schema literal wins over a program def schema" {
            val prog = "def schema sales_csv { customer: string, flag: boolean }\n"
            ids(body(prog, "files.raw, schema: { customer: string, tag: string }", "tag is not null")) shouldBe
                emptyList()
            ids(body(prog, "files.raw, schema: { customer: string, tag: string }", "flag is not null")) shouldContain
                "TTRP-EXP-001"
        }

        "same-level conflict: two program def schema with one name is TTRP-SCH-001" {
            ids("def schema a { x: integer }\ndef schema a { y: string }\ncontainer c target polars { }") shouldContain
                "TTRP-SCH-001"
        }

        "ad-hoc load of a schema-less storage with no schema is TTRP-SCH-002" {
            ids("container c target polars { s = load(stage.adhoc_csv) }") shouldContain "TTRP-SCH-002"
        }

        "an S23-invalid declared type is TTRP-SCH-003" {
            ids("def schema a { amount: money }\ncontainer c target polars { }") shouldContain "TTRP-SCH-003"
        }

        "the hero's expressions typecheck against resolved schemas with no hand-fed maps" {
            val errs = ResolutionFixtures.checker().check(ResolutionFixtures.program("hero.ttrp"), "hero.ttrp").errors
            errs.map { it.id.id } shouldNotContain "TTRP-EXP-001"
            errs.map { it.id.id } shouldNotContain "TTRP-TYP-001"
        }
    })
