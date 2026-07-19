// SPDX-License-Identifier: Apache-2.0
// DM-P2.S1 — view-state seam reconciliation (contracts §3, inherited from DM-P1).
// Drives WorkerLayoutStore / WsSidecarStore / VelesNoStore + the factory with in-memory fakes (no
// live LSP/WS). Ported from modeler DS-v1 `view-state-seam.test.ts` (TtrgLayoutBlockStore →
// WorkerLayoutStore) and extended with the sidecar + none impls that DM-P1 refined here.

import { describe, it, expect } from 'vitest';
import type { CanvasViewState } from '@tatrman/canvas-core';
import type { LayoutFile } from '@tatrman/lsp';
import type { TtrmLayoutPayload, TtrmLayoutCanvas } from '../ttrm-types.js';
import {
  WorkerLayoutStore,
  WsSidecarStore,
  VelesNoStore,
  detectOrphanedLayout,
  parseCanvasKey,
  type LayoutIO,
  type PrefsIO,
  type TtrmLayoutIO,
} from '../view-state-store.js';
import { makeViewStateStore } from '../view-state-store-factory.js';

// ---- in-memory fakes -------------------------------------------------------

function makeLayoutIO(seed: Record<string, LayoutFile> = {}): LayoutIO & {
  store: Record<string, LayoutFile>;
} {
  const store: Record<string, LayoutFile> = { ...seed };
  return {
    store,
    async getLayout(key) {
      return store[key] ?? { version: 1, nodes: {}, edges: {} };
    },
    async setLayout(key, layout) {
      store[key] = layout;
    },
  };
}

function makePrefsIO(): PrefsIO & { map: Map<string, unknown> } {
  const map = new Map<string, { skin?: string; mode?: 'auto' | 'manual'; collapsed?: string[] }>();
  return {
    map,
    get(key) {
      return map.get(key);
    },
    set(key, v) {
      map.set(key, { ...map.get(key), ...v });
    },
  };
}

function makeTtrmLayoutIO(seed: Record<string, TtrmLayoutPayload> = {}): TtrmLayoutIO & {
  store: Record<string, TtrmLayoutCanvas[]>;
} {
  const store: Record<string, TtrmLayoutCanvas[]> = {};
  for (const [uri, p] of Object.entries(seed)) store[uri] = p.canvases;
  return {
    store,
    async getLayout(uri) {
      return { exists: uri in store, version: 1, canvases: store[uri] ?? [], orphaned: [], errors: [] };
    },
    async setLayout(uri, canvases) {
      store[uri] = canvases;
    },
  };
}

const KEY = 'file:///proj/model.ttrg#main';

// ---- WorkerLayoutStore (ported) --------------------------------------------

describe('WorkerLayoutStore.read', () => {
  it('maps LayoutFile nodes + prefs into CanvasViewState, and never surfaces viewport', async () => {
    const io = makeLayoutIO({
      [KEY]: {
        version: 1,
        viewport: { zoom: 2, panX: 10, panY: 20, displayMode: 'with-types' },
        nodes: { A: { x: 1, y: 2 }, B: { x: 3, y: 4 } },
        edges: { e1: { bendPoints: [[5, 6]] } },
      },
    });
    const prefs = makePrefsIO();
    prefs.set(KEY, { skin: 'db.uml', mode: 'manual', collapsed: ['C'] });

    const vs = await new WorkerLayoutStore(io, prefs).read(KEY);

    expect(vs.nodes).toEqual({ A: { x: 1, y: 2 }, B: { x: 3, y: 4 } });
    expect(vs.skin).toBe('db.uml');
    expect(vs.mode).toBe('manual');
    expect(vs.collapsed).toEqual(['C']);
    expect('viewport' in vs).toBe(false);
    expect((vs as unknown as Record<string, unknown>).viewport).toBeUndefined();
  });

  it('sets skinStorage:"prefs" (GQ-1) and takes skin from prefs, else defaultSkin, else er.crow', async () => {
    const io = makeLayoutIO({ [KEY]: { version: 1, nodes: {}, edges: {} } });

    const prefsWith = makePrefsIO();
    prefsWith.set(KEY, { skin: 'db.uml' });
    const vsWith = await new WorkerLayoutStore(io, prefsWith, { defaultSkin: 'er.chen' }).read(KEY);
    expect(vsWith.skin).toBe('db.uml');
    expect(vsWith.skinStorage).toBe('prefs');

    const vsDefault = await new WorkerLayoutStore(io, makePrefsIO(), { defaultSkin: 'er.chen' }).read(KEY);
    expect(vsDefault.skin).toBe('er.chen');

    const vsFallback = await new WorkerLayoutStore(io, makePrefsIO()).read(KEY);
    expect(vsFallback.skin).toBe('er.crow');
  });

  it('derives mode from node presence unless prefs pins it', async () => {
    const withNodes = makeLayoutIO({ [KEY]: { version: 1, nodes: { A: { x: 0, y: 0 } }, edges: {} } });
    const empty = makeLayoutIO({ [KEY]: { version: 1, nodes: {}, edges: {} } });

    expect((await new WorkerLayoutStore(withNodes, makePrefsIO()).read(KEY)).mode).toBe('manual');
    expect((await new WorkerLayoutStore(empty, makePrefsIO()).read(KEY)).mode).toBe('auto');
    const pinnedAuto = makePrefsIO();
    pinnedAuto.set(KEY, { mode: 'auto' });
    expect((await new WorkerLayoutStore(withNodes, pinnedAuto).read(KEY)).mode).toBe('auto');
  });
});

