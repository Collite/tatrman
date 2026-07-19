// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.expr.catalog

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.expr.TtrpType

/**
 * RJ-P1 / 1.2.2 + 1.2.3 + 1.2.4 — the validity-spec loader, the reject-capability query, and the
 * internal validity functions. Pins the RJ-P0 YAMLs against drift and the internal-only hiding.
 */
class ValidityCatalogSpec :
    StringSpec({
        // ---- 1.2.2 loader ----
        "all nine shipped validity YAMLs load" {
            ValidityCatalog.all.map { it.function to it.typePair }.size shouldBe 9
        }

        "reject codes are unique across the spec set" {
            val codes = ValidityCatalog.all.map { it.code }
            codes.toSet().size shouldBe codes.size
        }

        "every regex is RE2-safe (no backreferences or lookarounds)" {
            val banned = listOf("(?=", "(?!", "(?<=", "(?<!")
            ValidityCatalog.all.mapNotNull { it.domain.regex }.forEach { rx ->
                banned.forEach { b -> (b in rx) shouldBe false }
                Regex(rx) // compiles
                Unit
            }
        }

        "every spec has a non-empty accept and reject corpus and a `->` type pair" {
            ValidityCatalog.all.forEach {
                (it.corpus.accept.isNotEmpty()) shouldBe true
                (it.corpus.reject.isNotEmpty()) shouldBe true
                ("->" in it.typePair) shouldBe true
            }
        }

        // ---- 1.2.3 reject-capability query ----
        "rejectCapability is non-null exactly for the RS-1 pairs" {
            ValidityCatalog.rejectCapability("cast", "text->int64").shouldNotBeNull().code shouldBe "TTRP-RJ-001"
            ValidityCatalog.rejectCapability("op.div", "numeric,numeric->numeric").shouldNotBeNull()
            ValidityCatalog.rejectCapability("fn.upper", "text->text").shouldBeNull()
            ValidityCatalog.rejectCapability("agg.sum", "numeric->numeric").shouldBeNull()
        }

        // ---- 1.2.4 internal validity functions ----
        "internal.* validity fns are registered but hidden from surface resolution" {
            // hidden from the surface: authoring `is_castable(...)` is an unknown function.
            BuiltinCatalog.resolve("is_castable") shouldBe emptyList()
            // reachable by the rewriter/emitter via the internal lookup.
            BuiltinCatalog.internal("internal.is_castable").shouldNotBeNull().internalOnly shouldBe true
            BuiltinCatalog.internal("internal.is_nonzero").shouldNotBeNull()
            BuiltinCatalog.internal("internal.is_parseable_dt").shouldNotBeNull()
            // a surface fn is NOT an internal fn.
            BuiltinCatalog.internal("fn.upper").shouldBeNull()
        }

        "internal validity fns are pure booleans" {
            listOf("internal.is_castable", "internal.is_nonzero", "internal.is_parseable_dt").forEach { id ->
                val e = BuiltinCatalog.internal(id).shouldNotBeNull()
                e.pure shouldBe true
                (e.returnType as ReturnTypeRule.Fixed).type shouldBe TtrpType.Bool
            }
        }

        "the v1 surface roster is all-pure (no volatile entry to trip RJ-104)" {
            BuiltinCatalog.resolve("upper").map { it.pure } shouldContainExactly listOf(true)
        }
    })
