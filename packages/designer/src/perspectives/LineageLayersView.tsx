// The lineage layers view (C-3/C-4, contracts §4.2) — purpose-built (NOT the canvas engine,
// C-1 γ). Renders the db→er→md→program→runs layer chain as ordered columns of object chips.
// P-2: a chip IS the same object as everywhere — clicking one fires the standard open-object
// event (select/open-subject). An α/β/γ scope control (β preselected), an upstream/downstream
// (impact) direction toggle on the SAME view, and — when a γ request degraded — a visible
// labeled degradation bar (DS-PERSP-001) with the γ control still selectable (the user sees
// what they asked for AND what they got).

import type { LineageGraph, LineageScope, LineageDirection, LineageLayer, LineageNode } from '@tatrman/perspectives';

export interface LineageViewHandlers {
  onScopeChange?: (scope: LineageScope) => void;
  onDirectionChange?: (dir: LineageDirection) => void;
  onOpenObject?: (qname: string) => void;
  /** re-root the lineage at a chip. The chip carries its own kind+qname, so this works on the
   *  live path WITHOUT getSymbolDetail (which can't resolve nested column/attribute qnames in v1). */
  onRootAt?: (qname: string, kind: string, label: string) => void;
}

const FACE_LABEL: Record<LineageLayer['face'], string> = {
  db: 'db', er: 'er', md: 'md', program: 'program', runs: 'runs',
};
const SCOPES: { id: LineageScope; label: string; hint: string }[] = [
  { id: 'column', label: 'α column', hint: 'the bind-chain only' },
  { id: 'neighborhood', label: 'β neighborhood', hint: 'who makes this number' },
  { id: 'fullPath', label: 'γ full path', hint: 'calc dependents + runs' },
];

const COLORS = { text: '#16283F', muted: '#6B7A8D', chip: '#FFFFFF', border: '#D5DEE9', line: '#CBD8E6', head: '#5B7EA6' };

const ROOTABLE = new Set(['column', 'attribute', 'measure', 'calc']);

function Chip({ node, onOpen, onRootAt }: { node: LineageNode; onOpen?: (q: string) => void; onRootAt?: (q: string, k: string, l: string) => void }) {
  const rootable = ROOTABLE.has(node.kind) && !!onRootAt;
  return (
    <div data-testid="lineage-chip" data-qname={node.ref.qname} data-kind={node.kind} style={{ display: 'flex', alignItems: 'stretch', marginBottom: 6, borderRadius: 7, overflow: 'hidden', border: `1px solid ${COLORS.border}`, background: COLORS.chip }}>
      <button
        type="button"
        data-testid="lineage-chip-open"
        onClick={() => onOpen?.(node.ref.qname)}
        style={{ flex: 1, textAlign: 'left', padding: '5px 9px', border: 'none', cursor: 'pointer', background: 'transparent', color: COLORS.text, fontSize: 12 }}
        title={node.ref.qname}
      >
        <span style={{ color: COLORS.muted, fontSize: 10, marginRight: 5 }}>{node.kind}</span>{node.label}
      </button>
      {rootable && (
        <button
          type="button"
          data-testid="lineage-chip-reroot"
          onClick={() => onRootAt!(node.ref.qname, node.kind, node.label)}
          title="Trace lineage from here"
          style={{ padding: '0 7px', border: 'none', borderLeft: `1px solid ${COLORS.border}`, cursor: 'pointer', background: '#F2F6FB', color: COLORS.head, fontSize: 12 }}
        >
          ⧉
        </button>
      )}
    </div>
  );
}

