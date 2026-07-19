// Kernel edges. D-4 β convention is kernel-owned geometry (data enters on the flow axis,
// control on the cross axis — enforced by the source/target Handle the mapper wires); the
// skin only restyles (stroke/dash/width) via skin.edgeStyle. One custom edge type handles
// both; the role decides base geometry, the skin decides paint.

import { BaseEdge, EdgeLabelRenderer, getBezierPath, type EdgeProps } from '@xyflow/react';
import type { EdgeStyleSpec, Theme } from '@tatrman/canvas-core';

export interface CEdgeData {
  style: EdgeStyleSpec;
  theme: Theme;
  cardinality?: { from?: string; to?: string };
  [key: string]: unknown;
}

// crow's-foot glyph for a cardinality (er.crow); other skins can ignore by not carrying cardinality
function crowFoot(card: string | undefined): string {
  if (!card) return '';
  if (card === '1') return '‖';
  if (card === '0..1') return '○|';
  if (card === '0..*' || card === '*' || card === 'n') return '{';
  if (card === '1..*') return '|{';
  return card;
}

function CardEnd({ x, y, card, testid, stroke }: { x: number; y: number; card?: string; testid: string; stroke: string }) {
  if (!card) return null;
  return (
    <EdgeLabelRenderer>
      <div
        data-testid={testid}
        data-card={card}
        style={{
          position: 'absolute', transform: `translate(-50%,-50%) translate(${x}px,${y}px)`,
          color: stroke, fontSize: 12, fontWeight: 'bold', pointerEvents: 'none',
        }}
      >
        {crowFoot(card)}
      </div>
    </EdgeLabelRenderer>
  );
}

export function CanvasEdgeView(props: EdgeProps) {
  const { style, theme, cardinality } = (props.data ?? {}) as CEdgeData;
  const spec: EdgeStyleSpec = style ?? { stroke: '#16283F', width: 2 };
  const [path, labelX, labelY] = getBezierPath({
    sourceX: props.sourceX, sourceY: props.sourceY, targetX: props.targetX, targetY: props.targetY,
    sourcePosition: props.sourcePosition, targetPosition: props.targetPosition,
  });
  return (
    <>
      <BaseEdge
        id={props.id}
        path={path}
        markerEnd={props.markerEnd}
        style={{ stroke: spec.stroke, strokeWidth: spec.width, strokeDasharray: spec.dash }}
      />
      {cardinality?.from && <CardEnd x={props.sourceX} y={props.sourceY} card={cardinality.from} testid="edge-card-source" stroke={spec.stroke} />}
      {cardinality?.to && <CardEnd x={props.targetX} y={props.targetY} card={cardinality.to} testid="edge-card-target" stroke={spec.stroke} />}
      {props.label && (
        <EdgeLabelRenderer>
          <div
            data-testid="edge-label"
            style={{
              position: 'absolute', transform: `translate(-50%,-50%) translate(${labelX}px,${labelY}px)`,
              background: theme === 'stage-navy' ? '#16283F' : '#fff', color: theme === 'stage-navy' ? '#E9F0F8' : '#4A4B4D',
              border: `0.8px solid ${spec.stroke}`, borderRadius: 8, padding: '1px 6px', fontSize: 10,
            }}
          >
            {String(props.label)}
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}
