// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.compile

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.tatrman.ttr.lexicon.Lang
import org.tatrman.ttr.lexicon.LexiconArea
import org.tatrman.ttr.lexicon.LexiconDataFile
import org.tatrman.ttr.lexicon.LexiconLoad
import org.tatrman.ttr.lexicon.LexiconValidator
import org.tatrman.ttr.lexicon.MatchMethod
import org.tatrman.ttr.lexicon.TargetClass
import org.tatrman.ttr.lexicon.TermNormalizer
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * RV-P1.6 T2/T3 (RV-42) — the grounding trigger slice, at build level.
 *
 * The second application of the RV-35 skill-entry pattern: grounding trigger words become ordinary
 * lexicon entries under the `ground:` target class, compiled through exactly the path an estate's
 * own `.lex.yaml` takes. Nothing here is kernel behaviour — patterns, calendars and normalization
 * stay generative service-side, which is what makes zero-entry grounding still work.
 */
class GroundingStdlibSpec :
    FunSpec({

        val snapshotHash = "sha256:" + "56".repeat(32)
        val builtAt = "2026-08-04T00:00:00Z"

        fun compileStdlib(extra: List<LexiconDataFile> = emptyList()) =
            LexiconCompiler.compile(
                LexiconSources(area = LexiconArea(LexiconStdlib.groundingSlices() + extra, emptyList())),
                ModelRefIndex.EMPTY,
                snapshotHash,
                builtAt,
            )

        fun dataFile(
            name: String,
            yaml: String,
        ): LexiconDataFile =
            LexiconValidator
                .loadDataFile(yaml, name)
                .shouldBeInstanceOf<LexiconLoad.Ok<LexiconDataFile>>()
                .value

        test("one slice ships per grounding kind — no kernel is silently without vocabulary") {
            LexiconStdlib
                .groundingSlices()
                .flatMap { file -> file.entries.map { it.target } }
                .toSet() shouldContainExactlyInAnyOrder
                LexiconValidator.GROUNDING_KINDS.map { "ground:$it" }
        }

        test("every shipped slice validates against ttr-lexicon/v1") {
            // `groundingSlices()` throws on a rejection, so reaching here is most of the assertion.
            LexiconStdlib.groundingSlices().forEach { file ->
                file.entries.isNotEmpty() shouldBe true
                file.entries.forEach { entry -> entry.terms.isNotEmpty() shouldBe true }
            }
        }

        test("compiling the slices alone yields GROUNDING_TRIGGER rows and no warnings") {
            val result = compileStdlib()

            result.warnings shouldBe emptyList()
            result.lexicon.entries
                .map { it.targetClass }
                .toSet() shouldBe setOf(TargetClass.GROUNDING_TRIGGER)
            // `ground:` refs never consult the model index — an EMPTY index must not make them dangle.
            result.lexicon.entries.size shouldBe
                LexiconStdlib.groundingSlices().sumOf { file -> file.entries.sumOf { it.terms.size } }
            // Bodies are an operator concept; a grounding kernel has none.
            result.operators.operators shouldBe emptyMap()
        }

        test("the kernels' own trigger words are all present (T2 — nothing regresses at T4)") {
            val byTerm = compileStdlib().lexicon.entries.groupBy { it.termNormalized }

            // chrono: the words `DateRecognizer` tests inline today.
            listOf("rok", "roce", "letos", "loni", "měsíc", "čtvrtletí", "období", "dnes", "year", "quarter")
                .forEach { term -> byTerm[TermNormalizer.normalize(term)] shouldNotBe null }
            // money: the recognizer's currency + scale words.
            listOf("kč", "czk", "eur", "usd", "tisíc", "milion", "thousand", "million")
                .forEach { term -> byTerm[TermNormalizer.normalize(term)] shouldNotBe null }
            // geo: category words only.
            listOf("město", "region", "kraj", "city")
                .forEach { term -> byTerm[TermNormalizer.normalize(term)] shouldNotBe null }
        }

        test("multi-word triggers are TOKENS — the words may be separated and reordered") {
            val byTerm = compileStdlib().lexicon.entries.associateBy { it.termNormalized }

            byTerm.getValue(TermNormalizer.normalize("fiskální rok")).method shouldBe MatchMethod.Tokens.wire
            byTerm.getValue(TermNormalizer.normalize("fiscal year")).method shouldBe MatchMethod.Tokens.wire
        }

        test("short codes and symbols are EXACT — a one-edit neighbourhood would collide") {
            val byTerm = compileStdlib().lexicon.entries.associateBy { it.termNormalized }

            listOf("Q1", "Q2", "Q3", "Q4", "Kč", "EUR", "USD").forEach { term ->
                byTerm.getValue(TermNormalizer.normalize(term)).method shouldBe MatchMethod.Exact.wire
            }
        }

        test("the geo slice carries NO place names — the gazetteer stays parked (RV-42)") {
            val geo =
                LexiconStdlib
                    .groundingSlices()
                    .single { file -> file.entries.any { it.target == "ground:geo" } }
            val terms = geo.entries.flatMap { it.terms }.map { it.text.lowercase() }

            // A place name would make this file a gazetteer, which is the boundary RV-42 parked.
            listOf("brno", "praha", "prague", "ostrava", "česko", "czechia").forEach { place ->
                terms.contains(place) shouldBe false
            }
        }

        test("a term may be BOTH an operator and a grounding trigger — overlap is the lattice's normal state") {
            // "období" is a chrono trigger; an estate is free to also bind it to an operator. Two
            // annotations on one span is what the lattice is FOR (RV-9/33) — the resolver narrows,
            // the compiler does not choose.
            val estateOp =
                dataFile(
                    "estate/ops.lex.yaml",
                    """
                    schema: ttr-lexicon/v1
                    entries:
                      - terms: [ { text: "období", lang: cs } ]
                        target: op:trend
                    """.trimIndent(),
                )

            val result = compileStdlib(listOf(estateOp))

            result.warnings shouldBe emptyList()
            result.lexicon.entries
                .filter { it.termNormalized == TermNormalizer.normalize("období") }
                .map { it.targetClass } shouldContainExactlyInAnyOrder
                listOf(TargetClass.GROUNDING_TRIGGER, TargetClass.OPERATOR)
        }

        test("a same-class duplicate with a different method warns and keeps the wider one") {
            // Same term, same target, two methods: contradictory authoring, not two entries.
            val estate =
                dataFile(
                    "estate/chrono.lex.yaml",
                    """
                    schema: ttr-lexicon/v1
                    entries:
                      - terms: [ { text: "rok", lang: cs, method: EXACT } ]
                        target: ground:chrono
                    """.trimIndent(),
                )

            val result = compileStdlib(listOf(estate))

            result.warnings.map { it.code } shouldContainExactly listOf(CompileWarning.METHOD_CONFLICT)
            result.lexicon.entries
                .single { it.termNormalized == "rok" && it.lang == Lang.CS.wire }
                .method shouldBe MatchMethod.Typos(1).wire
        }

        test("an estate EXTENDS the shipped slice rather than replacing it") {
            val estate =
                dataFile(
                    "estate/chrono.lex.yaml",
                    """
                    schema: ttr-lexicon/v1
                    entries:
                      - terms: [ { text: "fiskál", lang: cs, method: TYPOS(1) } ]
                        target: ground:chrono
                    """.trimIndent(),
                )

            val byTerm = compileStdlib(listOf(estate)).lexicon.entries.associateBy { it.termNormalized }

            byTerm["fiskál"]?.targetRef shouldBe "ground:chrono"
            byTerm["rok"]?.targetRef shouldBe "ground:chrono" // still there
        }
    })
