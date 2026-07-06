package org.tatrman.ttrp.expr

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.parser.TtrpAstDump
import org.tatrman.ttrp.parser.TtrpParser
import java.nio.file.Files
import java.nio.file.Path

/**
 * Every `golden.exprs` line parses to the one PL expression IR and matches its
 * committed snapshot (byte-for-byte). Regenerate with `-DupdateSnapshots=true`
 * (writes `expr/snapshots/golden-exprs.json`). Non-`==` lines parse with zero errors.
 */
class TtrpExpressionParseSpec :
    StringSpec({
        val update = System.getProperty("updateSnapshots") == "true"

        "golden.exprs each parse to the IR with no syntax errors" {
            for ((source, _) in ExprFixtures.goldenExprs()) {
                val parsed = TtrpParser.parseExpression(source)
                parsed.diagnostics.filter { it.severity == Severity.ERROR } shouldBe emptyList()
            }
        }

        "golden.exprs IR matches the committed snapshot" {
            val dump =
                ExprFixtures.goldenExprs().joinToString("\n") { (source, _) ->
                    val expr = TtrpParser.parseExpression(source).expression
                    "$source\n${TtrpAstDump.dumpExpression(expr)}"
                }
            val snapPath = "expr/snapshots/golden-exprs.json"
            if (update) {
                val out = Path.of("src/test/resources/$snapPath")
                Files.createDirectories(out.parent)
                Files.writeString(out, dump)
            } else {
                val committed =
                    ExprFixtures::class.java
                        .getResourceAsStream("/$snapPath")
                        ?.readBytes()
                        ?.decodeToString()
                        ?: error("missing snapshot $snapPath — run with -DupdateSnapshots=true")
                dump shouldBe committed
            }
        }
    })
