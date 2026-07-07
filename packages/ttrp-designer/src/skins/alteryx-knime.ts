import type { Skin } from './types.js';
import type { DesignerNode } from '../graph/derive-orchestration.js';

/** One glyph per T10 node kind (icon-per-kind, the Alteryx/KNIME idiom). */
const GLYPH: Record<string, string> = {
  Load: '⭳',
  Store: '⭱',
  Transfer: '⇄',
  Filter: '▽',
  Join: '⋈',
  Aggregate: 'Σ',
  Branch: '⑂',
  Switch: '⌥',
  Sort: '↕',
  Union: '⊍',
  Intersect: '∩',
  Except: '∖',
  Values: '≡',
  Limit: '⊤',
  Pivot: '⇱',
  Distinct: '≠',
  Project: '☰',
  Display: '▦',
  Container: '▧',
};

export const alteryxKnime: Skin = {
  id: 'alteryx-knime',
  orientation: 'LR',
  nodeLabel(n: DesignerNode): string {
    return `${GLYPH[n.kind] ?? '◻'} ${n.label}`;
  },
  nodeClasses(n: DesignerNode): string[] {
    const cls = [`kind-${n.kind}`];
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
        'background-color': '#0284c7',
        label: 'data(label)',
        color: '#fff',
        'text-valign': 'center',
        'font-size': 12,
        width: 'label',
        padding: '8px',
      },
    },
    { selector: 'node.container', style: { 'background-color': '#075985', shape: 'round-rectangle' } },
    { selector: 'node.synthesized', style: { 'background-color': '#64748b', 'border-style': 'dashed' } },
    // Data edges prominent; control edges minimal (dashed, faint).
    { selector: 'edge', style: { width: 2, 'line-color': '#0ea5e9', 'target-arrow-shape': 'triangle', 'target-arrow-color': '#0ea5e9', 'curve-style': 'bezier' } },
    { selector: 'edge.control', style: { width: 1, 'line-color': '#cbd5e1', 'line-style': 'dashed', 'target-arrow-color': '#cbd5e1' } },
  ],
};
