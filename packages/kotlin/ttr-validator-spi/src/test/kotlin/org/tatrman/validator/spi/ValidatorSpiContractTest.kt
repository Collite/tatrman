// SPDX-License-Identifier: Apache-2.0
package org.tatrman.validator.spi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * PL-P4.S2.T1 — the contracts §9 validator-SPI surface, pinned verbatim. Once green, this is the OPEN
 * contract the platform organ hosts and third-party plugins (kantheon's llm-guard) implement: a plugin
 * returns Pass | Deny | Advise and NEVER rewrites the plan.
 */
class ValidatorSpiContractTest :
    StringSpec({
        val ctx =
            ValidationContext(
                plan = "SELECT 1".toByteArray(),
                principal = PrincipalInfo("ada", roles = setOf("finance")),
                worldFingerprint = "sha256:w",
                door = Door.QUERY,
            )

        "Verdict is exactly Pass | Deny | Advise — there is no plan-rewrite variant" {
            // The sealed hierarchy's only members are the three verdicts; none carries a (rewritten) plan.
            Verdict::class.sealedSubclasses.map { it.simpleName }.shouldContainExactlyInAnyOrder(
                "Pass",
                "Deny",
                "Advise",
            )
            Verdict.Pass.shouldBeInstanceOf<Verdict>()
            Verdict.Deny("PLT-X", "blocked").code shouldBe "PLT-X"
            Verdict.Advise("PLT-Y", "heads up").warning shouldBe "heads up"
        }

        "validate's only output is a Verdict — a plugin cannot return a plan" {
            // Compile-time contract: PlanValidatorPlugin.validate(ctx): Verdict. Exercised via the fixture.
            val v: Verdict = RecordingFakePlugin(verdict = Verdict.Pass).validate(ctx)
            v shouldBe Verdict.Pass
        }

        "ValidationContext carries plan bytes, principal, worldFingerprint, and the door" {
            ctx.plan.decodeToString() shouldBe "SELECT 1"
            ctx.principal shouldBe PrincipalInfo("ada", setOf("finance"))
            ctx.worldFingerprint shouldBe "sha256:w"
            ctx.door shouldBe Door.QUERY
        }

        "the host's defensive copy defeats a mutating plugin — the dispatched plan is untouched" {
            val original = "SELECT 1".toByteArray()
            val plugin = RecordingFakePlugin(mutatePlan = true)
            // The HOST passes a COPY per invocation (implemented host-side in S2.T4); simulate that here.
            plugin.validate(
                ValidationContext(original.copyOf(), PrincipalInfo("svc"), "sha256:w", Door.PROGRAM),
            ) shouldBe
                Verdict.Pass
            original.decodeToString() shouldBe "SELECT 1" // the plugin mutated only its own copy
            plugin.seen.size shouldBe 1
        }

        "a plugin advertises spiVersion == 1 (the current SPI major)" {
            RecordingFakePlugin().spiVersion shouldBe PlanValidatorPlugin.SPI_VERSION
            PlanValidatorPlugin.SPI_VERSION shouldBe 1
        }

        "the fixture is ServiceLoader-instantiable (no-arg constructor) — the discovery contract" {
            // A host loads plugins via java.util.ServiceLoader from a plugin dir; that needs a JVM no-arg ctor.
            val loaded = RecordingFakePlugin::class.java.getDeclaredConstructor().newInstance()
            loaded.id shouldBe "recording-fake"
            loaded.spiVersion shouldBe 1
        }
    })
