// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.compile

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.lexicon.LexiconArea
import org.tatrman.ttr.lexicon.LexiconDataFile
import org.tatrman.ttr.lexicon.LexiconLoad
import org.tatrman.ttr.lexicon.LexiconValidator
import org.tatrman.ttr.lexicon.SourceTag
import org.tatrman.ttr.lexicon.TargetClass
import org.tatrman.ttr.metadata.model.Entity
import org.tatrman.ttr.metadata.model.ErSchema
import org.tatrman.ttr.metadata.model.LocalizedText
import org.tatrman.ttr.metadata.model.Model
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.model.ModelVersion
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.parser.loader.TtrLoader
import java.time.Instant

/**
 * RV-P1.2 T2 — the compiler's contract, written before the compiler.
 *
 * The five cases are the ones the task list names: convergence of the two declared surfaces,
 * RV-20 dangling-ref handling, RV-38 kind derivation, the METADATA layer's tag, and
 * byte-determinism. Nothing here reaches a file system or a clock.
 */
class LexiconCompilerSpec :
    FunSpec({

        val snapshotHash = "sha256:" + "ab".repeat(32)
        val builtAt = "2026-08-02T00:00:00Z"

        fun dataFile(
            name: String,
            yaml: String,
        ): LexiconDataFile =
            LexiconValidator
                .loadDataFile(yaml, name)
                .shouldBeInstanceOf<LexiconLoad.Ok<LexiconDataFile>>()
                .value

        fun ttrm(
            file: String,
            src: String,
        ): TtrmLexiconUnit {
            val parsed = TtrLoader.parseString(src, file)
            parsed.errors shouldBe emptyList()
            return TtrmLexiconUnit(file, parsed)
        }

        /** Everything resolves as a plain model object unless the ref is listed as a member. */
        fun index(
            objects: Set<String>,
            members: Set<String> = emptySet(),
        ): ModelRefIndex =
            ModelRefIndex { ref ->
                when (ref) {
                    in members -> TargetClass.MEMBER
                    in objects -> TargetClass.MODEL_OBJECT
                    else -> null
                }
            }

        // ---- (a) the two declared surfaces converge on ONE row --------------------------------

        test("the same logical entry, authored twice, compiles to one row") {
            // Same term, same target, same lang, same (defaulted) method — one authored in the
            // lexicon area, one as TTR-M sugar. A repo mid-migration has both; the artifact
            // must not carry the term twice, or every score is counted twice downstream.
            val yaml =
                """
                schema: ttr-lexicon/v1
                defaults: { lang: cs }
                entries:
                  - terms: [ { text: "tržba" } ]
                    target: md.measure.revenue
                """.trimIndent()
            val sugar =
                """
                model lexicon locale cs
                def term revenue_cs { for: md.measure.revenue, forms: ["tržba"] }
                """.trimIndent()

            val result =
                LexiconCompiler.compile(
                    LexiconSources(
                        area = LexiconArea(listOf(dataFile("aliases/measures.lex.yaml", yaml)), emptyList()),
                        ttrm = listOf(ttrm("model/lexicon/cs/measures.ttrm", sugar)),
                    ),
                    index(objects = setOf("md.measure.revenue")),
                    snapshotHash,
                    builtAt,
                )

            result.lexicon.entries shouldHaveSize 1
            result.lexicon.entries
                .single()
                .termNormalized shouldBe "tržba"
            result.lexicon.entries
                .single()
                .sourceTag shouldBe SourceTag.DECLARED
            result.warnings shouldBe emptyList()
        }

        test("the same term under two different targets is two rows, not a conflict") {
            // Dedup is by term+lang+target. Homonyms are legitimate — two bindings on one mention
            // is what the lattice is for (RV-2); collapsing them here would destroy the ambiguity
            // the resolver is supposed to see.
            //
            // They must come from two FILES: P1.1's RG-LEX-006 rejects a term declared twice
            // within one file ("no defined winner"), which is a rule about one author's typo, not
            // about the vocabulary as a whole. Cross-file homonyms are legal and reach here.
            fun file(target: String) =
                """
                schema: ttr-lexicon/v1
                defaults: { lang: cs }
                entries:
                  - terms: [ { text: "obrat" } ]
                    target: $target
                """.trimIndent()

            val result =
                LexiconCompiler.compile(
                    LexiconSources(
                        area =
                            LexiconArea(
                                listOf(
                                    dataFile("revenue.lex.yaml", file("md.measure.revenue")),
                                    dataFile("turnover.lex.yaml", file("md.measure.turnover")),
                                ),
                                emptyList(),
                            ),
                    ),
                    index(objects = setOf("md.measure.revenue", "md.measure.turnover")),
                    snapshotHash,
                    builtAt,
                )

            result.lexicon.entries shouldHaveSize 2
            result.warnings shouldBe emptyList()
        }

        // ---- (b) RV-20: dangling ref → dropped + warning ---------------------------------------

        test("a target that is not in the model snapshot is dropped with an attributable warning") {
            val yaml =
                """
                schema: ttr-lexicon/v1
                entries:
                  - terms: [ { text: "středisko" } ]
                    target: er.entity.cost_center
                  - terms: [ { text: "duch" } ]
                    target: er.entity.ghost
                """.trimIndent()

            val result =
                LexiconCompiler.compile(
                    LexiconSources(area = LexiconArea(listOf(dataFile("aliases/er.lex.yaml", yaml)), emptyList())),
                    index(objects = setOf("er.entity.cost_center")),
                    snapshotHash,
                    builtAt,
                )

            result.lexicon.entries.map { it.termNormalized } shouldContainExactly listOf("středisko")

            val warning = result.warnings.single()
            warning.code shouldBe CompileWarning.DANGLING_REF
            warning.provenance.file shouldBe "aliases/er.lex.yaml"
            // The line the dropped TERM was written on (5), not the `target:` line below it — a
            // build warning has to point at the word the author will search for.
            warning.provenance.line shouldBe 5
        }

        test("a dangling ref is a warning, never a failed build") {
            val yaml =
                """
                schema: ttr-lexicon/v1
                entries:
                  - terms: [ { text: "duch" } ]
                    target: er.entity.ghost
                """.trimIndent()

            val result =
                LexiconCompiler.compile(
                    LexiconSources(area = LexiconArea(listOf(dataFile("a.lex.yaml", yaml)), emptyList())),
                    ModelRefIndex.EMPTY,
                    snapshotHash,
                    builtAt,
                )

            result.lexicon.entries shouldBe emptyList()
            result.warnings shouldHaveSize 1
            result.lexicon.header.modelSnapshotHash shouldBe snapshotHash
        }

        // ---- (c) RV-38/RV-42: target_class derivation ------------------------------------------

        test("target_class comes from the model graph for model refs, and from the prefix otherwise") {
            val yaml =
                """
                schema: ttr-lexicon/v1
                entries:
                  - terms: [ { text: "zákazník" } ]
                    target: er.entity.customer
                  - terms: [ { text: "aktivní" } ]
                    target: er.attribute.status.active
                  - terms: [ { text: "rok" } ]
                    target: ground:chrono
                """.trimIndent()
            val skill =
                """
                ---
                schema: ttr-skill/v1
                op: op:trend
                triggers:
                  - { text: "vývoj", lang: cs }
                version: 1
                ---
                Retrieval: group by the finest requested time grain.
                """.trimIndent()

            val result =
                LexiconCompiler.compile(
                    LexiconSources(
                        area =
                            LexiconArea(
                                listOf(dataFile("a.lex.yaml", yaml)),
                                listOf(
                                    LexiconValidator
                                        .loadSkillFile(skill, "skills/trend.md")
                                        .shouldBeInstanceOf<LexiconLoad.Ok<org.tatrman.ttr.lexicon.SkillDef>>()
                                        .value,
                                ),
                            ),
                    ),
                    index(
                        objects = setOf("er.entity.customer"),
                        members = setOf("er.attribute.status.active"),
                    ),
                    snapshotHash,
                    builtAt,
                )

            result.lexicon.entries.associate { it.termNormalized to it.targetClass } shouldBe
                mapOf(
                    "zákazník" to TargetClass.MODEL_OBJECT,
                    "aktivní" to TargetClass.MEMBER,
                    "rok" to TargetClass.GROUNDING_TRIGGER,
                    "vývoj" to TargetClass.OPERATOR,
                )
            // No `kind` column exists to disagree with the graph (RV-38).
            result.warnings shouldBe emptyList()
        }

        test("op: and ground: refs never consult the model index") {
            // They are not model objects. Classifying them through the index would make every
            // operator dangle against a snapshot that will never contain it.
            val yaml =
                """
                schema: ttr-lexicon/v1
                entries:
                  - terms: [ { text: "rok" } ]
                    target: ground:chrono
                """.trimIndent()

            val result =
                LexiconCompiler.compile(
                    LexiconSources(area = LexiconArea(listOf(dataFile("g.lex.yaml", yaml)), emptyList())),
                    ModelRefIndex.EMPTY,
                    snapshotHash,
                    builtAt,
                )

            result.lexicon.entries
                .single()
                .targetClass shouldBe TargetClass.GROUNDING_TRIGGER
            result.warnings shouldBe emptyList()
        }

        test("skill bodies go to the operator library and never into the entry table") {
            val skill =
                """
                ---
                schema: ttr-skill/v1
                op: op:trend
                triggers:
                  - { text: "vývoj", lang: cs }
                version: 3
                ---
                Formatting: line chart default; period column first.
                """.trimIndent()

            val result =
                LexiconCompiler.compile(
                    LexiconSources(
                        area =
                            LexiconArea(
                                emptyList(),
                                listOf(
                                    LexiconValidator
                                        .loadSkillFile(skill, "skills/trend.md")
                                        .shouldBeInstanceOf<LexiconLoad.Ok<org.tatrman.ttr.lexicon.SkillDef>>()
                                        .value,
                                ),
                            ),
                    ),
                    ModelRefIndex.EMPTY,
                    snapshotHash,
                    builtAt,
                )

            val op = result.operators.operators.getValue("op:trend")
            op.version shouldBe 3
            op.body shouldBe "Formatting: line chart default; period column first."

            // RV-35: the matcher sees the trigger, never the body.
            result.lexicon.entries
                .single()
                .termNormalized shouldBe "vývoj"
            result.lexicon.toJson().contains("line chart") shouldBe false
        }

        // ---- (d) the METADATA layer -------------------------------------------------------------

        test("labels already in the model compile as METADATA rows") {
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
                                                    aliases = listOf("odběratel"),
                                                    displayLabel =
                                                        LocalizedText(mapOf("cs" to "Zákazník", "en" to "Customer")),
                                                ),
                                        ),
                                ),
                        ),
                    mappings = emptyList(),
                    queries = emptyMap(),
                )

            val result =
                LexiconCompiler.compile(
                    LexiconSources(model = model),
                    index(objects = setOf("er.entity.customer")),
                    snapshotHash,
                    builtAt,
                )

            result.lexicon.entries
                .map { it.sourceTag }
                .toSet() shouldBe setOf(SourceTag.METADATA)
            result.lexicon.entries
                .map { it.termNormalized }
                .toSet() shouldBe
                setOf("zákazník", "customer", "odběratel")
            result.lexicon.entries.forEach { it.targetRef shouldBe "er.entity.customer" }
        }

        test("a declared row wins over the identical metadata row") {
            // Both layers can name the same term for the same target. The author's file is the
            // one that carries an intentional method and a line to point at in a diagnostic.
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
                                                    displayLabel = LocalizedText(mapOf("cs" to "zákazník")),
                                                ),
                                        ),
                                ),
                        ),
                    mappings = emptyList(),
                    queries = emptyMap(),
                )
            val yaml =
                """
                schema: ttr-lexicon/v1
                defaults: { lang: cs }
                entries:
                  - terms: [ { text: "zákazník" } ]
                    target: er.entity.customer
                """.trimIndent()

            val result =
                LexiconCompiler.compile(
                    LexiconSources(
                        area = LexiconArea(listOf(dataFile("a.lex.yaml", yaml)), emptyList()),
                        model = model,
                    ),
                    index(objects = setOf("er.entity.customer")),
                    snapshotHash,
                    builtAt,
                )

            result.lexicon.entries shouldHaveSize 1
            result.lexicon.entries
                .single()
                .sourceTag shouldBe SourceTag.DECLARED
            result.lexicon.entries
                .single()
                .provenance.file shouldBe "a.lex.yaml"
        }

        // ---- (e) determinism --------------------------------------------------------------------

        test("input order does not change the artifact bytes") {
            val one =
                """
                schema: ttr-lexicon/v1
                entries:
                  - terms: [ { text: "zákazník" }, { text: "odběratel" } ]
                    target: er.entity.customer
                """.trimIndent()
            val two =
                """
                schema: ttr-lexicon/v1
                entries:
                  - terms: [ { text: "faktura" } ]
                    target: er.entity.invoice
                """.trimIndent()
            val refs = index(objects = setOf("er.entity.customer", "er.entity.invoice"))

            fun run(files: List<LexiconDataFile>) =
                LexiconCompiler.compile(
                    LexiconSources(area = LexiconArea(files, emptyList())),
                    refs,
                    snapshotHash,
                    builtAt,
                )

            val a = dataFile("a.lex.yaml", one)
            val b = dataFile("b.lex.yaml", two)

            run(listOf(a, b)).lexicon.toJson() shouldBe run(listOf(b, a)).lexicon.toJson()
        }

        test("the content hash covers the vocabulary, not the build clock") {
            // The RV-39 layer tuple asks exactly one question — "did the vocabulary change?" — so
            // a hash that moved because the clock moved would answer it wrongly every build.
            val yaml =
                """
                schema: ttr-lexicon/v1
                entries:
                  - terms: [ { text: "faktura" } ]
                    target: er.entity.invoice
                """.trimIndent()

            fun run(at: String) =
                LexiconCompiler.compile(
                    LexiconSources(area = LexiconArea(listOf(dataFile("a.lex.yaml", yaml)), emptyList())),
                    index(objects = setOf("er.entity.invoice")),
                    snapshotHash,
                    at,
                )

            val early = run("2026-01-01T00:00:00Z")
            val late = run("2026-12-31T23:59:59Z")

            early.lexicon.contentHash shouldBe late.lexicon.contentHash
            early.lexicon.toJson() shouldNotBe late.lexicon.toJson()
        }
    })
