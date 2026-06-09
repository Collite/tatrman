package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.checkAll

/**
 * Property-based check (Phase 2.4.9): for a single entity in package P (schema
 * `er`, namespace `entity`), a bare reference from within P resolves at the
 * same-package step, while its fully-qualified name resolves at step 6.
 */
class ResolverPropertySpec :
    StringSpec({
        val names =
            Arb.element(
                "alpha",
                "beta",
                "gamma",
                "delta",
                "epsilon",
                "zeta",
                "eta",
                "theta",
                "artikl",
                "produkt",
                "subjekt",
                "faktura",
                "polozka",
                "sklad",
            )

        "bare ref resolves same-package; FQN resolves fully-qualified" {
            checkAll(20, names, names) { pkg, entity ->
                val t =
                    Fixtures.symbolTable(
                        "$pkg/$entity.ttr" to
                            "package $pkg\nschema er namespace entity\ndef entity $entity { attributes: [] }",
                    )
                val resolver = Resolver(t)

                val bare =
                    resolver.resolveReference(
                        Resolver.Ref(entity, listOf(entity)),
                        ResolutionContext(schemaCode = "er", namespace = "entity", packageName = pkg),
                    )
                bare.shouldBeInstanceOf<ResolutionResult.Resolved>().viaStep shouldBe ResolutionStep.SamePackage

                val fqn = "$pkg.er.entity.$entity"
                val full =
                    resolver.resolveReference(
                        Resolver.Ref(fqn, fqn.split('.')),
                        ResolutionContext(schemaCode = "er", namespace = "entity", packageName = pkg),
                    )
                full.shouldBeInstanceOf<ResolutionResult.Resolved>().viaStep shouldBe ResolutionStep.FullyQualified
            }
        }
    })
