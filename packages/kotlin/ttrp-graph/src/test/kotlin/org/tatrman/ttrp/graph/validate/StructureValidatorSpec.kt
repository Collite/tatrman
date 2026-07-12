// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.validate

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.graph.GraphFixtures
import java.nio.file.Files

/**
 * The Stage-2.1 negative corpus (T2.1.1/T2.1.6): one fixture per structural violation,
 * each producing EXACTLY its named `TTRP-CTL-*` diagnostic with no frontend noise and
 * no spurious cascade. Table-driven over `fixtures/graph/neg/`.
 */
class StructureValidatorSpec :
    StringSpec({

        val negDir = GraphFixtures.root.resolve("neg")
        val files = Files.list(negDir).sorted().toList()

        "the graph negative corpus has all seven CTL fixtures" {
            files.size shouldBe 7
        }

        files.forEach { file ->
            val content = Files.readString(file)
            val expect =
                Regex("""//\s*expect:\s*(TTRP-CTL-\d+)""").find(content)?.groupValues?.get(1)
                    ?: error("fixture ${file.fileName} has no `expect:` header")

            "${file.fileName} produces exactly $expect" {
                val r = GraphFixtures.build(content)
                // Exact-set across front-half + graph: FF is a Phase-1 reject (CTL-001);
                // CTL-002..006 come from build/validate. No spurious cascade either way.
                r.allErrorIds shouldBe setOf(expect)
            }
        }
    })
