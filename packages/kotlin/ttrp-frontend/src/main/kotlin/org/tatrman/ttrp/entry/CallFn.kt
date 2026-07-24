// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId

/*
 * EN-P5 `call-fn` — the plugin-function call surface (contracts §6). Like the verb surface (EN-P2), the
 * spelling is deferred (`// EN: surface pins on PLA-2`), so a call is a structured [CallFnDemand], not a
 * parsed token; the id is a compile-time string literal (P2 — no dynamic ids). [CallFnResolver.resolve]
 * is the single deploy-time entry point: it typechecks each demand against the resolved SPI signature,
 * enforces purity (`call-fn` is the ONLY legal foreign call, and only to a `pure`-certified function),
 * resolves the version against the registry, and records the `{id, version}` pin for the FO §6 entry
 * record. Resolution is a DEPLOY step — replay reads the pins, never the registry (P-3).
 *
 * Diagnostics: **TTRP-EN-005** = purity (a non-`pure` function called); **TTRP-EN-006** = a malformed or
 * unresolvable call (dynamic id, arity/type mismatch, unknown id, or unsatisfiable version).
 */

/** One typed `call-fn` argument (deferred surface: its [type] is the arg expression's resolved type). */
data class CallFnArg(
    val type: String,
    val location: SourceLocation,
)

/** A structured `call-fn("<id>", args…)` demand at a site — the deferred-surface form (contracts §1/§6). */
data class CallFnDemand(
    val functionId: String,
    /** P2: the id must be a compile-time string literal — a computed/dynamic id is TTRP-EN-006. */
    val idIsLiteral: Boolean,
    /** The manifest §15 version constraint for the binding (`*` | `x.y.z` | `^x.y.z`). */
    val versionConstraint: String,
    val args: List<CallFnArg>,
    val location: SourceLocation,
)

/** A deploy-resolved plugin pin — the FO §6 `pluginPins` input (`{id, version}`). */
data class CallFnPin(
    val id: String,
    val version: String,
)

/** The result of resolving every `call-fn` demand in a program: the pins to stamp + any diagnostics. */
data class CallFnResolveResult(
    val pins: List<CallFnPin>,
    val diagnostics: List<TtrpDiagnostic>,
) {
    val ok: Boolean get() = diagnostics.none { it.severity == Severity.ERROR }
}

object CallFnResolver {
    /**
     * Resolve + typecheck every [demands] entry against [registry] (deploy-time). Each well-formed,
     * pure, signature-matching, registry-satisfiable demand yields one [CallFnPin]; every failure yields
     * a diagnostic and NO pin (an ill-formed call is never pinned). Deterministic in roster order.
     */
    fun resolve(
        demands: List<CallFnDemand>,
        registry: CanonFunctionResolver,
    ): CallFnResolveResult {
        val pins = mutableListOf<CallFnPin>()
        val diags = mutableListOf<TtrpDiagnostic>()
        for (d in demands) {
            if (!d.idIsLiteral) {
                diags += en006("`call-fn` requires a string-literal id — a computed id is not resolvable", d.location)
                continue
            }
            when (val r = registry.resolve(d.functionId, d.versionConstraint)) {
                is Resolution.UnknownId ->
                    diags += en006("no canon-function `${d.functionId}` is registered", d.location)
                is Resolution.NoSatisfyingVersion ->
                    diags +=
                        en006(
                            "canon-function `${d.functionId}` has no version satisfying `${d.versionConstraint}`",
                            d.location,
                        )
                is Resolution.Pinned -> {
                    val errs = checkPurity(d, r.fn) + checkSignature(d, r.fn)
                    diags += errs
                    if (errs.none { it.severity == Severity.ERROR }) {
                        pins += CallFnPin(d.functionId, r.fn.sig.version)
                    }
                }
            }
        }
        return CallFnResolveResult(pins.distinctBy { it.id to it.version }, diags)
    }

    /** TTRP-EN-005: a `call-fn` may only target a `pure`-certified function (the purity contract, §6). */
    private fun checkPurity(
        demand: CallFnDemand,
        fn: RegisteredFunction,
    ): List<TtrpDiagnostic> =
        if (fn.pure) {
            emptyList()
        } else {
            listOf(
                TtrpDiagnostic(
                    TtrpDiagnosticId.EN_005,
                    Severity.ERROR,
                    "canon-function `${fn.sig.id}` is not `pure`-certified — it may not be called from an apply program",
                    demand.location,
                ),
            )
        }

    /** TTRP-EN-006: the call's arity + per-arg types must match the resolved SPI signature. */
    private fun checkSignature(
        demand: CallFnDemand,
        fn: RegisteredFunction,
    ): List<TtrpDiagnostic> {
        val params = fn.sig.signature.params
        if (demand.args.size != params.size) {
            return listOf(
                en006(
                    "`call-fn(\"${fn.sig.id}\")` takes ${params.size} arg(s), got ${demand.args.size}",
                    demand.location,
                ),
            )
        }
        val out = mutableListOf<TtrpDiagnostic>()
        demand.args.forEachIndexed { i, arg ->
            val expected = params[i].type
            if (arg.type != expected) {
                out +=
                    en006(
                        "`call-fn(\"${fn.sig.id}\")` arg `${params[i].name}` expects `$expected`, got `${arg.type}`",
                        arg.location,
                    )
            }
        }
        return out
    }

    private fun en006(
        message: String,
        location: SourceLocation,
    ) = TtrpDiagnostic(TtrpDiagnosticId.EN_006, Severity.ERROR, message, location)
}
