package org.tatrman.plan.v1

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.tatrman.dfdsl.v1.FromOp
import org.tatrman.dfdsl.v1.LimitOp
import org.tatrman.dfdsl.v1.Operation
import org.tatrman.dfdsl.v1.Pipeline

/**
 * Wire round-trip + FQCN-stability guard for the transferred proto formats
 * (contracts §2). This is the safety net for the ownership transfer: the
 * generated classes must serialize/parse losslessly AND keep their exact
 * `org.tatrman.plan.v1.*` FQCNs so kantheon consumers compile unchanged (TR-3).
 */
class WireRoundTripSpec :
    FunSpec({
        test("PlanNode (scan → filter) round-trips through protobuf") {
            val scan =
                PlanNode
                    .newBuilder()
                    .setScan(
                        ScanNode
                            .newBuilder()
                            .setObject(
                                QualifiedName
                                    .newBuilder()
                                    .setSchemaCode(SchemaCode.DB)
                                    .setNamespace("dbo")
                                    .setName("customer"),
                            ).addOutputColumns(ColumnRef.newBuilder().setName("id")),
                    ).build()
            val root =
                PlanNode
                    .newBuilder()
                    .setFilter(
                        FilterNode
                            .newBuilder()
                            .setInput(scan)
                            .setCondition(
                                Expression.newBuilder().setFunction(
                                    FunctionCall
                                        .newBuilder()
                                        .setOperation("gt")
                                        .addOperands(
                                            Expression
                                                .newBuilder()
                                                .setColumnRef(ColumnRef.newBuilder().setName("id")),
                                        ).addOperands(
                                            Expression
                                                .newBuilder()
                                                .setLiteral(Literal.newBuilder().setIntValue(10).setType("int")),
                                        ),
                                ),
                            ),
                    ).build()

            PlanNode.parseFrom(root.toByteArray()) shouldBe root
        }

        test("PipelineContext with a parameter round-trips") {
            val ctx =
                PipelineContext
                    .newBuilder()
                    .setCorrelationId("corr-1")
                    .setUserId("u-1")
                    .addParameters(
                        ParameterBinding
                            .newBuilder()
                            .setName("limit")
                            .setType("int")
                            .setValue(Value.newBuilder().setIntValue(100)),
                    ).addAuthRoles("analyst")
                    .build()

            PipelineContext.parseFrom(ctx.toByteArray()) shouldBe ctx
        }

        test("dfdsl Pipeline with two operations round-trips") {
            val pipeline =
                Pipeline
                    .newBuilder()
                    .addOps(Operation.newBuilder().setFrom(FromOp.newBuilder().setQueryRef("q1").setAlias("a")))
                    .addOps(Operation.newBuilder().setLimit(LimitOp.newBuilder().setN(10)))
                    .build()

            Pipeline.parseFrom(pipeline.toByteArray()) shouldBe pipeline
        }

        test("FQCN stability: generated class is exactly org.tatrman.plan.v1.PlanNode") {
            PlanNode::class.qualifiedName shouldBe "org.tatrman.plan.v1.PlanNode"
        }
    })
