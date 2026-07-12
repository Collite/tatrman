// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.expr.CatalogId
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.FunctionCall

/**
 * er→db early rewrite with mandatory provenance (T1.3.5, E-d). Every rewritten ref
 * carries provenance, and [ErRewrite.renderErFirst] provides the "er spelling first"
 * rendering.
 *
 * SCOPE (review-001 1.3-B): in Phase 1 the typechecker resolves against the ER-NAMED
 * columns (see `TtrpChecker.loadModelObject`), so any diagnostic already carries the
 * er spelling the analyst wrote — there is NO db-spelled diagnostic yet to re-render.
 * Wiring provenance into `TtrpDiagnostic.render()` is therefore deferred until a
 * db-spelled diagnostic can arise (Phase 2, when db-named schemas flow downstream).
 * This spec asserts provenance capture + the `renderErFirst` helper; it does NOT
 * claim the render is wired into the diagnostic pipeline.
 *
 * T2.1.0 (review-001 1.3-A resolved): the full `customer` ⋈ `sales_txn` on
 * `relation customer_sales` join arm is now expressible — the shared fixture binds
 * the er tier and the relation carries join pairs — so the `on: relation` →
 * port-qualified join-condition `Expression` synthesis is exercised here.
 */
class TtrpErRewriteSpec :
    StringSpec({

        fun check(source: String) = ResolutionFixtures.checker().check(source, "er.ttrp")

        "hero_er.ttrp resolves with zero ERRORs; every er ref is rewritten to db with provenance" {
            val r = check(ResolutionFixtures.program("hero_er.ttrp"))
            r.errors.map { it.render() } shouldBe emptyList()
            val pairs = r.rewrites.map { it.erSpelling to it.dbSpelling }
            pairs shouldContain ("customer" to "customers")
            pairs shouldContain ("sales_txn" to "SALES_TXN")
            pairs shouldContain ("customerType" to "customer_type")
            r.rewrites.all { it.provenance.originName.isNotEmpty() } shouldBe true
        }

        // review-001 1.3-B: the `renderErFirst` HELPER produces er-first text; this is
        // the E-d rendering primitive, not (yet) wired into `TtrpDiagnostic.render()`.
        "the ErRewrite.renderErFirst helper renders the er spelling first (E-d primitive)" {
            val r = check(ResolutionFixtures.program("hero_er.ttrp"))
            r.rewrites.first { it.erSpelling == "customerType" }.renderErFirst() shouldBe
                "`customerType` (bound to `customer_type`)"
        }

        // T2.1.0: `on: relation customer_sales` synthesizes a port-qualified equality
        // tree from the relation's bound join pairs (customer.id ↔ sales_txn.customer).
        // customer is the join's LEFT arm, sales_txn the RIGHT.
        "on-relation synthesizes the port-qualified join-condition Expression (left.id = right.CUSTOMER)" {
            val r = check(ResolutionFixtures.program("hero_er.ttrp"))
            val join = r.rewrites.first { it.joinCondition != null }
            join.erSpelling shouldBe "customer.id = sales_txn.customer"
            join.provenance.originQname shouldBe "er.relation.customer_sales"
            val cond = join.joinCondition as FunctionCall
            cond.function shouldBe CatalogId.EQ
            val left = cond.args[0] as ColumnRef
            val right = cond.args[1] as ColumnRef
            left.port shouldBe "left"
            left.column shouldBe "id"
            right.port shouldBe "right"
            right.column shouldBe "CUSTOMER"
        }

        // review-001 1.3-A: the SUCCESS branch of resolveRelation (endpoints match →
        // rewrite recorded) was previously exercised by no test (res-004 fixtures only
        // hit the mismatch branch). Positive happy-path for a correct 2-entity join.
        "a correct 2-entity join records the relation rewrite with provenance (RES-004 happy path)" {
            val src =
                "import erp.er.*\n" +
                    "container c target polars {\n" +
                    "    a = load(customer)\n" +
                    "    b = load(sales_txn)\n" +
                    "    j = join(left: a, right: b, on: relation customer_sales)\n" +
                    "}\n"
            val r = check(src)
            r.errors.map { it.id.id } shouldBe emptyList()
            val join = r.rewrites.first { it.joinCondition != null }
            join.provenance.originName shouldBe "customer_sales"
            join.dbSpelling shouldBe "left.id = right.CUSTOMER"
        }

        "an unbound er attribute is TTRP-RES-005" {
            // `customer.segment` is the relocated unbound seed; a filter on it → RES-005.
            val src =
                "import erp.er.*\n" +
                    "container c target polars {\n" +
                    "    x = load(customer)\n" +
                    "    x = filter(x, segment = 'vip')\n" +
                    "}\n"
            check(src).errors.map { it.id.id } shouldContain "TTRP-RES-005"
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
