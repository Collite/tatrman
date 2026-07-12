// SPDX-License-Identifier: Apache-2.0
package org.tatrman.plan.v1

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * TableHint round-trip coverage (contracts §3, RO-21). The `hints` field is
 * adopted into plan.v1 verbatim from ai-platform so the November repoint is a
 * pure package swap. Un-reserving field 3 is legal pre-publish
 * (rename-before-publish invariant).
 *
 * "Byte-stable" here = serialize → parse → serialize is byte-equal, the
 * strongest guarantee for a wire format that must round-trip losslessly.
 */
class TableHintRoundTripSpec :
    FunSpec({
        test("TableScanNode carrying hints round-trips byte-stable") {
            val node =
                PlanNode
                    .newBuilder()
                    .setTableScan(
                        TableScanNode
                            .newBuilder()
                            .setTable(
                                QualifiedName
                                    .newBuilder()
                                    .setSchemaCode(SchemaCode.DB)
                                    .setNamespace("dbo")
                                    .setName("customer"),
                            ).addOutputColumns(ColumnRef.newBuilder().setName("id"))
                            .addHints(TableHint.newBuilder().setName("NOLOCK"))
                            .addHints(TableHint.newBuilder().setName("INDEX").addOptions("0")),
                    ).build()

            val bytes = node.toByteArray()
            val reparsed = PlanNode.parseFrom(bytes)
            reparsed shouldBe node
            reparsed.toByteArray().toList() shouldBe bytes.toList()
            reparsed.tableScan.hintsList.map { it.name } shouldBe listOf("NOLOCK", "INDEX")
            reparsed.tableScan.getHints(1).optionsList shouldBe listOf("0")
        }

        test("ScanNode carrying hints round-trips byte-stable") {
            val node =
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
                                    .setName("orders"),
                            ).addOutputColumns(ColumnRef.newBuilder().setName("id"))
                            .addHints(TableHint.newBuilder().setName("NOLOCK"))
                            .addHints(TableHint.newBuilder().setName("INDEX").addOptions("0")),
                    ).build()

            val bytes = node.toByteArray()
            val reparsed = PlanNode.parseFrom(bytes)
            reparsed shouldBe node
            reparsed.toByteArray().toList() shouldBe bytes.toList()
            reparsed.scan.hintsList.map { it.name } shouldBe listOf("NOLOCK", "INDEX")
        }

        test("a hint-free scan is unaffected by the schema change (backward-compat)") {
            val node =
                PlanNode
                    .newBuilder()
                    .setScan(
                        ScanNode
                            .newBuilder()
                            .setObject(
                                QualifiedName
                                    .newBuilder()
                                    .setSchemaCode(SchemaCode.DB)
                                    .setName("customer"),
                            ).addOutputColumns(ColumnRef.newBuilder().setName("id")),
                    ).build()

            val reparsed = PlanNode.parseFrom(node.toByteArray())
            reparsed shouldBe node
            reparsed.scan.hintsList.isEmpty() shouldBe true
        }
    })
