// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.pandas

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.AssignEntry
import org.tatrman.ttrp.ast.ChainElem
import org.tatrman.ttrp.ast.DottedRef
import org.tatrman.ttrp.ast.ExprArg
import org.tatrman.ttrp.ast.GroupByEntry
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.expr.AggregateCall
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.FunctionCall

/**
 * C2-a-β / C2-c-ii: the pandas hero decomposes statement-wise into the same node shape as
 * the SQL hero (the 6.3 identity precondition) — the chain receiver is the join left,
 * assignment is the SSA label, method roster maps 1:1.
 */
class TtrPandasDecompositionSpec :
    StringSpec({
        val decomp = PandasCorpus.decompositionOf("accept/hero-crunch-pandas.ttrp", "crunch")
        val stmts = decomp.statements.filterIsInstance<Assignment>()

        fun opNames(elems: List<ChainElem>): List<String> =
            elems.map { if (it is OpCall) it.name else "ref:${(it as DottedRef).parts.joinToString(".")}" }

        "SSA labels joined / sums / result" {
            stmts.map { it.target } shouldContainExactly listOf("joined", "sums", "result")
        }

        "`joined` = accounts -> join(inner, on op.eq) -> project(3) with left/right ports" {
            val joined = stmts.single { it.target == "joined" }
            opNames(joined.chain.elements) shouldBe listOf("ref:accounts", "join", "project")
            val join = joined.chain.elements[1] as OpCall
            ((join.args.single { it.name == "type" }.value as ExprArg).expr as ColumnRef).column shouldBe "inner"
            val on = (join.args.single { it.name == "on" }.value as ExprArg).expr as FunctionCall
            on.function.value shouldBe "op.eq" // `==` folds to the SAME op.eq as `=` (S9)
            (on.args[0] as ColumnRef).let {
                it.port shouldBe "left"
                it.column shouldBe "account_id"
            }
            (on.args[1] as ColumnRef).let {
                it.port shouldBe "right"
                it.column shouldBe "customer"
            }
            val proj = joined.chain.elements[2] as OpCall
            proj.args.map {
                ((it.value as ExprArg).expr as ColumnRef).let { c ->
                    "${c.port}.${c.column}"
                }
            } shouldContainExactly
                listOf("left.account_id", "left.region", "right.amount")
        }

        "`sums` = joined -> aggregate { by region; total_amount=sum; sale_count=count }" {
            val sums = stmts.single { it.target == "sums" }
            (sums.chain.elements[0] as DottedRef).parts shouldBe listOf("joined")
            val agg = sums.chain.elements[1] as OpCall
            agg.name shouldBe "aggregate"
            agg.config!!
                .entries
                .filterIsInstance<GroupByEntry>()
                .single()
                .keys shouldBe listOf("region")
            val assigns = agg.config!!.entries.filterIsInstance<AssignEntry>()
            assigns.map { it.name } shouldContainExactly listOf("total_amount", "sale_count")
            (assigns[0].value as AggregateCall).function.value shouldBe "agg.sum"
            (assigns[1].value as AggregateCall).let {
                it.function.value shouldBe "agg.count"
                it.args.size shouldBe 0
            }
        }

        "`result` = sums -> project(3) -> sort(total_amount) -> limit(100)" {
            val result = stmts.single { it.target == "result" }
            opNames(result.chain.elements) shouldBe listOf("ref:sums", "project", "sort", "limit")
            decomp.diagnostics.filter { it.severity == org.tatrman.ttrp.diagnostics.Severity.ERROR } shouldBe
                emptyList()
        }
    })
