// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.capability

import org.tatrman.ttrp.ast.ParamDecl
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * The T6 executor-capability gate (PL-P2.S1, contracts §7): a program may use an F-4 surface
 * (`param`s, `on failure of`, `retries`) ONLY against a world whose executor-type manifest declares
 * the matching capability. Otherwise it is an ordinary T6 compile error naming the missing
 * capability (P3) — the same "manifest permits, engine doesn't" gate `CapabilityChecker` applies to
 * node kinds/functions, here for the executor vocabulary. This is P3 (platform-vs-bash divergence)
 * made executable: the F-lite `bash` executor declares none, the `tatrman` platform executor all.
 *
 * A capability is "supported" iff SOME bound executor in the world declares it — a world with no
 * capable executor cannot run the program. (Worlds have one executor in practice; the `any` keeps
 * a multi-executor world honest without over-constraining.)
 */
object ExecutorManifestGate {
    fun check(
        document: TtrpDocument,
        graph: TtrpGraph,
        executors: Collection<EngineTypeManifest>,
    ): List<TtrpDiagnostic> {
        val out = mutableListOf<TtrpDiagnostic>()
        val caps = executors.map { it.executorCapability() }

        val params = document.statements.filterIsInstance<ParamDecl>()
        if (params.isNotEmpty() && caps.none { it.params }) {
            for (p in params) {
                out +=
                    diag(
                        TtrpDiagnosticId.CAP_201,
                        "runtime param `${p.name}` needs an executor that supports params",
                        p.location,
                    )
            }
        }

        for (c in graph.containers.values) {
            if (c.onFailureOf != null && caps.none { it.onFailure }) {
                out +=
                    diag(
                        TtrpDiagnosticId.CAP_202,
                        "`${c.label}` is an on-failure island; this world's executor does not support it",
                        c.location,
                    )
            }
            if (c.retries != null && caps.none { it.retries }) {
                out +=
                    diag(
                        TtrpDiagnosticId.CAP_203,
                        "`${c.label}` declares `retries`; this world's executor does not support it",
                        c.location,
                    )
            }
        }
        return out
    }

    private fun diag(
        id: TtrpDiagnosticId,
        message: String,
        location: org.tatrman.ttrp.ast.SourceLocation,
    ) = TtrpDiagnostic(id = id, severity = Severity.ERROR, message = message, location = location)
}