describe('WorkerLayoutStore.write', () => {
  it('round-trips positions while preserving viewport/edges, never injecting a viewport', async () => {
    const io = makeLayoutIO({
      [KEY]: {
        version: 1,
        viewport: { zoom: 2, panX: 10, panY: 20, displayMode: 'with-types' },
        nodes: { OLD: { x: 9, y: 9 } },
        edges: { e1: { bendPoints: [[5, 6]] } },
      },
    });
    const store = new WorkerLayoutStore(io, makePrefsIO());
    await store.write(KEY, { skin: 'db.uml', mode: 'manual', nodes: { A: { x: 1, y: 2 }, B: { x: 3, y: 4 } }, collapsed: [] });

    const written = io.store[KEY];
    expect(written.nodes).toEqual({ A: { x: 1, y: 2 }, B: { x: 3, y: 4 } });
    expect(written.viewport).toEqual({ zoom: 2, panX: 10, panY: 20, displayMode: 'with-types' });
    expect(written.edges).toEqual({ e1: { bendPoints: [[5, 6]] } });
  });

  it('sends skin/mode/collapsed to prefs.set', async () => {
    const prefs = makePrefsIO();
    await new WorkerLayoutStore(makeLayoutIO(), prefs).write(KEY, { skin: 'md.star', mode: 'manual', nodes: {}, collapsed: ['g1', 'g2'] });
    expect(prefs.get(KEY)).toEqual({ skin: 'md.star', mode: 'manual', collapsed: ['g1', 'g2'] });
  });

  it('positions survive a skin switch (C1-b-iv)', async () => {
    const store = new WorkerLayoutStore(makeLayoutIO(), makePrefsIO());
    const positions = { A: { x: 11, y: 22 }, B: { x: 33, y: 44 } };
    await store.write(KEY, { skin: 'er.crow', mode: 'manual', nodes: positions, collapsed: [] });
    const afterA = await store.read(KEY);
    await store.write(KEY, { ...afterA, skin: 'db.uml' });
    const afterB = await store.read(KEY);
    expect(afterB.skin).toBe('db.uml');
    expect(afterB.nodes).toEqual(positions);
  });
});

// ---- WsSidecarStore (.ttrl) ------------------------------------------------

