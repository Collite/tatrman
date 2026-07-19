// Shell tab + drill state (DS-P2 S1). Pure reducer functions over ShellState — tab = subject
// (A-2 β), preview-tab discipline (S-7: transient preview, promoted on pin/edit), in-tab drill
// breadcrumb (P-2, both faces). No React here; the shell components drive these.

import type { SkinId } from '@tatrman/canvas-core';
import type { ShellState, Subject, SubjectTab, DrillSegment } from './types.js';

export const emptyShell: ShellState = { tabs: [], activeTabId: null };

const tabIdFor = (subject: Subject, perspective?: 'binding' | 'lineage'): string =>
  perspective ? `${subject.ref}#${perspective}` : subject.ref;

function makeTab(subject: Subject, preview: boolean, perspective?: 'binding' | 'lineage'): SubjectTab {
  return { id: tabIdFor(subject, perspective), subject, perspective, drillPath: [], preview };
}

/**
 * Open (or focus) a subject. Existing tab ⇒ focus, never duplicate (and promote out of preview
 * if a non-preview open was requested). New tab: a preview open replaces the current preview
 * tab (only one transient preview at a time); a pinned open is added outright.
 */
export function openSubject(
  state: ShellState,
  subject: Subject,
  opts: { preview?: boolean; perspective?: 'binding' | 'lineage' } = {},
): ShellState {
  const preview = opts.preview ?? true;
  const id = tabIdFor(subject, opts.perspective);
  const existing = state.tabs.find((t) => t.id === id);
  if (existing) {
    const tabs = preview ? state.tabs : state.tabs.map((t) => (t.id === id ? { ...t, preview: false } : t));
    return { tabs, activeTabId: id };
  }
  const newTab = makeTab(subject, preview, opts.perspective);
  // a preview open evicts the previous (unpinned) preview tab
  const kept = preview ? state.tabs.filter((t) => !t.preview) : state.tabs;
  return { tabs: [...kept, newTab], activeTabId: id };
}

export function focusTab(state: ShellState, id: string): ShellState {
  if (!state.tabs.some((t) => t.id === id)) return state;
  return { ...state, activeTabId: id };
}

export function closeTab(state: ShellState, id: string): ShellState {
  const idx = state.tabs.findIndex((t) => t.id === id);
  if (idx < 0) return state;
  const tabs = state.tabs.filter((t) => t.id !== id);
  let activeTabId = state.activeTabId;
  if (activeTabId === id) {
    const next = tabs[idx] ?? tabs[idx - 1] ?? null;
    activeTabId = next?.id ?? null;
  }
  return { tabs, activeTabId };
}

/** pin/edit-gesture promotes a preview tab to a durable one (S-7). */
export function promoteTab(state: ShellState, id: string): ShellState {
  return { ...state, tabs: state.tabs.map((t) => (t.id === id ? { ...t, preview: false } : t)) };
}

/** navigating away from an UNPINNED preview tab drops it (preview discipline). */
export function dropPreviewIfLeaving(state: ShellState, newActiveId: string): ShellState {
  const leaving = state.tabs.find((t) => t.id === state.activeTabId);
  const tabs = leaving && leaving.preview && leaving.id !== newActiveId
    ? state.tabs.filter((t) => t.id !== leaving.id)
    : state.tabs;
  return { tabs, activeTabId: newActiveId };
}

export function setSkin(state: ShellState, id: string, skin: SkinId): ShellState {
  return { ...state, tabs: state.tabs.map((t) => (t.id === id ? { ...t, skin } : t)) };
}

/**
 * In-tab perspective switch (A-2 β): flip the SAME subject tab between its canvas (undefined) and
 * a derived perspective, WITHOUT changing the tab id — so the tab, its breadcrumb, and the
 * underlying canvas state (positions held by subject.ref) survive the round-trip. (Distinct from
 * a perspective pop-out, which openSubject()s a separate `ref#perspective` preview tab.)
 */
export function setPerspective(state: ShellState, id: string, perspective?: 'binding' | 'lineage'): ShellState {
  return updateTab(state, id, (t) => ({ ...t, perspective }));
}

// ---- in-tab drill (P-2 — same gesture family on both faces) ----
export function drillIn(state: ShellState, id: string, segment: DrillSegment): ShellState {
  return updateTab(state, id, (t) => ({ ...t, drillPath: [...t.drillPath, segment] }));
}

export function drillOut(state: ShellState, id: string): ShellState {
  return updateTab(state, id, (t) => ({ ...t, drillPath: t.drillPath.slice(0, -1) }));
}

/** breadcrumb click: pop to a level. index -1 = the subject root (empty drill path). */
export function drillTo(state: ShellState, id: string, index: number): ShellState {
  return updateTab(state, id, (t) => ({ ...t, drillPath: t.drillPath.slice(0, index + 1) }));
}

function updateTab(state: ShellState, id: string, fn: (t: SubjectTab) => SubjectTab): ShellState {
  return { ...state, tabs: state.tabs.map((t) => (t.id === id ? fn(t) : t)) };
}

export const activeTab = (state: ShellState): SubjectTab | null =>
  state.tabs.find((t) => t.id === state.activeTabId) ?? null;
