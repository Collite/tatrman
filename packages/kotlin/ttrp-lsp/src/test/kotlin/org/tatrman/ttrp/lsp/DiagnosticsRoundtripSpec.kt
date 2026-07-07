package org.tatrman.ttrp.lsp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.eclipse.lsp4j.DiagnosticSeverity
import org.tatrman.ttrp.lsp.test.TtrpLspHarness

class DiagnosticsRoundtripSpec :
    StringSpec({
        "broken hero (== ) publishes TTRP-EQ-001 with the suggested alternative" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero-broken.ttrp"
                h.open(uri, Fixtures.text("hero-broken.ttrp"))
                val diagnostics = h.awaitDiagnostics(uri)
                val eq = diagnostics.first { it.code.left == "TTRP-EQ-001" }
                eq.severity shouldBe DiagnosticSeverity.Error
                eq.source shouldBe "ttrp"
                eq.message shouldContain "use `=`"
                // The `==` is on the filter line — a mid-document, non-zero range.
                (eq.range.start.line > 0) shouldBe true
            }
        }

        "clean hero publishes no error diagnostics" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                h.open(uri, Fixtures.text("hero.ttrp"))
                val diagnostics = h.awaitDiagnostics(uri)
                diagnostics.filter { it.severity == DiagnosticSeverity.Error } shouldBe emptyList()
            }
        }

        "a didChange fixing the == clears the diagnostic" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero-broken.ttrp"
                val broken = Fixtures.text("hero-broken.ttrp")
                h.open(uri, broken)
                h.awaitDiagnostics(uri).map { it.code.left } shouldContain "TTRP-EQ-001"
                // Whole-document replace with the clean hero → EQ-001 clears.
                h.replace(uri, 2, Fixtures.text("hero.ttrp"))
                val after = h.awaitDiagnostics(uri)
                after.none { it.code.left == "TTRP-EQ-001" } shouldBe true
            }
        }
    })
