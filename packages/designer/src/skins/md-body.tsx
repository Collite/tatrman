// Shared md (multidimensional) render bodies for the two md skins (md.star-glyph / md.er-dialect).
// P-1 by construction: both skins render the SAME data through these helpers — the cube's
// measures + calc chips, and a dimension's ordered level-stack — so neither can smuggle a datum
// the other can't show. The two skins differ only in FRAMING (star polygon + orbit vs er cards).

import type { CanvasNode } from '@tatrman/canvas-core';
import { canvas as palette } from '@tatrman/tokens'; // canvas token family (contracts §6)

export interface MdRow {
  name: string;
  qname: string;
  kind: string;
  type: string | null;
  isKey: boolean;
}

const rows = (node: CanvasNode): MdRow[] => (node.slotData.rows as MdRow[] | undefined) ?? [];

export const measuresOf = (node: CanvasNode): MdRow[] => rows(node).filter((r) => r.kind === 'measure');
export const levelsOf = (node: CanvasNode): MdRow[] => rows(node).filter((r) => r.kind === 'level');
/** derived measures the grammar can't express — arrive via the designer fixture channel. */
export const calcsOf = (node: CanvasNode): string[] => (node.slotData.calcs as string[] | undefined) ?? [];

/** The cube's contents: its measures, then any fixture-filled derived (`calc`) measures. */
export function MdCubeBody({ node }: { node: CanvasNode }) {
  const measures = measuresOf(node);
  const calcs = calcsOf(node);
  return (
    <div data-testid="md-cube-body" style={{ display: 'flex', flexDirection: 'column', gap: 3, alignItems: 'center' }}>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 3, justifyContent: 'center' }}>
        {measures.map((m) => (
          <span key={m.qname} data-testid="md-measure" title={m.type ?? undefined}
            style={{ fontSize: 11, background: palette.headerTint, color: palette.ink, border: `1px solid ${palette.nodeStroke}`, borderRadius: 10, padding: '1px 7px' }}>
            {m.name}
          </span>
        ))}
      </div>
      {calcs.length > 0 && (
        <div data-testid="md-calc-banner" style={{ display: 'flex', gap: 4, flexWrap: 'wrap', justifyContent: 'center' }}>
          {calcs.map((c) => (
            <span key={c} data-testid="md-calc"
              style={{ fontSize: 10.5, background: palette.warnBgSoft, color: palette.warnInkSoft, border: `1px solid ${palette.warnBorderSoft}`, borderRadius: 8, padding: '1px 7px' }}>
              calc: {c}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

/** A dimension's ordered level-stack (coarse→fine); calc-driven levels carry their `via` map. */
export function MdDimBody({ node }: { node: CanvasNode }) {
  const levels = levelsOf(node);
  return (
    <div data-testid="md-dim-body" style={{ display: 'flex', flexDirection: 'column' }}>
      {levels.map((lvl, i) => (
        <div key={lvl.qname} data-testid="md-level"
          style={{ fontSize: 11, padding: '2px 8px', color: palette.ink, borderTop: i === 0 ? 'none' : `1px solid ${palette.divider}`, display: 'flex', gap: 6, alignItems: 'center' }}>
          <span style={{ color: palette.muted, width: 10 }}>{i === 0 ? '▸' : '·'}</span>
          <span data-testid="md-level-name" style={{ flex: 1 }}>{lvl.name}</span>
          {lvl.type && <span data-testid="md-level-via" style={{ color: palette.muted, fontSize: 9.5 }}>via {lvl.type.split('.').pop()}</span>}
        </div>
      ))}
    </div>
  );
}

// deterministic node sizing shared by both skins.
export const CUBE_SIZE = { width: 168, height: 132 };
export function dimSize(node: CanvasNode) {
  return { width: 150, height: 26 + Math.max(1, levelsOf(node).length) * 22 };
}
