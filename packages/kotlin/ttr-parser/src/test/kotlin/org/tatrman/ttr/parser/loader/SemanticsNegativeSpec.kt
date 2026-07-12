// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Grounding Phase 1 (grammar 4.2) — parser-level rejects for the `semantics { … }`
 * block. Table-driven over the shared `semantics-negative` roster (mirrors how the
 * `world-negative` roster is tested in WorldParseSpec): the block attaches ONLY to
 * table/column/entity/attribute and always requires a `{ … }` object body, so each
 * fixture must fail to parse.
 */
class SemanticsNegativeSpec :
    StringSpec({
        listOf(
            "neg-01-semantics-on-relation.ttrm",
            "neg-02-semantics-on-query.ttrm",
            "neg-03-bare-semantics.ttrm",
        ).forEach { name ->
            "rejects $name" {
                val src = readSemNegativeResource(name)
                TtrLoader.parseString(src).ok shouldBe false
            }
        }
    })

private fun readSemNegativeResource(name: String): String =
    object {}
        .javaClass.classLoader
        .getResource("semantics-negative/$name")
        ?.readText()
        ?: error("missing test resource: semantics-negative/$name")
