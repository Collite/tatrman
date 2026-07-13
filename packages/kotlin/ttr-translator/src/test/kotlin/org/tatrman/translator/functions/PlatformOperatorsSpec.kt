// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.functions

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.calcite.sql.`fun`.SqlStdOperatorTable
import org.tatrman.plan.v1.QualifiedName
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.translator.codec.sql.SqlValidator
import org.tatrman.translator.codec.sql.ValidateResult
import org.tatrman.translator.framework.InMemoryModelHandle
import org.tatrman.translator.framework.ModelAttribute
import org.tatrman.translator.framework.ModelEntity
import org.tatrman.translator.framework.SurfaceType
import org.tatrman.translator.framework.TranslatorFramework

/**
 * RG-P3 — the platform grounding operators (`period_start`/`period_end`/`geo_distance_m`) are
 * registered in the framework's operator table, so grounding recipe SQL resolves + validates.
 * Regression guard for the extraction omission that dropped `functions/PlatformOperators`.
 */
class PlatformOperatorsSpec :
    StringSpec({

        val customer =
            ModelEntity(
                qname =
                    QualifiedName
                        .newBuilder()
                        .setSchemaCode(SchemaCode.ER)
                        .setNamespace("entity")
                        .setName("customer")
                        .build(),
                attributes =
                    listOf(
                        ModelAttribute("id", SurfaceType.INT, nullable = false),
                        ModelAttribute("name", SurfaceType.TEXT, nullable = true),
                    ),
            )

        fun validate(sql: String): ValidateResult {
            val fw =
                TranslatorFramework(
                    InMemoryModelHandle(tables = emptyList(), entities = listOf(customer)),
                    schemaCode = SchemaCode.ER,
                    namespace = "entity",
                )
            return SqlValidator.validateAndConvert(fw.newPlanner(), sql)
        }

        "period_start / period_end resolve + validate (were: No match found for function signature)" {
            validate("SELECT period_start('202605') AS s FROM er.entity.customer")
                .shouldBeInstanceOf<ValidateResult.Success>()
            validate("SELECT period_end('202605', 'yyyyMM') AS e FROM er.entity.customer")
                .shouldBeInstanceOf<ValidateResult.Success>()
        }

        "geo_distance_m resolves + validates with four numeric operands" {
            validate("SELECT geo_distance_m(50.0, 14.0, 49.0, 15.0) AS d FROM er.entity.customer")
                .shouldBeInstanceOf<ValidateResult.Success>()
        }

        "the operator table exposes exactly the three platform functions, no std collisions" {
            PlatformOperators.ALL.map { it.name } shouldContainExactly
                listOf("PERIOD_START", "PERIOD_END", "GEO_DISTANCE_M")
            PlatformOperators.collisionsWith(SqlStdOperatorTable.instance()) shouldBe emptyList()
        }
    })
