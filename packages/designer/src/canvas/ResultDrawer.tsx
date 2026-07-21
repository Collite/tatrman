// SPDX-License-Identifier: Apache-2.0
// DM-P4.S2 (ported from modeler DS-P5.S2, D-5) — the run-result drawer: a bottom panel showing a
// display sink's table. Results live HERE (drawer) + as a base-layer preview chip on the display
// node — NEVER as in-canvas result cards (D-5). Lean HTML table, no grid dependency. Pure read
// (a run mutates no model doc) → OPEN.
import type { ArrowTable } from '../model/run-source.js';
import { canvas as palette } from '@tatrman/tokens'; // canvas token family (contracts §6)

export interface ResultDrawerProps {
  sinkRef: string;
  table: ArrowTable;
  onClose: () => void;
}

const fmt = (v: unknown): string => {
  if (v == null) return '';
  if (typeof v === 'number') return Number.isInteger(v) ? String(v) : v.toLocaleString(undefined, { maximumFractionDigits: 2 });
  return String(v);
};

export function ResultDrawer({ sinkRef, table, onClose }: ResultDrawerProps) {
  return (
    <div
      data-testid="result-drawer"
      style={{
        position: 'absolute', left: 0, right: 0, bottom: 0, maxHeight: '45%',
        display: 'flex', flexDirection: 'column', background: palette.nodeFill, borderTop: `2px solid ${palette.grid}`,
        boxShadow: '0 -4px 16px rgba(0,0,0,.08)', zIndex: 12,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 12px', borderBottom: `1px solid ${palette.divider}`, fontSize: 12.5 }}>
        <span aria-hidden>▤</span>
        <strong data-testid="result-sink">display {sinkRef}</strong>
        <span data-testid="result-rowcount" style={{ color: palette.muted }}>{table.numRows} rows</span>
        <button
          data-testid="result-close"
          onClick={onClose}
          style={{ marginLeft: 'auto', border: 'none', background: 'transparent', cursor: 'pointer', fontSize: 14, color: palette.muted }}
          aria-label="Close result drawer"
        >
          ✕
        </button>
      </div>
      <div style={{ overflow: 'auto' }}>
        <table data-testid="result-table" style={{ borderCollapse: 'collapse', width: '100%', fontSize: 12 }}>
          <thead>
            <tr>
              {table.columns.map((c) => (
                <th key={c} style={{ textAlign: 'left', padding: '5px 12px', borderBottom: `1.5px solid ${palette.grid}`, background: palette.tableHeaderBg, color: palette.slate, position: 'sticky', top: 0 }}>{c}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {table.rows.map((row, i) => (
              <tr key={i} data-testid="result-row">
                {row.map((cell, j) => (
                  <td key={j} style={{ padding: '4px 12px', borderBottom: `1px solid ${palette.divider}`, color: palette.ink, whiteSpace: 'nowrap' }}>{fmt(cell)}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
