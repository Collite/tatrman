package org.tatrman.ttrp.lsp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.lsp.protocol.TranspileParams
import org.tatrman.ttrp.lsp.test.TtrpLspHarness

class DocumentVersioningSpec :
    StringSpec({
        "transpile with a stale version fails; with the current version it succeeds" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                h.open(uri, Fixtures.text("hero.ttrp")) // v1
                h.replace(uri, 2, Fixtures.text("hero.ttrp")) // v2

                val stale = runCatching { h.remote.transpile(TranspileParams(uri, 1)).get() }
                stale.isFailure shouldBe true

                val fresh = h.remote.transpile(TranspileParams(uri, 2)).get()
                fresh.manifest!!.get("ttrpVersion").asInt shouldBe 1
            }
        }
    })
