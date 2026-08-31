// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.semantics.semanticsblock.Vocabulary
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The lock-step between the two vocabulary twins, made executable.
 *
 * `Vocabulary.kt` and `packages/semantics/src/semantics-block/vocabulary.ts` are the SAME
 * normative table written twice, and every comment in both says so — but a comment cannot
 * fail a build. This spec reads the TS file off disk (both packages live in one repo) and
 * asserts, by crude extraction, that the version, the role roster and its order, each
 * role's family, the entity-key list and the aggregation list agree.
 *
 * Crude parsing is the point: the job is to EXPLODE when one twin moves alone, not to model
 * TypeScript. If the TS file's shape changes enough to break the regexes, the failure is
 * still the right signal — someone edited the table and has to look at both sides.
 */
class VocabularyParitySpec :
    StringSpec({

        val ts = Files.readString(repoRoot().resolve("packages/semantics/src/semantics-block/vocabulary.ts"))

        /** `['a', 'b']` → `["a", "b"]`, for the two flat exported lists. */
        fun listAfter(marker: String): List<String> =
            Regex("""${Regex.escape(marker)}[^\[]*\[([^\]]*)\]""")
                .find(ts)
                ?.groupValues
                ?.get(1)
                ?.split(",")
                ?.map { it.trim().trim('\'', '"') }
                ?.filter { it.isNotEmpty() }
                ?: error("could not find '$marker' in vocabulary.ts")

        // Each role opens at exactly two spaces of indentation inside ATTRIBUTE_ROLES; a
        // role's `family:` is the first one after its opening brace.
        val rolesBlock = ts.substringAfter("export const ATTRIBUTE_ROLES").substringBefore("} as const;")
        val roleMarks = Regex("""\n {2}(\w+): \{""").findAll(rolesBlock).toList()

        "the TS twin is parseable — the regexes still find the table" {
            // If this fails, every other case below is vacuous, so it is asserted first.
            roleMarks.size shouldBe Vocabulary.ATTRIBUTE_ROLES.size
        }

        "same vocabulary version" {
            val v =
                Regex("""SEMANTICS_VOCABULARY_VERSION = (\d+)""").find(ts)?.groupValues?.get(1)
                    ?: error("no SEMANTICS_VOCABULARY_VERSION in vocabulary.ts")
            v.toInt() shouldBe Vocabulary.SEMANTICS_VOCABULARY_VERSION
        }

        "same role roster, in the same order" {
            // Order is not cosmetic: `Suggest.nearestMatch` breaks ties to the first candidate,
            // so a reordered roster changes which "did you mean" an author is shown.
            roleMarks.map { it.groupValues[1] } shouldBe Vocabulary.ALL_ROLES
        }

        "same family for every role" {
            val tsFamilies =
                roleMarks.mapIndexed { i, m ->
                    val end = if (i + 1 < roleMarks.size) roleMarks[i + 1].range.first else rolesBlock.length
                    val segment = rolesBlock.substring(m.range.last, end)
                    val family =
                        Regex("""family: '(\w+)'""").find(segment)?.groupValues?.get(1)
                            ?: error("role '${m.groupValues[1]}' has no family in vocabulary.ts")
                    m.groupValues[1] to family
                }
            tsFamilies shouldBe Vocabulary.ATTRIBUTE_ROLES.map { (role, spec) -> role to spec.family }
        }

        "same entity/table kinds" {
            listAfter("export const ENTITY_KINDS") shouldBe Vocabulary.ENTITY_KINDS
        }

        "same entity-block keys, in the same order" {
            listAfter("export const ALL_ENTITY_KEYS") shouldBe Vocabulary.ALL_ENTITY_KEYS
        }

        "same aggregation vocabulary, and the same default" {
            listAfter("export const AGGREGATIONS") shouldBe Vocabulary.AGGREGATIONS
            val default =
                Regex("""DEFAULT_AGGREGATION: Aggregation = '(\w+)'""").find(ts)?.groupValues?.get(1)
                    ?: error("no DEFAULT_AGGREGATION in vocabulary.ts")
            default shouldBe Vocabulary.DEFAULT_AGGREGATION
        }
    })

/** The repo root — the directory holding `pnpm-workspace.yaml`, walking up from the test's cwd. */
private fun repoRoot(): Path {
    var dir: Path? = Paths.get("").toAbsolutePath()
    while (dir != null) {
        if (Files.isRegularFile(dir.resolve("pnpm-workspace.yaml"))) return dir
        dir = dir.parent
    }
    error("could not locate the repo root (no pnpm-workspace.yaml above ${Paths.get("").toAbsolutePath()})")
}
