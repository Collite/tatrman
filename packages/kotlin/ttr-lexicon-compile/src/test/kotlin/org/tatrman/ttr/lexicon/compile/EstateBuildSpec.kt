// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.compile

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.lexicon.LexiconValidator
import org.tatrman.ttr.lexicon.LexiconWarnings
import org.tatrman.ttr.lexicon.MatchMethod
import org.tatrman.ttr.lexicon.MatchProfile
import org.tatrman.ttr.lexicon.Norm
import org.tatrman.ttr.lexicon.NormRule
import org.tatrman.ttr.lexicon.SourceTag
import org.tatrman.ttr.lexicon.TargetClass
import org.tatrman.ttr.lexicon.TyposRule
import org.tatrman.ttr.metadata.model.Attribute
import org.tatrman.ttr.metadata.model.Entity
import org.tatrman.ttr.metadata.model.ErSchema
import org.tatrman.ttr.metadata.model.LocalizedText
import org.tatrman.ttr.metadata.model.Model
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.model.ModelVersion
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import java.nio.file.Path
import java.time.Instant

/**
 * RV-P1.2 T7 — a whole estate compiles.
 *
 * The fixture mirrors hartland's actual shape, which is why it has **both** declared surfaces at
 * once: the `.ttrm` units under `model/lexicon/<locale>/` (the TTR-M sugar hartland ships) and the RV-36
 * root-level `lexicon/` data area. They are two surfaces of one layer, not two areas — recorded
 * here because "the lexicon" reads ambiguously in that repo otherwise.
 */
