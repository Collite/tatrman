// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.params

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.calcite.rel.RelNode
import org.apache.calcite.rex.RexCall
import org.apache.calcite.rex.RexNode
import org.apache.calcite.rex.RexShuttle
import org.apache.calcite.sql.SqlKind
import org.apache.calcite.sql.`fun`.SqlStdOperatorTable
import org.tatrman.translator.codec.sql.SqlValidator
import org.tatrman.translator.codec.sql.ValidateResult
import org.tatrman.translator.framework.FixtureModel
import org.tatrman.translator.framework.TranslatorFramework

class CaseFoldingParamsSpec :
    StringSpec({

        fun typeFactory() = TranslatorFramework(FixtureModel.handle()).newRelBuilder().typeFactory

        /** Parse + validate + type params, exactly as the RESOLVE stage does before case-folding. */
        fun typedRel(
            sql: String,
            params: List<SqlParam>,
        ): RelNode {
            val prepared = ParameterBridge.prepareSqlForCalcite(sql, params)
            val fw = TranslatorFramework(FixtureModel.handle())
            val r = SqlValidator.validateAndConvert(fw.newPlanner(), prepared.sql)
            r.shouldBeInstanceOf<ValidateResult.Success>()
            return ParameterTyper.applyTypes(r.rel, prepared, typeFactory())
        }

        /** Every RexCall in the tree (descends rels and nested rexes). */
        fun calls(rel: RelNode): List<RexCall> {
            val out = mutableListOf<RexCall>()
            val shuttle =
                object : RexShuttle() {
                    override fun visitCall(call: RexCall): RexNode {
                        out.add(call)
                        return super.visitCall(call)
                    }
                }

            fun visit(n: RelNode) {
                n.accept(shuttle)
                n.inputs.forEach(::visit)
            }
            visit(rel)
            return out
        }

        fun isLower(node: RexNode) = node is RexCall && node.op == SqlStdOperatorTable.LOWER

        "text-parameter equality is folded to LOWER on both sides" {
            val rel = typedRel("SELECT id FROM customers WHERE name = {n}", listOf(SqlParam("n", "text", "Alice")))
            val folded = CaseFoldingParams.apply(rel, typeFactory())

            val equalities = calls(folded).filter { it.kind == SqlKind.EQUALS }
            // The name = ? comparison now has LOWER(...) on both operands.
            equalities.any { eq -> eq.operands.size == 2 && eq.operands.all(::isLower) } shouldBe true
        }

        "text-parameter inequality (<>) is also folded" {
            val rel = typedRel("SELECT id FROM customers WHERE name <> {n}", listOf(SqlParam("n", "text", "Alice")))
            val folded = CaseFoldingParams.apply(rel, typeFactory())

            val neq = calls(folded).filter { it.kind == SqlKind.NOT_EQUALS }
            neq.any { c -> c.operands.size == 2 && c.operands.all(::isLower) } shouldBe true
        }

        "an integer-parameter equality is NOT folded" {
            val rel = typedRel("SELECT id FROM customers WHERE id = {cid}", listOf(SqlParam("cid", "int", 7)))
            val folded = CaseFoldingParams.apply(rel, typeFactory())

            // No LOWER anywhere — nothing to case-fold on an int comparison.
            calls(folded).none(::isLower) shouldBe true
        }

        "a literal (non-parameter) text equality is left alone" {
            // A param is present (so the pass runs) but the literal comparison must not be folded —
            // only comparisons that reference a text PARAMETER are case-folded.
            val rel =
                typedRel(
                    "SELECT id FROM customers WHERE name = 'Alice' AND id = {cid}",
                    listOf(SqlParam("cid", "int", 7)),
                )
            val folded = CaseFoldingParams.apply(rel, typeFactory())
            calls(folded).none(::isLower) shouldBe true
        }

        "the parameter's positional index is preserved through folding" {
            val prepared =
                ParameterBridge.prepareSqlForCalcite(
                    "SELECT id FROM customers WHERE name = {n}",
                    listOf(SqlParam("n", "text", "Alice")),
                )
            val fw = TranslatorFramework(FixtureModel.handle())
            val r = SqlValidator.validateAndConvert(fw.newPlanner(), prepared.sql)
            r.shouldBeInstanceOf<ValidateResult.Success>()
            val typed = ParameterTyper.applyTypes(r.rel, prepared, typeFactory())
            val folded = CaseFoldingParams.apply(typed, typeFactory())

            val params = mutableListOf<org.apache.calcite.rex.RexDynamicParam>()
            val collect =
                object : RexShuttle() {
                    override fun visitDynamicParam(d: org.apache.calcite.rex.RexDynamicParam): RexNode {
                        params.add(d)
                        return d
                    }
                }

            fun visit(n: RelNode) {
                n.accept(collect)
                n.inputs.forEach(::visit)
            }
            visit(folded)
            params.size shouldBe 1
            params[0].index shouldBe 0
        }
    })
