// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.FlowBody
import org.tatrman.ttrp.ast.FragmentBody
import org.tatrman.ttrp.ast.UsesWorld
import org.tatrman.ttrp.diagnostics.Severity
import java.nio.file.Files
import java.nio.file.Path

/**
 * Golden corpus: every fixture under `golden/` parses with zero ERROR diagnostics
 * AND matches its committed AST snapshot (byte-for-byte). Regenerate snapshots with
 * `./gradlew :packages:kotlin:ttrp-frontend:test -DupdateSnapshots=true`. Explicit
 * hero-inventory assertions guard against a "snapshot matches the wrong tree" blind
 * spot.
 */
class TtrpParserGoldenSpec :
    StringSpec({
        val fixtures =
            listOf(
                "hero.ttrp",
                "chains.ttrp",
                "ssa.ttrp",
                "containers.ttrp",
                "control.ttrp",
                "union-display.ttrp",
                "fragments.ttrp",
            )
        val update = System.getProperty("updateSnapshots") == "true"

        for (fixture in fixtures) {
            "golden/$fixture parses with zero ERROR diagnostics" {
                val result = TtrpParser.parseString(Fixtures.golden(fixture), fixture)
                result.diagnostics.filter { it.severity == Severity.ERROR } shouldBe emptyList()
            }

            "golden/$fixture matches its committed AST snapshot" {
                val doc = TtrpParser.parseString(Fixtures.golden(fixture), fixture).document
                val actual = TtrpAstDump.dump(doc)
                val snapName = fixture.removeSuffix(".ttrp") + ".json"
                if (update) {
                    val out = Path.of("src/test/resources/golden/snapshots/$snapName")
                    Files.createDirectories(out.parent)
                    Files.writeString(out, actual)
                } else {
                    val committed =
                        Fixtures::class.java
                            .getResourceAsStream("/golden/snapshots/$snapName")
                            ?.readBytes()
                            ?.decodeToString()
                            ?: error("missing snapshot $snapName — run with -DupdateSnapshots=true")
                    actual shouldBe committed
                }
            }
        }

        "hero statement inventory" {
            val doc = TtrpParser.parseString(Fixtures.golden("hero.ttrp"), "hero.ttrp").document

            doc.statements.filterIsInstance<UsesWorld>() shouldHaveSize 1

            val containers = doc.statements.filterIsInstance<ContainerDecl>()
            containers shouldHaveSize 2

            val accPrep = containers.single { it.name == "acc_prep" }
            (accPrep.body as FragmentBody).tag shouldBe "sql"
            // review-001 1.1-A: a TAGGED_BLOCK-bodied container's span must reach the
            // CLOSING fence line, not collapse onto the opening `"""sql` line. acc_prep
            // opens on source line 3 and its closing `"""` is on line 7.
            accPrep.location.line shouldBe 3
            accPrep.location.endLine shouldBe 7

            val crunch = containers.single { it.name == "crunch" }
            val crunchBody = crunch.body as FlowBody
            // sales, sales, j, sums, b, result, low, rejects — eight assignment statements.
            crunchBody.statements shouldHaveSize 8

            // 4 top-level wiring / chain statements after the two containers + uses world.
            val topLevelChains =
                doc.statements.count {
                    it is org.tatrman.ttrp.ast.ChainStmt
                }
            topLevelChains shouldBe 4
        }
    })
