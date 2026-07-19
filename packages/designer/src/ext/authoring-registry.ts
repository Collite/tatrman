// SPDX-License-Identifier: Apache-2.0
// FO-P0.S4.T5 — the authoring injection seam. ONE core build serves both tiers without the open
// bundle ever carrying edit code (FO-21): the open Studio Viewer never registers a loader, so
// `getAuthoringLoader()` is null and the shell stays edit-absent. The COMMERCIAL Studio build (which
// imports @tatrman/designer-authoring) calls `registerAuthoringLoader` at startup with a loader that
// resolves grants via the license service and drives the extension's `activateWithLicense()` —
// returning the mounted `ShellEditContext`, or null when refused (→ Viewer behavior).

import type { ModelDataSource } from '../data/model-data-source.js';
import type { ShellEditContext } from '../shell/edit-context.js';

/** Resolves the authoring context for the active backend, or null (no grant / no extension). */
export type AuthoringLoader = (ctx: { dataSource: ModelDataSource }) => Promise<ShellEditContext | null>;

let registered: AuthoringLoader | null = null;

/** Called once by the commercial build's entry, before the app renders. */
export function registerAuthoringLoader(loader: AuthoringLoader): void {
  registered = loader;
}

export function getAuthoringLoader(): AuthoringLoader | null {
  return registered;
}

/** test-only: clear the module-level registration between cases. */
export function __resetAuthoringLoader(): void {
  registered = null;
}
