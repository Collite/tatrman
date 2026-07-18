// SPDX-License-Identifier: Apache-2.0
// @tatrman/perspectives — pure-TS perspective generators for the TTR Designer (no React;
// depends on @tatrman/canvas-core only). DS-P0.S2 pins the generator contracts (§4); the
// binding + lineage bodies land in DS-P4.

export * from './types.js';
export { bindingGenerator, generateBindingRibbon, NotImplementedYet } from './binding.js';
export { lineageGenerator, generateLineage } from './lineage.js';
