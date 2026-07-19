// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.sql

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.plan.v1.Expression
import org.tatrman.plan.v1.PlanNode
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.Coordinate
import org.tatrman.ttr.md.resolve.Explanation
import org.tatrman.ttr.md.resolve.MemberRef
import org.tatrman.ttr.md.resolve.PathShape
import org.tatrman.ttr.md.resolve.Selector
import org.tatrman.ttr.semantics.md.AggKind
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.emit.EmitDiagnosticId
import org.tatrman.ttrp.emit.TtrpEmitException
import org.tatrman.ttrp.expr.MdPath
import org.tatrman.ttrp.expr.MdResolution

/**
 * MD dot-path S4-A — the `PlanNodeBuilder` seam: an `mdPath` expression in scalar position lowers to a
 * scalar subquery when the builder carries the lowering context (bindings) + this path's S3 resolution
 * (keyed by location, the graph-side annotation). Without either, it is a specific `UNSUPPORTED_NODE`
 * — never a silent pass. This exercises `expr()` directly; the full graph→SQL path (registering the MD
 * fact table in the island ModelHandle) is a later step (S4-A4).
 */
class PlanNodeBuilderMdSeamSpec :
    StringSpec({
        val loc = SourceLocation("q.ttrp", 1, 0, 1, 20, 0, 20)
        // raw components are unused — the graph-side resolution (keyed by location) drives lowering.
        val node = MdPath(components = emptyList(), location = loc)
        val resolution =
            MdResolution(
                location = loc,
                canonical = "sales[Customer.name: \"Kaufland\"].net @ sum",
                path =
                    CanonicalPath(
                        cubelet = "sales",
                        coordinates =
                            listOf(
                                Coordinate("Customer", "Customer.name", Selector.Pinned(MemberRef("Kaufland"))),
                            ),
                        measure = "net",
                        agg = AggKind.SUM,
                    ),
                shape = PathShape(freeDims = emptyList()),
                explanation = Explanation(steps = emptyList()),
            )
        val lowering = MdPathLowering(MdFixtures.salesBindings())

        "with lowering + resolution, an mdPath expression lowers to a scalar subquery" {
            val builder = PlanNodeBuilder(mdLowering = lowering, mdResolutions = mapOf(loc to resolution))
            val expr = builder.expr(node)

            expr.exprCase shouldBe Expression.ExprCase.SUBQUERY
            expr.subquery.kind shouldBe "scalar"
            expr.subquery.subquery.nodeCase shouldBe PlanNode.NodeCase.AGGREGATE
        }

        "without a lowering context, an mdPath is UNSUPPORTED_NODE (bindings not wired)" {
            val ex =
                shouldThrow<TtrpEmitException> {
                    PlanNodeBuilder(mdResolutions = mapOf(loc to resolution)).expr(node)
                }
            ex.id shouldBe EmitDiagnosticId.UNSUPPORTED_NODE
            ex.message!! shouldContain "no binding context"
        }

        "without a resolution, an mdPath is UNSUPPORTED_NODE (not resolved by S3)" {
            val ex =
                shouldThrow<TtrpEmitException> {
                    PlanNodeBuilder(mdLowering = lowering).expr(node)
                }
            ex.id shouldBe EmitDiagnosticId.UNSUPPORTED_NODE
            ex.message!! shouldContain "unresolved"
        }
    })
