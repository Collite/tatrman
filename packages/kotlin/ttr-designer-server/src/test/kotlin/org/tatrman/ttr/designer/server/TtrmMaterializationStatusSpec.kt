// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.h2.jdbcx.JdbcDataSource
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * S6-B5b — `ttrm/getMaterializationStatus` (dot-path contracts §7.2, D22): each bound cubelet's fact
 * table is compared against the live DB catalog (`information_schema`) — `materialized` (present +
 * complete), `declared-only` (absent), `drifted` (present, a bound column missing). DTO field names
 * pinned; per-cubelet filter.
 */
class TtrmMaterializationStatusSpec :
    StringSpec({
        val dbSeq = AtomicInteger()

        // `sales` → wide fact table f_sales; expected columns {customer_name, sale_date, net}.
        val modelSrc =
            """
            model md
            def domain Name  { type: string }
            def domain Date  { type: date }
            def domain Money { type: decimal }
            def dimension Customer { key: name, attributes: [ def attribute name { domain: md.Name, isKey: true } ] }
            def dimension Time     { key: day,  attributes: [ def attribute day  { domain: md.Date, isKey: true } ] }
            def measure net { domain: md.Money, class: additive, aggregation: sum }
            def cubelet sales { grain: [Customer.name, Time.day], measures: [net] }
            def md2db_cubelet sales_binding {
              cubelet: md.sales,
              target: { table: db.dbo.f_sales },
              shape: wide,
              attributes: { Customer.name: { column: customer_name }, Time.day: { column: sale_date } },
              measures: { net: { column: net } }
            }
            """.trimIndent()

        /** Deps over a repo carrying [modelSrc], with an H2 member DB seeded by [ddl] (may be empty). */
        fun deps(vararg ddl: String): DesignerServerDeps {
            val repo = Files.createTempDirectory("ttrm-materialize")
            Files.writeString(Files.createDirectories(repo.resolve("models")).resolve("model.ttrm"), modelSrc)
            val ds =
                JdbcDataSource().apply {
                    setURL("jdbc:h2:mem:ttrm_mat_${dbSeq.incrementAndGet()};DB_CLOSE_DELAY=-1")
                    user = "sa"
                }
            if (ddl.isNotEmpty()) {
                ds.connection.use { c -> c.createStatement().use { st -> ddl.forEach { st.execute(it) } } }
            }
            return DesignerServerDeps.forRepo(repo, memberDataSource = ds)
        }

        fun withProtocol(
            deps: DesignerServerDeps,
            block: suspend DefaultClientWebSocketSession.() -> Unit,
        ) = testApplication {
            application { designerServerModule(deps, watch = false) }
            val client = createClient { install(WebSockets) }
            client.webSocket("/ttrm") { block() }
        }

        "a present, complete fact table is materialized" {
            val d = deps("""CREATE TABLE "f_sales" ("customer_name" VARCHAR, "sale_date" DATE, "net" NUMERIC)""")
            withProtocol(d) {
                val s =
                    rpc(1, "ttrm/getMaterializationStatus")
                        .result()["statuses"]!!
                        .jsonArray
                        .single()
                        .jsonObject
                s["cubelet"]!!.jsonPrimitive.content shouldBe "sales"
                s["table"]!!.jsonPrimitive.content shouldBe "db.dbo.f_sales"
                s["status"]!!.jsonPrimitive.content shouldBe "materialized"
                s["detail"] shouldBe null
            }
        }

        "an absent fact table is declared-only" {
            withProtocol(deps()) {
                // no table created
                val s =
                    rpc(2, "ttrm/getMaterializationStatus")
                        .result()["statuses"]!!
                        .jsonArray
                        .single()
                        .jsonObject
                s["status"]!!.jsonPrimitive.content shouldBe "declared-only"
            }
        }

        "a present table missing a bound column is drifted, naming the column" {
            val d = deps("""CREATE TABLE "f_sales" ("customer_name" VARCHAR, "sale_date" DATE)""") // no net
            withProtocol(d) {
                val s =
                    rpc(3, "ttrm/getMaterializationStatus")
                        .result()["statuses"]!!
                        .jsonArray
                        .single()
                        .jsonObject
                s["status"]!!.jsonPrimitive.content shouldBe "drifted"
                s["detail"]!!.jsonPrimitive.content shouldContain "net"
            }
        }

        "the cubelet filter selects a single cubelet" {
            val d = deps("""CREATE TABLE "f_sales" ("customer_name" VARCHAR, "sale_date" DATE, "net" NUMERIC)""")
            withProtocol(d) {
                val statuses =
                    rpc(4, "ttrm/getMaterializationStatus", buildJsonObject { put("cubelet", "sales") })
                        .result()["statuses"]!!
                        .jsonArray
                statuses.size shouldBe 1
                statuses
                    .single()
                    .jsonObject["cubelet"]!!
                    .jsonPrimitive.content shouldBe "sales"
            }
        }
    })
