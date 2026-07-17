// SPDX-License-Identifier: Apache-2.0
// Veles data source (SV-P4·S2·T5): the READ-ONLY catalog view over the Veles JSON
// read API. Veles serves the model as browsable JSON at /model/{index,graph,object,
// search} (services/veles read routes), so this is a thin `fetch` client returning
// the `ttrm/*` read DTOs verbatim — no transport-specific mapping.
//
// Read-only by design (`capabilities.edit = false`): Veles is a served catalog, not a
// modeling repo, so there is no setLayout/mutation surface (contrast
// WsDesignerServerDataSource). Two gaps recorded in the T5 decisions doc:
//   - `.ttrl` layout (gap ③): Veles serves no sidecars, so `getLayout` is intentionally
//     NOT implemented (it is optional on ModelDataSource) — the viewer auto-lays-out.
//   - change notification (gap ⑤): Veles has no server push, so `onModelChanged` POLLS
//     the existing `GET /status` model_version and fires when it changes.

import type {
  ModelDataSource,
  ModelIndex,
  ModelGraphPayload,
  ObjectDetail,
  SearchHit,
  SearchParams,
  GraphScope,
  Disposable,
} from './model-data-source.js';
import type { TtrmSearchHit } from './ttrm-types.js';

export interface VelesDataSourceOptions {
  /** Injected for tests; defaults to global `fetch`. */
  fetchImpl?: typeof fetch;
  /** `onModelChanged` poll interval (ms). Default 15000. */
  pollIntervalMs?: number;
  /** Injected timer for tests; defaults to window.setInterval/clearInterval. */
  setIntervalImpl?: (cb: () => void, ms: number) => ReturnType<typeof setInterval>;
  clearIntervalImpl?: (h: ReturnType<typeof setInterval>) => void;
}

export class VelesReadError extends Error {
  constructor(
    readonly status: number,
    readonly path: string,
    message: string,
  ) {
    super(message);
    this.name = 'VelesReadError';
  }
}

export class VelesDataSource implements ModelDataSource {
  readonly capabilities = { edit: false } as const;
  private readonly base: string;
  private readonly fetchImpl: typeof fetch;
  private readonly pollIntervalMs: number;
  private readonly setIntervalImpl: (cb: () => void, ms: number) => ReturnType<typeof setInterval>;
  private readonly clearIntervalImpl: (h: ReturnType<typeof setInterval>) => void;

  /** @param base a same-origin path prefix (e.g. `/veles`, or `` for the origin root)
   *  or a full http(s) origin (e.g. `http://localhost:7260`). Endpoint paths are
   *  concatenated onto it; the browser resolves a relative base against its origin. */
  constructor(base: string, opts: VelesDataSourceOptions = {}) {
    this.base = base;
    this.fetchImpl = opts.fetchImpl ?? globalThis.fetch.bind(globalThis);
    this.pollIntervalMs = opts.pollIntervalMs ?? 15000;
    this.setIntervalImpl =
      opts.setIntervalImpl ?? ((cb, ms) => setInterval(cb, ms));
    this.clearIntervalImpl = opts.clearIntervalImpl ?? ((h) => clearInterval(h));
  }

  private async getJson<T>(path: string): Promise<T> {
    let res: Response;
    try {
      res = await this.fetchImpl(this.base + path, {
        headers: { Accept: 'application/json' },
        credentials: 'include',
      });
    } catch (err) {
      throw new VelesReadError(0, path, `Cannot reach Veles at ${this.base}${path}: ${String(err)}`);
    }
    if (!res.ok) {
      let detail = '';
      try {
        const body = (await res.json()) as { error?: string };
        if (body?.error) detail = `: ${body.error}`;
      } catch {
        /* non-JSON error body */
      }
      throw new VelesReadError(res.status, path, `Veles ${path} → ${res.status}${detail}`);
    }
    return (await res.json()) as T;
  }

  getModelIndex(): Promise<ModelIndex> {
    return this.getJson<ModelIndex>('/model/index');
  }

  getModelGraph(scope?: GraphScope): Promise<ModelGraphPayload> {
    const q = new URLSearchParams();
    if (scope?.schema) q.set('schema', scope.schema);
    if (scope?.package) q.set('package', scope.package);
    const qs = q.toString();
    return this.getJson<ModelGraphPayload>(`/model/graph${qs ? `?${qs}` : ''}`);
  }

  getObject(qname: string): Promise<ObjectDetail> {
    return this.getJson<ObjectDetail>(`/model/object?qname=${encodeURIComponent(qname)}`);
  }

  async search(q: SearchParams): Promise<SearchHit[]> {
    const query = q.query.trim();
    if (!query) return [];
    const params = new URLSearchParams({ query });
    if (q.limit) params.set('limit', String(q.limit));
    const res = await this.getJson<{ hits: TtrmSearchHit[] }>(`/model/search?${params.toString()}`);
    return res.hits;
  }

  /**
   * Poll `GET /status` and fire `cb(modelVersion)` when it changes (Veles has no
   * server push — gap ⑤). The first observed version seeds the baseline silently;
   * only subsequent changes notify. A failed poll is swallowed (best-effort — a
   * transient blip must not tear down the view).
   */
  onModelChanged(cb: (version: string) => void): Disposable {
    let last: string | null = null;
    const tick = () => {
      void this.getJson<{ model_version?: string }>('/status')
        .then((s) => {
          const v = s.model_version ?? '';
          if (last === null) {
            last = v;
          } else if (v !== last) {
            last = v;
            cb(v);
          }
        })
        .catch(() => {
          /* transient — try again next tick */
        });
    };
    const handle = this.setIntervalImpl(tick, this.pollIntervalMs);
    return { dispose: () => this.clearIntervalImpl(handle) };
  }
}
