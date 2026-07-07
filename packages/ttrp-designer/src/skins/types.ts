import type { StylesheetJson } from 'cytoscape';
import type { DesignerNode } from '../graph/derive-orchestration.js';
import type { Orientation } from '../cy/orientation.js';

/**
 * A per-canvas skin (C1-b, C1-b-iii): a visual dialect for the same graph. v1 roster =
 * `alteryx-knime` (icon-per-kind, data edges prominent, L→R) + `enso` (text-forward,
 * top-down). Switching skins never touches positions (C1-b-ii).
 */
export interface Skin {
  id: 'alteryx-knime' | 'enso';
  orientation: Orientation;
  style: StylesheetJson;
  nodeLabel(n: DesignerNode): string;
  nodeClasses(n: DesignerNode): string[];
}

export type SkinId = Skin['id'];
