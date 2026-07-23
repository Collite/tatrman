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
                .maxWithOrNull { a, b -> compareVersions(a.sig.version, b.sig.version) }
                ?: return Resolution.NoSatisfyingVersion
        return Resolution.Pinned(satisfying)
    }

    /**
     * `*` matches any; `x.y.z` is exact; `^x.y.z` is the npm caret — `>= floor` and `< the next
     * backwards-incompatible release` (a major bump for `>=1.0.0`, a minor bump for `0.x`, a patch bump
     * for `0.0.z`). This is the manifest §15 form; `0.x` is NOT treated as a whole compatible major.
     */
    private fun satisfies(
        version: String,
        constraint: String,
    ): Boolean {
        val trimmed = constraint.trim()
        return when {
            trimmed == "*" -> true
            trimmed.startsWith("^") -> {
                val floor = trimmed.removePrefix("^")
                compareVersions(version, floor) >= 0 && compareVersions(version, caretCeiling(floor)) < 0
            }
            else -> version == trimmed
        }
    }

    /** The exclusive upper bound of a `^floor` caret range (npm: the first non-zero field bumps). */
    private fun caretCeiling(floor: String): String {
        val (maj, min, pat) = parts(floor)
        return when {
            maj > 0 -> "${maj + 1}.0.0"
            min > 0 -> "0.${min + 1}.0"
            else -> "0.0.${pat + 1}"
        }
    }

    private fun parts(v: String): Triple<Int, Int, Int> {
        val p = v.split(".")
        return Triple(
            p.getOrNull(0)?.toIntOrNull() ?: 0,
            p.getOrNull(1)?.toIntOrNull() ?: 0,
            p.getOrNull(2)?.toIntOrNull() ?: 0,
        )
    }

    /** Numeric per-field semver comparison — no lexical or field-overflow hazard of a packed key. */
    private fun compareVersions(
        a: String,
        b: String,
    ): Int {
        val (am, an, ap) = parts(a)
        val (bm, bn, bp) = parts(b)
        return when {
            am != bm -> am.compareTo(bm)
            an != bn -> an.compareTo(bn)
            else -> ap.compareTo(bp)
        }
    }
}
