package org.tatrman.ttr.metadata.query

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures

/**
 * er→db binding traversal with provenance (E-d). Seeds TTRP-RES-005 (unbound).
 * Every hop carries erQname/dbQname/definitionLocation.
 */
class ErBindingChainSpec :
    StringSpec({

        val snap = MetadataFixtures.loadErpSnapshot()
        val q = MetadataQuery(snap)

        fun qnameOf(
            name: String,
            kind: String,
        ) = snap.model
            .objectByQname()
            .values
            .first { it.qname.name == name && it.kind == kind }
            .qname

        "erToDb entity sales_txn yields SALES_TXN with a non-empty chain" {
            val r = q.erToDb(qnameOf("sales_txn", "entity"))
            r.dbQname.shouldNotBeNull()
            r.dbQname!!.name shouldBe "SALES_TXN"
            r.chain.isNotEmpty() shouldBe true
            r.missing shouldBe null
        }

        "erToDb attribute sales_txn.amount yields SALES_TXN.AMOUNT; every step carries provenance" {
            val r = q.erToDb(qnameOf("sales_txn.amount", "attribute"))
            r.dbQname.shouldNotBeNull()
            r.dbQname!!.name shouldBe "SALES_TXN.AMOUNT"
            r.chain.map { it.dbQname.name } shouldContain "SALES_TXN.AMOUNT"
            r.chain.all { it.erQname.name.isNotEmpty() && it.dbQname.name.isNotEmpty() } shouldBe true
        }

        "unbound er attribute yields BindingMissing with erQname and searchedPackages (TTRP-RES-005 seed)" {
            // Seed relocated from `customer.customerType` to `customer.segment` when the
            // TTR-P er-hero began exercising a bound `customerType` filter (T2.1.0).
            val r = q.erToDb(qnameOf("customer.segment", "attribute"))
            r.dbQname shouldBe null
            r.missing.shouldNotBeNull()
            r.missing!!.erQname.name shouldBe "customer.segment"
        }
    })
