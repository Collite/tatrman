package org.tatrman.ttrp.lsp

import com.networknt.schema.InputFormat
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.eclipse.lsp4j.Position
import org.tatrman.ttrp.lsp.protocol.AuthoringContextParams
import org.tatrman.ttrp.lsp.protocol.ExplainParams
import org.tatrman.ttrp.lsp.protocol.TranspileParams
import org.tatrman.ttrp.lsp.protocol.ValidateParams
import org.tatrman.ttrp.lsp.test.TtrpLspHarness
import java.nio.file.Files
import java.nio.file.Path

class CustomMethodsSpec :
    StringSpec({
        val schemaPath = Path.of("../../../docs/features/ttr-p/architecture/authoring-context.schema.json")

        "validate reports TTRP-EQ-001 on a broken snippet and nothing on the clean hero" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val broken = Fixtures.text("hero-broken.ttrp")
                val brokenResult =
                    h.remote.validate(ValidateParams(source = broken, uri = "file:///hero.ttrp")).get()
                brokenResult.diagnostics.map { it.code } shouldContain "TTRP-EQ-001"

                val clean = Fixtures.text("hero.ttrp")
                val cleanResult =
                    h.remote.validate(ValidateParams(source = clean, uri = "file:///hero.ttrp")).get()
                cleanResult.diagnostics.none { it.severity == "error" } shouldBe true
            }
        }

        "authoringContext at a cursor in the hero container validates against the schema" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                val text = Fixtures.text("hero.ttrp")
                h.open(uri, text)
                val cursor = Fixtures.positionOf(text, "sums = j")
                val result =
                    h.remote
                        .authoringContext(
                            AuthoringContextParams(uri, Position(cursor.line, cursor.character)),
                        ).get()
                val bundle = result.bundle

                // Structural: scope.portsAtCursor names the crunch container's ports.
                val ports =
                    bundle.getAsJsonObject("scope").getAsJsonArray("portsAtCursor").map { it.asString }
                ports shouldContain "accounts"
                ports shouldContain "result"

                // Schema validation against the committed schema file.
                val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                val schema = factory.getSchema(Files.readString(schemaPath))
                val messages = schema.validate(bundle.toString(), InputFormat.JSON)
                messages.map { it.message } shouldBe emptyList()
            }
        }

        "authoringContext with no position omits scope and still validates" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val result = h.remote.authoringContext(AuthoringContextParams(null, null)).get()
                result.bundle.has("scope") shouldBe false
                val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                val schema = factory.getSchema(Files.readString(schemaPath))
                schema.validate(result.bundle.toString(), InputFormat.JSON).map { it.message } shouldBe emptyList()
            }
        }

        "explain on the hero returns the island/wave structure" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                h.open(uri, Fixtures.text("hero.ttrp"))
                val result = h.remote.explain(ExplainParams(uri, 1)).get()
                result.ok shouldBe true
                result.text.contains("acc_prep") shouldBe true
                result.text.contains("crunch") shouldBe true
            }
        }

        "transpile produces a bundle manifest with ttrpVersion 1 and a non-empty sha256 map" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                h.open(uri, Fixtures.text("hero.ttrp"))
                val result = h.remote.transpile(TranspileParams(uri, 1)).get()
                result.manifest!!.get("ttrpVersion").asInt shouldBe 1
                result.manifest!!.getAsJsonObject("files").size() shouldBeGreaterThan 0
            }
        }

        "a stale version is rejected with ContentModified (-32801) carrying the current version" {
            TtrpLspHarness().use { h ->
                h.initialize()
                val uri = "file:///hero.ttrp"
                h.open(uri, Fixtures.text("hero.ttrp"))
                h.replace(uri, 2, Fixtures.text("hero.ttrp")) // advance to v2
                val ex =
                    runCatching { h.remote.explain(ExplainParams(uri, 1)).get() }.exceptionOrNull()
                val cause =
                    generateSequence(ex) {
                        it.cause
                    }.mapNotNull { it as? org.eclipse.lsp4j.jsonrpc.ResponseErrorException }.first()
                cause.responseError.code shouldBe -32801
                val data = cause.responseError.data.toString()
                data.contains("2") shouldBe true // current version reported for replay
            }
        }
    })
