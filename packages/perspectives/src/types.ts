// SPDX-License-Identifier: Apache-2.0
// Perspective generator contracts (contracts §4). Pure TS — depends on @tatrman/canvas-core
// types only, no React (PF wall). DS-P0.S2 pins the SHAPES; the generators' bodies land in
// DS-P4 (they fail-fast until then).

import type { CanvasGraph } from '@tatrman/canvas-core';

export interface PerspectiveGenerator<In, Out extends PerspectiveResult> {
  id: 'binding' | 'lineage';
  /** pure function of graph-method data — testable without React */
  generate(input: In): Out;
}

export type PerspectiveResult =
  | { kind: 'canvas'; graph: CanvasGraph } // rendered by kernel + skin
  | { kind: 'custom'; view: 'binding-ribbon' | 'lineage-layers'; data: unknown }; // purpose-built (C-1 γ)

// ---- refs (lightweight; qname is the correlation key everywhere) ----
export interface EntityRef { qname: string; label?: string }
export interface TableRef { qname: string; label?: string }
export type ObjectKind = 'column' | 'attribute' | 'measure' | 'calc' | 'program' | 'run' | 'commit';
export interface ObjectRef { qname: string; kind: ObjectKind; label?: string }

// ---- lean model-graph view the pure generators read (structurally compatible with
// @tatrman/lsp's ModelGraph, so the designer adapter passes an lsp ModelGraph directly;
// perspectives stays lsp-free — canvas-core only, PF wall). ----
export interface PModelRow { name: string; qname: string; kind?: string }
export interface PModelNode { qname: string; label?: string; rows?: PModelRow[] }
export interface PModelEdge { fromNode: string; toNode: string; kind?: string; qname?: string }
export interface PModelGraph { nodes: PModelNode[]; edges?: PModelEdge[] }

// ---- §4.1 Binding (C-2 — er↔db only, purpose-built ribbon) ----
export interface QueryCard {
  qname: string;
  predicate: string; // one-line predicate
  provenance: TableRef[]; // stub up to base tables; dashed ribbon
}

/** One entity→target binding fact the generator consumes (from the `binding` model:
 *  `def er2db_entity`). A `query` target is FIRST-CLASS (C-2); `unresolved` = dangling. */
export interface EntityBindingData {
  entityQname: string;
  target:
    | { kind: 'table'; tableQname: string }
    | { kind: 'query'; queryQname: string }
    | { kind: 'unresolved'; raw?: string };
}
/** One attribute→column binding fact (`def er2db_attribute`), read for the selection expansion. */
export interface AttributeBindingData { attributeQname: string; columnQname: string }
/** Metadata for a query-bound entity — the one-line predicate + base-table provenance stub. */
export interface QueryInfo { qname: string; predicate: string; provenance: string[] }
/** The binding-model data the generator reads (er↔db only; C-2). */
export interface BindingMap {
  entities: EntityBindingData[];
  attributes: AttributeBindingData[];
  queries?: QueryInfo[];
}

export type BindingRow =
  | { kind: 'table'; entity: EntityRef; table: TableRef }
  | { kind: 'query'; entity: EntityRef; query: QueryCard } // FIRST-CLASS (C-2)
  | { kind: 'unresolved'; entity: EntityRef; diagnostic: 'DS-PERSP-002'; detail?: string }; // dangling bind — shown, never dropped

export interface BindingRibbon {
  rows: BindingRow[]; // entity→table at rest
  expanded?: { entity: string; pairs: { attribute: string; column: string }[] }; // on selection
}

export interface BindingInput {
  /** er + db model-graph data (label enrichment; the ribbon iterates the binding map, not the graphs) */
  er: PModelGraph;
  db: PModelGraph;
  bindings: BindingMap;
  selectedEntity?: string; // entity qname; expands exactly this entity to attribute→column pairs
}

// ---- §4.2 Lineage (C-3 + C-4 impact toggle) ----
export type LineageScope = 'column' | 'neighborhood' | 'fullPath'; // α/β/γ; DEFAULT neighborhood
export type LineageDirection = 'upstream' | 'downstream'; // impact = downstream (C-4)

export interface LineageQuery {
  root: ObjectRef; // any column/attribute/measure
  scope: LineageScope;
  direction: LineageDirection;
}

export type LineageFace = 'db' | 'er' | 'md' | 'program' | 'runs';
// layer order for the purpose-built layers view (db upstream → runs downstream)
export const LINEAGE_FACE_ORDER: LineageFace[] = ['db', 'er', 'md', 'program', 'runs'];

export interface LineageNode { kind: ObjectKind; ref: ObjectRef; label: string; face: LineageFace }
export interface LineageLayer { face: LineageFace; nodes: LineageNode[] }
export interface LineageEdge { from: string; to: string; relation: LineageRelation } // ObjectRef qnames

/** How two lineage objects relate. Data flows source→consumer along from→to (see LineageLink). */
export type LineageRelation = 'binds' | 'derives' | 'writes' | 'reads' | 'runs';

export interface LineageGraph {
  layers: LineageLayer[]; // db → er → md → program → runs (purpose-built layers view)
  edges: LineageEdge[];
  degraded?: {
    requested: 'fullPath';
    served: 'neighborhood';
    reason: 'runs-need-platform-backend'; // DS-PERSP-001, rendered as a hint
  };
}

// ---- composed cross-face model the generator reads (pure). Data flows source→consumer along
// from→to; 'binds'/'derives' are the model bind-chain (α), 'writes'/'reads' are program
// provenance (β adds these one hop), 'runs' are run instances (γ). ----
export interface LineageObject { qname: string; kind: ObjectKind; label: string; face: LineageFace }
export interface LineageLink { from: string; to: string; relation: LineageRelation } // qnames; from=source, to=consumer
export interface LineageModel { objects: LineageObject[]; links: LineageLink[] }

export interface LineageInput {
  query: LineageQuery;
  /** composed db+er+md(+program) objects and their cross-face links */
  model: LineageModel;
  /** PlatformRunsSource (optional). Absent + a γ request ⇒ degrade to β with DS-PERSP-001. */
  runs?: { forObject: string; runs: { qname: string; label: string }[] }[];
}
