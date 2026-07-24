// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * PL-P4.S3 (grammar 0.11) — parser-level rejects for the `security { … }` block.
 * The block is STRUCTURED (own / classify / grant / mask), not a free-form
 * `object_` bag (the `semantics`/`lexicon` precedent), precisely so that an
 * unknown verb and a row-level predicate are hard parse errors — contracts §11:
 * "row-level predicates stay Rego-side in v1". Table-driven over the shared
 * `security-negative` roster (mirrors `SemanticsNegativeSpec` / `WorldParseSpec`).
 */
class SecurityNegativeSpec :
    StringSpec({
        listOf(
            "neg-01-unknown-verb.ttrm",
            "neg-02-row-predicate.ttrm",
            "neg-03-grant-missing-on.ttrm",
        ).forEach { name ->
            "rejects $name" {
                val src = readSecurityNegativeResource(name)
                TtrLoader.parseString(src).ok shouldBe false
            }
        }
    })

private fun readSecurityNegativeResource(name: String): String =
    object {}
        .javaClass.classLoader
        .getResource("security-negative/$name")
        ?.readText()
        ?: error("missing test resource: security-negative/$name")