class EstateBuildSpec :
    FunSpec({

        val customer = QualifiedName(SchemaCode.ER, "entity", "customer")
        // RV-P3.1 T7 — attribute depth is `er.entity.<entity>.<attr>`, which is what the REAL
        // loader produces (verified against `FileBasedSource` over this fixture's own model/).
        // It used to read `("attribute", "status")` → `er.attribute.status`, a shape no loaded
        // model has ever had; the fixture's YAML was authored to match the invention, so three
        // declared rows were silently dropped the moment a real model was put behind them.
        val status = QualifiedName(SchemaCode.ER, "entity", "customer.status")
        val snapshotId = "sha256:" + "12".repeat(32)
        val builtAt = "2026-08-02T00:00:00Z"

        val model =
            Model(
                descriptor = ModelDescriptor(id = "estate", name = "estate"),
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
                                                displayLabel = LocalizedText(mapOf("cs" to "Odběratel")),
                                                attributes =
                                                    listOf(
                                                        Attribute(
                                                            internalId = "2",
                                                            qname = status,
                                                            sourceFile = "model/er/customer.ttrm",
                                                            entity = customer,
                                                            type = "string",
                                                            valueLabels =
                                                                mapOf("1" to LocalizedText(mapOf("cs" to "Aktivní"))),
                                                        ),
                                                    ),
                                            ),
                                    ),
                            ),
                    ),
                mappings = emptyList(),
                queries = emptyMap(),
            )

        fun estate(name: String): Path = Path.of(EstateBuildSpec::class.java.getResource("/$name")!!.toURI())

        // includeStdlib = false: these tests assert exactly what ONE repo contributes. The stdlib's
        // own coverage is LexiconStdlibSpec's; the two tests at the bottom cover them layered.
        fun build(name: String) =
            LexiconBuild.run(
                estate(name),
                model,
                snapshotId,
                builtAt,
                "ttr-lexicon-compile/test",
                includeStdlib = false,
            )

        test("the estate builds, and every declared surface reaches the artifact") {
            val outcome = build("estate")
            outcome.ok shouldBe true

            val byTerm =
                outcome.result.lexicon.entries
                    .associateBy { it.termNormalized to it.lang }

            // The area's alias file, both languages, the `en` term keeping its authored method.
            byTerm.getValue("zákazník" to "cs").targetClass shouldBe TargetClass.MODEL_OBJECT
            byTerm.getValue("customer" to "en").method shouldBe "TOKENS"
            // The TTR-M sugar, tagged with the unit's `locale cs`.
            byTerm.getValue("odběratel" to "cs").sourceTag shouldBe SourceTag.DECLARED
            // A value entry, via the attribute-depth member ref.
            byTerm.getValue("aktivní" to "cs").targetClass shouldBe TargetClass.MEMBER
            // Grounding triggers (RV-42) and the skill's frontmatter (RV-35).
            byTerm.getValue("rok" to "cs").targetClass shouldBe TargetClass.GROUNDING_TRIGGER
            // The file default reaches a term that did not state its own. (`rok` states EXACT —
            // RV-44's ⚑M-4 guard would suppress fuzz on a 3-char term anyway, so the slice says so.)
            byTerm.getValue("loni" to "cs").method shouldBe "TYPOS(1)"
            byTerm.getValue("vývoj" to "cs").targetRef shouldBe "op:trend"
            // The metadata layer, from the model's own displayLabel/valueLabels.
            byTerm.getValue("aktivní" to "cs").targetRef shouldBe "er.entity.customer.status.1"

            // `def example` is not vocabulary — the question text must not become a term.
            outcome.result.lexicon.entries
                .none { it.termNormalized.contains("kolik") } shouldBe true
            // Nor is the note beside the lexicon a skill.
            outcome.result.operators.operators.keys
                .toList() shouldBe listOf("op:trend")
        }

        test("the dangling ref in the sugar is dropped with a warning that names the file and line") {
            val outcome = build("estate")

            outcome.result.lexicon.entries
                .none { it.termNormalized == "duch" } shouldBe true

            val warning = outcome.result.warnings.single { it.code == CompileWarning.DANGLING_REF }
            warning.provenance.file shouldBe "model/lexicon/cs/measures.ttrm"
            warning.message shouldContain "er.entity.ghost"
        }

        // ---- RV-P3.0 T3: profiles reach the artifact, and the guard reaches the build output -----

        test("RV-44 — the estate's authored profiles are IN the artifact, resolved") {
            val outcome = build("estate")
            val byTerm =
                outcome.result.lexicon.entries
                    .associateBy { it.termNormalized to it.lang }

            // Inherited from the file's `defaults.match` — three strata, in authored order.
            byTerm.getValue("odběratelský účet" to "cs").matchProfile shouldBe
                MatchProfile(
                    listOf(
                        NormRule(Norm.CANONICAL, exact = 1.00, typos = TyposRule(1, 0.05)),
                        NormRule(Norm.FOLDED, exact = 0.90),
                        NormRule(Norm.LEMMA, exact = 0.80),
                    ),
                )
            // The term's own `match` replaces the default whole.
            byTerm.getValue("kód stavu" to "cs").matchProfile shouldBe
                MatchProfile(listOf(NormRule(Norm.CANONICAL, exact = 1.00)))
            // And a plain `method:` row from a different file still resolves to its sugar profile,
            // so "carries a profile" is a property of the LAYER, not of how the author wrote it.
            byTerm.getValue("customer" to "en").matchProfile shouldBe
                MatchProfile(listOf(NormRule(Norm.CANONICAL, tokens = true)))
        }

        test("⚑M-4 — the short-term guard warning rides the same stream as a dangling ref") {
            val outcome = build("estate")

            // Same list, same shape, same ordering — an author reads one stream, not two.
            val guard = outcome.result.warnings.single { it.code == LexiconWarnings.SHORT_TERM_TYPOS_GUARD }
            guard.provenance.file shouldBe "aliases/profiles.lex.yaml"
            guard.message shouldContain "AK"
            outcome.ok shouldBe true

            // …and the artifact records what was AUTHORED. Suppressing the rule here would leave
            // the matcher unable to tell "the author asked for fuzz and cannot have it" from "the
            // author asked for exact", which is the distinction the warning exists to preserve.
            outcome.result.lexicon.entries
                .single { it.termNormalized == "ak" }
                .matchProfile shouldBe MatchProfile.ofSugar(MatchMethod.Typos(1))
        }

        test("two builds of the same estate produce the same archive id") {
            build("estate").packed.id shouldBe build("estate").packed.id
        }

        test("an estate with no lexicon area builds with an empty declared layer and no warnings") {
            val outcome = build("empty-estate")

            outcome.ok shouldBe true
            outcome.result.warnings shouldBe emptyList()
            // The metadata layer still compiles — it is a layer of the MODEL (RV-39), so the
            // artifact's existence does not wait on anyone authoring their first alias.
            outcome.result.lexicon.entries
                .map { it.sourceTag }
                .toSet() shouldBe setOf(SourceTag.METADATA)
            outcome.result.lexicon.entries
                .map { it.termNormalized } shouldBe listOf("aktivní", "odběratel")
        }

        // ---- RV-P1.3 T7: the stdlib, layered ----------------------------------------------------

        fun buildWithStdlib(name: String) =
            LexiconBuild.run(estate(name), model, snapshotId, builtAt, "ttr-lexicon-compile/test")

        test("a build layers the operator stdlib under the estate by default") {
            val outcome = buildWithStdlib("empty-estate")

            outcome.result.operators.operators.keys
                .toList() shouldContainExactlyInAnyOrder
                LexiconStdlib.OPERATORS.map { "op:$it" }
            // An estate that authored nothing still answers to "ukaž".
            outcome.result.lexicon.entries
                .single { it.termNormalized == "ukaž" }
                .targetRef shouldBe "op:show"
            outcome.result.warnings shouldBe emptyList()
        }

        test("RV-P1.6 T3 — a build layers the GROUNDING stdlib in too, as GROUNDING_TRIGGER rows") {
            val outcome = buildWithStdlib("empty-estate")

            val grounding =
                outcome.result.lexicon.entries
                    .filter { it.targetClass == TargetClass.GROUNDING_TRIGGER }
            grounding.map { it.targetRef }.toSet() shouldContainExactlyInAnyOrder
                LexiconValidator.GROUNDING_KINDS.map { "ground:$it" }
            // The kernels' words are in the artifact an estate actually ships.
            grounding.map { it.termNormalized } shouldContain "rok"
            grounding.map { it.termNormalized } shouldContain "kč"
            grounding.map { it.termNormalized } shouldContain "město"
            outcome.result.warnings shouldBe emptyList()
        }

        test("RV-P1.6 T3 — an estate's own `ground:` entry sits beside the shipped slice, not instead of it") {
            // The estate fixture declares "rok" → ground:chrono itself; the stdlib declares it too.
            // Same term, same target, same method ⇒ one row, no warning, and the rest of the
            // shipped chrono vocabulary is still there.
            val outcome = buildWithStdlib("estate")

            outcome.result.lexicon.entries
                .filter { it.termNormalized == "rok" && it.targetRef == "ground:chrono" }
                .size shouldBe 1
            outcome.result.lexicon.entries
                .filter { it.targetClass == TargetClass.GROUNDING_TRIGGER }
                .map { it.termNormalized } shouldContain "čtvrtletí"
        }

        test("the estate's own trend.md wins over the stdlib one, and the build names both files") {
            // The override fixture is the estate's real skill file — nothing staged for the test.
            val outcome = buildWithStdlib("estate")

            val trend =
                outcome.result.operators.operators
                    .getValue("op:trend")
            trend.source.file shouldBe "skills/trend.md"
            trend.body shouldContain "line chart default"

            val note = outcome.result.warnings.single { it.code == CompileWarning.OPERATOR_OVERRIDE }
            note.message shouldContain "stdlib/skills/trend.md"
            note.message shouldContain "skills/trend.md"
        }
    })
