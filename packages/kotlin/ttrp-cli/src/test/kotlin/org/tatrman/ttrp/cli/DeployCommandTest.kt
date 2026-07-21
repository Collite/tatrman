// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.bundle.BundleTar
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

/**
 * PL-P2.S7 — `ttr deploy`: pack + stamp + multipart-deploy. The POST tests run against a JDK HttpServer
 * stub so the wire format (FormItem envelope vs FileItem bundle) and the PLT-ENV-001 hash invariant
 * (`sha256(uploaded tar) == envelope.artifact.bundleHash`) are verified without booting a real door.
 */
class DeployCommandTest :
    FunSpec({
        fun ttrp() = TtrpCommand().subcommands(DeployCommand())

        // A minimal built bundle dir (the deploy never parses the manifest — it tars the dir verbatim).
        fun bundleDir(): Path {
            val dir = Files.createTempDirectory("deploy-bundle")
            Files.writeString(
                dir.resolve("manifest.json"),
                """{"schemaVersion":2,"world":{"qname":"w","fingerprint":"sha256:w"},"islands":[],"waves":[]}""",
            )
            Files.writeString(dir.resolve("run.sh"), "echo hi\n")
            return dir
        }

        fun envelope(): Path {
            val f = Files.createTempFile("env", ".json")
            Files.writeString(
                f,
                """{"envelopeVersion":1,"name":"hero","version":1,"principal":"svc","connections":{}}""",
            )
            return f
        }

        test("--dry-run stamps artifact.bundleHash from the packed tar and does not POST") {
            val dir = bundleDir()
            val expected = BundleTar.sha256(BundleTar.pack(dir))
            val result =
                ttrp().test("deploy $dir --envelope ${envelope()} --door http://127.0.0.1:1 --dry-run")
            result.statusCode shouldBe 0
            result.stdout shouldContain "dry-run"
            result.stdout shouldContain "hero@1"
            result.stdout shouldContain expected // the injected artifact.bundleHash
        }

        test("POST /v1/envelopes: envelope is a form field, bundle a file part, hash matches the tar (PLT-ENV-001)") {
            var envelopePart: String? = null
            var bundleTar: ByteArray? = null
            var sawBundleFilename = false
            val server =
                stub(201, """{"name":"hero","version":1}""") { ex ->
                    val boundary = ex.requestHeaders.getFirst("Content-Type").substringAfter("boundary=")
                    val parts = splitMultipart(ex.requestBody.readBytes(), boundary)
                    envelopePart = parts["envelope"]?.let { String(it.bytes) }
                    bundleTar = parts["bundle"]?.bytes
                    sawBundleFilename = parts["bundle"]?.filename == "bundle.tar"
                }
            try {
                val dir = bundleDir()
                val result =
                    ttrp().test(
                        "deploy $dir --envelope ${envelope()} --door http://127.0.0.1:${server.address.port} --token t",
                    )
                result.statusCode shouldBe 0
                result.stdout shouldContain "accepted hero@1"

                sawBundleFilename shouldBe true
                bundleTar shouldNotBe null
                // The door re-derives sha256(bundle) and rejects a mismatch — the stamp must be the truth.
                val stampedHash = BundleTar.sha256(bundleTar!!)
                envelopePart!! shouldContain stampedHash
            } finally {
                server.stop(0)
            }
        }

        test("a 422 rejection surfaces the PLT-ENV issues and exits 1") {
            val server =
                stub(422, """{"issues":[{"code":"PLT-ENV-006","message":"principal `svc` does not exist"}]}""") {}
            try {
                val result =
                    ttrp().test(
                        "deploy ${bundleDir()} --envelope ${envelope()} --door http://127.0.0.1:${server.address.port} --token t",
                    )
                result.statusCode shouldBe 1
                result.stderr shouldContain "PLT-ENV-006"
            } finally {
                server.stop(0)
            }
        }

        test("a missing envelope exits 2 (pre-flight)") {
            val result =
                ttrp().test(
                    "deploy ${bundleDir()} --envelope /nonexistent/env.json --door http://127.0.0.1:1 --token t",
                )
            result.statusCode shouldBe 2
        }

        test("a target that is neither a bundle dir nor a .ttrp exits 2") {
            val result =
                ttrp().test(
                    "deploy /nonexistent/nope --envelope ${envelope()} --door http://127.0.0.1:1 --token t",
                )
            result.statusCode shouldBe 2
        }
    })

private data class Part(
    val filename: String?,
    val bytes: ByteArray,
)

/** A lenient multipart/form-data splitter — good enough to assert the deploy wire shape in tests. */
private fun splitMultipart(
    body: ByteArray,
    boundary: String,
): Map<String, Part> {
    val text = String(body, Charsets.ISO_8859_1)
    val delim = "--$boundary"
    val out = mutableMapOf<String, Part>()
    for (chunk in text.split(delim)) {
        val trimmed = chunk.trim('\r', '\n')
        if (trimmed.isEmpty() || trimmed == "--") continue
        val headerEnd = chunk.indexOf("\r\n\r\n")
        if (headerEnd < 0) continue
        val headers = chunk.substring(0, headerEnd)
        val name = Regex("name=\"([^\"]+)\"").find(headers)?.groupValues?.get(1) ?: continue
        val filename = Regex("filename=\"([^\"]+)\"").find(headers)?.groupValues?.get(1)
        val content = chunk.substring(headerEnd + 4).removeSuffix("\r\n")
        out[name] = Part(filename, content.toByteArray(Charsets.ISO_8859_1))
    }
    return out
}

private fun stub(
    status: Int,
    responseBody: String,
    capture: (HttpExchange) -> Unit,
): HttpServer {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/v1/envelopes") { ex ->
        capture(ex)
        val bytes = responseBody.toByteArray()
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }
    server.start()
    return server
}
