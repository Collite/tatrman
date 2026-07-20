// SPDX-License-Identifier: Apache-2.0
// The Designer Extensions surface (contracts §10, PL-P1.S8.T4). Composed atop the FO `src/ext/` loader
// per RO-31/VS-5: the §10 interfaces (discovery + panel activation) are the public API; FO §2
// (`gateExtension`) is the load-time license gate. ONE pipeline, not two:
//   discover (bundled OR backend-advertised `GET /v1/designer/extensions`)
//     → FO license gate (gateExtension) → dynamic import(moduleUrl) → activate({dataSource, backend, auth}).
// Invariants (VS-5): worker/loopback load zero (no baseUrl → no advertise); the SV read-api Veles loads
// zero (its server has no advertise endpoint → 404 → []); open builds still refuse `license:'platform'`.

import type { DataSourceCapabilities, ModelDataSource } from '../data/model-data-source.js';
import { gateExtension, type ExtensionManifest, type LicenseClient } from './extension-loader.js';

// ---- §10 interfaces (verbatim from contracts §10) ----

/** Backend the extension runs against. `kind: 'veles'` covers BOTH platform (ttrm) and SV read-api (VS-2). */
export interface BackendInfo {
  kind: 'worker' | 'designer-server' | 'veles';
  baseUrl: string | null;
  capabilities: DataSourceCapabilities;
}

export interface ExtensionContext {
  dataSource: ModelDataSource; // the MD6 adapter in force
  backend: BackendInfo;
  auth: { token(): Promise<string | null> };
}

/** §10 leaves `PanelContext` unspecified; a panel gets the same context the extension activated with. */
export type PanelContext = ExtensionContext;

export interface PanelContribution {
  id: string;
  title: string;
  icon?: string;
  /** Mount into [el]; returns a dispose fn. */
  mount(el: HTMLElement, ctx: PanelContext): () => void;
}

export interface DesignerExtension {
  id: string; // "cz.tatrman.runs"
  version: string;
  /** Panels contributed to the shell; the shell decides placement. */
  contributes: { panels?: PanelContribution[] };
  activate(ctx: ExtensionContext): void | Promise<void>;
}

// ---- discovery + loader ----

/** A backend-advertised extension entry (`GET /v1/designer/extensions`). FO §2 manifest shape + a moduleUrl. */
export interface AdvertisedExtension {
  id: string;
  version: string;
  moduleUrl: string;
  /** required license grants; absent/empty ⇒ `license: "none"` (VS-5). */
  requires?: string[];
}

export interface LoadDesignerExtensionsOptions {
  fetchImpl?: typeof fetch;
  /** Commercial build wires the license client (`GET /v1/license/grants`); absent in the open Viewer. */
  licenseClient?: LicenseClient;
  /** Dynamic ESM import; injectable for tests. Defaults to a runtime `import()`. */
  importModule?: (url: string) => Promise<unknown>;
  /** Bundled (open-build) entries; the backend-advertised entries append. */
  bundled?: AdvertisedExtension[];
}

export interface DesignerExtensionsResult {
  loaded: Array<{ id: string; version: string; extension: DesignerExtension }>;
  refused: Array<{ id: string; reason: string; locked: boolean }>;
}

/** Run the §10 pipeline for [ctx]. Never throws — a failing extension is isolated (logged + refused). */
export async function loadDesignerExtensions(
  ctx: ExtensionContext,
  opts: LoadDesignerExtensionsOptions = {},
): Promise<DesignerExtensionsResult> {
  const result: DesignerExtensionsResult = { loaded: [], refused: [] };
  const entries = await discover(ctx.backend, opts);

  for (const entry of entries) {
    const manifest: ExtensionManifest = {
      name: entry.id,
      version: entry.version,
      license: entry.requires && entry.requires.length > 0 ? 'platform' : 'none',
      requires: entry.requires ?? [],
    };
    const gate = await gateExtension(manifest, { licenseClient: opts.licenseClient });
    if (!gate.allowed) {
      result.refused.push({ id: entry.id, reason: gate.reason, locked: gate.locked });
      continue;
    }
    try {
      const mod = await (opts.importModule ?? defaultImport)(entry.moduleUrl);
      const extension = extensionOf(mod);
      if (!extension) {
        result.refused.push({ id: entry.id, reason: 'no-activate', locked: false });
        continue;
      }
      await extension.activate(ctx);
      result.loaded.push({ id: entry.id, version: entry.version, extension });
    } catch (err) {
      // Isolation: one bad extension never brings down the shell (§10 loading, T2).
      console.warn(`[designer-extensions] extension '${entry.id}' failed to activate:`, err);
      result.refused.push({ id: entry.id, reason: 'activate-failed', locked: false });
    }
  }
  return result;
}

/** Discover entries: bundled + backend-advertised. Only a `veles` backend with an http(s) baseUrl is
 *  asked; a 404/unreachable (worker, loopback, SV read-api) contributes nothing. */
async function discover(
  backend: BackendInfo,
  opts: LoadDesignerExtensionsOptions,
): Promise<AdvertisedExtension[]> {
  const bundled = opts.bundled ?? [];
  if (backend.kind !== 'veles' || !backend.baseUrl) return bundled;
  const fetchImpl = opts.fetchImpl ?? globalThis.fetch.bind(globalThis);
  try {
    const res = await fetchImpl(`${backend.baseUrl.replace(/\/$/, '')}/v1/designer/extensions`, {
      headers: { Accept: 'application/json' },
    });
    if (!res.ok) return bundled; // 404 on the SV read-api Veles → zero advertised (VS-5).
    const advertised = (await res.json()) as AdvertisedExtension[];
    return [...bundled, ...advertised];
  } catch {
    return bundled; // unreachable advertise endpoint → bundled only.
  }
}

function defaultImport(url: string): Promise<unknown> {
  return import(/* @vite-ignore */ url);
}

/** Accept a module that default-exports a DesignerExtension, or is one, or exports `activate` directly. */
function extensionOf(mod: unknown): DesignerExtension | null {
  const m = mod as { default?: unknown } & Partial<DesignerExtension>;
  const candidate = (m.default as Partial<DesignerExtension> | undefined) ?? m;
  if (candidate && typeof candidate.activate === 'function') {
    return {
      id: candidate.id ?? '',
      version: candidate.version ?? '',
      contributes: candidate.contributes ?? {},
      activate: candidate.activate.bind(candidate),
    };
  }
  return null;
}
