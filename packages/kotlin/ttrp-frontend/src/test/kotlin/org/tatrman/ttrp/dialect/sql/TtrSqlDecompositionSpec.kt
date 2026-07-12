// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.sql

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContainExactly
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
 * C2-a-β: the hero SQL island decomposes clause-wise into canonical AST — CTE names
 * become SSA labels (E-b's inverse), FROM/JOIN → `join(left:…, right:…, on:…, type:…)`
 * with `left`/`right` port qualifiers (C2-b-ii), GROUP BY → `aggregate {…}`, ORDER BY →
 * `sort`, LIMIT → `limit`. Asserted node-for-node.
 */
class TtrSqlDecompositionSpec :
    StringSpec({
        val decomp = SqlCorpus.decompositionOf("accept/hero-crunch.ttrp", "crunch")
        val stmts = decomp.statements.filterIsInstance<Assignment>()

        fun opNames(elems: List<ChainElem>): List<String> =
            elems.map { if (it is OpCall) it.name else "ref:${(it as DottedRef).parts.joinToString(".")}" }

        "the three CTE/final sub-chains carry SSA labels joined / sums / result" {
            stmts.map { it.target } shouldContainExactly listOf("joined", "sums", "result")
        }

        "`joined` = join(inner, on = op.eq) -> project(3)" {
            val joined = stmts.single { it.target == "joined" }
            opNames(joined.chain.elements) shouldBe listOf("join", "project")
            val join = joined.chain.elements[0] as OpCall
            // type: inner
            ((join.args.single { it.name == "type" }.value as ExprArg).expr as ColumnRef).column shouldBe "inner"
            // on: accounts.account_id = sales.customer → op.eq over left/right ports
            val on = (join.args.single { it.name == "on" }.value as ExprArg).expr as FunctionCall
            on.function.value shouldBe "op.eq"
            (on.args[0] as ColumnRef).let {
                it.port shouldBe "left"
                it.column shouldBe "account_id"
            }
            (on.args[1] as ColumnRef).let {
                it.port shouldBe "right"
                it.column shouldBe "customer"
            }
            val project = joined.chain.elements[1] as OpCall
            project.args.size shouldBe 3
        }

        "`sums` = joined -> aggregate { group by region; total_amount=sum; sale_count=count }" {
            val sums = stmts.single { it.target == "sums" }
            (sums.chain.elements[0] as DottedRef).parts shouldBe listOf("joined")
            val agg = sums.chain.elements[1] as OpCall
            agg.name shouldBe "aggregate"
            val cfg = agg.config!!.entries
            (cfg.filterIsInstance<GroupByEntry>().single().keys) shouldBe listOf("region")
            val assigns = cfg.filterIsInstance<AssignEntry>()
            assigns.map { it.name } shouldContainExactly listOf("total_amount", "sale_count")
            (assigns[0].value as AggregateCall).function.value shouldBe "agg.sum"
            (assigns[1].value as AggregateCall).let {
                it.function.value shouldBe "agg.count"
                it.args.size shouldBe 0 // COUNT(*) → agg.count with no args (== canonical count())
            }
        }

        "`result` = sums -> project(3) -> sort(total_amount) -> limit(100)" {
            val result = stmts.single { it.target == "result" }
            opNames(result.chain.elements) shouldBe listOf("ref:sums", "project", "sort", "limit")
            val sort = result.chain.elements[2] as OpCall
            ((sort.args.single().value as ExprArg).expr as ColumnRef).column shouldBe "total_amount"
            val limit = result.chain.elements[3] as OpCall
            ((limit.args.single().value as ExprArg).expr as org.tatrman.ttrp.expr.Literal)
                .let { (it.value as org.tatrman.ttrp.expr.LiteralValue.Num).raw } shouldBe "100"
        }

        "decomposition is diagnostic-clean and records both external tables as in-ports" {
            decomp.diagnostics.filter { it.severity == org.tatrman.ttrp.diagnostics.Severity.ERROR } shouldBe
                emptyList()
            decomp.derivedInPorts shouldContainExactly listOf("accounts", "sales")
            decomp shouldNotBe null
        }
    })
