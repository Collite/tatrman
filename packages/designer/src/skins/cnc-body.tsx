// Shared cnc (conceptual) render helpers for the two cnc skins (cnc.bubbles / cnc.cards).
// P-1 by construction: both read the SAME data — a concept's properties (attribute rows), its
// role, and synonyms — so neither smuggles a datum the other can't show. Role + synonyms arrive
// via the designer fixture channel (cnc can't declare them yet — CNC-GAPS); properties are real.

import type { CanvasNode } from '@tatrman/canvas-core';

export interface CncProp { name: string; qname: string; kind: string; type: string | null }

const rows = (node: CanvasNode): CncProp[] => (node.slotData.rows as CncProp[] | undefined) ?? [];

export const propertiesOf = (node: CanvasNode): CncProp[] => rows(node).filter((r) => r.kind === 'attribute');
export const roleOf = (node: CanvasNode): string | undefined => node.slotData.role as string | undefined;
export const synonymsOf = (node: CanvasNode): string[] => (node.slotData.synonyms as string[] | undefined) ?? [];

/** The role chip (fixture-filled) — shown at rest by both skins. */
export function RoleChip({ node }: { node: CanvasNode }) {
  const role = roleOf(node);
  if (!role) return null;
  return (
    <span data-testid="cnc-role" style={{ fontSize: 10, background: '#E6EEF7', color: '#33506e', border: '1px solid #B9CCE0', borderRadius: 8, padding: '0 6px' }}>
      {role}
    </span>
  );
}

/** Property + synonym chips — shared markup; the bubbles skin gates them on focus, cards shows
 *  them at rest. Stable testids (`cnc-prop` / `cnc-synonym`) drive the P-1 parity assertion. */
export function PropertyChips({ node }: { node: CanvasNode }) {
  const props = propertiesOf(node);
  const synonyms = synonymsOf(node);
  return (
    <div data-testid="cnc-props" style={{ display: 'flex', flexWrap: 'wrap', gap: 3, justifyContent: 'center' }}>
      {props.map((p) => (
        <span key={p.qname} data-testid="cnc-prop" style={{ fontSize: 10.5, background: '#F1F5FB', color: '#16283F', border: '1px solid #D9E4F1', borderRadius: 9, padding: '1px 6px' }}>
          {p.name}
        </span>
      ))}
      {synonyms.map((s) => (
        <span key={s} data-testid="cnc-synonym" style={{ fontSize: 10, background: '#fff', color: '#96989B', border: '1px dashed #CBD8E6', borderRadius: 9, padding: '1px 6px' }}>
          “{s}”
        </span>
      ))}
    </div>
  );
}

export function conceptSize(node: CanvasNode) {
  const props = propertiesOf(node).length + synonymsOf(node).length;
  return { width: 150, height: 60 + (props > 0 ? 22 : 0) };
}
