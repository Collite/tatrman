// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.expr

import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.DefaultMdPathResolver
import org.tatrman.ttr.md.resolve.Explanation
import org.tatrman.ttr.md.resolve.MdDiagId
import org.tatrman.ttr.md.resolve.MdDiagnostic
import org.tatrman.ttr.md.resolve.MdPathResolver
import org.tatrman.ttr.md.resolve.MemberSnapshot
import org.tatrman.ttr.md.resolve.PathComponent
import org.tatrman.ttr.md.resolve.PathShape
import org.tatrman.ttr.semantics.md.MdModel
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import java.time.Instant

/**
 * The per-compile-pass MD resolution context (S3-A), injected into the [ExpressionTypechecker]. When
 * [model] is null, `mdPath` expressions are left untyped — exactly like a null input schema defers
 * column typing. ttrp-frontend's model repo is the ER/DB/world tier ([org.tatrman.ttrp.resolve.ModelIndex]),
 * which carries **no** MD objects; the [MdModel] (cubelets/dimensions/measures, ttr-semantics) is a
 * separate representation. Production loading of it — and of the [members] snapshot — is a later seam
 * (the member catalog is S6-B); until then the checker injects them (tests supply the shared
 * `sales-model` + an InMemory snapshot, or null [members] for disconnected mode, R13).
 *
 * [asof] is the compile-time parameter (D17): declared via the `[ttrp] md-asof` manifest key, else
 * defaulted from the compile-pass clock, then threaded verbatim to the resolver (calc tokens like
 * `lastMonth` anchor on it, R12).
 */
data class MdContext(
    val model: MdModel?,
    val members: MemberSnapshot?,
    val asof: Instant,
    val resolver: MdPathResolver = DefaultMdPathResolver(),
)

/**
 * A resolved MD dot-path, carried beside the expression's type (S3-A1/A6). The frontend check result
 * exposes these so the future ttrp-lsp hover can surface the [canonical] desugaring, the [shape]
 * (R15), and the [explanation] (R14) — no LSP work in this arc, only the populated accessor. [path]
 * is the structured canonical form (§3); [canonical] is its rendered text.
 */
data class MdResolution(
    val location: SourceLocation,
    val canonical: String,
    val path: CanonicalPath,
    val shape: PathShape,
    val explanation: Explanation,
)

/**
 * Translate the frontend AST's [MdPathComponent]s (built by the walker at parse time) into the
 * resolver's own [PathComponent] input type (§1.2). The two types are intentionally distinct — the
 * resolver owns [PathComponent] so it never depends back on ttrp-frontend (MDS1). No `Pair` is
 * produced: a `qualifier.target` is two adjacent components and pairing is the resolver's job
 * ([org.tatrman.ttr.md.resolve.PairBinder]).
 */
internal fun List<MdPathComponent>.toResolverComponents(): List<PathComponent> = map { it.toResolverComponent() }

private fun MdPathComponent.toResolverComponent(): PathComponent =
    when (this) {
        is MdPathComponent.Name -> PathComponent.Ident(text)
        is MdPathComponent.IntLit -> PathComponent.IntLit(text)
        is MdPathComponent.StrLit -> PathComponent.Quoted(text)
        is MdPathComponent.MemberSet -> PathComponent.SetLit(atoms.map { it.toResolverAtom() })
        is MdPathComponent.Range -> PathComponent.RangeLit(lo.toResolverAtom(), hi.toResolverAtom())
        is MdPathComponent.Star -> PathComponent.Star
    }

private fun MdPathAtom.toResolverAtom(): PathComponent =
    when (this) {
        is MdPathAtom.Name -> PathComponent.Ident(text)
        is MdPathAtom.IntLit -> PathComponent.IntLit(text)
        is MdPathAtom.StrLit -> PathComponent.Quoted(text)
    }

/** Bridge a resolver [MdDiagId] to the frontend enum seat (`TTRP-MD-0NN`, contracts §6). */
internal fun MdDiagId.toFrontendId(): TtrpDiagnosticId =
    when (this) {
        MdDiagId.UNKNOWN_COMPONENT -> TtrpDiagnosticId.MD_001
        MdDiagId.UNRESOLVABLE -> TtrpDiagnosticId.MD_002
        MdDiagId.AMBIGUOUS -> TtrpDiagnosticId.MD_003
        MdDiagId.UNBINDABLE_SELECTOR -> TtrpDiagnosticId.MD_004
        MdDiagId.MULTIPLE_MEASURES -> TtrpDiagnosticId.MD_005
        MdDiagId.SAME_ATTR_REPETITION -> TtrpDiagnosticId.MD_006
        MdDiagId.BARE_MEMBER_DISCONNECTED -> TtrpDiagnosticId.MD_007
        MdDiagId.NON_SCALAR_IN_SCALAR_POS -> TtrpDiagnosticId.MD_008
        MdDiagId.INCOMPLETE_STRICT_LHS -> TtrpDiagnosticId.MD_009
        MdDiagId.SPREAD_WITHOUT_STRATEGY -> TtrpDiagnosticId.MD_010
        MdDiagId.UNKNOWN_MEMBER -> TtrpDiagnosticId.MD_011
        MdDiagId.SHADOWED_BY_COLUMN -> TtrpDiagnosticId.MD_012
        MdDiagId.CATALOG_LOST -> TtrpDiagnosticId.MD_013
        MdDiagId.SEARCH_BOUND_EXCEEDED -> TtrpDiagnosticId.MD_014
    }

/** The user-facing message for a resolver diagnostic: its §6 text, plus the per-occurrence detail. */
internal fun MdDiagnostic.frontendMessage(): String = if (detail.isNotEmpty()) "${id.text}: $detail" else id.text
