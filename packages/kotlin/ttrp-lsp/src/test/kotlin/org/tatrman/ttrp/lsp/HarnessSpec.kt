package org.tatrman.ttrp.lsp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.lsp.test.TtrpLspHarness

class HarnessSpec :
    StringSpec({
        "initialize handshake advertises the Stage-4.1 capabilities" {
            TtrpLspHarness().use { h ->
                val result = h.initialize()
                val caps = result.capabilities
                caps.shouldNotBeNull()
                caps.hoverProvider.left shouldBe true
                caps.definitionProvider.left shouldBe true
                caps.renameProvider.right.prepareProvider shouldBe true
                caps.textDocumentSync.left shouldBe org.eclipse.lsp4j.TextDocumentSyncKind.Incremental
            }
        }

        "clean close after initialize does not hang" {
            val h = TtrpLspHarness()
            h.initialize()
            h.close()
        }
    })
