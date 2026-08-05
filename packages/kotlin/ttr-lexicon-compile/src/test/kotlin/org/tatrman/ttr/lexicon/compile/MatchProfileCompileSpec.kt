// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.compile

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.lexicon.LexiconArea
import org.tatrman.ttr.lexicon.LexiconDataFile
import org.tatrman.ttr.lexicon.LexiconLoad
import org.tatrman.ttr.lexicon.LexiconValidator
import org.tatrman.ttr.lexicon.MatchProfile
import org.tatrman.ttr.lexicon.Norm
import org.tatrman.ttr.lexicon.NormRule
import org.tatrman.ttr.lexicon.SourceTag
import org.tatrman.ttr.lexicon.TargetClass
import org.tatrman.ttr.lexicon.TyposRule
import org.tatrman.ttr.metadata.model.Entity
import org.tatrman.ttr.metadata.model.ErSchema
import org.tatrman.ttr.metadata.model.LocalizedText
import org.tatrman.ttr.metadata.model.Model
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.model.ModelVersion
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import java.time.Instant

/**
 * RV-P3.0 T2 — sugar compilation and the **resolved profile** in the artifact (RV-44).
 *
 * The point of resolving here rather than at read time is that there is then exactly one place
 * that knows what `method: TYPOS(1)` means. Four services read this artifact; if each expanded the
 * sugar itself, "identical" would be a promise rather than a fact. So the load-bearing assertion in
 * this file is the boring one: a sugar row and its hand-expanded twin compile to the same profile.
 */