describe('WsSidecarStore', () => {
  it('parses <uri>#<canvas> keys; defaults canvas to "main"', () => {
    expect(parseCanvasKey('file:///m.ttrg#erA')).toEqual({ uri: 'file:///m.ttrg', canvas: 'erA' });
    expect(parseCanvasKey('file:///m.ttrg')).toEqual({ uri: 'file:///m.ttrg', canvas: 'main' });
  });

  it('reads the addressed canvas from the sidecar payload (skin carried, not prefs)', async () => {
    const io = makeTtrmLayoutIO({
      'file:///proj/model.ttrg': {
        exists: true,
        version: 1,
        orphaned: [],
        errors: [],
        canvases: [
          { key: 'main', skin: 'db.uml', mode: 'manual', nodes: [{ qname: 'A', x: 1, y: 2 }], collapsed: ['g1'] },
          { key: 'other', skin: 'er.crow', mode: 'auto', nodes: [], collapsed: [] },
        ],
      },
    });
    const vs = await new WsSidecarStore(io).read(KEY);
    expect(vs.skin).toBe('db.uml');
    expect(vs.mode).toBe('manual');
    expect(vs.nodes).toEqual({ A: { x: 1, y: 2 } });
    expect(vs.collapsed).toEqual(['g1']);
    expect(vs.skinStorage).toBe('ttrg');
  });

  it('returns an auto view-state when the canvas is absent from the sidecar', async () => {
    const vs = await new WsSidecarStore(makeTtrmLayoutIO()).read(KEY);
    expect(vs.mode).toBe('auto');
    expect(vs.nodes).toEqual({});
    expect(vs.skin).toBe('er.crow');
  });

  it('round-trips positions and preserves sibling canvases (read-modify-write)', async () => {
    const io = makeTtrmLayoutIO({
      'file:///proj/model.ttrg': {
        exists: true,
        version: 1,
        orphaned: [],
        errors: [],
        canvases: [{ key: 'other', skin: 'er.crow', mode: 'auto', nodes: [], collapsed: [] }],
      },
    });
    const store = new WsSidecarStore(io);
    await store.write(KEY, { skin: 'md.star', mode: 'manual', nodes: { A: { x: 5, y: 6 } }, collapsed: [] });

    const canvases = io.store['file:///proj/model.ttrg'];
    expect(canvases.find((c) => c.key === 'other')).toBeDefined(); // sibling preserved
    const main = canvases.find((c) => c.key === 'main')!;
    expect(main.skin).toBe('md.star');
    expect(main.nodes).toEqual([{ qname: 'A', x: 5, y: 6 }]);
    expect((await store.read(KEY)).nodes).toEqual({ A: { x: 5, y: 6 } });
  });
});

// ---- VelesNoStore (none) ---------------------------------------------------

describe('VelesNoStore', () => {
  it('reads an auto view-state and no-ops on write (DM-CAP-003)', async () => {
    const store = new VelesNoStore();
    const before = await store.read(KEY);
    expect(before.mode).toBe('auto');
    expect(before.nodes).toEqual({});
    // write is a no-op: reading afterwards is still the auto default.
    await store.write(KEY, { skin: 'db.uml', mode: 'manual', nodes: { A: { x: 1, y: 1 } }, collapsed: [] });
    expect(await store.read(KEY)).toEqual(before);
  });
});

// ---- factory ---------------------------------------------------------------

describe('makeViewStateStore', () => {
  it('selects the store from layoutPersist', () => {
    expect(makeViewStateStore('in-file', { kind: 'in-file', layout: makeLayoutIO(), prefs: makePrefsIO() })).toBeInstanceOf(WorkerLayoutStore);
    expect(makeViewStateStore('sidecar', { kind: 'sidecar', layout: makeTtrmLayoutIO() })).toBeInstanceOf(WsSidecarStore);
    expect(makeViewStateStore('none', { kind: 'none' })).toBeInstanceOf(VelesNoStore);
  });

  it('throws when the IO kind does not match the persist mechanism (wiring bug, not silent degrade)', () => {
    expect(() => makeViewStateStore('sidecar', { kind: 'none' })).toThrow(/does not match/);
  });
});

// ---- orphan detection (DS-CANV-001) ----------------------------------------

describe('detectOrphanedLayout', () => {
  it('flags exactly the view-state node keys absent from the graph', () => {
    const vs: CanvasViewState = {
      skin: 'er.crow',
      mode: 'manual',
      nodes: { A: { x: 0, y: 0 }, B: { x: 1, y: 1 }, GHOST: { x: 2, y: 2 } },
      collapsed: [],
    };
    expect(detectOrphanedLayout(vs, { nodes: [{ id: 'A' }, { id: 'B' }, { id: 'C' }] })).toEqual(['GHOST']);
  });

  it('returns [] when every view-state node exists in the graph', () => {
    const vs: CanvasViewState = { skin: 'er.crow', mode: 'manual', nodes: { A: { x: 0, y: 0 } }, collapsed: [] };
    expect(detectOrphanedLayout(vs, { nodes: [{ id: 'A' }] })).toEqual([]);
  });
});
