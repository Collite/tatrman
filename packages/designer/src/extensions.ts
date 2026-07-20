// SPDX-License-Identifier: Apache-2.0
// `@tatrman/designer/extensions` — the public Designer Extensions surface (contracts §10). Extension
// authors import the interfaces from here; the shell imports the loader. Composed atop the FO `src/ext/`
// license gate (RO-31 / VS-5) — see `ext/designer-extensions.ts`. This is the OPEN contract: any change
// requires a contracts.md changelog entry on BOTH the PL and FO sides (tell the reviewer).
export type {
  DesignerExtension,
  PanelContribution,
  PanelContext,
  ExtensionContext,
  BackendInfo,
  AdvertisedExtension,
  LoadDesignerExtensionsOptions,
  DesignerExtensionsResult,
} from './ext/designer-extensions.js';
export { loadDesignerExtensions } from './ext/designer-extensions.js';
