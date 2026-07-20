// SPDX-License-Identifier: Apache-2.0
// PL-P1.S9.T1 — the STATIC adapter: an E-5 bundle manifest (`schemaVersion: 2`) → a read-only program
// graph for the Designer's TTR-P panel (contracts §6). Distinct from `to-processing-graph.ts`, which
// maps the LIVE `ttrp/getGraph` wire (and deliberately skips island-level transfers). Here the manifest
// is the source of truth: islands → nodes (labeled engine/executor), transfers → edges (via staging),
// `waves` → column/level order, `onFailureOf` → a distinct error edge. The `lineage` section is NOT
// rendered here — it feeds a separate column-lineage panel (PL-P2).

/** The subset of the E-5 manifest (contracts §5 / manifest-v2 schema) this panel reads. */
export interface ManifestIsland {
  name: string;
  engine: string;
  executor: string;
  invocation?: string;
  onFailureOf?: string | null;
}

export interface ManifestTransfer {
  from: string;
  to: string;
  via: string;
  file?: string;
}

export interface BundleManifestV2 {
  schemaVersion: 2;
  program?: string;
  islands: ManifestIsland[];
  transfers?: ManifestTransfer[];
  waves?: string[][];
  // `lineage` intentionally omitted from this view (separate panel).
}

export interface ProgramGraphNode {
  id: string;
  label: string;
  engine: string;
  executor: string;
  /** The wave (0-based column) this node sits in, or null if it appears in no wave. */
  wave: number | null;
}

export interface ProgramGraphEdge {
  id: string;
  from: string;
  to: string;
  /** `transfer` = a staged data hand-off; `error` = an `onFailureOf` recovery link. */
  kind: 'transfer' | 'error';
  /** For a transfer, the staging storage it flows through (`via`). */
  via?: string;
}

export interface ProgramGraph {
  nodes: ProgramGraphNode[];
  edges: ProgramGraphEdge[];
  /** The raw wave levels (island names + transfer ids), verbatim — the panel lays these out as columns. */
  waves: string[][];
}

/** The transfer's synthetic id — its `via`-less identity used in the `waves` list (e.g. `x0`). */
function transferId(t: ManifestTransfer, index: number): string {
  // A manifest transfer has no explicit id; its wave token derives from its file stem when present
  // (`transfers/x0.py` → `x0`), else a positional fallback.
  const stem = t.file?.split('/').pop()?.replace(/\.[^.]+$/, '');
  return stem && stem.length > 0 ? stem : `transfer-${index}`;
}

/**
 * Build the program graph from an E-5 manifest. Pure + deterministic (same manifest ⇒ same graph).
 * Islands become nodes (label `<name> · <engine>/<executor>`), placed in their wave column; transfers
 * become `transfer` edges carrying `via`; each island's `onFailureOf` becomes a distinct `error` edge.
 */
export function manifestToProgramGraph(manifest: BundleManifestV2): ProgramGraph {
  const waves = manifest.waves ?? [];
  const waveOf = new Map<string, number>();
  waves.forEach((level, i) => level.forEach((token) => waveOf.set(token, i)));

  const nodes: ProgramGraphNode[] = manifest.islands.map((isl) => ({
    id: isl.name,
    label: `${isl.name} · ${isl.engine}/${isl.executor}`,
    engine: isl.engine,
    executor: isl.executor,
    wave: waveOf.has(isl.name) ? (waveOf.get(isl.name) as number) : null,
  }));

  const edges: ProgramGraphEdge[] = [];
  (manifest.transfers ?? []).forEach((t, i) => {
    edges.push({ id: `transfer:${transferId(t, i)}`, from: t.from, to: t.to, kind: 'transfer', via: t.via });
  });
  // Error edges: an island that runs `onFailureOf: X` is wired X --(error)--> island.
  for (const isl of manifest.islands) {
    if (isl.onFailureOf) {
      edges.push({ id: `error:${isl.onFailureOf}->${isl.name}`, from: isl.onFailureOf, to: isl.name, kind: 'error' });
    }
  }

  return { nodes, edges, waves };
}
