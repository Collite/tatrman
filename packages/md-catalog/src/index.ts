// SPDX-License-Identifier: Apache-2.0
export type { TimeShape, IntShape, CatalogShape, CatalogParam, CatalogEntry } from './types.js';
export { MD_CALC_CATALOG } from './catalog.js';

/**
 * Catalog semver — the cross-repo sync key (contracts.md §8.2). Bump rules:
 * adding an entry or an optional param = minor; changing a signature/semantics =
 * major; doc/typo = patch. ai-platform pins the version it has lowerings for.
 */
export const MD_CATALOG_VERSION = '0.1.0';
