// The kernel's custom React Flow node. ONE component for every skin: it resolves the skin,
// renders the skin's node body (skin.renderNode), draws the base-layer chrome at the skin's
// declared anchors (NodeBaseChrome), and places Handles from the skin's port geometry —
// port VISIBILITY is base (every port renders, incl. unconnected err/rejects stubs, D-2);
// port shape/placement is skin.

import { Handle, Position, type NodeProps } from '@xyflow/react';
import type { BindingHint, CanvasNode as CanvasNodeT, NodeBaseState, Theme } from '@tatrman/canvas-core';
import { canvas as palette } from '@tatrman/tokens'; // canvas token family (contracts §6)
import type { DesignerSkin } from './skin-component.js';
import { NodeBaseChrome, PreviewChip } from './base/BaseLayer.js';

export interface CNodeData {
  node: CanvasNodeT;
  skin: DesignerSkin;
  state: NodeBaseState;
  theme: Theme;
  /** S-5 show-bindings ghost chip (er canvas only); absent ⇒ nothing drawn. */
  bindingHint?: BindingHint;
  /** S-6 display preview chip (processing display nodes); absent ⇒ nothing drawn. */
  preview?: { rows: number };
  [key: string]: unknown;
}

/** The ghost table/query chip under a bound entity (S-5 show-bindings; base-layer drawn). */
export function BindingHintChip({ hint }: { hint: BindingHint }) {
  const warn = hint.kind === 'unresolved';
  return (
    <div
      data-testid="binding-hint-chip"
      data-binding-kind={hint.kind}
      style={{
        position: 'absolute', left: 0, right: 0, bottom: -20, margin: '0 auto', width: 'fit-content', maxWidth: '100%',
        display: 'flex', alignItems: 'center', gap: 4, padding: '1px 7px', borderRadius: 9,
        fontSize: 10, whiteSpace: 'nowrap', pointerEvents: 'none',
        background: warn ? palette.errBg : palette.nodeFillGhost, color: warn ? palette.err : palette.edgeCnc,
        border: `1px dashed ${warn ? palette.errBorder : palette.nodeStrokeGhost}`, opacity: 0.9,
      }}
    >
      <span aria-hidden>{hint.kind === 'query' ? '⚙' : warn ? '⚠' : '▦'}</span>{hint.target}
    </div>
  );
}

// port placement (skin) + orientation (skin.flow) → RF Handle Position (D-4: data on the
// flow axis, control on the cross axis)
function handlePosition(placement: string, orientation: 'LR' | 'TD'): { type: 'source' | 'target'; position: Position } {
  const lr = orientation === 'LR';
  switch (placement) {
    case 'flow-in': return { type: 'target', position: lr ? Position.Left : Position.Top };
    case 'flow-out': return { type: 'source', position: lr ? Position.Right : Position.Bottom };
    case 'cross-in': return { type: 'target', position: lr ? Position.Top : Position.Left };
    case 'cross-out': return { type: 'source', position: lr ? Position.Bottom : Position.Right };
    default: return { type: 'source', position: Position.Right };
  }
}

const PORT_COLOR: Record<string, string> = { data: palette.ink, control: palette.muted, err: palette.errPort, rejects: palette.errPort };

export function CanvasNodeView({ data, selected }: NodeProps) {
  const { node, skin, state: baseState, theme, bindingHint, preview } = data as CNodeData;
  const size = skin.nodeSize(node);
  const anchors = skin.declareAnchors(size);
  const state: NodeBaseState = { ...baseState, selected: !!selected };
  const RenderNode = skin.renderNode;

  return (
    <div style={{ position: 'relative', width: size.width, height: size.height }} data-node-id={node.id} data-testid="canvas-node">
      {/* ports — always visible (D-2). geometry from the skin; a Handle per port. */}
      {node.ports.map((p) => {
        const geo = skin.portGeometry(p, node);
        const hp = handlePosition(geo.placement, skin.flow.orientation);
        const color = PORT_COLOR[p.role] ?? palette.ink;
        return (
          <Handle
            key={p.id}
            id={p.id}
            type={hp.type}
            position={hp.position}
            data-testid={`port-${p.role}`}
            data-connected={p.connected}
            style={{
              width: (geo.size ?? 9),
              height: (geo.size ?? 9),
              background: theme === 'stage-navy' ? palette.accentDeep : palette.nodeFill,
              border: `1.6px solid ${color}`,
              borderRadius: geo.shape === 'square' ? 0 : geo.shape === 'diamond' ? 2 : '50%',
              opacity: p.role === 'control' ? 0.6 : 1,
            }}
          />
        );
      })}

      {/* the skin's node body — kind mark, label, rows/measures, etc. */}
      <RenderNode node={node} state={state} anchors={anchors} theme={theme} />

      {/* the app-drawn base layer — identical for a given state across skins (unless claimed) */}
      <NodeBaseChrome state={state} anchors={anchors} claims={skin.claims} />

      {/* S-5 show-bindings ghost chip (base-layer decoration; er canvas only) */}
      {bindingHint && <BindingHintChip hint={bindingHint} />}

      {/* S-6 display preview chip (base-layer; processing display nodes with a run result) */}
      {preview && <PreviewChip rows={preview.rows} point={anchors.previewChip} />}
    </div>
  );
}
