// Shared node body for the two processing skins (stage / script). Both render the same kernel
// model (containers as REGIONS — header + engine/dialect badge + ghost hint + ⌕ drill + `"""sql
// derived marking; store/display/op leaves), and both render the D-2 port strip (every declared
// port, incl. the unconnected rejects stub). They differ in chrome: Stage = light icon-cards on
// the ice grid; Script = text-forward dark pills showing description-or-code. Geometry/axes come
// from the SkinDefinition; this is body only.

import { createContext, useContext } from 'react';
import type { CanvasNode, NodeRenderProps, NodeSize } from '@tatrman/canvas-core';
import { TOKENS } from '@tatrman/canvas-core';
import { canvas as palette } from '@tatrman/tokens'; // canvas token family (contracts §6)

export type ProcVariant = 'stage' | 'script';

/** Drill trigger for the region ⌕ button (contracts §3: ⌕ AND double-click are co-equal drill
 *  triggers). The kernel's node render prop carries no callback, so ProcessingCanvas provides the
 *  (already region-guarded) handler through context; absent ⇒ ⌕ falls back to double-click only. */
export const ProcessingDrillContext = createContext<((id: string, label: string) => void) | null>(null);

interface PortMeta { id: string; direction: 'in' | 'out'; role: string; connected: boolean; label?: string }

const portsOf = (node: CanvasNode): PortMeta[] => (node.slotData.ports as PortMeta[] | undefined) ?? [];
const isRegion = (node: CanvasNode) => node.kind === 'container-ref';

const KIND_GLYPH: Record<string, string> = {
  'container-ref': '▣', store: '⛁', display: '▤', op: '▶',
};
const ROLE_GLYPH: Record<string, string> = { data: '•', control: '⋯', err: '⚠', rejects: '⚠' };

/** Script pill text: description if present, else the code fragment, else the label (T4 fallback). */
export function bodyDisplayText(node: CanvasNode): string {
  return node.bodyText ?? (node.slotData.code as string | undefined) ?? node.label;
}

/** deterministic size — header + derived marking + (script) body lines + port strip + ghost hint. */
export function procNodeSize(node: CanvasNode, variant: ProcVariant): NodeSize {
  const width = variant === 'script' ? 250 : 210;
  const derived = node.slotData.fragmentDerived ? 18 : 0;
  const bodyLines = variant === 'script' ? Math.min(4, bodyDisplayText(node).split('\n').length) : 0;
  const body = bodyLines * 15;
  const strip = portsOf(node).length > 0 ? 26 : 0;
  const ghost = isRegion(node) ? 16 : 0;
  return { width, height: 34 + derived + body + strip + ghost + 10 };
}

function PortStrip({ node, variant }: { node: CanvasNode; variant: ProcVariant }) {
  const ports = portsOf(node);
  if (ports.length === 0) return null;
  const muted = variant === 'script' ? palette.scriptMuted : TOKENS.gray.muted;
  return (
    <div data-testid="port-strip" style={{ display: 'flex', flexWrap: 'wrap', gap: 4, padding: '3px 8px' }}>
      {ports.map((p) => {
        const reject = p.role === 'rejects' || p.role === 'err';
        return (
          <span
            key={p.id}
            data-testid="port-chip"
            data-role={p.role}
            data-connected={p.connected}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: 9.5, lineHeight: 1.2,
              padding: '0 5px', borderRadius: 7, whiteSpace: 'nowrap',
              color: reject ? TOKENS.status.error : muted,
              border: `1px ${p.connected ? 'solid' : 'dashed'} ${reject ? palette.errBorder : (variant === 'script' ? palette.slate : TOKENS.gray.line)}`,
              opacity: p.connected ? 1 : 0.85,
            }}
          >
            <span aria-hidden>{ROLE_GLYPH[p.role] ?? '•'}</span>{p.label ?? p.id.split(/[.:]/).pop()}
          </span>
        );
      })}
    </div>
  );
}

export function ProcessingNodeBody({ node, variant }: { node: NodeRenderProps['node']; variant: ProcVariant }) {
  const region = isRegion(node);
  const drill = useContext(ProcessingDrillContext);
  const engine = node.slotData.engine as string | undefined;
  const dialect = node.slotData.dialect as string | undefined;
  const fragmentDerived = !!node.slotData.fragmentDerived;
  const dark = variant === 'script';

  const bg = dark ? TOKENS.stageNavy : TOKENS.card;
  const fg = dark ? palette.inkInverse : TOKENS.stageNavy;
  const border = dark ? palette.slate : palette.nodeStroke;
  const headerBg = region ? (dark ? palette.regionDark : palette.bg) : 'transparent';

  return (
    <div
      data-testid="processing-node"
      data-kind={node.kind}
      data-region={region || undefined}
      style={{
        width: '100%', height: '100%', background: bg, color: fg,
        border: `1.4px ${region ? 'solid' : dark ? 'solid' : 'solid'} ${border}`,
        borderRadius: region ? 10 : 8, overflow: 'hidden', fontSize: 12,
        boxShadow: region ? 'inset 0 0 0 1px rgba(150,152,155,.25)' : 'none',
      }}
    >
      <div
        style={{
          display: 'flex', alignItems: 'center', gap: 6, padding: '5px 9px',
          background: region ? headerBg : 'transparent',
          borderBottom: region ? `1px solid ${border}` : 'none', fontWeight: 600,
        }}
      >
        <span data-testid="kind-glyph" aria-hidden>{KIND_GLYPH[node.kind] ?? '▶'}</span>
        <span data-testid="node-label" style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{node.label}</span>
        {engine && (
          <span data-testid="engine-badge" style={{ fontSize: 9.5, padding: '1px 6px', borderRadius: 8, background: dark ? palette.badgeBgDark : palette.badgeBg, color: dark ? palette.scriptMuted : palette.slate, whiteSpace: 'nowrap' }}>
            {engine}{dialect ? ` · ${dialect}` : ''}
          </span>
        )}
        {region && (
          <button
            data-testid="region-drill"
            aria-label={`Open ${node.label}`}
            className="nodrag"
            onClick={(e) => { e.stopPropagation(); drill?.(node.id, node.label); }}
            style={{ border: 'none', background: 'transparent', cursor: 'pointer', color: 'inherit', fontSize: 13, padding: 0 }}
            title="Open (drill in)"
          >
            ⌕
          </button>
        )}
      </div>

      {fragmentDerived && (
        <div data-testid="derived-marking" style={{ fontSize: 9.5, padding: '2px 9px', color: dark ? palette.warnInkDark : TOKENS.status.warnFg, fontStyle: 'italic' }}>
          {'"""sql · derived view'}
        </div>
      )}

      {dark && (
        <pre
          data-testid="body-text"
          style={{ margin: 0, padding: '3px 9px', fontSize: 10.5, lineHeight: 1.35, color: palette.codeText, whiteSpace: 'pre-wrap', overflow: 'hidden', maxHeight: 62, fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}
        >
          {bodyDisplayText(node)}
        </pre>
      )}

      {region && (
        <div data-testid="ghost-content" aria-hidden style={{ fontSize: 9, padding: '1px 9px', color: TOKENS.gray.muted, opacity: 0.7, letterSpacing: '.5px' }}>
          ▪ ▪ ▪ contents
        </div>
      )}

      <PortStrip node={node} variant={variant} />
    </div>
  );
}
