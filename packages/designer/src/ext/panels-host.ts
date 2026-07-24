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

  if (panels.length === 0) {
    return { dispose: () => {}, loaded: res.loaded.map((l) => l.id), refused: res.refused };
  }

  // Each mount owns its OWN container child (not the whole host). A stale mount's dispose then removes only
  // its container, so a late-resolving cancelled mount can't blank a newer live dock (StrictMode re-mount race).
  const container = document.createElement('div');
  container.className = 'ttr-panels__root';
  host.appendChild(container);

  const tabsBar = document.createElement('div');
  tabsBar.className = 'ttr-panels__tabs';
  const body = document.createElement('div');
  body.className = 'ttr-panels__body';

  let activeDispose: (() => void) | null = null;
  const tabButtons: HTMLButtonElement[] = [];

  const show = (index: number): void => {
    activeDispose?.();
    activeDispose = null;
    body.textContent = '';
    tabButtons.forEach((b, i) => b.classList.toggle('ttr-panels__tab--active', i === index));
    const panelEl = document.createElement('div');
    panelEl.className = 'ttr-panels__panel';
    panelEl.dataset.panel = panels[index].id;
    body.appendChild(panelEl);
    try {
      activeDispose = panels[index].mount(panelEl, ctx);
    } catch (err) {
      // R2-10: a panel whose mount() throws must NOT break the dock or reject the host — degrade to an
      // inline error and keep the other tabs alive (§10 isolation now covers the mount phase, not just load).
      panelEl.textContent = `panel "${panels[index].id}" failed to render`;
      console.error(`designer panel ${panels[index].id} mount failed`, err);
    }
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

  container.append(tabsBar, body);
  show(0);

  return {
    dispose: () => {
      activeDispose?.();
      container.remove();
    },
    loaded: res.loaded.map((l) => l.id),
    refused: res.refused,
  };
}

/**
 * Resolve an extension `moduleUrl` against the backend origin, REFUSING a cross-origin target (R2-2). The
 * loader sends the bearer to — and then executes code from — this URL, so it must be the SAME origin as the
 * backend; an absolute `moduleUrl` pointing elsewhere would otherwise exfiltrate the token and run
 * third-party code in the Studio origin. With no backend origin, only a relative (schemeless) URL is allowed.
 */
export function resolveExtensionUrl(url: string, baseUrl: string | null | undefined): string {
  if (!baseUrl) {
    if (/^[a-z][a-z0-9+.-]*:/i.test(url)) {
      throw new Error(`extension bundle ${url}: absolute module refused with no backend origin`);
    }
    return url;
  }
  const backendOrigin = new URL(baseUrl).origin;
  const abs = new URL(url, baseUrl);
  if (abs.origin !== backendOrigin) {
    throw new Error(`extension bundle ${url}: cross-origin module refused (expected ${backendOrigin})`);
  }
  return abs.href;
}

/**
 * Import an extension bundle whose bytes sit behind the bearer-authenticated door: fetch with the token,
 * then `import()` the code via a blob URL (a bare dynamic `import(url)` can't attach an Authorization
 * header). The `moduleUrl` is resolved + same-origin-checked against the backend origin (R2-2).
 */
function authedBlobImport(ctx: ExtensionContext, fetchImpl: typeof fetch = fetch): (url: string) => Promise<unknown> {
  return async (url: string) => {
    const abs = resolveExtensionUrl(url, ctx.backend.baseUrl);
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
