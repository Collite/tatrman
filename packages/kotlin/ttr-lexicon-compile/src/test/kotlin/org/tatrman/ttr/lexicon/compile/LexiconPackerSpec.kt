// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.compile

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.lexicon.CompiledLexicon
import org.tatrman.ttr.lexicon.LexiconArchive
import org.tatrman.ttr.lexicon.LexiconArea
import org.tatrman.ttr.lexicon.LexiconDataFile
import org.tatrman.ttr.lexicon.LexiconLoad
import org.tatrman.ttr.lexicon.LexiconValidator
import org.tatrman.ttr.lexicon.OperatorLibrary
import org.tatrman.ttr.lexicon.SkillDef
import org.tatrman.ttr.lexicon.TargetClass
import org.tatrman.ttr.snapshot.SnapshotReadResult
import org.tatrman.ttr.snapshot.SnapshotReader

/**
 * RV-P1.2 T4/T5 — the archive half of the compile, under the (a3) ruling.
 *
 * The load-bearing claim tested here is the one the ruling rests on: a SERVING consumer reads
 * this archive with `ttr-snapshot` + `ttr-lexicon` and never touches the compiler.
 */
class LexiconPackerSpec :
    FunSpec({

        val snapshotHash = "sha256:" + "cd".repeat(32)

        fun compiled(): CompileResult {
            val yaml =
                """
                schema: ttr-lexicon/v1
                defaults: { lang: cs }
                entries:
                  - terms: [ { text: "středisko" }, { text: "nákladové středisko", method: TOKENS } ]
                    target: er.entity.cost_center
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

            return LexiconCompiler.compile(
                LexiconSources(
                    area =
                        LexiconArea(
                            listOf(
                                LexiconValidator
                                    .loadDataFile(yaml, "aliases/er.lex.yaml")
                                    .shouldBeInstanceOf<LexiconLoad.Ok<LexiconDataFile>>()
                                    .value,
                            ),
                            listOf(
                                LexiconValidator
                                    .loadSkillFile(skill, "skills/trend.md")
                                    .shouldBeInstanceOf<LexiconLoad.Ok<SkillDef>>()
                                    .value,
                            ),
                        ),
                ),
                ModelRefIndex { if (it == "er.entity.cost_center") TargetClass.MODEL_OBJECT else null },
                snapshotHash,
                "2026-08-02T00:00:00Z",
            )
        }

        test("packing the same compile twice produces byte-identical archives") {
            val a = LexiconPacker.pack(compiled(), snapshotHash, "ttr-lexicon-compile/test")
            val b = LexiconPacker.pack(compiled(), snapshotHash, "ttr-lexicon-compile/test")

            a.bytes.contentEquals(b.bytes) shouldBe true
            a.id shouldBe b.id
        }

        test("a changed term changes the archive id") {
            val base = LexiconPacker.pack(compiled(), snapshotHash, "ttr-lexicon-compile/test")
            val changed =
                LexiconPacker.pack(
                    compiled().let { it.copy(lexicon = it.lexicon.copy(entries = it.lexicon.entries.drop(1))) },
                    snapshotHash,
                    "ttr-lexicon-compile/test",
                )

            changed.id shouldNotBe base.id
        }

        test("a serving consumer reads it with ttr-snapshot + ttr-lexicon only") {
            // No compiler type appears below this line — that is the (a3) ruling's whole point,
            // and the reason the artifact model lives in ttr-lexicon rather than here.
            val packed = LexiconPacker.pack(compiled(), snapshotHash, "veles 0.11.2")

            val contents = SnapshotReader.read(packed.bytes).shouldBeInstanceOf<SnapshotReadResult.Ok>().contents

            contents.manifest.kind shouldBe LexiconArchive.KIND
            contents.manifest.resolvedFrom[LexiconArchive.RESOLVED_FROM_MODEL] shouldBe snapshotHash

            val lexicon = CompiledLexicon.fromJson(contents.docs.getValue(LexiconArchive.LEXICON))
            lexicon.header.modelSnapshotHash shouldBe snapshotHash
            lexicon.entries.map { it.termNormalized } shouldBe
                listOf("nákladové středisko", "středisko", "vývoj")

            val operators = OperatorLibrary.fromJson(contents.docs.getValue(LexiconArchive.OPERATORS))
            operators.operators.keys.toList() shouldBe listOf("op:trend")
            // RV-35 again, this time across the container: the body is in the other document.
            contents.docs.getValue(LexiconArchive.LEXICON).contains("time grain") shouldBe false
        }
    })
