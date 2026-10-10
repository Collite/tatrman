// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Rules written in Czech / English TTR-B sentences (AG B4) through the shipped commands: they
 * `ttrp check` clean, `ttrp explain` the same as the hand-written canonical program (modulo generated
 * names), and `ttrp build` a bundle whose host statements and action-display entries are IDENTICAL
 * to the canonical program's.
 */
class TtrbRulesCliTest :
    FunSpec({
        val programs: Path =
            run {
                val rel = Path.of("packages/kotlin/ttrp-frontend/src/test/resources/ttrb-rules/programs")
                var dir: Path? = Path.of("").toAbsolutePath()
                while (dir != null) {
                    if (Files.isDirectory(dir.resolve(rel))) return@run dir.resolve(rel)
                    dir = dir.parent
                }
                error("could not locate the ttrb-rules fixture programs")
            }

        fun ttrp() = TtrpCommand().subcommands(BuildCommand(), CheckCommand(), ExplainCommand())

        /** Labels (`name#k`, `~n`) renamed in order of first appearance; the header (file path) dropped. */
        fun explain(program: String): String {
            val r = ttrp().test("explain ${programs.resolve(program)}")
            withClue(r.output) { r.statusCode shouldBe 0 }
            val body =
                r.stdout
                    .lines()
                    .drop(1)
                    .joinToString("\n")
            val label = Regex("(?<![\\p{L}\\p{N}_#~])(~\\d+|[\\p{L}_][\\p{L}\\p{N}_]*#\\d+)(?![\\p{L}\\p{N}_#])")
            val seen = LinkedHashMap<String, String>()
            return label.replace(body) { m -> seen.getOrPut(m.value) { "L${seen.size + 1}" } }
        }

        fun build(program: String): Path {
            val out = Files.createTempDirectory("ttrp-rules")
            val r = ttrp().test("build ${programs.resolve(program)} --out $out")
            withClue(r.output) { r.statusCode shouldBe 0 }
            return out.resolve(program.removeSuffix(".ttrp") + ".bundle")
        }

        fun statements(bundle: Path): Map<String, String> =
            Files.list(bundle.resolve("islands")).use { s -> s.toList() }.associate { it.name to Files.readString(it) }

        fun displays(bundle: Path): List<JsonElement> =
            Json
                .parseToJsonElement(Files.readString(bundle.resolve("manifest.json")))
                .jsonObject
                .getValue("displays")
                .jsonArray

        for (p in listOf("rozhodnuti-cs", "rozhodnuti-en", "sklad-cs", "sklad-en")) {
            test("check: $p.ttrp exits 0 with no diagnostics") {
                val r = ttrp().test("check ${programs.resolve("$p.ttrp")}")
                withClue(r.output) { r.statusCode shouldBe 0 }
                r.stdout.trim() shouldBe ""
            }
        }

        for (scenario in listOf("rozhodnuti", "sklad")) {
            for (lang in listOf("cs", "en")) {
                test("explain: $scenario-$lang.ttrp ≡ $scenario-canonical.ttrp, modulo generated names") {
                    explain("$scenario-$lang.ttrp") shouldBe explain("$scenario-canonical.ttrp")
                }

                test("build: $scenario-$lang.ttrp runs the same host statements + action displays as the canonical") {
                    val rules = build("$scenario-$lang.ttrp")
                    val canonical = build("$scenario-canonical.ttrp")
                    statements(rules) shouldBe statements(canonical)
                    displays(rules) shouldBe displays(canonical)
                }
            }
        }

        test("build: the decision bundle routes each action to its display, held to the imported schema") {
            val bundle = build("rozhodnuti-cs.ttrp")
            statements(bundle).keys.sorted() shouldContainExactly
                listOf("rozhodnuti.send_email.sql", "rozhodnuti.update_field.sql")
            val manifest = Files.readString(bundle.resolve("manifest.json"))
            manifest shouldContain "\"schema\": \"shop.akce.send_email\""
            manifest shouldContain "\"schema\": \"shop.akce.update_field\""
            statements(bundle).getValue("rozhodnuti.send_email.sql") shouldContain "'oddělení:obchod'"
            statements(bundle).getValue("rozhodnuti.send_email.sql") shouldContain "'faktura;dodací_list'"
        }
    })