class MatchProfileCompileSpec :
    FunSpec({

        val snapshotHash = "sha256:" + "cd".repeat(32)
        val builtAt = "2026-08-06T00:00:00Z"

        fun dataFile(
            name: String,
            yaml: String,
        ): LexiconDataFile =
            LexiconValidator
                .loadDataFile(yaml, name)
                .shouldBeInstanceOf<LexiconLoad.Ok<LexiconDataFile>>()
                .value

        fun index(objects: Set<String>): ModelRefIndex =
            ModelRefIndex { ref -> if (ref in objects) TargetClass.MODEL_OBJECT else null }

        fun compile(
            yaml: String,
            objects: Set<String> = setOf("er.Customer", "er.DistributionCentre"),
            model: Model? = null,
        ): CompileResult =
            LexiconCompiler.compile(
                LexiconSources(
                    area = LexiconArea(listOf(dataFile("aliases.lex.yaml", yaml)), emptyList()),
                    model = model,
                ),
                index(objects),
                snapshotHash,
                builtAt,
            )

        fun profileOf(
            result: CompileResult,
            term: String,
        ) = result.lexicon.entries
            .single { it.termNormalized == term }
            .matchProfile

        context("M-T6 — `method:` sugar compiles to the profile it means") {
            test("EXACT / TYPOS(d) / TOKENS, byte-exact against the addendum's table") {
                val result =
                    compile(
                        """
                        schema: ttr-lexicon/v1
                        defaults: { lang: cs }
                        entries:
                          - terms:
                              - { text: "zákazník", method: EXACT }
                              - { text: "zákaznický účet", method: TYPOS(2) }
                              - { text: "celkem za zákazníky", method: TOKENS }
                            target: er.Customer
                        """.trimIndent(),
                    )

                profileOf(result, "zákazník") shouldBe
                    MatchProfile(listOf(NormRule(Norm.CANONICAL, exact = 1.00)))
                profileOf(result, "zákaznický účet") shouldBe
                    MatchProfile(listOf(NormRule(Norm.CANONICAL, exact = 1.00, typos = TyposRule(2, 0.05))))
                profileOf(result, "celkem za zákazníky") shouldBe
                    MatchProfile(listOf(NormRule(Norm.CANONICAL, tokens = true)))
            }

            test("a sugar row and its hand-expanded twin are the SAME row after compilation") {
                // The p3-0 T7 equivalence case, asserted at the producer. Two files, one term each,
                // identical but for how the author spelled the matching — and one artifact row.
                val sugar =
                    compile(
                        """
                        schema: ttr-lexicon/v1
                        entries:
                          - terms: [ { text: "zákazník", lang: cs, method: TYPOS(1) } ]
                            target: er.Customer
                        """.trimIndent(),
                    )
                val expanded =
                    compile(
                        """
                        schema: ttr-lexicon/v1
                        entries:
                          - terms:
                              - text: "zákazník"
                                lang: cs
                                match: [ { norm: canonical, exact: 1.00, typos: { distance: 1, penalty: 0.05 } } ]
                            target: er.Customer
                        """.trimIndent(),
                    )

                // Everything but provenance: the two spellings occupy different numbers of lines,
                // and where a term was written is the one thing that SHOULD differ between them.
                fun rows(result: CompileResult) =
                    result.lexicon.entries.map { it.copy(provenance = it.provenance.copy(line = 0)) }

                rows(sugar) shouldBe rows(expanded)
            }
        }

        context("the resolved profile lands in the artifact, so no consumer re-derives sugar") {
            test("every DECLARED row carries one — authored or expanded") {
                val result =
                    compile(
                        """
                        schema: ttr-lexicon/v1
                        defaults:
                          lang: cs
                          match:
                            - { norm: canonical, exact: 1.00, typos: { distance: 1, penalty: 0.05 } }
                            - { norm: folded,    exact: 0.90 }
                        entries:
                          - terms: [ { text: "zákazník" }, { text: "DC", method: EXACT } ]
                            target: er.Customer
                        """.trimIndent(),
                    )

                profileOf(result, "zákazník").shouldNotBeNull().rules shouldContainExactly
                    listOf(
                        NormRule(Norm.CANONICAL, exact = 1.00, typos = TyposRule(1, 0.05)),
                        NormRule(Norm.FOLDED, exact = 0.90),
                    )
                // Its own `method:` wins over the file's `match:` — whole, not merged.
                profileOf(result, "dc").shouldNotBeNull().rules shouldContainExactly
                    listOf(NormRule(Norm.CANONICAL, exact = 1.00))
            }

            test("the `method` column is the profile's projection, and still readable by a pre-M consumer") {
                val result =
                    compile(
                        """
                        schema: ttr-lexicon/v1
                        entries:
                          - terms:
                              - text: "zákazník"
                                lang: cs
                                match:
                                  - { norm: canonical, exact: 1.00, typos: { distance: 2, penalty: 0.05 } }
                                  - { norm: folded,    exact: 0.90 }
                            target: er.Customer
                        """.trimIndent(),
                    )

                result.lexicon.entries
                    .single()
                    .method shouldBe "TYPOS(2)"
            }

            test("⚑M-2 — a METADATA row carries NO profile, so the member/label tier keeps engine scores") {
                val customer = QualifiedName(SchemaCode.ER, "entity", "customer")
                val model =
                    Model(
                        descriptor = ModelDescriptor(id = "t", name = "t"),
                        version = ModelVersion("v1", Instant.EPOCH),
                        schemas =
                            mapOf(
                                "er" to
                                    ErSchema(
                                        entities =
                                            mapOf(
                                                customer to
                                                    Entity(
                                                        internalId = "1",
                                                        qname = customer,
                                                        sourceFile = "model/er/customer.ttrm",
                                                        displayLabel = LocalizedText(mapOf("cs" to "Zákazník")),
                                                    ),
                                            ),
                                    ),
                            ),
                        mappings = emptyList(),
                        queries = emptyMap(),
                    )
                val result =
                    compile(
                        """
                        schema: ttr-lexicon/v1
                        entries:
                          - terms: [ { text: "klient", lang: cs, method: EXACT } ]
                            target: ${customer.dotted()}
                        """.trimIndent(),
                        objects = setOf(customer.dotted()),
                        model = model,
                    )

                val declared = result.lexicon.entries.single { it.sourceTag == SourceTag.DECLARED }
                val metadata = result.lexicon.entries.single { it.sourceTag == SourceTag.METADATA }
                declared.matchProfile.shouldNotBeNull()
                metadata.matchProfile shouldBe null
            }
        }

        context("the version tuple tracks profiles — P1.4's discipline, kept") {
            fun withFoldedScore(score: String) =
                compile(
                    """
                    schema: ttr-lexicon/v1
                    entries:
                      - terms:
                          - text: "zákazník"
                            lang: cs
                            match: [ { norm: canonical, exact: 1.00 }, { norm: folded, exact: $score } ]
                        target: er.Customer
                    """.trimIndent(),
                )

            test("contentHash MOVES when a profile changes") {
                withFoldedScore("0.90").lexicon.contentHash shouldNotBe
                    withFoldedScore("0.80").lexicon.contentHash
            }

            test("contentHash HOLDS when nothing about the profile changes") {
                withFoldedScore("0.90").lexicon.contentHash shouldBe withFoldedScore("0.90").lexicon.contentHash
            }

            test("the declared LAYER hash moves too — a profile-only edit is an edit to that layer") {
                withFoldedScore("0.90")
                    .lexicon.header.sourceHashes.declared shouldNotBe
                    withFoldedScore("0.80")
                        .lexicon.header.sourceHashes.declared
            }
        }

        context("two ways of saying it, disagreeing") {
            test("same term and target, different profiles ⇒ RG-LEXC-002, and the build says which won") {
                // The pre-RV-44 rule caught two `method:`s. Two profiles that agree on `method` and
                // disagree on the norms would have slipped through it — hence the widening.
                val area =
                    LexiconArea(
                        listOf(
                            dataFile(
                                "a.lex.yaml",
                                """
                                schema: ttr-lexicon/v1
                                entries:
                                  - terms:
                                      - text: "zákazník"
                                        lang: cs
                                        match: [ { norm: canonical, exact: 1.00 } ]
                                    target: er.Customer
                                """.trimIndent(),
                            ),
                            dataFile(
                                "b.lex.yaml",
                                """
                                schema: ttr-lexicon/v1
                                entries:
                                  - terms:
                                      - text: "zákazník"
                                        lang: cs
                                        match: [ { norm: folded, exact: 0.90 } ]
                                    target: er.Customer
                                """.trimIndent(),
                            ),
                        ),
                        emptyList(),
                    )

                val result =
                    LexiconCompiler.compile(
                        LexiconSources(area = area),
                        index(setOf("er.Customer")),
                        snapshotHash,
                        builtAt,
                    )

                val warning = result.warnings.single()
                warning.code shouldBe CompileWarning.METHOD_CONFLICT
                warning.message shouldBe
                    "term \"zákazník\" → `er.Customer` is declared with both EXACT and " +
                    "match[folded/exact 0.9]; kept EXACT"
                result.lexicon.entries
                    .single()
                    .matchProfile shouldBe
                    MatchProfile(listOf(NormRule(Norm.CANONICAL, exact = 1.00)))
            }
        }
    })
