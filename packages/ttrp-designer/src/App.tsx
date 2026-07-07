import { useEffect, useMemo, useState } from 'react';
import { LspClient } from './lsp/ws-client.js';
import type { GetGraphResult, GetLayoutResult, NodePos } from './graph/types.js';
import { deriveCanvas } from './graph/derive-orchestration.js';
import { ROOT, current, enter, popTo, type ViewStack } from './state/view-stack.js';
import { DEFAULT_SKIN, skinFor, type SkinId } from './skins/index.js';
import { Canvas } from './components/Canvas.js';
import { Header } from './components/Header.js';
import type { RenderInput } from './cy/adapter.js';

const HERO_URI = import.meta.env?.VITE_TTRP_DOC_URI ?? 'file:///hero.ttrp';

/** Read-only Designer (Stage 5.3): connect, pull graph/world/layout, render the current canvas. */
export function App() {
  const [graph, setGraph] = useState<GetGraphResult | null>(null);
  const [layout, setLayout] = useState<GetLayoutResult | null>(null);
  const [view, setView] = useState<ViewStack>(ROOT);
  const [skinByCanvas, setSkinByCanvas] = useState<Record<string, SkinId>>({});

  useEffect(() => {
    const client = new LspClient();
    let cancelled = false;
    void (async () => {
      try {
        await client.connect();
        await client.initialize();
        // In a real session the host supplies the open document; here we just pull.
        const [g, l] = await Promise.all([client.getGraph(HERO_URI, 1), client.getLayout(HERO_URI)]);
        if (!cancelled) {
          setGraph(g);
          setLayout(l);
        }
      } catch {
        // Loopback server not running (dev). Canvas stays empty; no crash.
      }
    })();
    return () => {
      cancelled = true;
      client.close();
    };
  }, []);

  const canvasKey = current(view);
  const skinId = skinByCanvas[canvasKey] ?? layoutSkin(layout, canvasKey) ?? DEFAULT_SKIN;

  const input: RenderInput | null = useMemo(() => {
    if (!graph) return null;
    return {
      elements: deriveCanvas(graph, canvasKey),
      skin: skinFor(skinId),
      autoLayout: graph.autoLayout[canvasKey] ?? {},
      manual: manualPositions(layout, canvasKey),
    };
  }, [graph, layout, canvasKey, skinId]);

  return (
    <div className="flex h-full flex-col">
      <Header
        breadcrumb={view.stack}
        skin={skinId}
        onNavigate={(d) => setView(popTo(view, d))}
        onSkinChange={(s) => setSkinByCanvas({ ...skinByCanvas, [canvasKey]: s })}
      />
      <div className="min-h-0 flex-1">
        {input ? <Canvas input={input} /> : <div className="p-4 text-slate-500">Connect the Designer server on ws://127.0.0.1:9257/lsp…</div>}
      </div>
      <button
        className="hidden"
        data-testid="enter-crunch"
        onClick={() => setView(enter(view, 'crunch'))}
      />
    </div>
  );
}

function layoutSkin(layout: GetLayoutResult | null, canvasKey: string): SkinId | null {
  const c = layout?.canvases.find((x) => x.key === canvasKey);
  return (c?.skin as SkinId | undefined) ?? null;
}

function manualPositions(layout: GetLayoutResult | null, canvasKey: string): Record<string, NodePos> | undefined {
  const c = layout?.canvases.find((x) => x.key === canvasKey);
  if (!c || c.mode !== 'manual') return undefined;
  return Object.fromEntries(c.nodes.map((n) => [n.zeta, n]));
}
