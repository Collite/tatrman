package org.tatrman.ttrp.lsp.protocol

/**
 * `ttrp/applyGraphEdit` (contracts §4): the closed β edit vocabulary (C1-d-i) → a
 * formatter-owned `WorkspaceEdit`. One flat shape (gson-friendly — lsp4j deserializes it);
 * `op` is the discriminator, the rest are per-op optional fields. Unknown `op` is an error
 * (TTRP-EDIT-003), never passthrough. δ node/edge surface form stays internal (C1-d-iv):
 * edits emit γ-hybrid canonical text, nothing else.
 */
data class GraphEdit(
    val op: String = "",
    /** addNode: target container canvas. */
    val canvas: String? = null,
    /** addNode: T10 node kind (`Load`, `Filter`, …). */
    val kind: String? = null,
    /** addNode/createContainer: the SSA/container name. */
    val name: String? = null,
    /** addNode: upstream ζ the new node reads from (optional). */
    val afterZeta: String? = null,
    /** removeNode/renameVariable/setProperty: the node ζ. */
    val zeta: String? = null,
    /** connect/disconnect: port refs (`container.port` / `container` for default). */
    val from: String? = null,
    val to: String? = null,
    /** addControlEdge: endpoints + kind (`after` FS / `with` SS). */
    val a: String? = null,
    val b: String? = null,
    val controlKind: String? = null,
    /** createContainer/assignTarget: engine instance name. */
    val target: String? = null,
    /** deleteContainer/assignTarget/bindContainerPorts: container path. */
    val path: String? = null,
    /** createContainer: fragment dialect (`sql`/`pandas`/`ttrb`), or null for a canonical container. */
    val dialect: String? = null,
    /** renameVariable: the new name. */
    val newName: String? = null,
    /** setProperty: property + its text value (through the one expression grammar). */
    val property: String? = null,
    val valueText: String? = null,
)

data class ApplyGraphEditParams(
    val uri: String = "",
    val version: Int = 0,
    val edits: List<GraphEdit> = emptyList(),
)
