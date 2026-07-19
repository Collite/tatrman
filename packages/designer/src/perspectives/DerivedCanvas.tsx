// The derived-canvas host (contracts §4, C-1). Every perspective renders through here:
//  · it ALWAYS shows the DerivedBanner (DS-CANV-002 — derived ⇒ read-only + auto-layout);
//  · a `custom` result routes to the purpose-built view registry (binding-ribbon / lineage-layers,
//    C-1 γ — NOT the canvas engine);
//  · it NEVER persists view-state (C-1). A ViewStateStore may be handed in for symmetry with the
//    live canvas, but the derived path must never call it — the regression guard is a spy test.

import type { PerspectiveResult, BindingRibbon, LineageGraph, LineageScope, LineageDirection } from '@tatrman/perspectives';
import type { ViewStateStore } from '@tatrman/canvas-core';
import { DerivedBanner } from '../canvas/base/BaseLayer.js';
import { BindingRibbonView } from './BindingRibbonView.js';
import { LineageLayersView, type LineageViewHandlers } from './LineageLayersView.js';

export interface DerivedCanvasHandlers {
  /** binding: select an entity → parent regenerates with the expansion. */
  onSelectEntity?: (qname: string | null) => void;
  /** lineage: current query state (controlled) + scope/direction/open-object handlers. */
  lineage?: { scope?: LineageScope; direction?: LineageDirection; handlers?: LineageViewHandlers };
}

export interface DerivedCanvasProps {
  result: PerspectiveResult;
  bannerText?: string;
  handlers?: DerivedCanvasHandlers;
  /** C-1 guard ONLY: present for shape-symmetry with the live canvas; never written here. */
  viewStateStore?: ViewStateStore;
}

export function DerivedCanvas({ result, bannerText, handlers }: DerivedCanvasProps) {
  return (
    <div data-testid="derived-canvas" style={{ position: 'relative', flex: 1, minHeight: 0, overflow: 'auto', background: '#F7FAFD' }}>
      <DerivedBanner {...(bannerText ? { text: bannerText } : {})} />
      <div style={{ paddingTop: 44 }}>
        {result.kind === 'canvas' ? (
          <div data-testid="derived-canvas-graph" style={{ padding: 24, color: '#6B7A8D' }}>
            Derived canvas graph ({result.graph.nodes.length} nodes) — kernel render.
          </div>
        ) : result.view === 'binding-ribbon' ? (
          <BindingRibbonView ribbon={result.data as BindingRibbon} onSelectEntity={handlers?.onSelectEntity} />
        ) : (
          <LineageLayersView
            graph={result.data as LineageGraph}
            scope={handlers?.lineage?.scope}
            direction={handlers?.lineage?.direction}
            handlers={handlers?.lineage?.handlers}
          />
        )}
      </div>
    </div>
  );
}
