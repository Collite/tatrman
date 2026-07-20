// SPDX-License-Identifier: Apache-2.0
// PL-P1.S9.T2 — a ProcessingGraphSource that renders a STATIC bundle manifest fetched from Veles's
// stored-manifest endpoint (`GET /v1/manifests/{ref}`, S9.T4). The manifest → program graph →
// ProcessingGraph pipeline is read-only (contracts §6); container drill-in is not part of the static
// view. Bearer-authed like the ttrm surface (dev factory sets the header — browsers cannot).

import type { ProcessingGraph } from '@tatrman/canvas-core';
import type { ProcessingGraphSource } from '../processing-source.js';
import { manifestToProgramGraph, type BundleManifestV2 } from './manifest-program-graph.js';
import { programGraphToProcessing } from './program-graph-to-processing.js';

export interface ManifestSourceOptions {
  /** Injectable fetch (Node/dev factory can set the bearer header a browser cannot). */
  fetchImpl?: typeof fetch;
  /** Bearer token for Veles ingress (dev only — never a deep-linkable param). */
  token?: string;
  /** Default manifest ref when a caller passes an empty programRef. */
  defaultRef?: string;
}

/** Raised when Veles has no manifest under the requested ref (surface a not-found state, not a crash). */
export class ManifestNotFoundError extends Error {
  constructor(public readonly ref: string) {
    super(`no stored manifest '${ref}'`);
    this.name = 'ManifestNotFoundError';
  }
}

export class ManifestProgramSource implements ProcessingGraphSource {
  private readonly base: string;

  constructor(
    origin: string,
    private readonly opts: ManifestSourceOptions = {},
  ) {
    this.base = origin.replace(/\/$/, '');
  }

  async getProgramGraph(programRef: string): Promise<ProcessingGraph> {
    const ref = programRef || this.opts.defaultRef || '';
    const manifest = await this.fetchManifest(ref);
    return programGraphToProcessing(manifestToProgramGraph(manifest), ref);
  }

  /** The static manifest view has no container drill-in (islands are leaves) — return an empty region. */
  async getContainerGraph(containerRef: string): Promise<ProcessingGraph> {
    return { id: containerRef, face: 'processing', nodes: [], edges: [] };
  }

  private async fetchManifest(ref: string): Promise<BundleManifestV2> {
    const f = this.opts.fetchImpl ?? fetch;
    const headers: Record<string, string> = {};
    if (this.opts.token) headers.Authorization = `Bearer ${this.opts.token}`;
    const resp = await f(`${this.base}/v1/manifests/${encodeURIComponent(ref)}`, { headers });
    if (resp.status === 404) throw new ManifestNotFoundError(ref);
    if (!resp.ok) throw new Error(`Veles manifest fetch failed: ${resp.status}`);
    return (await resp.json()) as BundleManifestV2;
  }
}
