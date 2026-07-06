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

        // (f) — nested object/list values are rejected into a `ttr/semantics-non-scalar`
        // diagnostic (one per offending entry). NB the Kotlin loader gates definitions
        // on walker errors (unlike the TS loader which keeps the AST alongside the
        // diagnostic), so on rejection `definitions` is empty; the contract verified
        // here is the diagnostic itself.
        "rejects a nested object/list value with a ttr/semantics-non-scalar diagnostic" {
            val r =
                TtrLoader.parseString(
                    "model er\ndef entity E { semantics { role: event_date, bad: { x: 1 }, worse: [1, 2] } }",
                )
            r.errors.filter { it.code == DiagnosticCode.SemanticsNonScalarValue } shouldHaveSize 2
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
