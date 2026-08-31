// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.parser.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.parser.diagnostics.DiagnosticCode
import org.tatrman.ttr.parser.model.AttributeDef
import org.tatrman.ttr.parser.model.ColumnDef
import org.tatrman.ttr.parser.model.EntityDef
import org.tatrman.ttr.parser.model.SemanticsValue
import org.tatrman.ttr.parser.model.TableDef

/**
 * Grounding Phase 1 (grammar 4.2) — the `semantics { … }` block on the four
 * attachment kinds. Mirrors the TS parser suite
 * (`packages/parser/src/__tests__/semantics-block.test.ts`); the parser stays
 * mechanical (scalar folding + duplicate bookkeeping + non-scalar rejection),
 * with all vocabulary/shape checking deferred to ttr-semantics.
 */
class SemanticsBlockSpec :
    StringSpec({

        fun str(v: SemanticsValue?): String? = (v as? SemanticsValue.Str)?.value

        // (a) — attaches on all four attachment kinds and lands on the AST node.
        "parses on an entity and lands on the node" {
            val r = TtrLoader.parseString("model er\ndef entity E { semantics { kind: period_table } }")
            r.errors shouldBe emptyList()
            val def = r.definitions[0] as EntityDef
            str(def.semantics?.entries?.get("kind")) shouldBe "period_table"
        }

        "parses on an inline attribute and lands on the node" {
            val r =
                TtrLoader.parseString(
                    "model er\ndef entity E { attributes: [ def attribute a { type: date, semantics { role: period_start } } ] }",
                )
            val attr = (r.definitions[0] as EntityDef).attributes[0]
            str(attr.semantics?.entries?.get("role")) shouldBe "period_start"
        }

        "parses on a standalone attribute" {
            val r = TtrLoader.parseString("model er\ndef attribute a { type: date, semantics { role: due_date } }")
            str((r.definitions[0] as AttributeDef).semantics?.entries?.get("role")) shouldBe "due_date"
        }

        "parses on a table and its inline column" {
            val r =
                TtrLoader.parseString(
                    "model db schema dbo\ndef table t { semantics { kind: poi }, columns: [ def column p { type: text, semantics { role: geo_point } } ] }",
                )
            r.errors shouldBe emptyList()
            val def = r.definitions[0] as TableDef
            str(def.semantics?.entries?.get("kind")) shouldBe "poi"
            str((def.columns[0] as ColumnDef).semantics?.entries?.get("role")) shouldBe "geo_point"
        }

        // (b) — entries preserved as raw key→value pairs of the right primitive shape.
        "captures id values as opaque text, strings unquoted, numbers/bools as primitives" {
            val src =
                """
                model er
                def entity E {
                    attributes: [
                        def attribute a {
                            type: date,
                            semantics { role: period_code, code_format: "yyyyMM", period: acme.AccountingPeriod, digits: 6, active: true }
                        }
                    ]
                }
                """.trimIndent()
            val e = (TtrLoader.parseString(src).definitions[0] as EntityDef).attributes[0].semantics!!.entries
            str(e["role"]) shouldBe "period_code"
            str(e["code_format"]) shouldBe "yyyyMM"
            str(e["period"]) shouldBe "acme.AccountingPeriod"
            e["digits"] shouldBe SemanticsValue.Num(6.0)
            e["active"] shouldBe SemanticsValue.Bool(true)
        }

        // (c) — duplicate key bookkeeping (search-block precedent), last-wins.
        "records a repeated key in duplicateProperties (last-wins)" {
            val def =
                TtrLoader
                    .parseString(
                        "model er\ndef entity E { semantics { role: event_date, role: document_date } }",
                    ).definitions[0] as EntityDef
            def.semantics!!.duplicateProperties shouldContain "role"
            str(def.semantics?.entries?.get("role")) shouldBe "document_date"
        }

        "a clean block yields no duplicateProperties" {
            val def =
                TtrLoader
                    .parseString(
                        "model er\ndef entity E { semantics { kind: fx_rate } }",
                    ).definitions[0] as EntityDef
            def.semantics?.duplicateProperties shouldBe emptyList()
        }

        // (e) — source location present + ANTLR-convention-correct on the block.
        "has an accurate source location on the block node" {
            val def =
                TtrLoader.parseString("model er\ndef entity E { semantics { kind: poi } }").definitions[0] as EntityDef
            val s = def.semantics!!.source
            s.line shouldBe 2
            s.endLine shouldBe 2
            (s.endColumn > s.column) shouldBe true
            (s.offsetEnd > s.offsetStart) shouldBe true
        }

        // (f) — MS: lists and nested objects are CARRIED, verbatim and unvalidated.
        //
        // They used to be rejected here so the validator's input stayed flat. That
        // flatness was never a grammar fact — `value` has always admitted `list` and
        // `object_` — it was this walker discarding structure, which left the vocabulary-v3
        // `measures:` surface unrepresentable. Which keys MAY hold a list or an object is
        // vocabulary knowledge and belongs to ttr-semantics, not to the parser.
        "carries a nested object and a list verbatim" {
            val r =
                TtrLoader.parseString(
                    "model er\ndef entity E { semantics { role: event_date, bad: { x: 1 }, worse: [1, 2] } }",
                )
            r.errors.filter { it.code == DiagnosticCode.SemanticsNonScalarValue } shouldHaveSize 0
            val e = r.definitions[0] as EntityDef
            e.semantics!!.entries["role"] shouldBe SemanticsValue.Str("event_date")
            e.semantics!!.entries["bad"] shouldBe
                SemanticsValue.ObjV(mapOf("x" to SemanticsValue.Num(1.0)))
            e.semantics!!.entries["worse"] shouldBe
                SemanticsValue.ListV(listOf(SemanticsValue.Num(1.0), SemanticsValue.Num(2.0)))
        }

        // The v3 authoring surface (MS contracts §1.1) end to end through the walker.
        "carries the v3 mention surface: name, code and a mixed measures list" {
            val r =
                TtrLoader.parseString(
                    "model er\ndef entity sales { semantics { kind: period_table, name: customer_name, " +
                        "code: doc_no, measures: [amount_czk, { attribute: quantity, aggregation: avg }] } }",
                )
            r.errors shouldHaveSize 0
            val e = r.definitions[0] as EntityDef
            e.semantics!!.entries["name"] shouldBe SemanticsValue.Str("customer_name")
            e.semantics!!.entries["code"] shouldBe SemanticsValue.Str("doc_no")
            // Both item spellings survive, in DECLARED ORDER — the first measure is the
            // default measure, so the order is contract, not incidental.
            e.semantics!!.entries["measures"] shouldBe
                SemanticsValue.ListV(
                    listOf(
                        SemanticsValue.Str("amount_czk"),
                        SemanticsValue.ObjV(
                            mapOf(
                                "attribute" to SemanticsValue.Str("quantity"),
                                "aggregation" to SemanticsValue.Str("avg"),
                            ),
                        ),
                    ),
                )
        }

        // A functionCall value still has no data meaning in a semantics block, so the
        // non-scalar diagnostic survives for exactly that case rather than being retired.
        "still rejects a functionCall value" {
            val r =
                TtrLoader.parseString(
                    "model er\ndef entity E { semantics { role: event_date, bad: now() } }",
                )
            r.errors.filter { it.code == DiagnosticCode.SemanticsNonScalarValue } shouldHaveSize 1
        }

        // Scalar-only folding proven positively: a clean block with mixed scalar
        // kinds keeps every entry (the non-scalar path above drops only the bad ones).
        "keeps every scalar entry in a clean block" {
            val e =
                TtrLoader
                    .parseString(
                        "model er\ndef entity E { semantics { role: period_code, code_format: \"yyyyMM\", digits: 6 } }",
                    ).definitions[0]
                    .let { (it as EntityDef).semantics!!.entries }
            e.keys shouldBe setOf("role", "code_format", "digits")
        }
    })
