// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.yaml.snakeyaml.Yaml

/**
 * EN-P5.1 T5 — SPI-shape anti-drift. The Kotlin canon-function mirror ([CanonFunctionSig] /
 * [TypedSignature] / [TypedParam]) must expose exactly the fields of the published
 * `@tatrman/package-sdk` `spi.ts` `CanonFunction` / `TypedSignature`. The expected field lists live in
 * a provenance-stamped resource copied from the SPI (the writability-schema-copy discipline); if the
 * SPI grows a field, this fails until the mirror + the copy are updated in lockstep.
 */
class CanonFunctionSpiParitySpec :
    StringSpec({
        val expected =
            Yaml()
                .load<Map<String, Any>>(
                    CanonFunctionSpiParitySpec::class.java
                        .getResourceAsStream("/entry/canon-function-spi.json")!!
                        .reader(),
                )

        @Suppress("UNCHECKED_CAST")
        fun fields(type: String): Set<String> = (expected[type] as List<String>).toSet()

        fun kotlinFields(cls: Class<*>): Set<String> = cls.declaredFields.map { it.name }.toSet()

        "CanonFunctionSig exposes exactly the SPI CanonFunction fields (id, version, signature)" {
            kotlinFields(CanonFunctionSig::class.java) shouldBe fields("CanonFunction")
        }

        "TypedSignature exposes exactly the SPI TypedSignature fields (params, returns)" {
            kotlinFields(TypedSignature::class.java) shouldBe fields("TypedSignature")
        }

        "TypedParam exposes exactly the SPI param fields (name, type)" {
            kotlinFields(TypedParam::class.java) shouldBe fields("TypedParam")
        }
    })
