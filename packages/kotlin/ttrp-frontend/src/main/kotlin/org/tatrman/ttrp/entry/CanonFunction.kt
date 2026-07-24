// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

/*
 * EN-P5 call-fn — the tatrman-side view of the canon-function SPI (contracts §6; the A3 discharge).
 * These types mirror the published `@tatrman/package-sdk` `CanonFunction`/`TypedSignature` SPI
 * (`packages/package-sdk/src/spi.ts`) field-for-field; the parity is anti-drift-pinned by
 * `CanonFunctionSpiParitySpec` (the writability-schema-copy discipline). The registry + resolver
 * below are the tatrman contract for the FO-P4 loader's `CanonFunctionRegistry` (F-3 seam) — this arc
 * ships the resolution contract + a fixture registry; the loader owns the live registry.
 */

/** One typed parameter of a canon-function signature — mirrors `spi.ts` `TypedSignature.params[]`. */
data class TypedParam(
    val name: String,
    val type: String,
)

/** A canon-function's typed signature — mirrors `spi.ts` `TypedSignature`. */
data class TypedSignature(
    val params: List<TypedParam>,
    val returns: String,
)

/**
 * The published canon-function SPI shape (`spi.ts` `CanonFunction`): a pure, versioned function
 * callable from TTR-P (TWR/MWR/FIFO where beyond the language). Purity is the SPI *contract*, not a
 * field — it is declared per-plugin on the manifest §15 `PluginRef.determinism` and carried by
 * [RegisteredFunction] below, never on the SPI shape.
 */
data class CanonFunctionSig(
    val id: String,
    val version: String,
    val signature: TypedSignature,
)

/**
 * A registry entry: the SPI [sig] plus the manifest-declared [determinism] (`PluginRef.determinism`,
 * `pure` when the plugin certifies determinism). A canon-function that does not certify `pure` is
 * refused at a `call-fn` site (TTRP-EN-005) — purity is compile-enforced, never assumed.
 */
data class RegisteredFunction(
    val sig: CanonFunctionSig,
    val determinism: String?,
) {
    val pure: Boolean get() = determinism == "pure"
}

/** The outcome of resolving a `call-fn` id + version constraint against the registry (deploy-time). */
sealed interface Resolution {
    /** The id + a satisfying version were found — carries the pinned entry. */
    data class Pinned(
        val fn: RegisteredFunction,
    ) : Resolution

    /** No plugin with this id is registered (→ TTRP-EN-006 at deploy). */
    data object UnknownId : Resolution

    /** The id is known but no registered version satisfies the constraint (→ TTRP-EN-006 at deploy). */
    data object NoSatisfyingVersion : Resolution
}

/**
 * The deploy-time resolution seam — the tatrman-side contract for the FO-P4 loader's
 * `CanonFunctionRegistry` (F-3). Resolution happens ONCE at deploy; the resulting `{id, version}` pin
 * is stamped into the plan and the entry record, and replay reads the pin — the registry is never
 * consulted again (P-3). The loader installs the real registry; [CanonFunctionFixtureRegistry] is the
 * arc's fixture implementation.
 */
interface CanonFunctionResolver {
    /** Resolve [id] under a semver [constraint] (`*` | `x.y.z` | `^x.y.z`) to a pinned entry, or a miss. */
    fun resolve(
        id: String,
        constraint: String,
    ): Resolution
}
