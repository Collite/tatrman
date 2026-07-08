package org.tatrman.ttrp.dialect.sql

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.ExprArg
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.expr.ColumnRef
import org.tatrman.ttrp.expr.FunctionCall

/**
 * T6.1.6 semi/anti desugar. `x IN (subquery)` → SEMI join correlated on `left.x = right.y`;
 * `x NOT IN` / `NOT EXISTS` → ANTI. Guards the regression where a leading `NOT EXISTS` was
 * emitted as SEMI (its negation was computed in `unwrapPredicate` and discarded).
 */
class TtrSqlSemiAntiSpec :
    StringSpec({
        fun joinOf(container: String): OpCall {
            val decomp = SqlCorpus.decompositionOf("accept/semi-anti-forms.ttrp", container)
            val assign =
                decomp.statements
                    .filterIsInstance<Assignment>()
                    .last { a -> a.chain.elements.any { it is OpCall && it.name == "join" } }
            return assign.chain.elements.first { it is OpCall && it.name == "join" } as OpCall
        }

        fun typeOf(join: OpCall): String =
            ((join.args.single { it.name == "type" }.value as ExprArg).expr as ColumnRef).column

        fun onOf(join: OpCall): FunctionCall? =
            join.args.firstOrNull { it.name == "on" }?.let { (it.value as ExprArg).expr as FunctionCall }

        "`x IN (subquery)` → SEMI join correlated on left.x = right.y" {
            val join = joinOf("c_in")
            typeOf(join) shouldBe "semi"
            val on = onOf(join)!!
            on.function.value shouldBe "op.eq"
            (on.args[0] as ColumnRef).let {
                it.port shouldBe "left"
                it.column shouldBe "account_id"
            }
            (on.args[1] as ColumnRef).let {
                it.port shouldBe "right"
                it.column shouldBe "customer"
            }
        }

        "`x NOT IN (subquery)` → ANTI join" {
            typeOf(joinOf("c_notin")) shouldBe "anti"
        }

        "`EXISTS (subquery)` → SEMI join (unconditioned)" {
            val join = joinOf("c_exists")
            typeOf(join) shouldBe "semi"
            onOf(join) shouldBe null
        }

        "`NOT EXISTS (subquery)` → ANTI join (regression: was SEMI)" {
            typeOf(joinOf("c_notexists")) shouldBe "anti"
        }
    })
