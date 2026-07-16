// SPDX-License-Identifier: Apache-2.0
// Import via `./generated/...` (NOT `../src/generated/...`): the compiled
// dist/index.js must resolve its siblings inside dist/ (→ dist/generated/*.js).
// The old `../src/generated/*.js` path only ever resolved through the in-repo
// vitest source-alias and left the PUBLISHED package's `.` entry broken for
// external consumers (the .js exists only in dist/, never in src/).
export { PROPERTY_MAP, SEARCH_SUB_PROPERTIES } from './generated/property-map.js';
export type { DefinitionKind, PropertyInfo } from './generated/property-map.js';
export { TTR_GRAMMAR_VERSION } from './generated/version.js';