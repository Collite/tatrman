// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.wire

import org.tatrman.plan.v1.JoinType
import org.tatrman.plan.v1.PlanNode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.calcite.rel.RelNode
import org.apache.calcite.rel.core.Join
import org.apache.calcite.rel.core.JoinRelType
import org.tatrman.translator.framework.FixtureModel
import org.tatrman.translator.framework.TranslatorFramework

/**
 * NX-A.S1 — the `plan.v1` wire + RelNode↔plan codec can represent SEMI/ANTI joins.
 *
 * No decorrelation here (that is NX-A.S2). Each case builds a Calcite `LogicalJoin`
 * of the given type by hand over two trivial fixture inputs and exercises the codec
 * both ways plus a proto-byte round-trip.
 */
class JoinCodecSemiAntiSpec :
    StringSpec({

        // Build a bare `LogicalJoin` of [type] over the fixture's `customers`/`orders`
        // tables on `customers.id = orders.customer_id`. The join is the root rel.
        fun semiAntiJoin(
            fw: TranslatorFramework,
            type: JoinRelType,
        ): RelNode {
            val builder = fw.newRelBuilder()
            builder.scan("customers").scan("orders")
            val cond =
                builder.equals(
                    builder.field(2, 0, "id"),
                    builder.field(2, 1, "customer_id"),
                )
            return builder.join(type, cond).build()
        }

        listOf(
            Triple("SEMI", JoinRelType.SEMI, JoinType.SEMI),
            Triple("ANTI", JoinRelType.ANTI, JoinType.ANTI),
        ).forEach { (label, relType, wireType) ->

            "encodes a $label join to JoinNode.$label" {
                val fw = TranslatorFramework(FixtureModel.handle())
                val rel = semiAntiJoin(fw, relType)
                val plan = PlanNodeEncoder.encode(rel)
                plan.nodeCase shouldBe PlanNode.NodeCase.JOIN
                plan.join.joinType shouldBe wireType
            }

            "decodes JoinNode.$label back to a $label LogicalJoin" {
                val fw = TranslatorFramework(FixtureModel.handle())
                val plan = PlanNodeEncoder.encode(semiAntiJoin(fw, relType))
                val decoded = PlanNodeDecoder.decode(plan, fw)
                decoded.shouldBeInstanceOf<Join>()
                (decoded as Join).joinType shouldBe relType
            }

            "$label join round-trips through proto bytes" {
                val fw = TranslatorFramework(FixtureModel.handle())
                val plan = PlanNodeEncoder.encode(semiAntiJoin(fw, relType))
                val decoded = PlanNode.parseFrom(plan.toByteArray())
                decoded shouldBe plan
            }

            "$label join survives a decode→encode identity" {
                val fw = TranslatorFramework(FixtureModel.handle())
                val plan = PlanNodeEncoder.encode(semiAntiJoin(fw, relType))
                val reencoded = PlanNodeEncoder.encode(PlanNodeDecoder.decode(plan, fw))
                reencoded.join.joinType shouldBe wireType
            }
        }
    })
