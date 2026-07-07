package org.tatrman.ttrp.lsp.edit

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.FlowBody
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.lsp.protocol.GraphEdit
import org.tatrman.ttrp.parser.TtrpParser

/**
 * β edit synthesis (T5.4.1/2): each op yields formatter-owned canonical text; the result
 * re-parses cleanly with the structural change applied; the same edit list is deterministic;
 * fragment/derived targets + unknown ops are typed errors (no partial edits).
 */
class GraphEditSynthesisSpec :
    StringSpec({

        val synth = GraphEditSynthesizer()
        val base = "uses world \"acme.worlds.dev\"\n"

        fun ok(r: GraphEditSynthesizer.Result): String {
            r.shouldBeInstanceOf<GraphEditSynthesizer.Result.Ok>()
            return r.newText
        }

        "createContainer + addNode + connect builds a clean, re-parseable program" {
            val edits =
                listOf(
                    GraphEdit(op = "createContainer", name = "db_prep", target = "erp_pg"),
                    GraphEdit(op = "addNode", canvas = "db_prep", name = "accts", kind = "Load"),
                    GraphEdit(op = "createContainer", name = "crunch", target = "polars"),
                    GraphEdit(op = "connect", from = "db_prep", to = "crunch.accounts"),
                )
            val text = ok(synth.apply(base, edits, "hero.ttrp"))
            val parsed = TtrpParser.parseString(text, "hero.ttrp")
            parsed.diagnostics.none { it.severity == Severity.ERROR } shouldBe true

            val containers = parsed.document.statements.filterIsInstance<ContainerDecl>()
            containers.map { it.name } shouldBe listOf("db_prep", "crunch")
            val dbPrep = containers.first { it.name == "db_prep" }
            val body = dbPrep.body as FlowBody
            body.statements.filterIsInstance<Assignment>().any { it.target == "accts" } shouldBe true
            // The cross-container wire is present at program level.
            text shouldContain "db_prep -> crunch.accounts"
        }

        "same edit list from the same base is byte-identical (determinism, P2/C1-d-ii)" {
            val edits =
                listOf(
                    GraphEdit(op = "createContainer", name = "db_prep", target = "erp_pg"),
                    GraphEdit(op = "addNode", canvas = "db_prep", name = "accts", kind = "Load"),
                )
            val a = ok(synth.apply(base, edits, "hero.ttrp"))
            val b = ok(synth.apply(base, edits, "hero.ttrp"))
            a shouldBe b
        }

        "assignTarget re-targets a container" {
            val start = "uses world \"acme.worlds.dev\"\n\ncontainer c1 target erp_pg {\n}\n"
            val text =
                ok(synth.apply(start, listOf(GraphEdit(op = "assignTarget", path = "c1", target = "polars")), "h.ttrp"))
            val c1 =
                TtrpParser
                    .parseString(
                        text,
                        "h.ttrp",
                    ).document.statements
                    .filterIsInstance<ContainerDecl>()
                    .first()
            c1.target.text shouldBe "polars"
        }

        "addNode into a fragment container is rejected (TTRP-EDIT-002)" {
            val start = "uses world \"acme.worlds.dev\"\n\ncontainer frag target erp_pg \"\"\"sql\nselect 1\n\"\"\"\n"
            val r =
                synth.apply(
                    start,
                    listOf(GraphEdit(op = "addNode", canvas = "frag", name = "x", kind = "Filter")),
                    "h.ttrp",
                )
            r.shouldBeInstanceOf<GraphEditSynthesizer.Result.Err>()
            (r as GraphEditSynthesizer.Result.Err).id shouldBe TtrpDiagnosticId.EDIT_002
        }

        "unknown op is a typed error (TTRP-EDIT-003), no partial edit" {
            val r = synth.apply(base, listOf(GraphEdit(op = "frobnicate")), "h.ttrp")
            r.shouldBeInstanceOf<GraphEditSynthesizer.Result.Err>()
            (r as GraphEditSynthesizer.Result.Err).id shouldBe TtrpDiagnosticId.EDIT_003
        }

        "addNode into an unknown container is TTRP-EDIT-004" {
            val r =
                synth.apply(
                    base,
                    listOf(GraphEdit(op = "addNode", canvas = "nope", name = "x", kind = "Load")),
                    "h.ttrp",
                )
            r.shouldBeInstanceOf<GraphEditSynthesizer.Result.Err>()
            (r as GraphEditSynthesizer.Result.Err).id shouldBe TtrpDiagnosticId.EDIT_004
        }
    })
