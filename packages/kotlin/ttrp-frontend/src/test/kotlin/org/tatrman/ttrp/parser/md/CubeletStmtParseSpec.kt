// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.parser.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.withClue
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.parser.TtrpParser

/**
 * MD cubelet statements — contracts §1.2 (`cubeletStmt`/`withClause`) + §11 R24 (D20–D24).
 *
 * Pins the *grammar only*: the four operators (`=`/`:=`/`+=`/`-=`), the optional `with` object,
 * an mdPath-LHS slice assignment, and free interleaving with ordinary TTR-P statements — while a
 * plain `x = a` variable assignment keeps parsing unchanged. Dispatch between variable/cubelet/
 * slice is semantic (R24), proven in S5C; here we only require a clean parse and the expected
 * statement-kind split.
 *
 * TDD: **red** until S0-B adds the `cubeletStmt` rule + the `CubeletStmt` AST node.
 */
class CubeletStmtParseSpec :
    StringSpec({
        val source =
            (
                CubeletStmtParseSpec::class.java.getResourceAsStream("/md/cubelet-stmts.ttrp")
                    ?: error("fixture not found: /md/cubelet-stmts.ttrp")
            ).readBytes()
                .decodeToString()

        val parsed = TtrpParser.parseString(source, "cubelet-stmts.ttrp")
        val kinds = parsed.document.statements.map { it::class.simpleName }

        "the cubelet-statement program parses with no syntax errors" {
            withClue("errors: ${parsed.diagnostics.filter { it.severity == Severity.ERROR }}") {
                parsed.diagnostics.filter { it.severity == Severity.ERROR } shouldBe emptyList()
            }
        }

        "all eight statements are recognised" {
            parsed.document.statements.size shouldBe 8
        }

        "the four new operators + the slice LHS surface as cubelet statements" {
            // Exactly six CubeletStmt nodes — this pins the statement-ordering boundary that is the
            // whole point of the design: `C = sales.2025.net` is NOT chain-viable (a chain can't
            // consume the numeric `.2025` component), so it falls to cubeletStmt too, alongside
            // `C :=`, `D := with`, `C +=`, `C -=`, and the `kaufland…net =` slice.
            kinds.count { it == "CubeletStmt" } shouldBe 6
        }

        "plain chain/variable assignments still parse as assignments" {
            // Exactly two Assignments: `x = source` and `y = other -> filter(...)`.
            kinds.count { it == "Assignment" } shouldBe 2
        }
    })
