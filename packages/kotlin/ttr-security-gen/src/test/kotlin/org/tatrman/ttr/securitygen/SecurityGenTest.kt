// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.securitygen

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.tatrman.ttr.parser.loader.TtrLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * PL-P4.S3.T3 — the generator's golden + property contract. A fixture `security`
 * block → Rego fragments + `data.json`, pinned against committed goldens under
 * `src/test/golden/`, plus the five load-bearing properties (contracts §11).
 *
 * Regenerate goldens after an intended change: `./gradlew :packages:kotlin:ttr-security-gen:test
 * -DupdateGolden=true` (writes goldens, then fails to force review).
 */
class SecurityGenTest :
    StringSpec({

        // The canonical fixture: every verb once, across a table-level object and a
        // column-level object (dotted qname).
        val model =
            """
            model db schema dbo
            def table sales { columns: [ def column region { type: text }, def column amount { type: decimal } ] }
            def table order_line { columns: [ def column customer_email { type: text } ] }
            security {
                own      sales: team_sales
                classify order_line.customer_email: pii
                grant    read on sales to accounting
                grant    export on sales to finance
                mask     order_line.customer_email
            }
            """.trimIndent()

        fun generate(): GeneratedPolicy {
            val parsed = TtrLoader.parseString(model)
            parsed.ok shouldBe true
            return SecurityGen.generate(parsed.securityBlocks)
        }

        // ---- goldens (byte-for-byte) ----

        "emitted files match the committed goldens" {
            val policy = generate()
            policy.files().forEach { (name, content) -> assertGolden(name, content) }
            // Exactly the expected file set: one rego per object-with-allow-rules + data.json.
            policy.files().keys shouldContainExactly listOf("data.json", "sales.rego").sorted()
        }

        // ---- the five pinned properties (contracts §11) ----

        "1 · every rego fragment declares package tatrman.generated.<sanitized-qname>" {
            val policy = generate()
            policy.regoFiles.keys shouldContainExactly listOf("sales.rego")
            policy.regoFiles.getValue("sales.rego") shouldContain "package tatrman.generated.sales"
        }

        "2 · every generated file carries the '# GENERATED — do not edit' header" {
            generate().files().forEach { (name, content) ->
                withClue(name) { content shouldContain GENERATED_MARKER }
            }
            // rego fragments carry it as a leading `#` comment.
            generate().regoFiles.values.forEach { it shouldStartWith "# $GENERATED_MARKER" }
        }

        "3 · grants only grant — no generated fragment can contain a deny (deny-overrides precondition)" {
            generate().regoFiles.values.forEach { rego ->
                // Check the RULE body (comment lines carry the "deny-overrides" prose).
                val body = rego.lines().filterNot { it.trimStart().startsWith("#") }.joinToString("\n")
                body shouldContain "allow if"
                body shouldNotContain "deny"
                body shouldNotContain "not allow"
            }
        }

        "4 · classifications land as data; grant roles are referenced verbatim in Rego (HQ-1)" {
            val policy = generate()
            // classification is DATA, never a Rego rule.
            policy.dataJson shouldContain "\"classification\": \"pii\""
            policy.regoFiles.values.forEach { it shouldNotContain "pii" }
            // grant role tokens copied verbatim into the fragment (no classification→role baking).
            val sales = policy.regoFiles.getValue("sales.rego")
            sales shouldContain "\"accounting\""
            sales shouldContain "\"finance\""
            // owner + mask also land as data.
            policy.dataJson shouldContain "\"owner\": \"team_sales\""
            policy.dataJson shouldContain "\"masked\": true"
        }

        "5 · same block ⇒ same bytes (determinism replay)" {
            SecurityGen.generate(TtrLoader.parseString(model).securityBlocks).files() shouldBe
                SecurityGen.generate(TtrLoader.parseString(model).securityBlocks).files()
        }

        // ---- fail-closed: a mangle collision is a hard error, never a silent merge ----

        "a qname collision (two refs → one sanitized token) fails closed" {
            val collide =
                """
                model db schema dbo
                def table a_b { columns: [ def column x { type: text } ] }
                security { own a.b: r1  own a_b: r2 }
                """.trimIndent()
            val ex =
                runCatching { SecurityGen.generate(TtrLoader.parseString(collide).securityBlocks) }
                    .exceptionOrNull()
            (ex is IllegalStateException) shouldBe true
            ex!!.message!! shouldContain "collision"
        }
    })

private fun withClue(
    clue: String,
    block: () -> Unit,
) = io.kotest.assertions.withClue(clue, block)

// ---- golden helper (the ttr-import-schema GoldenSupport idiom) ----

private val goldenDir: Path = Path.of("src/test/golden/canonical")

private fun assertGolden(
    name: String,
    actual: String,
) {
    val path = goldenDir.resolve(name)
    if (System.getProperty("updateGolden") == "true") {
        Files.createDirectories(path.parent)
        Files.writeString(path, actual)
        return // write ALL files; inspect + commit, then re-run to verify byte-for-byte
    }
    check(Files.exists(path)) { "missing golden: $path — run with -DupdateGolden=true to create it" }
    actual shouldBe path.readText()
}
