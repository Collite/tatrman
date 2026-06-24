import type { Rule } from '../rule.js';
import { STRUCTURE_RULES } from './structure.js';
import { SEARCH_RULES } from './search.js';
import { REFERENCE_RULES } from './references.js';
import { IMPORT_RULES } from './imports.js';
import { PACKAGE_RULES } from './packages.js';
import { AREA_RULES } from './areas.js';
import { GRAPH_RULES } from './graph.js';
import { PROJECT_RULES } from './project.js';

/** Every rule in the registry, assembled from the per-category rule modules. */
export const ALL_RULES: Rule[] = [
  ...STRUCTURE_RULES,
  ...SEARCH_RULES,
  ...REFERENCE_RULES,
  ...IMPORT_RULES,
  ...PACKAGE_RULES,
  ...AREA_RULES,
  ...GRAPH_RULES,
  ...PROJECT_RULES,
];
