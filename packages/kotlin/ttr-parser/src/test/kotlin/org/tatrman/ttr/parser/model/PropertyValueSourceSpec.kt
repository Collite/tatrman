// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.loader.TtrLoader

/**
 * D4 — every PropertyValue variant gains a `source: SourceLocation` field.
 *
 * Generic PropertyValues surface in ANY-typed slots like RelationDef.from/to,
 * so a relation with a rich `from` object exercises StringValue, NumberValue,
 * BoolValue, NullValue, IdValue, ListValue, ObjectValue and FunctionCall in one
 * parse. The `source` accessor below is exhaustive over the sealed hierarchy, so
 * it fails to compile until every variant carries the field.
 */
class PropertyValueSourceSpec :
    StringSpec({

        // Exhaustive `when` — references `.source` on each variant. Compiles only
        // once the D4 field exists on all of them (incl. the new TripleStringValue).
        fun sourceOf(v: PropertyValue): SourceLocation =
            when (v) {
                is PropertyValue.StringValue -> v.source
                is PropertyValue.TripleStringValue -> v.source
                is PropertyValue.NumberValue -> v.source
                is PropertyValue.BoolValue -> v.source
                is PropertyValue.NullValue -> v.source
                is PropertyValue.IdValue -> v.source
                is PropertyValue.ListValue -> v.source
                is PropertyValue.ObjectValue -> v.source
                is PropertyValue.FunctionCall -> v.source
                is TaggedBlockValue -> v.source
            }

        "each PropertyValue variant inside a relation carries a populated source" {
            val r =
                TtrLoader.parseString(
                    """
                    def relation R {
                        from: {
                            name: "A",
                            count: 3,
                            active: true,
                            missing: null,
                            ref: db.dbo.X,
                            cols: ["a", "b"],
                            fn: lower("x")
                        }
                        to: db.dbo.Y
                    }
                    """.trimIndent(),
                )
            r.ok shouldBe true
            val rel = r.definitions[0] as RelationDef

            val from = rel.from as PropertyValue.ObjectValue
            // The object itself and every nested value have a real source line.
            (sourceOf(from).line >= 1) shouldBe true
            from.entries.values.forEach { (sourceOf(it).line >= 1) shouldBe true }

            // `to` is an IdValue; its source is populated too.
            (sourceOf(rel.to as PropertyValue).line >= 1) shouldBe true
        }
    })
