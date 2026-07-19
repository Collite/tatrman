// The base layer (contracts §2 / D-1) — ONE component set both faces share. Drawn by the
// app at the skin's declared anchor points; identical for a given NodeBaseState regardless
// of skin (the P-3 sweep property). Never-claimable chrome (selection/focus, read-only/
// derived, orphaned-layout) is ALWAYS app-drawn here; status/diagnostics are drawn here
// UNLESS the skin claims them.

import { BADGES, type BadgeSpec, type NodeBaseState, type RunStatus, type AnchorPoint, type AnchorDeclaration } from '@tatrman/canvas-core';

function Badge({ spec, point, testid, count }: { spec: BadgeSpec; point?: AnchorPoint; testid: string; count?: number }) {
  return (
    <div
      data-testid={testid}
      data-badge={spec.id}
      title={spec.meaning}
      style={{
        position: 'absolute',
        left: point?.x ?? 0,
        top: point?.y ?? 0,
        transform: 'translate(-50%,-50%)',
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        height: 18,
        minWidth: 18,
        padding: count != null ? '0 5px' : 0,
        borderRadius: 9,
        background: spec.bg,
        color: spec.fg,
        border: spec.ring ? `1.5px solid ${spec.ring}` : 'none',
        fontSize: 11,
        lineHeight: 1,
        justifyContent: 'center',
        pointerEvents: 'none',
        zIndex: 6,
      }}
    >
      <span aria-hidden>{spec.glyph}</span>
      {count != null && <span>{count}</span>}
    </div>
  );
}

const STATUS_BADGE: Record<Exclude<RunStatus, 'idle'>, BadgeSpec> = {
  running: BADGES.running,
  done: BADGES.done,
  failed: BADGES.error, // failed run ⇒ the ✕ badge
};

export function StatusBadge({ status, point }: { status: RunStatus; point?: AnchorPoint }) {
  if (status === 'idle') return null;
  return <Badge spec={STATUS_BADGE[status]} point={point} testid="status-badge" />;
}

export function DiagnosticsBadge({ diagnostics, point }: { diagnostics: { errorCount: number; warnCount: number }; point?: AnchorPoint }) {
  const { errorCount, warnCount } = diagnostics;
  if (errorCount === 0 && warnCount === 0) return null;
  return (
    <>
      {warnCount > 0 && <Badge spec={BADGES.warn} point={point} testid="diag-warn" count={warnCount} />}
      {errorCount > 0 && (
        <Badge spec={BADGES.error} point={point ? { ...point, y: point.y + 20 } : undefined} testid="diag-error" count={errorCount} />
      )}
    </>
  );
}

/** never-claimable chrome — selection ring + 🔒 (read-only/derived) + orphan mark (D-1). */
export function ChromeOverlay({ state, chrome }: { state: NodeBaseState; chrome: AnchorPoint }) {
  return (
    <>
      {state.selected && (
        <div
          data-testid="selection-ring"
          style={{ position: 'absolute', inset: -4, borderRadius: 14, border: '2.5px solid #F2A200', pointerEvents: 'none', zIndex: 4 }}
        />
      )}
      {(state.readOnly || state.derived) && <Badge spec={BADGES.readOnly} point={chrome} testid="readonly-badge" />}
      {state.orphanedLayout && <Badge spec={BADGES.orphan} point={{ ...chrome, x: chrome.x + 20 }} testid="orphan-badge" />}
    </>
  );
}

export function PreviewChip({ rows, point }: { rows: number; point?: AnchorPoint }) {
  return (
    <div
      data-testid="preview-chip"
      style={{
        position: 'absolute', left: point?.x ?? 0, top: point?.y ?? 0, transform: 'translate(0,-50%)',
        background: '#FBF2DA', border: '1.2px solid #F2A200', color: '#8a6a10',
        borderRadius: 11, padding: '2px 8px', fontSize: 10.5, pointerEvents: 'none', zIndex: 6, whiteSpace: 'nowrap',
      }}
    >
      ▤ {rows} rows
    </div>
  );
}

export function DerivedBanner({ text = 'Derived canvas — read-only, auto-layout only.' }: { text?: string }) {
  return (
    <div
      data-testid="derived-banner"
      style={{ position: 'absolute', top: 10, left: 12, zIndex: 8, background: '#EDF2F9', border: '1px solid #5B7EA6', color: '#33506e', borderRadius: 8, padding: '6px 12px', fontSize: 12 }}
    >
      🔒 {text}
    </div>
  );
}

/**
 * The composite base chrome for one node. Status/diagnostics are drawn here unless the skin
 * claims them (a claiming skin renders them itself from the same NodeBaseState). Never-claimable
 * chrome is always drawn.
 */
export function NodeBaseChrome({
  state, anchors, claims,
}: { state: NodeBaseState; anchors: AnchorDeclaration; claims?: { status?: true; diagnostics?: true } }) {
  return (
    <>
      {!claims?.status && state.runStatus && <StatusBadge status={state.runStatus} point={anchors.status} />}
      {!claims?.diagnostics && state.diagnostics && <DiagnosticsBadge diagnostics={state.diagnostics} point={anchors.diagnostics} />}
      <ChromeOverlay state={state} chrome={anchors.chrome} />
    </>
  );
}
