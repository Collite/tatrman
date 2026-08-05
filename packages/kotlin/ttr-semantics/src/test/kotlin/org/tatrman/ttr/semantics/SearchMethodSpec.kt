// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.parser.diagnostics.DiagnosticCode
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.TableDef

/**
 * RV-P1.5 T4 (grammar 0.12, RV-32) — Kotlin parity for the match-method meaning.
 * Mirrors the TS `packages/semantics/src/__tests__/search-method.test.ts`; the
 * diagnostic MESSAGES are a cross-target contract, so they are asserted here too.
 */
class SearchMethodSpec :
    StringSpec({

        fun search(body: String) =
            (TtrLoader.parseString("def table T { search { $body } }").definitions[0] as TableDef).search

        "an authored method wins and keeps its distance" {
            effectiveMatchMethod(search("searchable method: TYPOS(2)")) shouldBe
                EffectiveMatchMethod(MatchMethodName.TYPOS, 2, MatchMethodOrigin.AUTHORED)
        }

        "TYPOS with no argument takes the RV-32 default distance" {
            effectiveMatchMethod(search("searchable method: TYPOS"))?.maxDistance shouldBe 1
        }

        "EXACT and TOKENS resolve with no distance" {
            effectiveMatchMethod(search("searchable method: EXACT")) shouldBe
                EffectiveMatchMethod(MatchMethodName.EXACT, null, MatchMethodOrigin.AUTHORED)
            effectiveMatchMethod(search("searchable method: TOKENS"))?.maxDistance shouldBe null
        }

        "the method name is case-insensitive on the way in" {
            effectiveMatchMethod(search("searchable method: typos(2)"))?.name shouldBe MatchMethodName.TYPOS
        }

        "bare `searchable` takes the RV-32 default TYPOS(1)" {
            effectiveMatchMethod(search("searchable")) shouldBe
                EffectiveMatchMethod(MatchMethodName.TYPOS, 1, MatchMethodOrigin.DEFAULT)
        }

        "`searchable: false` is not included — there is no method at all" {
            effectiveMatchMethod(search("searchable: false")) shouldBe null
            effectiveMatchMethod(null) shouldBe null
        }

        "`fuzzy: true` maps to TYPOS(1) and `fuzzy: false` to EXACT" {
            effectiveMatchMethod(search("searchable: true, fuzzy: true")) shouldBe
                EffectiveMatchMethod(MatchMethodName.TYPOS, 1, MatchMethodOrigin.LEGACY_FUZZY)
            effectiveMatchMethod(search("searchable: true, fuzzy: false")) shouldBe
                EffectiveMatchMethod(MatchMethodName.EXACT, null, MatchMethodOrigin.LEGACY_FUZZY)
        }

        "an explicit method wins over a legacy fuzzy" {
            effectiveMatchMethod(search("searchable method: TOKENS, fuzzy: true"))?.origin shouldBe
                MatchMethodOrigin.AUTHORED
        }

        "the fuzzy deprecation message is the cross-target contract text" {
            val d = validateSearchMethod(search("searchable: true, fuzzy: true"))
            d.size shouldBe 1
            d[0].code shouldBe DiagnosticCode.SearchFuzzyDeprecated
            d[0].isError shouldBe false
            d[0].message shouldBe
                "'fuzzy: true' is deprecated (grammar 0.12) — replace it with 'searchable method: TYPOS(1)'"
            validateSearchMethod(search("searchable: true, fuzzy: false"))[0].message shouldContain
                "searchable method: EXACT"
        }

        "the 0.12 form is clean" {
            validateSearchMethod(search("searchable method: TYPOS(2)")) shouldBe emptyList()
            validateSearchMethod(search("searchable")) shouldBe emptyList()
        }

        "an unknown method is an error and falls back to the default" {
            val d = validateSearchMethod(search("searchable method: TYPSO(2)"))
            d.size shouldBe 1
            d[0].code shouldBe DiagnosticCode.UnknownMatchMethod
            d[0].isError shouldBe true
            d[0].message shouldContain "TOKENS"
            effectiveMatchMethod(search("searchable method: TYPSO"))?.origin shouldBe MatchMethodOrigin.DEFAULT
        }

        "EXACT and TOKENS take no argument" {
            val d = validateSearchMethod(search("searchable method: EXACT(2)"))
            d.size shouldBe 1
            d[0].code shouldBe DiagnosticCode.InvalidMatchMethodArgument
            d[0].message shouldContain "takes no argument"
            validateSearchMethod(search("searchable method: TOKENS(1)")).size shouldBe 1
        }

        "TYPOS's distance must be a whole number in 1..3 — the range ttr-lexicon accepts" {
            listOf("TYPOS(0)", "TYPOS(-1)", "TYPOS(1.5)", "TYPOS(4)", "TYPOS(7)").forEach { bad ->
                val d = validateSearchMethod(search("searchable method: $bad"))
                d.size shouldBe 1
                d[0].code shouldBe DiagnosticCode.InvalidMatchMethodArgument
                d[0].message shouldContain "1..3"
            }
            listOf("TYPOS(1)", "TYPOS(2)", "TYPOS(3)").forEach { ok ->
                validateSearchMethod(search("searchable method: $ok")).size shouldBe 0
            }
            effectiveMatchMethod(search("searchable method: TYPOS(0)"))?.maxDistance shouldBe 1
        }

        "a rejected argument renders like the other targets (2, not 2.0)" {
            validateSearchMethod(search("searchable method: TYPOS(0)"))[0].message shouldContain "got '0'"
        }
    })
