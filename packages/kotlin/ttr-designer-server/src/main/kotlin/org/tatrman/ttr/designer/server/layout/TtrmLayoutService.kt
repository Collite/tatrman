// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server.layout

import org.tatrman.ttr.designer.server.methods.fullQname
import org.tatrman.ttr.metadata.registry.RegistrySnapshot
import org.tatrman.ttr.parser.loader.TtrlLoader
import org.tatrman.ttr.parser.model.TtrlCanvas
import org.tatrman.ttr.parser.model.TtrlDocument
import org.tatrman.ttr.writer.TtrlWriter
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * `ttrm/getLayout`'s read path (T1 — TP-5 `.ttrl` migration). Deliberately **not** a reuse
 * of `ttrp-lsp`'s `LayoutService`/`ZetaKeys`: those are hard-coupled to `TtrpGraph`'s SSA
 * node identity and per-canvas chain-length orphaning, neither of which TTR-M has (a
 * `.ttrg` node's identity is just its qname — confirmed against
 * `samples/v1.1-mini/graphs/all_er.ttrg`'s `layout.nodes` keys, which are already full
 * qnames in the `fullQname()` format every other `ttrm/…` handler uses). This class reuses
 * only the genuinely format-generic layer: `TtrlLoader`/`TtrlDocument`/`TtrlCanvas` (the
 * `.ttrl` grammar itself doesn't know or care what kind of document it's paired with —
 * `TtrlCanvas.key`'s doc comment already anticipates "a TTR-M qname").
 *
 * **Known simplification, not a bug:** orphaning here checks a node's qname against
 * *the whole model registry* (does an object with this qname exist anywhere), not against
 * the specific `.ttrg` file's own `objects:` list the way TTR-P's per-canvas chain-length
 * check is scoped to one graph. Precise per-graph membership would require the JVM side to
 * parse `.ttrg`'s `objects:` list, which nothing here does today (that parser lives only
 * TS-side, in `@tatrman/parser`, and T2's migration tool is the one place that will need
 * it). Global existence is a strictly looser check — it can under-report orphans (a node
 * removed from one graph's `objects:` but still declared elsewhere in the model won't be
 * flagged) but never over-reports. Tighten in a follow-up if that gap matters in practice.
 */
class TtrmLayoutService {
    /** Read + analyze the sidecar paired to [uri]. Missing sidecar ⇒ [TtrmLayoutResult.absent]. */
    fun getLayout(
        uri: String,
        snapshot: RegistrySnapshot?,
    ): TtrmLayoutResult {
        val path = sidecarPath(uri) ?: return TtrmLayoutResult.absent()
        if (!Files.isRegularFile(path)) return TtrmLayoutResult.absent()

        val parsed = TtrlLoader.parseFile(path)
        if (!parsed.ok || parsed.document == null) {
            return TtrmLayoutResult(
                exists = true,
                version = 1,
                canvases = emptyList(),
                orphaned = emptyList(),
                errors = parsed.errors.map { "${it.line}:${it.column}: ${it.message}" },
            )
        }
        val doc = parsed.document!!

        val knownQnames: Set<String> =
            snapshot
                ?.model
                ?.objectByQname()
                ?.values
                ?.mapTo(mutableSetOf()) { fullQname(it.qname) }
                ?: emptySet()
        val orphaned =
            if (snapshot == null) {
                emptyList()
            } else {
                doc.canvases
                    .flatMap { it.nodes }
                    .map { it.zeta }
                    .filter { it !in knownQnames }
                    .distinct()
            }

        return TtrmLayoutResult(
            exists = true,
            version = doc.version,
            canvases = doc.canvases,
            orphaned = orphaned,
            errors = emptyList(),
        )
    }

    /**
     * Rewrite the sidecar paired to [uri] **wholesale** from [canvases] (writer isolation,
     * matching `ttrp-lsp`'s `LayoutService.setLayout` — never a surgical sidecar edit). Per
     * T3.1's ratified edit-application model, writes straight to disk; there is no
     * `WorkspaceEdit` to return. TTR-M carries no `chains` (T1's 1.1.2 finding: no SSA
     * concept), so every canvas's `chains` is always empty here.
     */
    fun setLayout(
        uri: String,
        canvases: List<TtrlCanvas>,
    ): Boolean {
        val path = sidecarPath(uri) ?: return false
        val doc =
            TtrlDocument(
                version = 1,
                canvases = canvases.map { it.copy(chains = emptyMap()) },
                sourceFile = path.toString(),
            )
        Files.writeString(path, TtrlWriter.write(doc))
        return true
    }

    /** Filename pairing: `x.ttrg` → `x.ttrl` (same directory, sibling of the graph file). */
    fun sidecarPath(uri: String): Path? {
        val path =
            runCatching { Path.of(URI(uri)) }.getOrNull() ?: runCatching { Path.of(uri) }.getOrNull() ?: return null
        val name = path.fileName?.toString() ?: return null
        val base = if (name.contains('.')) name.substringBeforeLast('.') else name
        val dir = path.parent ?: Path.of(".")
        return dir.resolve("$base.ttrl")
    }
}

data class TtrmLayoutResult(
    val exists: Boolean,
    val version: Int,
    val canvases: List<TtrlCanvas>,
    /** Qnames present in the sidecar but not found in the current model registry. */
    val orphaned: List<String>,
    val errors: List<String>,
) {
    companion object {
        fun absent() =
            TtrmLayoutResult(
                exists = false,
                version = 1,
                canvases = emptyList(),
                orphaned = emptyList(),
                errors = emptyList(),
            )
    }
}
