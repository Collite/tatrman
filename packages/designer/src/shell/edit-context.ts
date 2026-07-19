// SPDX-License-Identifier: Apache-2.0
// The FO-21 edit seam (Designer Merge, DM-P2.S3/S5 / contracts §4). The OPEN Studio Viewer ships NO
// edit code: `ShellFrame` reaches edit ONLY through an injected `ShellEditContext`, which is ABSENT
// in the open build. Crucially, the interface is **marker-free** — the shell never names an edit RPC
// (`addObjectToGraph`, …) nor renders an edit label (`+ Add object`, `Remove from graph`); those
// strings live wholly inside the authoring extension's contributed surfaces, so the open bundle is
// grep-clean (the `check-viewer-bundle.mjs` FO-21 guard). The shell only calls GENERIC verbs
// (`removeNode`/`saveNode`) and mounts extension-rendered slots (`render*`).
//
// The commercial build's FO-P0.S4 license loader supplies this context (DM-P3): its verbs map to the
// real mutation ops and its slots mount the insertion doors / pickers onto the Viewer core.

import type { ReactNode } from 'react';

/** Props the shell hands each edit slot: the current graph + a callback to re-fetch after a mutation. */
export interface EditSlotProps {
  graphRef: string;
  currentImports: string[];
  onApplied(): void;
}

/** Props the processing canvas hands the insertion-doors slot (DM-P3.S3; DM-P4 consumes it). An edge
 *  insertion target is the row-less `{edgeId, from, to, role}` shape the authoring extension owns —
 *  the shell never names an op, only forwards these opaquely. */
export interface ProcessingDoorsSlotProps {
  edges: Array<{ edgeId: string; from: { node: string; port: string }; to: { node: string; port: string }; role: 'data' | 'control' | 'transfer' }>;
  midpointOf?: (edgeId: string) => { x: number; y: number };
  selectedEdgeId?: string | null;
  openPaletteRef?: { current: (() => void) | null };
  onApplied(): void;
}

/**
 * The edit capability the shell reads (never hard-codes). `editable` = the active backend's
 * `capabilities.edit` AND a license grant (contracts §4). When the whole context is undefined (open
 * build) the shell is edit-absent: gestures route to peek + `DS-EDIT-001` (the DS read-only behavior).
 * All members use generic names / are extension-rendered — no edit-RPC or affordance string leaks into
 * the open bundle (FO-21).
 */
export interface ShellEditContext {
  editable: boolean;
  /** remove a node from the current graph; resolves true when an edit was applied. */
  removeNode(graphRef: string, qname: string): Promise<boolean>;
  /** persist a whole-node source edit (the drawer save). */
  saveNode(qname: string, text: string): Promise<{ ok: boolean; reason?: string }>;
  /** the subject-toolbar edit actions — the add-object entry point + its picker live in here. */
  renderToolbarActions(props: EditSlotProps): ReactNode;
  /** the node context menu (Remove, …) rendered over a right-clicked node. */
  renderNodeMenu(props: { qname: string; graphRef: string; onApplied(): void; onClose(): void }): ReactNode;
  /** the missing-objects affordance (interactive removal). */
  renderMissingObjects(props: { graphRef: string; missingObjects: string[]; onApplied(): void }): ReactNode;
  /** the processing-canvas insertion doors (DM-P3.S3). OPTIONAL — DM-P4's OPEN ProcessingCanvas mounts
   *  it only when a context is present; absent in the open build. */
  renderProcessingDoors?(props: ProcessingDoorsSlotProps): ReactNode;
}