export function LineageLayersView({ graph, scope = 'neighborhood', direction = 'upstream', handlers }: {
  graph: LineageGraph;
  scope?: LineageScope;
  direction?: LineageDirection;
  handlers?: LineageViewHandlers;
}) {
  return (
    <div data-testid="lineage-layers-view" style={{ padding: '14px 18px', display: 'flex', flexDirection: 'column', gap: 12, overflow: 'auto' }}>
      {/* controls: scope (α/β/γ) + direction (impact) */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
        <div data-testid="lineage-scope-control" role="group" aria-label="scope" style={{ display: 'inline-flex', border: `1px solid ${COLORS.border}`, borderRadius: 7, overflow: 'hidden' }}>
          {SCOPES.map((s) => (
            <button
              key={s.id}
              data-testid={`scope-${s.id}`}
              aria-pressed={scope === s.id}
              onClick={() => handlers?.onScopeChange?.(s.id)}
              title={s.hint}
              style={{ padding: '4px 11px', border: 'none', cursor: 'pointer', fontSize: 12, background: scope === s.id ? '#16283F' : '#fff', color: scope === s.id ? '#fff' : COLORS.text }}
            >
              {s.label}
            </button>
          ))}
        </div>
        <label data-testid="lineage-direction-toggle" style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: COLORS.text, cursor: 'pointer', userSelect: 'none' }}>
          <input
            type="checkbox"
            data-testid="impact-toggle"
            checked={direction === 'downstream'}
            onChange={() => handlers?.onDirectionChange?.(direction === 'downstream' ? 'upstream' : 'downstream')}
          />
          impact (downstream)
        </label>
      </div>

      {/* degradation hint (DS-PERSP-001) — visible, labeled; the γ control stays selectable above */}
      {graph.degraded && (
        <div data-testid="lineage-degraded-hint" data-diagnostic="DS-PERSP-001" style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 12px', background: '#FBF2DA', color: '#8a6a10', border: '1px solid #E0C56A', borderRadius: 7, fontSize: 12 }}>
          <span aria-hidden>ⓘ</span>
          Showing <b>{graph.degraded.served}</b> — <b>{graph.degraded.requested}</b> needs a platform backend (runs). DS-PERSP-001.
        </div>
      )}

      {/* the layer chain: ordered columns, db (upstream) → runs (downstream). The columns show the
          face grouping; the inter-column separator is a neutral divider (NOT a per-gap flow arrow —
          it never implied a real link). The actual derivation links are drawn below from graph.edges. */}
      <div style={{ display: 'flex', alignItems: 'stretch', gap: 0 }}>
        {graph.layers.length === 0 ? (
          <div data-testid="lineage-empty" style={{ color: COLORS.muted, fontSize: 12 }}>No lineage for this root.</div>
        ) : (
          graph.layers.map((layer, i) => (
            <div key={layer.face} style={{ display: 'flex', alignItems: 'stretch' }}>
              <div data-testid="lineage-layer" data-face={layer.face} style={{ minWidth: 140, padding: '0 10px' }}>
                <div style={{ fontSize: 10.5, textTransform: 'uppercase', letterSpacing: 0.5, color: COLORS.head, marginBottom: 6, fontWeight: 700 }}>{FACE_LABEL[layer.face]}</div>
                {layer.nodes.map((n) => <Chip key={n.ref.qname} node={n} onOpen={handlers?.onOpenObject} onRootAt={handlers?.onRootAt} />)}
              </div>
              {i < graph.layers.length - 1 && (
                <div aria-hidden style={{ width: 1, alignSelf: 'stretch', background: COLORS.line, margin: '18px 0' }} />
              )}
            </div>
          ))
        )}
      </div>

      {/* the real derivation links (C-3): each edge from graph.edges, oriented as the generator
          returns it (source→consumer upstream; the transpose under impact, C-4). Resolving via the
          rendered nodes means a link to an off-canvas object falls back to its qname tail — no
          fabricated face-to-face arrows (which the old decorative per-column ▸ implied). */}
      {graph.edges.length > 0 && (
        <div data-testid="lineage-links" style={{ display: 'flex', flexDirection: 'column', gap: 4, borderTop: `1px solid ${COLORS.border}`, paddingTop: 10 }}>
          <div style={{ fontSize: 10.5, textTransform: 'uppercase', letterSpacing: 0.5, color: COLORS.head, fontWeight: 700 }}>{direction === 'downstream' ? 'impact links' : 'derivation links'}</div>
          {graph.edges.map((e) => (
            <div
              key={`${e.from}|${e.to}|${e.relation}`}
              data-testid="lineage-edge"
              data-from={e.from}
              data-to={e.to}
              data-relation={e.relation}
              style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 12, color: COLORS.text }}
            >
              <span title={e.from}>{labelFor(graph, e.from)}</span>
              <span aria-hidden style={{ color: COLORS.head }}>―{e.relation}→</span>
              <span title={e.to}>{labelFor(graph, e.to)}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/** Resolve an edge endpoint qname to the label shown on its chip; fall back to the qname's leaf. */
function labelFor(graph: LineageGraph, qname: string): string {
  for (const layer of graph.layers) {
    for (const n of layer.nodes) if (n.ref.qname === qname) return n.label;
  }
  const parts = qname.split('.');
  return parts[parts.length - 1] ?? qname;
}
