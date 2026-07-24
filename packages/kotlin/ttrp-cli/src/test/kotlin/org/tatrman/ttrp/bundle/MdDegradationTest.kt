// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.h2.jdbcx.JdbcDataSource
import org.tatrman.ttr.designer.server.DesignerServerDeps
import org.tatrman.ttr.designer.server.designerServerModule
import org.tatrman.ttr.md.resolve.CatalogUnavailable
import org.tatrman.ttr.metadata.members.DegradingMemberCatalog
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.member.WsMemberSource
import org.tatrman.ttrp.project.TtrpManifest
import org.tatrman.ttrp.resolve.MdRepo
import org.tatrman.ttrp.resolve.TtrpChecker
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger

/**
 * S6-B5 — the GI-19 degradation ladder end to end (contracts §7.1), observed through the checker's
 * [TtrpChecker.Report] diagnostics. Same connected program as S6-B4:
 *  - catalog **unreachable at pass start** ⇒ [CatalogUnavailable] (hard error);
 *  - catalog **lost mid-session** ⇒ the held snapshot keeps resolving members + a `TTRP-MD-013` warning.
 */
class MdDegradationTest :
    FunSpec({
        val projectRoot = Paths.get("src/test/resources/fixtures/md-project")
        val modelsRoot = projectRoot.resolve("models")
        val dbSeq = AtomicInteger()

        val program =
            """
            uses world "acme.worlds.dev"

            container pick(out kept) target erp_pg {
                src  = load(erp.accounts)
                kept = filter(src, Kaufland.plan.month.6.net > 100)
            }

            pick.kept -> display(md_out)
            """.trimIndent()

        val mdModel = MdRepo.loadFrom(modelsRoot)?.model

        fun manifest() = TtrpManifest(world = "acme.worlds.dev", manifestDir = projectRoot)

        fun checker(catalog: DegradingMemberCatalog) =
            TtrpChecker(manifest(), modelsRoot, mdModel = mdModel, memberCatalog = catalog)

        fun memberDb(): JdbcDataSource =
            JdbcDataSource().apply {
                setURL("jdbc:h2:mem:md_degrade_${dbSeq.incrementAndGet()};DB_CLOSE_DELAY=-1")
                user = "sa"
                connection.use { c ->
                    c.createStatement().use { it.execute("""CREATE TABLE "d_customer" ("cust_name" VARCHAR)""") }
                    c.prepareStatement("""INSERT INTO "d_customer" VALUES ('Kaufland'),('Lidl')""").use { it.execute() }
                }
            }

        fun startServer(): EmbeddedServer<*, *> =
            embeddedServer(CIO, port = 0) {
                designerServerModule(
                    DesignerServerDeps.forRepo(projectRoot, memberDataSource = memberDb()),
                    watch = false,
                )
            }.also { it.start(wait = false) }

        fun port(s: EmbeddedServer<*, *>) =
            runBlocking {
                s.engine
                    .resolvedConnectors()
                    .first()
                    .port
            }

        test("catalog unreachable at pass start is a hard error (CatalogUnavailable)") {
            // Claim a port, then free it, so nothing is listening — the connect is refused.
            val dead = startServer()
            val deadPort = port(dead)
            dead.stop(0, 0)
            WsMemberSource("ws://127.0.0.1:$deadPort/ttrm").use { source ->
                shouldThrow<CatalogUnavailable> { checker(DegradingMemberCatalog(source)).check(program) }
            }
        }

        test("catalog lost mid-session keeps resolving on the held snapshot + surfaces TTRP-MD-013") {
            val server = startServer()
            WsMemberSource("ws://127.0.0.1:${port(server)}/ttrm").use { source ->
                val catalog = DegradingMemberCatalog(source)

                // Pass 1 — server up: bare `Kaufland` resolves, no staleness.
                val r1 = checker(catalog).check(program)
                r1.errors.shouldBeEmpty()
                r1.diagnostics.filter { it.id == TtrpDiagnosticId.MD_013 }.shouldBeEmpty()

                server.stop(0, 0) // catalog lost mid-session

                // Pass 2 — same catalog: still resolves (held snapshot) but warns MD-013.
                val r2 = checker(catalog).check(program)
                r2.errors.shouldBeEmpty() // held snapshot still resolves Kaufland — no hard failure
                val stale = r2.diagnostics.filter { it.id == TtrpDiagnosticId.MD_013 }
                stale.shouldNotBeEmpty()
                stale.single().severity shouldBe Severity.WARNING
            }
        }
    })
