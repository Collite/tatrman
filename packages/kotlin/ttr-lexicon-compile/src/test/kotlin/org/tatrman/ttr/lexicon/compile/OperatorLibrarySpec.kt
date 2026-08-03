// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.compile

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.lexicon.LexiconArea
import org.tatrman.ttr.lexicon.LexiconLoad
import org.tatrman.ttr.lexicon.LexiconValidator
import org.tatrman.ttr.lexicon.SkillDef
import org.tatrman.ttr.lexicon.sha256

/**
 * RV-P1.2 T5 — the operator library: estate overrides stdlib, and the build says so.
 */
class OperatorLibrarySpec :
    FunSpec({

        fun skill(
            file: String,
            body: String,
            version: Int = 1,
        ): SkillDef =
            LexiconValidator
                .loadSkillFile(
                    """
                    ---
                    schema: ttr-skill/v1
                    op: op:trend
                    triggers:
                      - { text: "vývoj", lang: cs }
                    version: $version
                    ---
                    $body
                    """.trimIndent(),
                    file,
                ).shouldBeInstanceOf<LexiconLoad.Ok<SkillDef>>()
                .value

        fun compile(skills: List<SkillDef>) =
            LexiconCompiler.compile(
                LexiconSources(area = LexiconArea(emptyList(), skills)),
                ModelRefIndex.EMPTY,
                "sha256:" + "ef".repeat(32),
                "2026-08-02T00:00:00Z",
            )

        test("the estate skill wins over the stdlib one, and the build notes it") {
            // Order is the loader's statement of precedence: stdlib first, estate second. The
            // compiler does not infer it from a path — an estate is free to lay its files out
            // however it likes, and a rule read off a directory name would break the first time
            // it did.
            val result =
                compile(
                    listOf(
                        skill("stdlib/skills/trend.md", "Stdlib behavior.", version = 1),
                        skill("lexicon/skills/trend.md", "Estate behavior.", version = 2),
                    ),
                )

            val op = result.operators.operators.getValue("op:trend")
            op.body shouldBe "Estate behavior."
            op.version shouldBe 2
            op.source.file shouldBe "lexicon/skills/trend.md"
            op.checksum shouldBe sha256("Estate behavior.".toByteArray(Charsets.UTF_8))

            val note = result.warnings.single()
            note.code shouldBe CompileWarning.OPERATOR_OVERRIDE
            note.message shouldBe
                "`op:trend` is defined in stdlib/skills/trend.md and overridden by lexicon/skills/trend.md"
        }

        test("one skill per op id is silent") {
            compile(listOf(skill("lexicon/skills/trend.md", "Only behavior."))).warnings shouldBe emptyList()
        }
    })
