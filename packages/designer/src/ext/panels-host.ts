// SPDX-License-Identifier: Apache-2.0
//
// PL-P2.S8 — the open-side host that loads the platform §10 extensions and mounts their contributed
// panels into a tabbed dock. Complements `loadDesignerExtensions` (which loads + activates); this is the
// piece that actually places `contributes.panels` on screen. Panels mount into a raw element and return a
// dispose fn (the §10 contract), so the host is framework-free.

import {
  type ExtensionContext,
  type LoadDesignerExtensionsOptions,
  loadDesignerExtensions,
  type PanelContribution,
} from './designer-extensions.js';

export interface MountedPanels {
  dispose: () => void;
  loaded: string[];
  refused: Array<{ id: string; reason: string; locked: boolean }>;
}

export interface MountPanelsOptions extends LoadDesignerExtensionsOptions {
  /** Fetch used by the default authed blob-import (bearer-gated bundles); injectable for tests. */
  fetchImpl?: typeof fetch;
}

/**
 * Load the advertised extensions and mount their panels into [host] as a tabbed dock. Returns a dispose
 * fn plus the loaded/refused roster. Extensions that fail to load never break the host (§10 isolation).
 */
export async function mountDesignerPanels(
  host: HTMLElement,
  ctx: ExtensionContext,
  opts: MountPanelsOptions = {},
): Promise<MountedPanels> {
  const importModule = opts.importModule ?? authedBlobImport(ctx, opts.fetchImpl);
  const res = await loadDesignerExtensions(ctx, { ...opts, importModule });
  const panels: PanelContribution[] = res.loaded.flatMap((l) => l.extension.contributes.panels ?? []);

  host.textContent = '';
  if (panels.length === 0) {
    return { dispose: () => {}, loaded: res.loaded.map((l) => l.id), refused: res.refused };
  }

  const tabsBar = document.createElement('div');
  tabsBar.className = 'ttr-panels__tabs';
  const body = document.createElement('div');
  body.className = 'ttr-panels__body';

  let activeDispose: (() => void) | null = null;
  const tabButtons: HTMLButtonElement[] = [];

  const show = (index: number): void => {
    activeDispose?.();
    body.textContent = '';
    tabButtons.forEach((b, i) => b.classList.toggle('ttr-panels__tab--active', i === index));
    const panelEl = document.createElement('div');
    panelEl.className = 'ttr-panels__panel';
    panelEl.dataset.panel = panels[index].id;
    body.appendChild(panelEl);
    activeDispose = panels[index].mount(panelEl, ctx);
  };

  panels.forEach((panel, i) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'ttr-panels__tab';
    btn.dataset.panel = panel.id;
    btn.textContent = panel.title;
    btn.addEventListener('click', () => show(i));
    tabButtons.push(btn);
    tabsBar.appendChild(btn);
  });

  host.append(tabsBar, body);
  show(0);

  return {
    dispose: () => {
      activeDispose?.();
      host.textContent = '';
    },
    loaded: res.loaded.map((l) => l.id),
    refused: res.refused,
  };
}

/**
 * Import an extension bundle whose bytes sit behind the bearer-authenticated door: fetch with the token,
 * then `import()` the code via a blob URL (a bare dynamic `import(url)` can't attach an Authorization
 * header). `moduleUrl`s are resolved against the backend origin.
 */
function authedBlobImport(ctx: ExtensionContext, fetchImpl: typeof fetch = fetch): (url: string) => Promise<unknown> {
  return async (url: string) => {
    const abs = ctx.backend.baseUrl ? new URL(url, ctx.backend.baseUrl).href : url;
    const token = await ctx.auth.token();
    const resp = await fetchImpl(abs, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
    if (!resp.ok) throw new Error(`extension bundle ${url}: HTTP ${resp.status}`);
    const code = await resp.text();
    const blobUrl = URL.createObjectURL(new Blob([code], { type: 'text/javascript' }));
    try {
      return await import(/* @vite-ignore */ blobUrl);
    } finally {
      URL.revokeObjectURL(blobUrl);
    }
  };
}
