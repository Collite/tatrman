// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.members

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.h2.jdbcx.JdbcDataSource
import org.tatrman.ttr.semantics.md.DomainBinding
import java.io.PrintWriter
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * S6-A5 — [DbMemberSource] over an in-memory H2 (PG-compatible SQL surface): `SELECT DISTINCT` off
 * the md2db binding, keyset paging across many pages, refresh-driven cache, and SQL failure →
 * [MemberSourceUnavailable].
 */
class DbMemberSourceSpec :
    StringSpec({
        val dbSeq = AtomicInteger()

        /** A fresh in-memory H2 (identifiers stay case-sensitive as created — quoted lowercase, like PG). */
        fun freshDb(): JdbcDataSource =
            JdbcDataSource().apply {
                setURL("jdbc:h2:mem:members_${dbSeq.incrementAndGet()};DB_CLOSE_DELAY=-1")
                user = "sa"
            }

        fun exec(
            ds: DataSource,
            vararg sql: String,
        ) = ds.connection.use { c -> c.createStatement().use { st -> sql.forEach { st.execute(it) } } }

        val binding = DomainBinding(domain = "md.Name", table = "db.dbo.d_customer", column = "name")

        "distinctMembers returns the de-duplicated, non-null members of the backing column" {
            val ds = freshDb()
            exec(
                ds,
                """CREATE TABLE "d_customer" ("name" VARCHAR)""",
                """INSERT INTO "d_customer" VALUES ('Kaufland'),('Lidl'),('Kaufland'),('Aldi'),(NULL)""",
            )
            val source = DbMemberSource(ds, listOf(binding), published = setOf("md.Name"))
            source.distinctMembers("md.Name") shouldBe listOf("Aldi", "Kaufland", "Lidl") // sorted, deduped, no NULL
        }

        "keyset paging returns every member when the set spans several pages" {
            val ds = freshDb()
            val names = (1..25).map { "cust_%02d".format(it) }
            exec(ds, """CREATE TABLE "d_customer" ("name" VARCHAR)""")
            ds.connection.use { c ->
                c.prepareStatement("""INSERT INTO "d_customer" VALUES (?)""").use { ps ->
                    names.forEach {
                        ps.setString(1, it)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
            }
            // pageSize 10 over 25 members ⇒ 3 pages (10 + 10 + 5); all must come back, in order.
            val source = DbMemberSource(ds, listOf(binding), published = setOf("md.Name"), pageSize = 10)
            source.distinctMembers("md.Name") shouldBe names
        }

        "members are cached until invalidate() — a refresh clears, not a TTL" {
            val ds = freshDb()
            exec(
                ds,
                """CREATE TABLE "d_customer" ("name" VARCHAR)""",
                """INSERT INTO "d_customer" VALUES ('Kaufland')""",
            )
            val source = DbMemberSource(ds, listOf(binding), published = setOf("md.Name"))

            source.distinctMembers("md.Name") shouldBe listOf("Kaufland")
            exec(ds, """DROP TABLE "d_customer"""") // table gone, but the cache holds
            source.distinctMembers("md.Name") shouldBe listOf("Kaufland") // served from cache

            source.invalidate()
            shouldThrow<MemberSourceUnavailable> { source.distinctMembers("md.Name") } // now re-reads → fails
        }

        "publishedDomains keeps only published domains that have an md2db binding" {
            val ds = freshDb()
            val source =
                DbMemberSource(
                    ds,
                    listOf(binding), // only md.Name is bound
                    published = setOf("md.Name", "md.Region"), // md.Region published but unbound
                )
            source.publishedDomains() shouldBe setOf("md.Name")
        }

        "a broken connection surfaces as MemberSourceUnavailable (feeds the degradation ladder)" {
            val broken =
                object : DataSource {
                    override fun getConnection(): Connection = throw SQLException("connection refused")

                    override fun getConnection(
                        username: String?,
                        password: String?,
                    ): Connection = throw SQLException("connection refused")

                    override fun getLogWriter(): PrintWriter = throw UnsupportedOperationException()

                    override fun setLogWriter(out: PrintWriter?) = throw UnsupportedOperationException()

                    override fun setLoginTimeout(seconds: Int) = throw UnsupportedOperationException()

                    override fun getLoginTimeout(): Int = 0

                    override fun getParentLogger(): Logger = throw UnsupportedOperationException()

                    override fun <T : Any?> unwrap(iface: Class<T>?): T = throw UnsupportedOperationException()

                    override fun isWrapperFor(iface: Class<*>?): Boolean = false
                }
            val source = DbMemberSource(broken, listOf(binding), published = setOf("md.Name"))
            shouldThrow<MemberSourceUnavailable> { source.distinctMembers("md.Name") }
        }
    })
