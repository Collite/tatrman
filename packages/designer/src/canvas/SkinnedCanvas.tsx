// The modeling canvas render module (DS-P1.S2) — replaces the Cytoscape Canvas. Maps a
// ModelGraph → CanvasGraph, resolves the skin (per kind, pickable), lays out (loaded
// positions or deterministic auto), and renders the kernel with the base layer. Wires the
// modeling affordances onto React Flow: node select, remove (context menu → lsp), drag →
// layout persist. NO cytoscape import (the DS-P0 verdict engine is RF).
//
// DM-P2.S2 seam rewire: the canvas no longer talks to `LspClient.setLayout` directly. It EMITS a
// view change (`onPersistView`) and the shell (DM-P2.S3) persists it through the ViewStateStore
// selected by `capabilities.layoutPersist` (DM-P2.S1) — Worker in-file / WS `.ttrl` / Veles none.
// This is what makes the one canvas run against all three backends.

import { useEffect, useMemo, useRef, useState } from 'react';
import type { DisplayMode, ModelGraph } from '@tatrman/lsp';
import {
  modelGraphToCanvas, layoutAuto, type BindingHint, type CanvasGraph, type Positions, type SkinId, type SchemaCode,
} from '@tatrman/canvas-core';
import { createSkinRegistry } from '../skins/index.js';
import type { DesignerSkin } from './skin-component.js';
import { CanvasKernel } from './Kernel.js';

/** A view change the canvas emits for the shell to persist through its ViewStateStore (DM-P2.S1/S3). */
export interface CanvasViewChange {
  positions: Positions;
  displayMode: DisplayMode;
  mode: 'auto' | 'manual';
}

export interface SkinnedCanvasProps {
  graph: ModelGraph | null;
  displayMode: DisplayMode;
  nodePositions: Record<string, { x: number; y: number }>;
  /** the ViewStateStore key for this canvas (the graph uri); null ⇒ persistence is skipped. */
  canvasKey: string | null;
  selectedQname?: string | null;
  /** skin id from view-state (may be unknown ⇒ DS-SKIN-002 fallback + truth-chip note) */
  initialSkin?: SkinId;
  onNodeSelect: (qname: string | null) => void;
  onRemoveNode: (qname: string) => void;
  /** emit a view change to persist (positions/displayMode/mode); the shell writes it to the store. */
  onPersistView?: (change: CanvasViewChange) => void;
  onDrillIn?: (id: string, label: string) => void;
  /** S-5 show-bindings decoration (er canvas only): ghost table/query chips under bound entities.
   *  Controlled by the parent (session-local, never persisted); the toggle registers in ⌘K there. */
  bindingHints?: Record<string, BindingHint>;
  showBindings?: boolean;
  onToggleShowBindings?: () => void;
}

/** displayMode projection: 'just-names' shows headers only; otherwise rows render. */
function project(cg: CanvasGraph, mode: DisplayMode): CanvasGraph {
  if (mode !== 'just-names') return cg;
  return { ...cg, nodes: cg.nodes.map((n) => ({ ...n, slotData: { ...n.slotData, rows: [] } })) };
}

