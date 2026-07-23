// SPDX-License-Identifier: Apache-2.0
package org.tatrman.validator.spi

/**
 * The C-5-i plan-validator plugin SPI (contracts §9). A plugin runs AFTER the host's own deterministic
 * validation pipeline (RLS/DENY/MASK/TopN/coercion for the query door; the coarse connection/engine grant
 * for the program door) and returns a [Verdict]. **Plugins never rewrite plans** — there is no rewrite
 * surface on this interface, and [ValidationContext.plan] is bytes the HOST defensively copies per
 * invocation (so a misbehaving plugin cannot affect the plan the host dispatches; the host-side copy is
 * the platform organ's responsibility).
 *
 * **Discovery.** Hosts load plugins via `java.util.ServiceLoader` — each plugin jar ships a
 * `META-INF/services/org.tatrman.validator.spi.PlanValidatorPlugin` entry and a no-arg constructor — from a
 * host-configured plugin directory in an isolated classloader. No plugin installed ⇒ the host's own
 * deterministic verdict stands (a plugin can only ADD a Deny/Advise, never loosen the host).
 *
 * **Dependency-free by design.** [ValidationContext.plan] is opaque bytes so any org can implement this
 * contract without a proto/plan dependency: `plan.v1 PlanNode` bytes for [Door.QUERY] (the shared tongue,
 * D-3); the [Door.PROGRAM] door carries door-specific bytes (a program island has no PlanNode — see
 * tatrman-platform#16). A plugin declares its door relevance and returns [Verdict.Pass] for the rest.
 */
interface PlanValidatorPlugin {
    /** A stable plugin id, e.g. `"kantheon-llm-guard"` — cited in audit records. */
    val id: String

    /** The SPI major version this plugin was built against; a host rejects a plugin whose version != [SPI_VERSION]. */
    val spiVersion: Int

    /** Inspect [ctx] and return a [Verdict]. MUST NOT attempt to carry plan changes out — the only output is the verdict. */
    fun validate(ctx: ValidationContext): Verdict

    companion object {
        /** The current SPI major version. */
        const val SPI_VERSION: Int = 1
    }
}

/**
 * What a plugin sees (contracts §9). [plan] is the (host-copied) plan bytes; [principal] is enrichment,
 * never authority — the host has already authorized, and the plugin trusts the context (§3, it does not
 * parse the bearer); [door] discriminates the two entry doors.
 */
class ValidationContext(
    val plan: ByteArray,
    val principal: PrincipalInfo,
    val worldFingerprint: String,
    val door: Door,
)

/** The two validation entry doors (contracts §15). */
enum class Door { PROGRAM, QUERY }

/** The caller identity a plugin may enrich with — NOT an authority signal (the host authorizes). */
data class PrincipalInfo(
    val subject: String,
    val roles: Set<String> = emptySet(),
)

/**
 * A plugin's decision. There is no rewrite outcome — a verdict is a gate ([Deny]) or an advisory ([Advise]),
 * never a plan mutation. Composition at the host is deny-overrides: any [Deny] fails the validation.
 */
sealed interface Verdict {
    /** The plugin is satisfied — no objection. */
    data object Pass : Verdict

    /** Block the plan, with a stable [code] and a UI-safe [reason]. */
    data class Deny(
        val code: String,
        val reason: String,
    ) : Verdict

    /** Non-blocking (H-3): the host logs + audits the [warning] but lets the plan proceed. */
    data class Advise(
        val code: String,
        val warning: String,
    ) : Verdict
}
