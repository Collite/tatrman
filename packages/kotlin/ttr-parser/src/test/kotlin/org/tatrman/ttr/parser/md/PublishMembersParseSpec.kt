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
 * The *typed* `publishMembers` flag surfaces on the TS `MdDomainDef` AST (mirror test in
 * `packages/parser/src/__tests__`); the Kotlin `ttr-parser` does not model MD defs typed yet
 * (that is the S1 `MdModel` port), so here we assert only that the grammar accepts the clause.
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

        // Note (S1 seam): the Kotlin ttr-parser parses `def domain` syntactically but emits no
        // typed Definition for MD defs yet (the MdModel port is S1), so this asserts parse-level
        // success only — the typed `publishMembers` flag is exercised TS-side (md-publish-members.test.ts).
        "the unpublished domain still parses (default: not published)" {
            val r = TtrLoader.parseString("model md\ndef domain Money { type: decimal }\n", "unpub.ttrm")
            withClue("parse errors: ${r.errors}") { r.ok shouldBe true }
        }
    })
