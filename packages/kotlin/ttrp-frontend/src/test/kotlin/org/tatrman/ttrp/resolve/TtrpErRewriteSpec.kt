package org.tatrman.ttrp.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * er→db early rewrite with mandatory provenance (T1.3.5, E-d). Every rewritten ref
 * carries provenance; diagnostics on rewritten refs render the er spelling first.
 *
 * NOTE (scoped blocker — see tasks-p1-s1.3-resolution.md §Blockers): the design-doc
 * er-hero's `customer` ⋈ `sales_txn` on `relation customer_sales` join arm is
 * inexpressible against the shared fixture (customer / customer.customerType /
 * customer_sales are deliberately UNBOUND — customer.customerType is ttr-metadata's
 * RES-005 seed). This spec exercises the full entity→table + attribute→column rewrite
 * on the BOUND `sales_txn` arm.
 */
class TtrpErRewriteSpec :
    StringSpec({

        fun check(source: String) = ResolutionFixtures.checker().check(source, "er.ttrp")

        "hero_er.ttrp resolves with zero ERRORs; every er ref is rewritten to db with provenance" {
            val r = check(ResolutionFixtures.program("hero_er.ttrp"))
            r.errors.map { it.render() } shouldBe emptyList()
            val pairs = r.rewrites.map { it.erSpelling to it.dbSpelling }
            pairs shouldContain ("sales_txn" to "SALES_TXN")
            pairs shouldContain ("amount" to "AMOUNT")
            r.rewrites.all { it.provenance.originName.isNotEmpty() } shouldBe true
        }

        "the er→db rewrite renders the er spelling first (E-d)" {
            val r = check(ResolutionFixtures.program("hero_er.ttrp"))
            r.rewrites.first { it.erSpelling == "amount" }.renderErFirst() shouldBe "`amount` (bound to `AMOUNT`)"
        }

        "an entity with no er2db binding is TTRP-RES-005" {
            check("import erp.er.*\ncontainer c target polars { c = load(customer) }")
                .errors
                .map { it.id.id } shouldContain "TTRP-RES-005"
        }

        "a relation not between the joined entities is TTRP-RES-004" {
            val src =
                "import erp.er.*\n" +
                    "container c target polars {\n" +
                    "    a = load(sales_txn)\n" +
                    "    b = load(sales_txn)\n" +
                    "    j = join(left: a, right: b, on: relation customer_sales)\n" +
                    "}\n"
            check(src).errors.map { it.id.id } shouldContain "TTRP-RES-004"
        }
    })
