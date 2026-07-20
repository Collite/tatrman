// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.agent

import kotlinx.serialization.Serializable
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.ExplainStep
import org.tatrman.ttr.md.resolve.MdDiagnostic
import org.tatrman.ttr.md.resolve.ResolutionOutcome

/*
 * The MCP tool output envelopes (dot-path contracts §9). The resolver's @Serializable types
 * (CanonicalPath, ExplainStep) are the DTOs — carried verbatim (their field names are the public
 * contract). Only the flat md_resolve status envelope and the diagnostic-code projection are added
 * here; MDS6 keeps this module a thin adapter with no language logic.
 */

/** `md_resolve` result: the sealed [ResolutionOutcome] flattened to §9's `{ status, … }` shape. */
@Serializable
data class ResolveResult(
    val status: String, // "resolved" | "ambiguous" | "failed"
    val path: CanonicalPath? = null,
    val shape: List<String>? = null,
    val explanation: List<ExplainStep>? = null,
    val alternatives: List<CanonicalPath>? = null,
    val diagnostics: List<DiagnosticDto>? = null,
)

/** `md_explain` result (§9): the resolved path's derivation steps + its shape. */
@Serializable
data class ExplainResult(
    val explanation: List<ExplainStep>,
    val shape: List<String>,
)

/** `md_list_members` result (§9): a prefix page of members + whether more exist beyond `limit`. */
@Serializable
data class ListMembersResult(
    val members: List<String>,
    val truncated: Boolean,
)

/**
 * A diagnostic on the wire: the **stable** `TTRP-MD-0NN` code (not the enum name it serializes to)
 * plus its detail and offending token. Mirrors the frontend's roster projection.
 */
@Serializable
data class DiagnosticDto(
    val code: String,
    val detail: String,
    val token: String? = null,
)

fun MdDiagnostic.toDto(): DiagnosticDto = DiagnosticDto(code = code, detail = detail, token = token)

/** Flatten a resolver outcome into the §9 `md_resolve` envelope. */
fun ResolutionOutcome.toResolveResult(): ResolveResult =
    when (this) {
        is ResolutionOutcome.Resolved ->
            ResolveResult(
                status = "resolved",
                path = path,
                shape = shape.freeDims,
                explanation = explanation.steps,
            )

        is ResolutionOutcome.Ambiguous ->
            ResolveResult(
                status = "ambiguous",
                // §9: alternatives are the candidate paths; each candidate's own explanation is
                // reachable via md_explain on that path if the caller wants to compare derivations.
                alternatives = alternatives.map { it.path },
            )

        is ResolutionOutcome.Failed ->
            ResolveResult(
                status = "failed",
                diagnostics = diagnostics.map { it.toDto() },
            )
    }
