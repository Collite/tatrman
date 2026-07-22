// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * EN-P0.2 anti-drift gate: the compile-time [RowBatchInputKind] model stays field-parity with the
 * FROZEN FO §5 shape. It parses a provenance-stamped COPY of the platform's `row-batch.schema.json`
 * (see `/entry/row-batch.schema.json`, `$comment` header) and asserts, level by level, that the
 * model's field rosters and op/source vocabularies equal the schema's `required` sets and enums.
 *
 * A failure here means one side moved: reconcile against the §5 owner (the ratified demand shape) —
 * never silently edit one copy to match the other.
 */
class RowBatchInputParitySpec :
    StringSpec({

        val schema: JsonObject =
            RowBatchInputParitySpec::class.java
                .getResourceAsStream("/entry/row-batch.schema.json")!!
                .use { Json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject }

        fun required(node: JsonObject): List<String> = node["required"]!!.jsonArray.map { it.jsonPrimitive.content }

        fun defs(name: String): JsonObject = schema["\$defs"]!!.jsonObject[name]!!.jsonObject

        fun enumOf(node: JsonObject): List<String> = node["enum"]!!.jsonArray.map { it.jsonPrimitive.content }

        "batch envelope fields match §5 required" {
            required(schema) shouldBe RowBatchInputKind.batchFields.map { it.name }
            RowBatchInputKind.batchFields.all { it.required } shouldBe true
        }

        "proposal fields match §5 required (all present, some nullable-in-value)" {
            required(defs("proposal")) shouldBe RowBatchInputKind.proposalFields.map { it.name }
        }

        "source fields match §5 required" {
            required(defs("source")) shouldBe RowBatchInputKind.sourceFields.map { it.name }
        }

        "target is a oneOf whose two branches match the table / entity rosters (FO-32)" {
            val branches = defs("target")["oneOf"]!!.jsonArray.map { it.jsonObject }
            val requiredSets = branches.map { required(it).toSet() }
            requiredSets shouldBe
                listOf(
                    RowBatchInputKind.targetTableFields.map { it.name }.toSet(),
                    RowBatchInputKind.targetEntityFields.map { it.name }.toSet(),
                )
        }

        "the op vocabulary matches EntryOp (incl. the A1 delete-rows amendment)" {
            val schemaOps = enumOf(defs("proposal")["properties"]!!.jsonObject["op"]!!.jsonObject)
            schemaOps shouldBe EntryOp.entries.map { it.wire }
        }

        "the source-type vocabulary matches EntrySourceType" {
            val schemaTypes = enumOf(defs("source")["properties"]!!.jsonObject["type"]!!.jsonObject)
            schemaTypes shouldBe EntrySourceType.entries.map { it.wire }
        }

        "the kind discriminator + entity lowering constant match §5" {
            schema["properties"]!!
                .jsonObject["kind"]!!
                .jsonObject["const"]!!
                .jsonPrimitive.content shouldBe
                RowBatchInputKind.KIND
            defs("target")["oneOf"]!!
                .jsonArray[1]
                .jsonObject["properties"]!!
                .jsonObject["lowering"]!!
                .jsonObject["const"]!!
                .jsonPrimitive.content shouldBe RowBatchInputKind.ENTITY_LOWERING_V1
        }
    })
