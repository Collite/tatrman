// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import kotlinx.coroutines.runBlocking
import org.h2.jdbcx.JdbcDataSource
import org.tatrman.ttr.designer.server.DesignerServerDeps
import org.tatrman.ttr.designer.server.designerServerModule
import org.tatrman.ttrp.member.ConnectedMemberCatalog
import org.tatrman.ttrp.member.WsMemberSource
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

/**
 * S6-B4 — the connected/disconnected compile E2E. The SAME program carries a **bare** member
 * (`Kaufland`): disconnected it is illegal (TTRP-MD-007, R13); connected it resolves against members
 * served by a live ttr-designer-server (`ttrm/getMembers` over an H2-backed member DataSource), and
 * the run manifest records a real `memberFingerprint`. Proves the whole S6-B loop end to end:
 * WsMemberSource → DegradingMemberCatalog → TtrpChecker → bundle.
 */
class MdConnectedCompileTest :
    FunSpec({
        val projectRoot = Paths.get("src/test/resources/fixtures/md-project")
        val dbSeq = AtomicInteger()

        // A bare leading member `Kaufland` (Name is the fixture's only `publish: members` domain, bound
        // to d_customer.cust_name). The int `6` forces MdPath recognition; `month.6` is a qualified pair.
        val program =
            """
            uses world "acme.worlds.dev"

            container pick(out kept) target erp_pg {
                src  = load(erp.accounts)
                kept = filter(src, Kaufland.plan.month.6.net > 100)
            }

            pick.kept -> display(md_out)
            """.trimIndent()

        fun manifest() = TtrpManifest(world = "acme.worlds.dev", manifestDir = projectRoot)

        /** H2 seeded with the `d_customer.cust_name` member column (quoted-lowercase, PG-style). */
        fun memberDb(): JdbcDataSource =
            JdbcDataSource().apply {
                setURL("jdbc:h2:mem:md_connected_${dbSeq.incrementAndGet()};DB_CLOSE_DELAY=-1")
                user = "sa"
                connection.use { c ->
                    c.createStatement().use { it.execute("""CREATE TABLE "d_customer" ("cust_name" VARCHAR)""") }
                    c.prepareStatement("""INSERT INTO "d_customer" VALUES (?)""").use { ps ->
                        listOf("Kaufland", "Lidl", "Aldi").forEach {
                            ps.setString(1, it)
                            ps.addBatch()
                        }
                        ps.executeBatch()
                    }
                }
            }

        test("disconnected: a bare member is illegal (TTRP-MD-007) — the build fails") {
            val ex =
                shouldThrow<IllegalArgumentException> {
                    BundleAssembler("1.0.0").build(
                        source = program,
                        fileName = "connected.ttrp",
                        pipelineManifest = manifest(),
                        modelsRoot = projectRoot.resolve("models"),
                        outDir = Files.createTempDirectory("ttrp-md-disconnected"),
                        // no memberCatalog ⇒ disconnected (R13)
                    )
                }
            ex.message shouldContain "TTRP-MD-007"
        }

        test("connected: the bare member resolves against server members; manifest records the fingerprint") {
            val ds = memberDb()
            val server =
                embeddedServer(CIO, port = 0) {
                    designerServerModule(DesignerServerDeps.forRepo(projectRoot, memberDataSource = ds), watch = false)
                }
            server.start(wait = false)
            val port =
                runBlocking {
                    server.engine
                        .resolvedConnectors()
                        .first()
                        .port
                }
            val catalog = ConnectedMemberCatalog.forUrl("ws://127.0.0.1:$port/ttrm")
            try {
                val result =
                    BundleAssembler("1.0.0").build(
                        source = program,
                        fileName = "connected.ttrp",
                        pipelineManifest = manifest(),
                        modelsRoot = projectRoot.resolve("models"),
                        outDir = Files.createTempDirectory("ttrp-md-connected"),
                        memberCatalog = catalog,
                    )
                // A snapshot was taken (connected) and its fingerprint recorded beside md-asof.
                result.manifest.md.shouldNotBeNull()
                result.manifest.md!!
                    .memberFingerprint
                    .shouldNotBeNull()
                result.manifest.md!!.memberFingerprint!! shouldMatch Regex("^sha256:[0-9a-f]{64}$")
            } finally {
                server.stop(0, 0)
            }
        }

        test("the WS member source round-trips the published Name domain from the server") {
            val ds = memberDb()
            val server =
                embeddedServer(CIO, port = 0) {
                    designerServerModule(DesignerServerDeps.forRepo(projectRoot, memberDataSource = ds), watch = false)
                }
            server.start(wait = false)
            val port =
                runBlocking {
                    server.engine
                        .resolvedConnectors()
                        .first()
                        .port
                }
            WsMemberSource("ws://127.0.0.1:$port/ttrm").use { source ->
                source.publishedDomains() shouldBe setOf("Name") // simple-name key, matching the resolver
                source.distinctMembers("Name") shouldBe listOf("Aldi", "Kaufland", "Lidl") // sorted, deduped
            }
            server.stop(0, 0)
        }
    })
