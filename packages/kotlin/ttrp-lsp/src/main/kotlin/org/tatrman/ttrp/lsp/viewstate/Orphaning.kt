package org.tatrman.ttrp.lsp.viewstate

import org.tatrman.ttr.parser.model.TtrlDocument
import org.tatrman.ttrp.graph.model.TtrpGraph

/**
 * Deterministic orphaning (C1-c-i, verbatim): a sidecar layout entry keeps its position
 * only if it *provably* still identifies the same node; otherwise it orphans and falls
 * back to auto-layout. Two triggers, both "never re-attach by guess" (P2):
 *
 *  1. **Chain-length change** — if a base name's recorded chain length (`chains` in the
 *     sidecar) differs from its current SSA chain length, ALL that name's entries orphan.
 *     An inserted mid-chain reassignment shifts every later ordinal, so `sales#2` may now
 *     name a different statement than when the sidecar was written; the whole group is
 *     untrustworthy, not just the shifted keys.
 *  2. **Missing key** — an entry whose exact ζ key is absent from the current graph
 *     (rename/delete) orphans (covers sidecars written before `chains` existed).
 */
object Orphaning {
    data class Result(
        /** ζ keys that lost their attachment (per canvas is not needed — ζ is globally unique). */
        val orphaned: Set<String>,
        /** Sidecar canvas keys that no longer exist in the graph (→ TTRP-LAY-003). */
        val unknownCanvases: Set<String>,
    )

    fun analyze(
        graph: TtrpGraph,
        sidecar: TtrlDocument,
    ): Result {
        val canvasKeys = ZetaKeys.canvasKeys(graph)
        val orphaned = LinkedHashSet<String>()
        val unknown = LinkedHashSet<String>()

        for (canvas in sidecar.canvases) {
            val currentKeys = canvasKeys[canvas.key]
            if (currentKeys == null) {
                unknown += canvas.key
                orphaned += canvas.nodes.map { it.zeta } // every entry on a vanished canvas orphans
                continue
            }
            val currentChains = ZetaKeys.chainLengths(currentKeys.keys)
            for (entry in canvas.nodes) {
                val base = ZetaKeys.baseOf(entry.zeta)
                val recorded = canvas.chains[base]
                val current = currentChains[base]
                val chainChanged = recorded != null && recorded != current
                val missing = entry.zeta !in currentKeys
                if (chainChanged || missing) orphaned += entry.zeta
            }
        }
        return Result(orphaned, unknown)
    }
}
