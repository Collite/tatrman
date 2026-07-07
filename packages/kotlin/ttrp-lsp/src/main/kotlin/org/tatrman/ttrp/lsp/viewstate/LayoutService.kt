package org.tatrman.ttrp.lsp.viewstate

import org.tatrman.ttr.parser.loader.TtrlLoader
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttr.parser.model.TtrlCanvas
import org.tatrman.ttr.parser.model.TtrlDocument
import org.tatrman.ttr.parser.model.TtrlMode
import org.tatrman.ttr.parser.model.TtrlNodeEntry
import org.tatrman.ttr.writer.TtrlWriter
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.lsp.protocol.CanvasLayoutView
import org.tatrman.ttrp.lsp.protocol.GetLayoutResult
import org.tatrman.ttrp.lsp.protocol.LayoutDiagnosticView
import org.tatrman.ttrp.lsp.protocol.LayoutPayload
import org.tatrman.ttrp.lsp.protocol.NodePosView
import org.tatrman.ttrp.lsp.protocol.SetLayoutResult
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * `ttrp/getLayout` / `ttrp/setLayout` behind the LSP methods (Stage 5.2). Owns the
 * `.ttrl` sidecar lifecycle: filename pairing, parse (→ orphan analysis + pair-integrity
 * diagnostics), and **wholesale** rewrite via [TtrlWriter] with the SSA chain lengths
 * recorded from the current graph (the orphaning discriminator). Never edits the sidecar
 * surgically (writer isolation).
 */
class LayoutService {
    /** Read + analyze the sidecar paired to [uri] against [graph]. Missing sidecar ⇒ empty (auto). */
    fun getLayout(
        uri: String,
        graph: TtrpGraph?,
    ): GetLayoutResult {
        val path = sidecarPath(uri) ?: return empty()
        if (!Files.isRegularFile(path)) return empty()
        val parsed = TtrlLoader.parseFile(path)
        if (!parsed.ok || parsed.document == null) {
            return GetLayoutResult(
                exists = true,
                version = 1,
                canvases = emptyList(),
                orphaned = emptyList(),
                diagnostics = listOf(diag(TtrpDiagnosticId.LAY_002)),
            )
        }
        val doc = parsed.document!!
        val diagnostics = mutableListOf<LayoutDiagnosticView>()
        var orphaned = emptyList<String>()
        if (graph != null) {
            val analysis = Orphaning.analyze(graph, doc)
            orphaned = analysis.orphaned.toList()
            if (analysis.orphaned.isNotEmpty()) diagnostics += diag(TtrpDiagnosticId.LAY_001)
            if (analysis.unknownCanvases.isNotEmpty()) diagnostics += diag(TtrpDiagnosticId.LAY_003)
        }
        return GetLayoutResult(
            exists = true,
            version = doc.version,
            canvases = doc.canvases.map { it.toView() },
            orphaned = orphaned,
            diagnostics = diagnostics,
        )
    }

    /**
     * Rewrite the sidecar paired to [uri] wholesale from [payload]. Rejects (no write) a
     * payload that puts nodes on an auto canvas or references a derived canvas; injects the
     * current SSA chain lengths so future orphaning is precise.
     */
    fun setLayout(
        uri: String,
        payload: LayoutPayload,
        graph: TtrpGraph?,
    ): SetLayoutResult {
        val path =
            sidecarPath(uri)
                ?: return SetLayoutResult(false, listOf(err("non-file uri has no sidecar: $uri")))
        // gson (lsp4j's deserializer) ignores Kotlin defaults, so absent JSON fields arrive as
        // null even on non-null properties — guard every collection access.
        val canvases = payload.canvases ?: emptyList()
        val diagnostics = mutableListOf<LayoutDiagnosticView>()
        for (canvas in canvases) {
            if (canvas.mode == "auto" && (canvas.nodes ?: emptyList()).isNotEmpty()) {
                diagnostics += err("canvas `${canvas.key}` is auto but carries nodes — reset to auto drops them")
            }
        }
        if (diagnostics.isNotEmpty()) return SetLayoutResult(false, diagnostics)

        val chainsByCanvas = graph?.let { ZetaKeys.chainLengthsByCanvas(it) } ?: emptyMap()
        val doc =
            TtrlDocument(
                version = payload.version,
                canvases =
                    canvases.map { c ->
                        val manual = c.mode == "manual"
                        TtrlCanvas(
                            key = c.key,
                            skin = c.skin,
                            mode = if (manual) TtrlMode.MANUAL else TtrlMode.AUTO,
                            nodes = (c.nodes ?: emptyList()).map { TtrlNodeEntry(it.zeta, it.x, it.y) },
                            collapsed = c.collapsed ?: emptyList(),
                            // Record chains only for manual canvases (auto persists nothing to orphan).
                            chains = if (manual) chainsByCanvas[c.key].orEmpty() else emptyMap(),
                            location = SourceLocation.UNKNOWN,
                        )
                    },
                sourceFile = path.toString(),
            )
        Files.writeString(path, TtrlWriter.write(doc))
        return SetLayoutResult(true, emptyList())
    }

