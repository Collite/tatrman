// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.b

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.ChainElem
import org.tatrman.ttrp.ast.DottedRef
import org.tatrman.ttrp.ast.ExprArg
import org.tatrman.ttrp.ast.GroupByEntry
import org.tatrman.ttrp.ast.AssignEntry
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.expr.AggregateCall
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.FunctionCall

/**
 * T7.1.5: the TTR-B hero decomposes sentence-wise into canonical AST — Load/Load bind the
 * two sources (SSA), Join binds `joined`, the anaphoric tail (filter → aggregate → sort →
 * limit → display) chains off `joined`, and `Show … as region_totals` binds the terminal.
 * Asserted node-for-node (the same shape the SQL/pandas heroes decompose to).
 */
class TtrbDecompositionSpec :
    StringSpec({
        val decomp = TtrbCorpus.decompose("hero-sentences.ttrb")
        val stmts = decomp.statements.filterIsInstance<Assignment>()

        fun opNames(elems: List<ChainElem>): List<String> =
            elems.map { if (it is OpCall) it.name else "ref:${(it as DottedRef).parts.joinToString(".")}" }

        "decomposition is diagnostic-clean" {
            decomp.diagnostics.filter { it.severity == org.tatrman.ttrp.diagnostics.Severity.ERROR } shouldBe
                emptyList()
        }

        "the four SSA labels are accounts / sales / joined / region_totals" {
            stmts.map { it.target } shouldContainExactly listOf("accounts", "sales", "joined", "region_totals")
        }

        "`accounts` and `sales` are Load bindings" {
            opNames(stmts[0].chain.elements) shouldBe listOf("load")
            opNames(stmts[1].chain.elements) shouldBe listOf("load")
        }

        "`joined` = join(inner, on = op.eq over left/right ports)" {
            val join =
                stmts
                    .single { it.target == "joined" }
                    .chain.elements
                    .single() as OpCall
            join.name shouldBe "join"
            ((join.args.single { it.name == "type" }.value as ExprArg).expr as ColumnRef).column shouldBe "inner"
            val on = (join.args.single { it.name == "on" }.value as ExprArg).expr as FunctionCall
            on.function.value shouldBe "op.eq"
            (on.args[1] as ColumnRef).let {
                it.port shouldBe "right"
                it.column shouldBe "account_id"
            }
        }

        "`region_totals` = joined -> filter -> aggregate -> sort -> limit -> display" {
            val result = stmts.single { it.target == "region_totals" }
            opNames(result.chain.elements) shouldBe
                listOf("ref:joined", "filter", "aggregate", "sort", "limit", "display")
            val agg = result.chain.elements[2] as OpCall
            val cfg = agg.config!!.entries
            cfg.filterIsInstance<GroupByEntry>().single().keys shouldBe listOf("region")
            val total = cfg.filterIsInstance<AssignEntry>().single()
            total.name shouldBe "total"
            (total.value as AggregateCall).function.value shouldBe "agg.sum"
            ((total.value as AggregateCall).args.single() as ColumnRef).column shouldBe "amount"
        }

        "the bare-model load is recorded as a derived in-port (the file load + bound names are not)" {
            decomp.derivedInPorts shouldContainExactly listOf("accounts")
        }
    })
