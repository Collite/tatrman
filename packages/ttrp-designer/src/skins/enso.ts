import type { Skin } from './types.js';
import type { DesignerNode } from '../graph/derive-orchestration.js';

/**
 * Text-forward skin (Enso idiom, C1-b): the node label is its er/provenance name if
 * present, else the ζ label; nodes flow top→down. Same element set as any other skin —
 * only classes/labels differ (C1-b-ii: switching never touches positions).
 */
export const enso: Skin = {
  id: 'enso',
  orientation: 'TD',
  nodeLabel(n: DesignerNode): string {
    if (n.provenance?.originName) return n.provenance.originName;
    return n.label;
  },
  nodeClasses(n: DesignerNode): string[] {
    const cls = [`kind-${n.kind}`, 'enso-node'];
    if (n.isContainer) cls.push('container');
    if (n.synthesized) cls.push('synthesized');
    if (n.derived) cls.push('derived');
    return cls;
  },
  style: [
    {
      selector: 'node',
      style: {
        shape: 'round-rectangle',
        'background-color': '#f8fafc',
        'border-width': 1,
        'border-color': '#334155',
        label: 'data(label)',
        color: '#0f172a',
        'text-valign': 'center',
        'font-size': 13,
        width: 'label',
        padding: '10px',
      },
    },
    { selector: 'node.container', style: { 'background-color': '#e2e8f0', 'border-width': 2 } },
    { selector: 'node.synthesized', style: { 'border-style': 'dashed', color: '#64748b' } },
    { selector: 'edge', style: { width: 1.5, 'line-color': '#334155', 'target-arrow-shape': 'triangle', 'target-arrow-color': '#334155', 'curve-style': 'taxi', 'taxi-direction': 'downward' } },
    { selector: 'edge.control', style: { 'line-style': 'dashed', 'line-color': '#94a3b8', 'target-arrow-color': '#94a3b8' } },
  ],
};