    /** Deterministic abstract auto-layout per canvas (the `autoLayout` getGraph field). */
    fun autoLayouts(graph: TtrpGraph?): Map<String, Map<String, AbstractCoord>> =
        graph?.let { CanvasGraphs.autoLayouts(it) } ?: emptyMap()

    /**
     * Atomic ζ pair rewrite (C1-c-i): migrate `old#n → new#n` sidecar node keys (and their
     * `chains` base) in the same operation as an LSP `textDocument/rename`, so text + sidecar
     * never disagree. Positions are preserved (a rename is not a chain-length change); a
     * missing sidecar is a no-op. Wholesale rewrite via [TtrlWriter] (never surgical).
     */
    fun migrateKeys(
        uri: String,
        remaps: List<Pair<String, String>>,
    ) {
        if (remaps.isEmpty()) return
        val path = sidecarPath(uri) ?: return
        if (!Files.isRegularFile(path)) return
        val doc = TtrlLoader.parseFile(path).document ?: return
        val keyMap = remaps.toMap()
        val baseMap = remaps.associate { ZetaKeys.baseOf(it.first) to ZetaKeys.baseOf(it.second) }
        val migrated =
            doc.copy(
                canvases =
                    doc.canvases.map { c ->
                        c.copy(
                            nodes = c.nodes.map { n -> keyMap[n.zeta]?.let { n.copy(zeta = it) } ?: n },
                            chains = c.chains.mapKeys { (base, _) -> baseMap[base] ?: base },
                        )
                    },
            )
        Files.writeString(path, TtrlWriter.write(migrated))
    }

    /** Filename pairing (contracts §1): `x.ttrp`→`x.ttrl`, `report.ttr.sql`→`report.ttrl`. */
    fun sidecarPath(uri: String): Path? {
        val path =
            runCatching { Path.of(URI(uri)) }.getOrNull() ?: runCatching { Path.of(uri) }.getOrNull() ?: return null
        val name = path.fileName?.toString() ?: return null
        val base =
            when {
                name.endsWith(".ttr.sql") -> name.removeSuffix(".ttr.sql")
                name.endsWith(".ttr.py") -> name.removeSuffix(".ttr.py")
                name.contains('.') -> name.substringBeforeLast('.')
                else -> name
            }
        val dir = path.parent ?: Path.of(".")
        return dir.resolve("$base.ttrl")
    }

    private fun empty() = GetLayoutResult(false, 1, emptyList(), emptyList(), emptyList())

    private fun TtrlCanvas.toView() =
        CanvasLayoutView(
            key = key,
            skin = skin,
            mode = if (mode == TtrlMode.MANUAL) "manual" else "auto",
            nodes = nodes.map { NodePosView(it.zeta, it.x, it.y) },
            collapsed = collapsed,
        )

    private fun diag(id: TtrpDiagnosticId) = LayoutDiagnosticView(id.id, "warning", id.suggestedAlternative ?: id.id)

    private fun err(message: String) = LayoutDiagnosticView("TTRP-LAY-000", "error", message)
}
