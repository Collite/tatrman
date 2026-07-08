package org.tatrman.ttrp.conform.eval

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * T7.2.5 (comparator): shape equivalence over normalized graphs — SSA names never fail a
 * match (label-free node signature); an extra node is a shape-mismatch; the `extraCalcNodes`
 * tolerance drops interposed Calc/Project from the signature.
 */
class EvalComparatorSpec :
    StringSpec({
        "identical shapes → Pass (SSA labels ignored — signature is label-free)" {
            val a = EvalTestGraphs.graphOf(listOf("filter", "aggregate", "sort", "limit"))
            val b = EvalTestGraphs.graphOf(listOf("filter", "aggregate", "sort", "limit"))
            EvalComparator.compare(a, b) shouldBe EvalComparator.Verdict.Pass
        }

        "order does not matter — the signature is a sorted multiset" {
            val a = EvalTestGraphs.graphOf(listOf("sort", "limit"))
            val b = EvalTestGraphs.graphOf(listOf("limit", "sort"))
            EvalComparator.compare(a, b) shouldBe EvalComparator.Verdict.Pass
        }

        "an extra node → ShapeMismatch (deny), with the extra render in the diff" {
            val candidate = EvalTestGraphs.graphOf(listOf("sort", "limit", "limit"))
            val expected = EvalTestGraphs.graphOf(listOf("sort", "limit"))
            val v = EvalComparator.compare(candidate, expected)
            v.shouldBeInstanceOf<EvalComparator.Verdict.ShapeMismatch>()
            (v.diff.contains("Limit(10)")) shouldBe true
        }

        "extraCalcNodes tolerance drops an interposed Calc from the signature → Pass" {
            val candidate = EvalTestGraphs.graphOf(listOf("filter", "calc", "aggregate"))
            val expected = EvalTestGraphs.graphOf(listOf("filter", "aggregate"))
            EvalComparator.compare(candidate, expected, EvalComparator.Tolerance(extraCalcNodes = true)) shouldBe
                EvalComparator.Verdict.Pass
            // …but denied by default.
            EvalComparator
                .compare(candidate, expected)
                .shouldBeInstanceOf<EvalComparator.Verdict.ShapeMismatch>()
        }
    })
