// The binding ribbon (C-2, contracts §4.1) — a PURPOSE-BUILT view, NOT the canvas engine
// (C-1 γ). Two columns (entities ← ribbons → targets); query-bound entities render as
// first-class query cards (gear glyph, dashed border + dashed ribbon, base-table provenance);
// a dangling bind renders with the DS-PERSP-002 warning treatment, never hidden. Selecting an
// entity expands it to attribute→column ribbons (the parent regenerates with selectedEntity).

import type { BindingRibbon, BindingRow } from '@tatrman/perspectives';

const COLORS = {
  line: '#CBD8E6', card: '#FFFFFF', border: '#D5DEE9', text: '#16283F', muted: '#6B7A8D',
  query: '#8a6a10', queryBg: '#FBF2DA', queryBorder: '#E0C56A',
  warn: '#B3261E', warnBg: '#FBE9E7', warnBorder: '#E7A9A2',
};

/** A dashed (query) or solid (table) ribbon connector drawn between the two columns. */
function Ribbon({ dashed }: { dashed?: boolean }) {
  return (
    <svg width={48} height={20} style={{ flex: '0 0 auto' }} aria-hidden data-testid="binding-ribbon-path">
      <line
        x1={0} y1={10} x2={48} y2={10}
        stroke={COLORS.line} strokeWidth={2}
        strokeDasharray={dashed ? '4 3' : undefined}
      />
    </svg>
  );
}

function EntityCard({ label, qname, selected, onClick }: { label: string; qname: string; selected: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      data-testid="binding-entity"
      data-qname={qname}
      aria-pressed={selected}
      onClick={onClick}
      style={{
        textAlign: 'left', minWidth: 150, padding: '8px 12px', borderRadius: 8, cursor: 'pointer',
        background: COLORS.card, border: `1.5px solid ${selected ? '#5B7EA6' : COLORS.border}`,
        color: COLORS.text, fontSize: 13, fontWeight: 600,
        boxShadow: selected ? '0 0 0 2px rgba(91,126,166,0.25)' : undefined,
      }}
    >
      {label}
    </button>
  );
}

function BindingRowView({ row, selected, expandedPairs, onSelect }: {
  row: BindingRow;
  selected: boolean;
  expandedPairs?: { attribute: string; column: string }[];
  onSelect: (qname: string) => void;
}) {
  const entityCard = (
    <EntityCard
      label={row.entity.label ?? row.entity.qname}
      qname={row.entity.qname}
      selected={selected}
      onClick={() => onSelect(row.entity.qname)}
    />
  );

  let target: React.ReactNode;
  let dashed = false;
  if (row.kind === 'table') {
    target = (
      <div data-testid="binding-table" style={{ minWidth: 150, padding: '8px 12px', borderRadius: 8, background: COLORS.card, border: `1.5px solid ${COLORS.border}`, color: COLORS.text, fontSize: 13 }}>
        <span style={{ color: COLORS.muted, marginRight: 6 }}>▦</span>{row.table.label ?? row.table.qname}
      </div>
    );
  } else if (row.kind === 'query') {
    dashed = true;
    target = (
      <div
        data-testid="binding-query-card"
        style={{ minWidth: 190, padding: '8px 12px', borderRadius: 8, background: COLORS.queryBg, border: `1.5px dashed ${COLORS.queryBorder}`, color: COLORS.query, fontSize: 12.5 }}
      >
        <div style={{ fontWeight: 700 }}><span aria-hidden style={{ marginRight: 6 }}>⚙</span>{row.query.qname.split('.').pop()}</div>
        {row.query.predicate && <div style={{ marginTop: 2, fontStyle: 'italic' }}>{row.query.predicate}</div>}
        <div data-testid="binding-query-provenance" style={{ marginTop: 4, fontSize: 11, color: COLORS.muted }}>
          from {row.query.provenance.length ? row.query.provenance.map((t) => t.label ?? t.qname.split('.').pop()).join(', ') : '—'}
        </div>
      </div>
    );
  } else {
    // unresolved (DS-PERSP-002) — shown with the warning treatment, never dropped.
    dashed = true;
    target = (
      <div
        data-testid="binding-unresolved"
        data-diagnostic="DS-PERSP-002"
        style={{ minWidth: 150, padding: '8px 12px', borderRadius: 8, background: COLORS.warnBg, border: `1.5px dashed ${COLORS.warnBorder}`, color: COLORS.warn, fontSize: 12.5 }}
      >
        <span aria-hidden style={{ marginRight: 6 }}>⚠</span>unresolved bind
        {row.detail && <span style={{ color: COLORS.muted }}> → {row.detail}</span>}
        <div style={{ fontSize: 10.5, color: COLORS.muted, marginTop: 2 }}>DS-PERSP-002</div>
      </div>
    );
  }

  return (
    <div data-testid="binding-row" data-kind={row.kind} style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 0 }}>
        {entityCard}
        <Ribbon dashed={dashed} />
        {target}
      </div>
      {selected && expandedPairs && (
        <div data-testid="binding-expansion" style={{ marginLeft: 24, paddingLeft: 12, borderLeft: `2px solid ${COLORS.line}`, display: 'flex', flexDirection: 'column', gap: 2 }}>
          {expandedPairs.map((p) => (
            <div key={p.attribute} data-testid="binding-pair" style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: COLORS.muted }}>
              <span style={{ color: COLORS.text }}>{p.attribute}</span>
              <Ribbon />
              <span>{p.column}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export function BindingRibbonView({ ribbon, onSelectEntity }: {
  ribbon: BindingRibbon;
  onSelectEntity?: (qname: string | null) => void;
}) {
  const selectedEntity = ribbon.expanded?.entity ?? null;
  return (
    <div data-testid="binding-ribbon-view" style={{ padding: '16px 20px', display: 'flex', flexDirection: 'column', gap: 10, overflow: 'auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', maxWidth: 460, fontSize: 11, letterSpacing: 0.4, textTransform: 'uppercase', color: COLORS.muted }}>
        <span>entity (er)</span><span>target (db)</span>
      </div>
      {ribbon.rows.map((row) => {
        const selected = row.entity.qname === selectedEntity;
        return (
          <BindingRowView
            key={row.entity.qname}
            row={row}
            selected={selected}
            expandedPairs={selected ? ribbon.expanded?.pairs : undefined}
            onSelect={(q) => onSelectEntity?.(q === selectedEntity ? null : q)}
          />
        );
      })}
    </div>
  );
}
