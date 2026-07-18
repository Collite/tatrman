// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.withClue
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * MD dot-path — TTR-M `publish: members` domain clause (contracts §1.4, the only Layer A change
 * in the arc). Grammar-level coverage on the Kotlin target: `def domain … { … publish: members }`
 * must **parse** (a domain body property), while a plain domain (no clause) stays legal — default
 * is "not published".
 *
 * The *typed* `publishMembers` flag now surfaces on both the TS `MdDomainDef` AST (mirror test in
 * `packages/parser/src/__tests__`) and the Kotlin `MdDomainDef` (added by the S1-0 MD-def port —
 * asserted in [org.tatrman.ttr.parser.md.MdDefParseSpec]). This spec deliberately stays at the
 * grammar level: it only asserts the clause *parses*, independent of the typed flag.
 *
 * TDD: **red** until S0-B adds `PUBLISH` + `publishProperty` to `mdDomainProperty` in TTR.g4.
 */
class PublishMembersParseSpec :
    StringSpec({
        val model =
            """
            model md
            def domain CustCode { type: string, publish: members }
            def domain Money { type: decimal }
            """.trimIndent() + "\n"

        "a domain with `publish: members` parses cleanly" {
            val r = TtrLoader.parseString(model, "publish-members.ttrm")
            withClue("parse errors: ${r.errors}") { r.ok shouldBe true }
        }

        // The typed `publishMembers` flag is exercised in MdDefParseSpec (Kotlin) and
        // md-publish-members.test.ts (TS); here we assert grammar-level parse success only.
        "the unpublished domain still parses (default: not published)" {
            val r = TtrLoader.parseString("model md\ndef domain Money { type: decimal }\n", "unpub.ttrm")
            withClue("parse errors: ${r.errors}") { r.ok shouldBe true }
        }
    })
