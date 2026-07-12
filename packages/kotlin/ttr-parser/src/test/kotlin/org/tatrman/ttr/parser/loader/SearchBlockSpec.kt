// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.model.AttributeDef
import org.tatrman.ttr.parser.model.ColumnDef
import kotlin.reflect.full.memberProperties

/**
 * v2.0.0 search-block correctness (contracts.md §2.5, AST-NAMING.md).
 *
 * The defining guard of the migration: `searchable` (and `fuzzy`) live ONLY
 * inside the `search { }` block. The old top-level `ColumnDef.searchable` /
 * `AttributeDef.searchable` field that ai-platform still carries is GONE on the
 * modeler-canonical model. These reflection checks fail to even compile/run
 * against a model that re-introduces the field, so they lock the shape down.
 */
class SearchBlockSpec :
    StringSpec({

        "column search block: searchable lands on ColumnDef.search.searchable" {
            val r =
                TtrLoader.parseString(
                    """
                    def column X {
                        type: int
                        search { searchable: true }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val c = r.definitions[0] as ColumnDef
            c.search.searchable shouldBe true
        }

        "ColumnDef has NO top-level searchable property (v2.0.0)" {
            ColumnDef::class.memberProperties.none { it.name == "searchable" } shouldBe true
        }

        "AttributeDef has NO top-level searchable property (v2.0.0)" {
            AttributeDef::class.memberProperties.none { it.name == "searchable" } shouldBe true
        }

        "old v1 top-level searchable on a column fails to parse" {
            val r =
                TtrLoader.parseString(
                    """
                    def column X {
                        type: int
                        searchable: true
                    }
                    """.trimIndent(),
                )
            r.errors.isNotEmpty() shouldBe true
        }

        "attribute fuzzy without searchable warns ttr/fuzzy-without-searchable" {
            val r =
                TtrLoader.parseString(
                    """
                    def attribute Y {
                        type: text
                        search { fuzzy: true }
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            r.warnings.any { it.message.contains("fuzzy-without-searchable") } shouldBe true
        }
    })
