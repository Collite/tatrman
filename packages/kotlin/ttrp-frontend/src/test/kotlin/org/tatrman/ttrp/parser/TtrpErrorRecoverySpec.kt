package org.tatrman.ttrp.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/**
 * Error-recovery baseline (Stage 1.1): ANTLR's default strategy stays on; a bad
 * statement must not swallow the rest of the document. Syntax errors surface as
 * TTRP-PRS-001 with accurate locations.
 */
class TtrpErrorRecoverySpec :
    StringSpec({
        "two independent syntax errors are both reported" {
            // Broken chain on line 2, broken container header on line 6.
            val src =
                buildString {
                    appendLine("a = load(files.x)")
                    appendLine("b = -> filter(amount > 0)") // line 2: chain starts with ->
                    appendLine("c = load(files.y)")
                    appendLine("c -> display")
                    appendLine("")
                    appendLine("container = target pg { }") // line 6: missing name
                }
            val diags = TtrpParser.parseString(src).diagnostics.filter { it.severity == Severity.ERROR }
            diags.size shouldBeGreaterThanOrEqual 2
            val lines = diags.map { it.location.line }.toSet()
            (2 in lines) shouldBe true
            (6 in lines) shouldBe true
        }

        "statements after a syntax error still parse" {
            val src = "%%% broken line\n\na = load(files.x)\n"
            val result = TtrpParser.parseString(src)
            result.diagnostics.count { it.severity == Severity.ERROR } shouldBeGreaterThanOrEqual 1
            result.document.statements
                .filterIsInstance<Assignment>()
                .map { it.target } shouldContain "a"
        }

        "an unterminated tagged block reports an error at the opening fence line" {
            val src = "container c target pg \"\"\"sql\nselect 1 from t\n" // no closing fence
            val result = TtrpParser.parseString(src)
            val errors = result.diagnostics.filter { it.severity == Severity.ERROR }
            errors.map { it.id } shouldContain TtrpDiagnosticId.PRS_001
            // some diagnostic lands on the fence line (line 1).
            (errors.any { it.location.line == 1 }) shouldBe true
        }
    })