export function SkinnedCanvas({
  graph, displayMode, nodePositions, canvasKey, selectedQname, initialSkin, onNodeSelect, onRemoveNode, onPersistView, onDrillIn,
  bindingHints, showBindings, onToggleShowBindings,
}: SkinnedCanvasProps) {
  const registry = useMemo(() => createSkinRegistry(), []);
  const kind: SchemaCode = (graph?.schemaCode as SchemaCode) ?? 'er';

  // S-5: the show-bindings decoration only applies to the er canvas.
  const bindingsToggleable = kind === 'er' && (!!bindingHints || !!onToggleShowBindings);
  const canvasGraph = useMemo(
    () => {
      if (!graph) return null;
      const cg = project(modelGraphToCanvas(graph), displayMode);
      return kind === 'er' && showBindings && bindingHints ? { ...cg, bindingHints } : cg;
    },
    [graph, displayMode, kind, showBindings, bindingHints],
  );

  // skin: default per kind, changeable via the picker. An id NOT in this kind's roster (unknown,
  // OR a known id of the wrong kind/face) ⇒ default + DS-SKIN-002 note. `resolve` is id-only and
  // kind-blind, so roster membership — not global resolution — is the correct fallback test.
  const roster = registry.roster('modeling', kind);
  const [skinChoice, setSkinChoice] = useState<Record<string, SkinId>>({});
  const requested = skinChoice[kind] ?? initialSkin;
  const fellBack = !!requested && !roster.some((s) => s.id === requested);
  const skinId: SkinId = fellBack || !requested ? registry.defaultSkin('modeling', kind) : requested;
  const skin = registry.resolve(skinId) as DesignerSkin;

  // positions: loaded (from .ttrg layout, skin-independent) else deterministic auto-layout.
  const [positions, setPositions] = useState<Positions | null>(null);
  const [mode, setMode] = useState<'auto' | 'manual'>('auto');
  const modeRef = useRef<'auto' | 'manual'>('auto'); // mirrors `mode` for reads inside the layout
  const workingPositions = useRef<Positions>({});     // effect (which excludes `mode` from deps)

  useEffect(() => {
    if (!canvasGraph || !skin) { setPositions(null); return; }
    const loaded = Object.keys(nodePositions).length > 0
      && canvasGraph.nodes.every((n) => nodePositions[n.id] || nodePositions[n.qname]);
    if (loaded) {
      const p: Positions = {};
      for (const n of canvasGraph.nodes) p[n.id] = nodePositions[n.id] ?? nodePositions[n.qname];
      workingPositions.current = p;
      setPositions(p);
      setMode('manual'); modeRef.current = 'manual';
      return;
    }
    // Positions survive a skin switch (C1-b-iv): if the user has manually placed a full working
    // set (drag, mode=manual), keep it rather than re-running auto-layout for the new skin. Only
    // auto-layout when we don't already own every node's position (fresh graph / added nodes).
    const haveFullWorking = canvasGraph.nodes.length > 0
      && canvasGraph.nodes.every((n) => workingPositions.current[n.id]);
    if (modeRef.current === 'manual' && haveFullWorking) {
      setPositions({ ...workingPositions.current });
      return;
    }
    let alive = true;
    setMode('auto'); modeRef.current = 'auto';
    layoutAuto(canvasGraph, { orientation: skin.flow.orientation, sizeOf: (n) => skin.nodeSize(n) })
      .then((p) => { if (alive) { workingPositions.current = p; setPositions(p); } });
    return () => { alive = false; };
    // re-layout when the graph or skin (orientation/size) changes; NOT on selection
  }, [canvasGraph, skinId]);

  // drag → update working positions + emit a persist-view (debounced). The shell writes it through
  // the ViewStateStore selected by the active backend's capabilities.layoutPersist (DM-P2.S1/S3).
  const persistTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onNodeDrag = (id: string, pos: { x: number; y: number }) => {
    workingPositions.current = { ...workingPositions.current, [id]: pos };
    setPositions({ ...workingPositions.current });
    setMode('manual'); modeRef.current = 'manual';
    if (!canvasKey) return;
    if (persistTimer.current) clearTimeout(persistTimer.current);
    persistTimer.current = setTimeout(() => {
      onPersistView?.({ positions: { ...workingPositions.current }, displayMode, mode: 'manual' });
    }, 400);
  };

  // displayMode change → emit a persist-view (positions + the new mode), matching the v1.1 modeler
  // behavior. Skips the initial load (no spurious persist on open).
  const prevMode = useRef<DisplayMode | null>(null);
  useEffect(() => {
    if (!canvasKey) { prevMode.current = displayMode; return; }
    if (prevMode.current === null) { prevMode.current = displayMode; return; } // first render after load
    if (prevMode.current === displayMode) return;
    prevMode.current = displayMode;
    onPersistView?.({ positions: { ...workingPositions.current }, displayMode, mode: modeRef.current });
  }, [displayMode, canvasKey]);

  // context-menu remove
  const [menu, setMenu] = useState<{ qname: string; x: number; y: number } | null>(null);

  if (!canvasGraph || !positions || !skin) {
    return <div data-testid="skinned-canvas-empty" style={{ padding: 20, color: '#96989B' }}>laying out…</div>;
  }

  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column' }} data-testid="skinned-canvas" onClick={() => setMenu(null)}>
      {/* picker toolbar + truth chip (E-4) */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '6px 12px', background: '#fff', borderBottom: '1px solid #CBD8E6', flex: '0 0 auto', fontSize: 12.5 }}>
        <span style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '.06em', color: '#96989B' }}>Notation</span>
        <select
          data-testid="skin-picker"
          value={skinId}
          onChange={(e) => setSkinChoice((c) => ({ ...c, [kind]: e.target.value }))}
          style={{ font: 'inherit', fontSize: 12.5, padding: '3px 8px', border: '1px solid #CBD8E6', borderRadius: 6 }}
        >
          {roster.map((s) => (
            <option key={s.id} value={s.id}>{s.displayName}</option>
          ))}
        </select>
        {bindingsToggleable && (
          <label data-testid="show-bindings-toggle" style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12, color: '#33506e', cursor: 'pointer', userSelect: 'none' }} title="Ghost the bound db table under each entity (session-local)">
            <input
              type="checkbox"
              data-testid="show-bindings-checkbox"
              checked={!!showBindings}
              onChange={() => onToggleShowBindings?.()}
            />
            show bindings
          </label>
        )}
        <span data-testid="truth-chip" style={{ marginLeft: 'auto', fontSize: 11.5, color: '#96989B', border: '1px dashed #CBD8E6', padding: '3px 10px', borderRadius: 10 }}>
          canvas={kind} · skin={skinId} · mode={mode}
          {fellBack && <span data-testid="skin-fallback" style={{ color: '#8a6a10' }}> · substituted ({requested} not available here)</span>}
          <span style={{ color: '#96989B' }}> · skin: prefs (GQ-1)</span>
        </span>
      </div>

      <div style={{ flex: 1, minHeight: 0, position: 'relative' }}>
        <CanvasKernel
          graph={canvasGraph}
          registry={registry}
          skinId={skinId}
          positions={positions}
          selectedId={selectedQname ?? null}
          onSelect={onNodeSelect}
          onNodeDrag={onNodeDrag}
          onNodeContextMenu={(id, x, y) => setMenu({ qname: id, x, y })}
          onDrillIn={onDrillIn}
        />
        {menu && (
          <div
            data-testid="node-context-menu"
            style={{ position: 'fixed', left: menu.x, top: menu.y, background: '#fff', border: '1px solid #CBD8E6', borderRadius: 6, boxShadow: '0 4px 12px rgba(0,0,0,.15)', zIndex: 20, fontSize: 13, overflow: 'hidden' }}
          >
            <button
              data-testid="remove-node"
              onClick={() => { onRemoveNode(menu.qname); setMenu(null); }}
              style={{ display: 'block', width: '100%', textAlign: 'left', padding: '7px 16px', border: 'none', background: '#fff', cursor: 'pointer', font: 'inherit', color: '#B3261E' }}
            >
              Remove from graph
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
