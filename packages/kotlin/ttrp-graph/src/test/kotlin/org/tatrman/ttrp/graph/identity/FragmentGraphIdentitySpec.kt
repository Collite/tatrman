package org.tatrman.ttrp.graph.identity

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.graph.GraphFixtures
import org.tatrman.ttrp.graph.explain.NormalizedGraphJson

/**
 * THE KEY GATE (T6.3.5, partial — embedded ≡ canonical). A hero island authored as a
 * `"""sql` / `"""pandas` fragment and the SAME island written in canonical TTR-P compile
 * to BYTE-IDENTICAL normalized graphs (canonical serialization, byte compare — a
 * structural comparator would hide serializer nondeterminism, itself a P2 bug).
 *
 * The bare-`.ttr.sql`/`.ttr.py` third surface is added once bare-program wrapper synthesis
 * (T6.3.3) + full C2-d resolution (T6.3.4) land — see the Phase-6 progress doc.
 */
class FragmentGraphIdentitySpec :
    StringSpec({

        val heroSqlEmbedded =
            """
            uses world "acme.worlds.dev"
            acc = load(erp.accounts)
            sal = load(files.sales_2026, schema: sales_csv)
            container crunch(in accounts, in sales, out result) target erp_pg ""${'"'}sql
            WITH joined AS (
                SELECT accounts.account_id, accounts.region, sales.amount
                FROM accounts
                JOIN sales ON accounts.account_id = sales.customer
            ),
            sums AS (
                SELECT region, SUM(amount) AS total_amount, COUNT(*) AS sale_count
                FROM joined
                GROUP BY region
            )
            SELECT region, total_amount, sale_count
            FROM sums
            ORDER BY total_amount DESC NULLS LAST
            LIMIT 100
            ""${'"'}
            acc -> crunch.accounts
            sal -> crunch.sales
            crunch.result -> display(main_result)
            """.trimIndent()

        val heroSqlCanonical =
            """
            uses world "acme.worlds.dev"
            acc = load(erp.accounts)
            sal = load(files.sales_2026, schema: sales_csv)
            container crunch(in accounts, in sales, out result) target erp_pg {
              joined = join(left: accounts, right: sales, on: left.account_id = right.customer, type: inner) -> project(left.account_id, left.region, right.amount)
              sums = joined -> aggregate {
                group by region
                total_amount = sum(amount)
                sale_count = count()
              }
              result = sums -> project(region, total_amount, sale_count) -> sort(total_amount) -> limit(100)
            }
            acc -> crunch.accounts
            sal -> crunch.sales
            crunch.result -> display(main_result)
            """.trimIndent()

        val heroPandasEmbedded =
            """
            uses world "acme.worlds.dev"
            acc = load(erp.accounts)
            sal = load(files.sales_2026, schema: sales_csv)
            container crunch(in accounts, in sales, out result) target polars ""${'"'}pandas
            joined = accounts.join(right: sales, type: inner, on: left.account_id == right.customer).select(left.account_id, left.region, right.amount)
            sums = joined.aggregate(by: region, total_amount: sum(amount), sale_count: count())
            sums.select(region, total_amount, sale_count).sort(by: total_amount, dir: desc, nulls: last).limit(100)
            ""${'"'}
            acc -> crunch.accounts
            sal -> crunch.sales
            crunch.result -> display(main_result)
            """.trimIndent()

        val heroPandasCanonical = heroSqlCanonical.replace("target erp_pg", "target polars")

        "the SQL hero island serializes deterministically and non-trivially" {
            val a = NormalizedGraphJson.write(GraphFixtures.graphOf(heroSqlEmbedded))
            val b = NormalizedGraphJson.write(GraphFixtures.graphOf(heroSqlEmbedded))
            a shouldBe b
            // guard against a vacuous pass: the real decomposed roster must be present.
            listOf(
                "Join(type=INNER",
                "Aggregate(by=[region]",
                "agg.sum(col(amount))",
                "Sort([total_amount])",
                "Limit(100)",
            ).all { a.contains(it) } shouldBe true
        }

        "embedded \"\"\"sql ≡ canonical — byte-identical normalized graphs" {
            val embedded = NormalizedGraphJson.write(GraphFixtures.graphOf(heroSqlEmbedded))
            val canonical = NormalizedGraphJson.write(GraphFixtures.graphOf(heroSqlCanonical))
            embedded shouldBe canonical
        }

        "embedded \"\"\"pandas ≡ canonical — byte-identical normalized graphs (target polars)" {
            val embedded = NormalizedGraphJson.write(GraphFixtures.graphOf(heroPandasEmbedded))
            val canonical = NormalizedGraphJson.write(GraphFixtures.graphOf(heroPandasCanonical))
            embedded shouldBe canonical
        }
    })
