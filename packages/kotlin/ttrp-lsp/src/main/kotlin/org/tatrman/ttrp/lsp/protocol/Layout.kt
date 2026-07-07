package org.tatrman.ttrp.lsp.protocol

/**
 * `ttrp/getLayout` + `ttrp/setLayout` wire shapes (contracts §4). The `.ttrl` sidecar is
 * paired to the document by filename (`x.ttrp` ↔ `x.ttrl`, `report.ttr.sql` ↔
 * `report.ttrl`), read as a parsed layout, and rewritten **wholesale** (never surgically —
 * writer isolation, v1.1 §15). getLayout also flags orphaned ζ entries + pair-integrity
 * diagnostics (C1-c-i) so the canvas can badge them (never silent decay).
 */
data class GetLayoutParams(
    val uri: String = "",
)

data class GetLayoutResult(
    /** False when no sidecar exists yet — all canvases are implicitly `mode: auto`. */
    val exists: Boolean,
    val version: Int,
    val canvases: List<CanvasLayoutView>,
    /** ζ keys whose stored position no longer attaches (orphaned → auto-layout, TTRP-LAY-001). */
    val orphaned: List<String>,
    val diagnostics: List<LayoutDiagnosticView>,
)

data class SetLayoutParams(
    val uri: String = "",
    val layout: LayoutPayload = LayoutPayload(),
)

data class SetLayoutResult(
    val ok: Boolean,
    val diagnostics: List<LayoutDiagnosticView>,
)

data class LayoutPayload(
    val version: Int = 1,
    val canvases: List<CanvasLayoutView> = emptyList(),
)

data class CanvasLayoutView(
    val key: String,
    val skin: String? = null,
    /** `auto` | `manual`. */
    val mode: String = "auto",
    val nodes: List<NodePosView> = emptyList(),
    val collapsed: List<String> = emptyList(),
)

data class NodePosView(
    val zeta: String,
    val x: Double,
    val y: Double,
)

data class LayoutDiagnosticView(
    val code: String,
    val severity: String,
    val message: String,
)
