// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import java.nio.file.Files

/**
 * The loopback `GET /out/{name}` route (T5.4.6): serves files from the current bundle out/
 * dir only, path-traversal guarded, 404 when no run output is set.
 */
class OutFilesRouteSpec :
    StringSpec({

        "serves an existing out/ file and blocks path traversal" {
            val outDir = Files.createTempDirectory("bundle-out")
            Files.writeString(outDir.resolve("main_result.arrow"), "ARROW-BYTES")
            val holder = OutDirHolder().apply { set(outDir) }

            testApplication {
                val deps = DesignerServerDeps.forRepo(MetadataFixtures.erpProjectRoot())
                application { designerServerModule(deps, watch = false, outDir = holder) }
                val client = createClient {}

                val ok = client.get("/out/main_result.arrow")
                ok.status shouldBe HttpStatusCode.OK
                ok.readRawBytes().toString(Charsets.UTF_8) shouldBe "ARROW-BYTES"

                // Path traversal is refused.
                client.get("/out/..%2f..%2fetc%2fpasswd").status shouldBe HttpStatusCode.Forbidden
                // Unknown file is a 404.
                client.get("/out/missing.arrow").status shouldBe HttpStatusCode.NotFound
            }
        }

        "404 when no run output directory is set" {
            testApplication {
                val deps = DesignerServerDeps.forRepo(MetadataFixtures.erpProjectRoot())
                application { designerServerModule(deps, watch = false, outDir = OutDirHolder()) }
                createClient {}.get("/out/x.arrow").status shouldBe HttpStatusCode.NotFound
            }
        }
    })
