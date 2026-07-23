// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

/**
 * The arc's fixture canon-function registry (contracts §6) — a [CanonFunctionResolver] over an explicit
 * SHIPPED roster, modelled on the `ValidityCatalog`/`EntryVerbCatalog` SHIPPED-list house pattern (no
 * classpath scanning). It stands in for the FO-P4 loader's live `CanonFunctionRegistry` so the arc can
 * prove the deploy-time resolution + pin contract end-to-end. The roster carries the two things the
 * loader's registry carries: the published [CanonFunctionSig] (id/version/signature) and the
 * manifest-declared determinism (`pure` ⇒ callable; anything else ⇒ refused at the call site, EN-005).
 */
object CanonFunctionFixtureRegistry : CanonFunctionResolver {
    /** A pure, versioned TWR canon-function (`twr`-shaped, the demand §6 exemplar), two published versions. */
    val SHIPPED: List<RegisteredFunction> =
        listOf(
            RegisteredFunction(
                CanonFunctionSig(
                    "twr",
                    "1.0.0",
                    TypedSignature(listOf(TypedParam("flows", "number[]"), TypedParam("values", "number[]")), "number"),
                ),
                determinism = "pure",
            ),
            RegisteredFunction(
                CanonFunctionSig(
                    "twr",
                    "1.2.0",
                    TypedSignature(listOf(TypedParam("flows", "number[]"), TypedParam("values", "number[]")), "number"),
                ),
                determinism = "pure",
            ),
            RegisteredFunction(
                CanonFunctionSig(
                    "fifo-cost",
                    "2.1.0",
                    TypedSignature(listOf(TypedParam("lots", "number[]"), TypedParam("qty", "number")), "number"),
                ),
                determinism = "pure",
            ),
            // A canon-function whose plugin does NOT certify determinism — legal to register, illegal to
            // call from a (pure) apply program: a `call-fn` to it raises TTRP-EN-005.
            RegisteredFunction(
                CanonFunctionSig("wall-clock", "1.0.0", TypedSignature(emptyList(), "date")),
                determinism = null,
            ),
        )

    private val byId: Map<String, List<RegisteredFunction>> = SHIPPED.groupBy { it.sig.id }

    /** All roster ids, in first-seen order (the authoring roster + completeness checks). */
    val ids: List<String> = SHIPPED.map { it.sig.id }.distinct()

    override fun resolve(
        id: String,
        constraint: String,
    ): Resolution {
        val candidates = byId[id] ?: return Resolution.UnknownId
        val satisfying =
            candidates
                .filter { satisfies(it.sig.version, constraint) }
                .maxByOrNull { sortKey(it.sig.version) }
                ?: return Resolution.NoSatisfyingVersion
        return Resolution.Pinned(satisfying)
    }

    /** `*` matches any; `x.y.z` is exact; `^x.y.z` is same-major and ≥ the floor (the manifest §15 forms). */
    private fun satisfies(
        version: String,
        constraint: String,
    ): Boolean {
        val trimmed = constraint.trim()
        return when {
            trimmed == "*" -> true
            trimmed.startsWith("^") -> {
                val floor = trimmed.removePrefix("^")
                major(version) == major(floor) && sortKey(version) >= sortKey(floor)
            }
            else -> version == trimmed
        }
    }

    private fun major(v: String): Int = v.split(".").firstOrNull()?.toIntOrNull() ?: 0

    /** A monotonic, comparable key for a `x.y.z` version (each field bounded well under 1e6). */
    private fun sortKey(v: String): Long {
        val p = v.split(".").map { it.toIntOrNull() ?: 0 }
        return p.getOrElse(0) { 0 }.toLong() * 1_000_000L +
            p.getOrElse(1) { 0 }.toLong() * 1_000L +
            p.getOrElse(2) { 0 }.toLong()
    }
}
