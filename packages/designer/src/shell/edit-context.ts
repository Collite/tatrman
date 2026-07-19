// SPDX-License-Identifier: Apache-2.0
// The FO-21 edit seam (Designer Merge, DM-P2.S3 / contracts §4). The OPEN Studio Viewer ships NO
// edit code: `ShellFrame` renders edit affordances only through an injected `ShellEditContext`,
// which is ABSENT in the open build (`editable` effectively false, no `ops`, no surfaces). The
// commercial build's FO-P0.S4 license loader mounts `@tatrman/designer-authoring` (DM-P3), which
// supplies this context — its ops back `applyGraphEdit`/add/remove and its `render*` functions mount
// the insertion doors + pickers onto the Viewer core. So the wall is a *repo* wall (edit code lives
// in tatrman-platform) + a *license* gate (the loader), exactly as contracts §4 specifies.
//
// DM-P2.S3 defines the seam shape and the OPEN edit-absent behavior; DM-P3 implements the ops +
// surfaces in the authoring extension.

import type { ReactNode } from 'react';

/** The mutation ops the authoring extension contributes (DM-P3). All return an applied/failed signal. */
export interface EditOpClient {
  /** add an object to the graph; resolves true when an edit was applied. */
  addObjectToGraph(graphRef: string, qname: string, autoImport: boolean): Promise<boolean>;
  /** remove an object from the graph; resolves true when an edit was applied. */
  removeObjectFromGraph(graphRef: string, qname: string): Promise<boolean>;
  /** apply a structured `GraphOp[]` (the one C1-d emission path — insertion doors + drawer save). */
  applyGraphEdit(ops: unknown[]): Promise<{ ok: boolean; reason?: string }>;
  /** whole-node source edit (drawer save) — a `set-source` op under the hood. */
  setSource(qname: string, text: string): Promise<{ ok: boolean; reason?: string }>;
}

/**
 * The edit capability the shell reads (never hard-codes). `editable` = the active backend's
 * `capabilities.edit` AND a license grant (contracts §4). When the whole context is undefined (open
 * build) the shell is edit-absent: gestures route to peek + `DS-EDIT-001` (the DS read-only behavior).
 */
export interface ShellEditContext {
  editable: boolean;
  ops: EditOpClient;
  /** the add-object picker surface, contributed by the authoring extension (mounted iff editable). */
  renderAddObjectPicker(props: {
    currentImports: string[];
    onSelect(qname: string, autoImport: boolean): void;
    onClose(): void;
  }): ReactNode;
  /** the missing-objects drawer surface (its remove action is an edit → authoring-owned). */
  renderMissingObjectsDrawer(props: {
    missingObjects: string[];
    onRemove(qname: string): void;
    onClose(): void;
  }): ReactNode;
}
