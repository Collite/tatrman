// SPDX-License-Identifier: Apache-2.0
package org.tatrman.validator.spi

/**
 * A published test fixture (java-test-fixtures): a [PlanValidatorPlugin] that RECORDS every context it sees
 * and returns a scripted [verdict]. Host suites (e.g. the platform organ's PluginHostTest, S2.T3) consume it
 * via `testImplementation(testFixtures("org.tatrman:ttr-validator-spi"))`. [mutatePlan] makes it ATTEMPT to
 * mutate `ctx.plan` — used to prove the host's defensive copy defeats a misbehaving plugin. The no-arg
 * secondary constructor lets a host load it through `ServiceLoader` (Kotlin default params are not a JVM
 * no-arg constructor).
 */
class RecordingFakePlugin(
    override val id: String = "recording-fake",
    override val spiVersion: Int = PlanValidatorPlugin.SPI_VERSION,
    private val verdict: Verdict = Verdict.Pass,
    private val mutatePlan: Boolean = false,
) : PlanValidatorPlugin {
    constructor() : this(id = "recording-fake")

    val seen: MutableList<ValidationContext> = mutableListOf()

    override fun validate(ctx: ValidationContext): Verdict {
        seen += ctx
        if (mutatePlan && ctx.plan.isNotEmpty()) {
            ctx.plan[0] = (ctx.plan[0] + 1).toByte()
        }
        return verdict
    }
}
