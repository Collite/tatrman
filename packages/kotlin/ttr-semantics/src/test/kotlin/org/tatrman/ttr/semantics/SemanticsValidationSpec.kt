// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.diagnostics.DiagnosticCode
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.semantics.semanticsblock.SemanticsAnalyzer
import org.tatrman.ttr.semantics.semanticsblock.Vocabulary
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Grounding Phase 1 (grammar 4.2) — the `semantics { … }` vocabulary/shape
 * validator (TTR-SEM-200…211). Mirrors the TS suite
 * (`packages/semantics/src/__tests__/semantics-block-validation.test.ts`); case
 * names are kept aligned for conformance triage.
 */
class SemanticsValidationSpec :
    StringSpec({

        fun ent(body: String): String = "model er\ndef entity E {\n$body\n}"

        fun codesFor(src: String): List<DiagnosticCode> =
            SemanticsAnalyzer
                .analyzeSemantics(
                    TtrLoader.parseString(src, "x.ttrm").definitions,
                ).diagnostics
                .map { it.code }

        fun diagsFor(src: String) =
            SemanticsAnalyzer.analyzeSemantics(TtrLoader.parseString(src, "x.ttrm").definitions).diagnostics

        "SEMANTICS_VOCABULARY_VERSION is 2 (v2 adds the journal-role family; lock-step with the proto enums)" {
            Vocabulary.SEMANTICS_VOCABULARY_VERSION shouldBe 2
        }

        "the journal-role family is registered (S5C-B.4, contracts §12 R30)" {
            val roles = Vocabulary.ATTRIBUTE_ROLES
            // valid_from/valid_to reused; the four new roles added.
            listOf("valid_flag", "valid_from", "valid_to", "version", "authored_by", "written_at")
                .all { it in roles } shouldBe true
            roles.getValue("version").typeConstraint shouldBe Vocabulary.TypeConstraint.Numeric
            roles.getValue("authored_by").typeConstraint shouldBe Vocabulary.TypeConstraint.Text
            roles.getValue("written_at").typeConstraint shouldBe Vocabulary.TypeConstraint.Date
            roles.getValue("valid_flag").typeConstraint shouldBe null // boolean — unconstrained
        }

        "200 — unknown key (with nearest-match suggestion)" {
            val d =
                diagsFor(
                    ent(
                        "attributes: [ def attribute a { type: { type: varchar, length: 6 }, semantics { role: period_code, code_forma: \"x\" } } ]",
                    ),
                )
            val hit = d.firstOrNull { it.code == DiagnosticCode.SemUnknownKey }
            hit.shouldNotBeNull()
            hit.suggestion shouldBe "code_format"
        }

        "201 — unknown role (suggests event_date for event_dat)" {
            val d = diagsFor(ent("attributes: [ def attribute a { type: date, semantics { role: event_dat } } ]"))
            val hit = d.firstOrNull { it.code == DiagnosticCode.SemUnknownRole }
            hit.shouldNotBeNull()
            hit.suggestion shouldBe "event_date"
            (hit.message.contains("event_date")) shouldBe true
        }

        "202 — unknown kind (suggests period_table)" {
            val d = diagsFor(ent("semantics { kind: periodtable }"))
            val hit = d.firstOrNull { it.code == DiagnosticCode.SemUnknownKind }
            hit.shouldNotBeNull()
            hit.suggestion shouldBe "period_table"
        }

        "203 — duplicate key" {
            codesFor(
                ent("attributes: [ def attribute a { type: date, semantics { role: event_date, role: due_date } } ]"),
            ) shouldContain DiagnosticCode.SemDuplicateKey
        }

        "204 — kind on an attribute, and role on an entity" {
            codesFor(
                ent("attributes: [ def attribute a { type: date, semantics { kind: poi } } ]"),
            ) shouldContain DiagnosticCode.SemMisplacedKeyword
            codesFor(ent("semantics { role: event_date }")) shouldContain DiagnosticCode.SemMisplacedKeyword
        }

        "205 — type-constraint violation (amount on a text column)" {
            codesFor(
                ent(
                    "attributes: [ def attribute a { type: { type: varchar, length: 3 }, semantics { role: amount, currency: a } } ]",
                ),
            ) shouldContain DiagnosticCode.SemTypeConstraint
        }

        "206 — completeness (period_table missing period_end)" {
            val src =
                ent(
                    listOf(
                        "semantics { kind: period_table },",
                        "attributes: [",
                        "  def attribute s { type: date, semantics { role: period_start } },",
                        "  def attribute c { type: { type: varchar, length: 6 }, semantics { role: period_code } }",
                        "]",
                    ).joinToString("\n"),
                )
            codesFor(src) shouldContain DiagnosticCode.SemCompleteness
        }

        "207 — more than one event_date on an entity" {
            val src =
                ent(
                    listOf(
                        "attributes: [",
                        "  def attribute a { type: date, semantics { role: event_date } },",
                        "  def attribute b { type: date, semantics { role: event_date } }",
                        "]",
                    ).joinToString("\n"),
                )
            codesFor(src) shouldContain DiagnosticCode.SemMultipleEventDate
        }

        "208 — period: to a nonexistent entity, and to a non-period_table entity" {
            codesFor(
                ent("attributes: [ def attribute a { type: date, semantics { role: event_date, period: Nope } } ]"),
            ) shouldContain DiagnosticCode.SemBadPeriodRef
            val miskinded =
                listOf(
                    "model er",
                    "def entity P { semantics { kind: poi }, attributes: [ def attribute x { type: decimal, semantics { role: geo_lat } }, def attribute y { type: decimal, semantics { role: geo_lon } } ] }",
                    "def entity E { attributes: [ def attribute a { type: date, semantics { role: event_date, period: P } } ] }",
                ).joinToString("\n")
            codesFor(miskinded) shouldContain DiagnosticCode.SemBadPeriodRef
        }

        "209 — currency: to a missing sibling, and to a non-currency_code sibling" {
            codesFor(
                ent("attributes: [ def attribute a { type: decimal, semantics { role: amount, currency: nope } } ]"),
            ) shouldContain DiagnosticCode.SemBadCurrencyRef
            val roleless =
                ent(
                    listOf(
                        "attributes: [",
                        "  def attribute a { type: decimal, semantics { role: amount, currency: c } },",
                        "  def attribute c { type: date, semantics { role: event_date } }",
                        "]",
                    ).joinToString("\n"),
                )
            codesFor(roleless) shouldContain DiagnosticCode.SemBadCurrencyRef
        }

        "210 — geo_lat without geo_lon, and geo_point + pair" {
            codesFor(
                ent(
                    "semantics { kind: poi }, attributes: [ def attribute a { type: decimal, semantics { role: geo_lat } } ]",
                ),
            ) shouldContain DiagnosticCode.SemGeoPair
            val both =
                ent(
                    listOf(
                        "semantics { kind: poi },",
                        "attributes: [",
                        "  def attribute p { type: text, semantics { role: geo_point } },",
                        "  def attribute a { type: decimal, semantics { role: geo_lat } },",
                        "  def attribute o { type: decimal, semantics { role: geo_lon } }",
                        "]",
                    ).joinToString("\n"),
                )
            codesFor(both) shouldContain DiagnosticCode.SemGeoPair
        }

        "211 — valid_from without valid_to" {
            codesFor(
                ent("attributes: [ def attribute a { type: date, semantics { role: valid_from } } ]"),
            ) shouldContain DiagnosticCode.SemValidPair
        }

        "green path — the golden 59-semantics.ttrm fixture yields zero diagnostics" {
            val src = Files.readString(fixturesDir().resolve("59-semantics.ttrm"))
            diagsFor(src) shouldBe emptyList()
        }

        "green path — the golden 60-semantics-db.ttrm fixture yields zero diagnostics" {
            val src = Files.readString(fixturesDir().resolve("60-semantics-db.ttrm"))
            diagsFor(src) shouldBe emptyList()
        }
    })

private fun fixturesDir(): Path {
    var dir: Path? = Paths.get("").toAbsolutePath()
    while (dir != null) {
        val candidate = dir.resolve("tests/conformance/fixtures")
        if (Files.isDirectory(candidate)) return candidate
        dir = dir.parent
    }
    error("could not locate tests/conformance/fixtures")
}
