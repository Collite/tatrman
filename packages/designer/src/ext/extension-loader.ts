// SPDX-License-Identifier: Apache-2.0
// FO-P0.S4.T3 — the core-side license-aware extension loader (FO contracts §2).
//
// ONE core build serves both tiers. The open Studio Viewer ships no license client and no authoring
// code (FO-21), so this loader must refuse `license:'platform'` manifests on its own — WITHOUT the
// extension present — surfacing a visible-but-locked affordance (never a silent hide). The commercial
// build injects a `LicenseClient` (→ `GET /v1/license/grants`, PL demand §1) and an `activate` closure
// that wraps `@tatrman/designer-authoring`'s frozen `activate()` and returns the mounted
// `ShellEditContext`. The loader resolves grants, checks `requires`, and loads iff every grant is
// present — fail-closed when the service is unreachable.
//
// The manifest shape mirrors `@tatrman/designer-authoring/src/manifest.ts` (structural, not imported —
// the authoring package is absent from the open bundle, and no shared package is published until the
// FO-P0.S3 publish cut; same mirroring discipline as `graph-ops`/`shell-edit-context`).

import type { ShellEditContext } from '../shell/edit-context.js';

/** The manifest fields the core loader gates on (subset of the extension's full manifest). */
export interface ExtensionManifest {
  name: string;
  version: string;
  /** `'none'` = no gate; `'platform'` = requires a license grant, absent from the open bundle. */
  license: 'none' | 'platform';
  /** required grant names, e.g. `['authoring']` — every one must be present to load. */
  requires: string[];
}

/** License service client (PL demand §1): `GET /v1/license/grants` (bearer) → `{grants, expiresAt}`. */
export interface LicenseClient {
  fetchGrants(): Promise<{ grants: string[]; expiresAt?: string }>;
}

/**
 * Injected by the commercial build only. Wraps `@tatrman/designer-authoring`'s `activate()` and
 * returns the mounted `ShellEditContext` (or `null` if the extension itself declined). Absent in the
 * open Viewer — where the loader never reaches it.
 */
export type ActivateExtension = (grants: string[]) => ShellEditContext | null;

export interface LoadOptions {
  /** present only when a license client is wired (commercial build). */
  licenseClient?: LicenseClient;
  /** present only in the commercial build (the authoring extension's activate wrapper). */
  activate?: ActivateExtension;
}

export type LoadRefusal = 'no-license-client' | 'grant-absent' | 'service-unreachable';

export type LoadResult =
  | { loaded: true; context: ShellEditContext }
  | { loaded: false; reason: LoadRefusal; locked: boolean };

/**
 * Resolve whether an extension loads in the current build, per FO contracts §2. Never throws — a
 * failed license lookup is fail-closed (`locked`, not loaded). Enforcement is honesty, not DRM: the
 * real wall is the FO-21 repo split (commercial code is simply absent from the open bundle); this
 * loader exists so one core build serves both tiers and the locked state is always *visible*.
 */
export async function loadExtension(
  manifest: ExtensionManifest,
  opts: LoadOptions = {},
): Promise<LoadResult> {
  // `license:'none'` — no license gate; load directly (still needs an activate to produce a context).
  if (manifest.license === 'none') {
    const context = opts.activate?.([]) ?? null;
    return context
      ? { loaded: true, context }
      : { loaded: false, reason: 'grant-absent', locked: false };
  }

  // `license:'platform'` in the open Viewer: no license client / no extension present → refuse, locked.
  if (!opts.licenseClient || !opts.activate) {
    return { loaded: false, reason: 'no-license-client', locked: true };
  }

  // Commercial build — resolve grants, fail-closed on an unreachable service (PL demand §1).
  let grants: string[];
  try {
    ({ grants } = await opts.licenseClient.fetchGrants());
  } catch {
    return { loaded: false, reason: 'service-unreachable', locked: true };
  }

  if (!manifest.requires.every((grant) => grants.includes(grant))) {
    return { loaded: false, reason: 'grant-absent', locked: true };
  }

  const context = opts.activate(grants);
  return context
    ? { loaded: true, context }
    : { loaded: false, reason: 'grant-absent', locked: true };
}
