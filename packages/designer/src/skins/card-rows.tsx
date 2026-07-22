// Shared row-card body for the two S2 modeling skins (er.crow / db.table-classic). Both are
// "header + rows" cards; they differ in kind mark, row glyphs, and cardinality treatment.

import type { CanvasNode, NodeSize } from '@tatrman/canvas-core';
import { canvas as palette } from '@tatrman/tokens'; // canvas token family (contracts §6)

export interface Row {
  name: string;
  qname: string;
  kind: 'column' | 'attribute';
  type: string | null;
  isKey: boolean;
  optional: boolean;
  isNameAttribute: boolean;
  isCodeAttribute: boolean;
}

export const rowsOf = (node: CanvasNode): Row[] => (node.slotData.rows as Row[] | undefined) ?? [];

export const HEADER_H = 30;
export const ROW_H = 20;

export function cardSize(node: CanvasNode, width: number): NodeSize {
  return { width, height: HEADER_H + Math.max(1, rowsOf(node).length) * ROW_H + 6 };
}

export function RowCard({
  node, kindMark, headerBg, showTypes,
}: { node: CanvasNode; kindMark: string; headerBg: string; showTypes: boolean }) {
  const rows = rowsOf(node);
  return (
    <div
      data-testid="row-card"
      style={{ width: '100%', height: '100%', background: palette.nodeFill, border: `1.3px solid ${palette.nodeStroke}`, borderRadius: 8, overflow: 'hidden', fontSize: 12 }}
    >
      <div style={{ height: HEADER_H, background: headerBg, color: palette.nodeFill, display: 'flex', alignItems: 'center', gap: 6, padding: '0 10px', fontWeight: 'bold' }}>
        <span data-testid="kind-mark" aria-hidden>{kindMark}</span>
        <span data-testid="node-label">{node.label}</span>
      </div>
      <div>
        {rows.map((r) => (
          <div
            key={r.qname}
            data-testid="card-row"
            data-key={r.isKey || undefined}
            style={{ height: ROW_H, display: 'flex', alignItems: 'center', gap: 6, padding: '0 10px', color: palette.ink, borderTop: `1px solid ${palette.divider}` }}
          >
            <span data-testid="key-mark" style={{ width: 12, color: palette.aliveDeep }}>{r.isKey ? '⚿' : ''}</span>
            <span style={{ flex: 1 }}>{r.name}</span>
            {showTypes && r.type && <span data-testid="row-type" style={{ color: palette.muted, fontSize: 10.5 }}>{r.type}</span>}
          </div>
        ))}
      </div>
    </div>
  );
}
