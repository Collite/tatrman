// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.h2.jdbcx.JdbcDataSource
import org.tatrman.ttr.designer.server.rpc.RpcCodes
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * S6-B1 — the `ttrm/getMemberDomains` + `ttrm/getMembers` protocol (dot-path contracts §7.2) over the
 * WS-spec harness, backed by an in-memory H2 member DataSource seeded with a customer dimension table.
 * Pins the DTO field names (public protocol from v1), published-domain filtering, cursor paging with a
 * fingerprint stable across pages, and the not-found shape for an unpublished domain.
 */
class TtrmMemberMethodsSpec :
    StringSpec({
        val dbSeq = AtomicInteger()

        // A published `Name` domain bound to d_customer.name; `Region` is unpublished (must not appear).
        val modelSrc =
            """
            model md
            def domain Code   { type: string }
            def domain Name   { type: string, publish: members }
            def domain Region { type: string }
            def dimension Customer {
              key: code,
              attributes: [
                def attribute code   { domain: md.Code, isKey: true },
                def attribute name   { domain: md.Name },
                def attribute region { domain: md.Region }
              ]
            }
            def md2db_domain name_src { domain: md.Name, source: { table: db.dbo.d_customer, column: name } }
            """.trimIndent()

        val customers = listOf("Aldi", "Kaufland", "Lidl", "Metro", "Rewe")

        fun deps(): DesignerServerDeps {
            val repo = Files.createTempDirectory("ttrm-members")
            val models = Files.createDirectories(repo.resolve("models"))
            Files.writeString(models.resolve("model.ttrm"), modelSrc)

            val ds =
                JdbcDataSource().apply {
                    setURL("jdbc:h2:mem:ttrm_members_${dbSeq.incrementAndGet()};DB_CLOSE_DELAY=-1")
                    user = "sa"
                }
            ds.connection.use { c ->
                c.createStatement().use { it.execute("""CREATE TABLE "d_customer" ("name" VARCHAR)""") }
                c.prepareStatement("""INSERT INTO "d_customer" VALUES (?)""").use { ps ->
                    // Insert out of order + a duplicate to prove the source sorts/de-dups.
                    (customers.reversed() + "Lidl").forEach {
                        ps.setString(1, it)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
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

        "getMemberDomains lists only published domains with count + fingerprint + placement" {
            withProtocol(deps()) {
                val domains = rpc(1, "ttrm/getMemberDomains").result()["domains"]!!.jsonArray
                domains.size shouldBe 1 // Region is unpublished ⇒ absent
                val d = domains[0].jsonObject
                d["qname"]!!.jsonPrimitive.content shouldBe "md.Name"
                d["attribute"]!!.jsonPrimitive.content shouldBe "Customer.name"
                d["dimension"]!!.jsonPrimitive.content shouldBe "Customer"
                d["count"]!!.jsonPrimitive.content shouldBe "5"
                d["fingerprint"]!!.jsonPrimitive.content shouldMatch Regex("^sha256:[0-9a-f]{64}$")
            }
        }

        "getMembers returns the sorted, de-duplicated members and a fingerprint" {
            withProtocol(deps()) {
                val r = rpc(2, "ttrm/getMembers", buildJsonObject { put("domain", "md.Name") }).result()
                r["members"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContainExactly customers
                r["nextCursor"]!!.jsonPrimitive.contentOrNullString() shouldBe null // one page holds all 5
                r["fingerprint"]!!.jsonPrimitive.content shouldMatch Regex("^sha256:[0-9a-f]{64}$")
            }
        }

        "getMembers cursor-pages with a fingerprint stable across pages" {
            withProtocol(deps()) {
                val p1 =
                    rpc(
                        3,
                        "ttrm/getMembers",
                        buildJsonObject {
                            put("domain", "md.Name")
                            put("limit", 2)
                        },
                    ).result()
                p1["members"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContainExactly
                    listOf("Aldi", "Kaufland")
                val cursor1 = p1["nextCursor"]!!.jsonPrimitive.content
                cursor1 shouldBe "Kaufland"

                val p2 =
                    rpc(
                        4,
                        "ttrm/getMembers",
                        buildJsonObject {
                            put("domain", "md.Name")
                            put("limit", 2)
                            put("cursor", cursor1)
                        },
                    ).result()
                p2["members"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContainExactly listOf("Lidl", "Metro")

                // The fingerprint spans the full domain, so it is identical on every page.
                p1["fingerprint"]!!.jsonPrimitive.content shouldBe p2["fingerprint"]!!.jsonPrimitive.content
            }
        }

        "getMembers honours a prefix filter" {
            withProtocol(deps()) {
                val r =
                    rpc(
                        5,
                        "ttrm/getMembers",
                        buildJsonObject {
                            put("domain", "md.Name")
                            put("prefix", "L")
                        },
                    ).result()
                r["members"]!!.jsonArray.map { it.jsonPrimitive.content } shouldContainExactly listOf("Lidl")
            }
        }

        "getMembers on an unpublished domain is the established not-found error" {
            withProtocol(deps()) {
                val err = rpc(6, "ttrm/getMembers", buildJsonObject { put("domain", "md.Region") })
                err.errorCode() shouldBe RpcCodes.NOT_FOUND
                err.errorKind() shouldBe "not-found"
            }
        }
    })

/** `content` for a JSON primitive, or null when it is JSON `null` (JsonNull's content is the string "null"). */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullString(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
