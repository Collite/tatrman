// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.tatrman.ttr.metadata.writability.EntityWritability
import org.tatrman.ttr.metadata.writability.Lowering
import org.tatrman.ttr.metadata.writability.WhyNot
import org.tatrman.ttr.metadata.writability.WhyNotCode
import org.tatrman.ttr.metadata.writability.WritabilityPayload

/**
 * EN-P1.2 T2 — anti-drift: the classifier's serialized §7 payload stays byte-compatible with the FROZEN
 * FO §7 shape. Drives the assertions FROM a provenance-stamped copy of the platform's
 * `writability.schema.json` (see `/entry/writability.schema.json`, `$comment`): the schema's `enum`s +
 * `required` sets + `additionalProperties:false` are checked against the code's `WhyNotCode` and against
 * a real serialized payload instance. A failure means one side moved — reconcile against the FO §7 owner.
 */
class WritabilitySchemaCompatSpec :
    StringSpec({

        val schema: JsonObject =
            WritabilitySchemaCompatSpec::class.java
                .getResourceAsStream("/entry/writability.schema.json")!!
                .use { Json.parseToJsonElement(it.readBytes().decodeToString()).jsonObject }

        val entityDefs =
            schema["\$defs"]!!
                .jsonObject["entity"]!!
                .jsonObject["oneOf"]!!
                .jsonArray
                .map { it.jsonObject }
        val writableBranch = entityDefs.first { it["properties"]!!.jsonObject.containsKey("rung") }
        val notWritableBranch = entityDefs.first { it["properties"]!!.jsonObject.containsKey("whyNot") }

        fun required(o: JsonObject): Set<String> = o["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()

        "the whyNot code enum matches WhyNotCode" {
            val schemaCodes =
                notWritableBranch["properties"]!!
                    .jsonObject["whyNot"]!!
                    .jsonObject["properties"]!!
                    .jsonObject["code"]!!
                    .jsonObject["enum"]!!
                    .jsonArray
                    .map { it.jsonPrimitive.content }
                    .toSet()
            schemaCodes shouldBe WhyNotCode.entries.map { it.name }.toSet()
        }

        "rung v1 is an accepted rung in the schema" {
            val rungs =
                writableBranch["properties"]!!
                    .jsonObject["rung"]!!
                    .jsonObject["enum"]!!
                    .jsonArray
                    .map { it.jsonPrimitive.content }
            rungs.contains("v1") shouldBe true
        }

        "a serialized payload validates structurally against the schema (required + additionalProperties + enum)" {
            val payload =
                WritabilityPayload(
                    modelVersion = "mv-1",
                    entities =
                        listOf(
                            EntityWritability.Writable("er.entity.a", "v1", Lowering("T", mapOf("id" to "ID"))),
                            EntityWritability.NotWritable(
                                "er.entity.b",
                                WhyNot(WhyNotCode.NO_DECLARED_WRITEBACK, "no binding", "rung-v3"),
                            ),
                        ),
                )
            val root = Json.parseToJsonElement(payload.toJson()).jsonObject

            // top-level required + closed
            required(schema).all { root.containsKey(it) } shouldBe true
            root.keys shouldBe setOf("modelVersion", "entities")

            val entities = root["entities"]!!.jsonArray.map { it.jsonObject }
            val writable = entities.first { it["writable"]!!.jsonPrimitive.content == "true" }
            val notWritable = entities.first { it["writable"]!!.jsonPrimitive.content == "false" }

            // each branch carries exactly the schema's required keys (payload emits no optionals here
            // except lowering.binding / whyNot.unlockedBy which are populated), no extras.
            required(writableBranch).all { writable.containsKey(it) } shouldBe true
            writable.keys.all { it in setOf("qname", "writable", "rung", "lowering") } shouldBe true
            writable["lowering"]!!.jsonObject.keys.all { it in setOf("baseTable", "binding") } shouldBe true

            required(notWritableBranch).all { notWritable.containsKey(it) } shouldBe true
            notWritable.keys.all { it in setOf("qname", "writable", "whyNot") } shouldBe true
            notWritable["whyNot"]!!.jsonObject.keys.all { it in setOf("code", "detail", "unlockedBy") } shouldBe true
        }
    })
