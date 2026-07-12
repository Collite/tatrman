// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.lsp.protocol

/**
 * `ttrp/getGraph` + `ttrp/getWorld` wire shapes (contracts §4). These are the
 * Designer's two-surface feed (C1-a): getGraph is the authored graph the canvas
 * renders and edits; getWorld is the engine/target palette.
 *
 * **Authored, not lowered.** `getGraph` serializes the *authored* build graph
 * (containers, authored node kinds incl. `Branch`/`Join`, ports, edges) — NOT the
 * capability-lowered normalized graph. The canvas is a second *authoring* surface
 * (A4: two surfaces, one graph): a node/edit maps back to the statement the analyst
 * wrote, so it must show `Branch`, never the polars `branch→filter` lowering. The
 * derived orchestration overlay (synthesized transfers, island engines, waves) rides
 * in [DerivedView], computed from the collapsed execution graph.
 *
 * **ζ keys.** Every node carries a `zeta` identity string `<container>/<label>`
 * (program leaves: just `<label>`), where `label` is the SSA-qualified name
 * (`sales#2`) or the anonymous `~n` form. Stage 5.2 consumes these as the `.ttrl`
 * sidecar keys and refines the anonymous spelling.
 */
data class GetGraphParams(
    val uri: String = "",
    val version: Int = 0,
)

data class GetGraphResult(
    val graph: GraphView,
    /** Per-node er provenance (E-d), keyed by ζ; only nodes with an er origin appear. */
    val provenance: Map<String, ProvenanceView>,
    /** Derived-container ζ paths (bare-fragment sub-graphs, C1-b-iv). Empty for canonical authoring. */
    val derived: List<String>,
    /** The derived orchestration overlay (islands/transfers/waves) — B-T6 derived-only. */
    val orchestration: DerivedView,
    /**
     * Deterministic auto-layout coordinates per canvas (Stage 5.2 fills this — the
     * `autoLayout` contract addition, C1-b). Canvas key → ζ key → abstract `{layer,index}`.
     * Empty until Stage 5.2 wires `AutoLayout`.
     */
    val autoLayout: Map<String, Map<String, AbstractCoord>> = emptyMap(),
)

data class GraphView(
    val program: String,
    val containers: List<ContainerView>,
    /** Program-level leaves: Display, Store (author sinks), synthesized Transfer. */
    val leaves: List<NodeView>,
    /** Program-level edges (container-port ⇄ container-port ⇄ leaf); cross-container carry `via`. */
    val edges: List<EdgeView>,
)

data class ContainerView(
    val path: String,
    val target: String,
    val derived: Boolean,
    /** Fragment dialect tag (`sql`/`pandas`/`ttrb`) if this is a fragment container, else null. */
    val fragment: String?,
    /** Direction (`in`/`out`/`err`) → declared port names. */
    val ports: Map<String, List<String>>,
    val nodes: List<NodeView>,
    /** Container-internal edges (ζ → ζ). */
    val edges: List<EdgeView>,
)

data class NodeView(
    val zeta: String,
    /** T10 roster kind (the node's simple class name: `Load`, `Filter`, `Branch`, …). */
    val kind: String,
    /** SSA-qualified label (`sales#2`) or anonymous `~n`. */
    val label: String,
    val range: RangeView?,
    val ports: List<PortView>,
    /** True for compiler-synthesized nodes (movement Transfer) — read-only on canvas. */
    val synthesized: Boolean = false,
    val provenance: ProvenanceView? = null,
)

data class PortView(
    val name: String,
    /** `data` | `control`. */
    val kind: String,
    /** `in` | `out`. */
    val direction: String,
)

data class EdgeView(
    /** Source endpoint ζ (container-member ζ, container path, or leaf ζ). */
    val from: String,
    /** Target endpoint ζ. */
    val to: String,
    /** Source port name (`out`/`true`/`false`/…), for anchoring to a specific port. */
    val fromPort: String,
    /** Target port name (`in`/`left`/`right`/…). */
    val toPort: String,
    /** `data` | `control-fs` | `control-ss`. */
    val type: String,
    /** Synthesized-transfer ζ for a cross-engine data edge (C3-d-iv), else null. */
    val via: String? = null,
)

/** LSP-style 0-based range (line/endLine already decremented from the 1-based [SourceLocation]). */
data class RangeView(
    val line: Int,
    val column: Int,
    val endLine: Int,
    val endColumn: Int,
)

data class ProvenanceView(
    val originQname: String,
    val originName: String,
)

/** The derived execution/orchestration overlay (B-T6): islands + transfers + waves. */
data class DerivedView(
    val islands: List<IslandView>,
    val transfers: List<TransferView>,
    val waves: List<List<String>>,
)

data class IslandView(
    val id: String,
    val name: String,
    val engine: String,
    val invocation: String?,
)

data class TransferView(
    val id: String,
    val fromIsland: String?,
    val toIsland: String?,
    val via: String?,
    val format: String,
)

/** Abstract layout coordinate (Stage 5.2 `AutoLayout`): the client maps to pixels per skin orientation. */
data class AbstractCoord(
    val layer: Int,
    val index: Int,
)

// ---- getWorld ----

data class GetWorldParams(
    val uri: String = "",
)

data class GetWorldResult(
    val world: String,
    val fingerprint: String,
    val engines: List<EngineView>,
    val executors: List<EngineView>,
    val storages: List<StorageView>,
    val staging: String?,
)

data class EngineView(
    val name: String,
    val type: String?,
    val version: String?,
)

data class StorageView(
    val name: String,
    val type: String?,
    val staging: Boolean,
)
