// SPDX-License-Identifier: Apache-2.0
// FO-P0.S4.T1 (test-first) — the core-side license-aware extension loader (FO contracts §2).
//
// The OPEN Studio Viewer ships NO license client and NO authoring code (FO-21). This spec pins the
// core loader's contract: `license:'none'` loads; `license:'platform'` in the open build is REFUSED
// with a visible-but-locked affordance (never silently hidden); the commercial build (a license
// client + an injected `activate`) resolves grants and loads iff every required grant is present,
// fail-closed on an unreachable service.
//
// Red until T3 implements `../ext/extension-loader` + `../ext/LockedAuthoringAffordance`.
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import {
  loadExtension,
  type ExtensionManifest,
  type LicenseClient,
  type ActivateExtension,
} from '../ext/extension-loader.js';
import { LockedAuthoringAffordance } from '../ext/LockedAuthoringAffordance.js';
import type { ShellEditContext } from '../shell/edit-context.js';

afterEach(() => cleanup());

/** A minimal stand-in for the mounted authoring context (the real one comes from the extension). */
const fakeContext = (): ShellEditContext => ({
  editable: true,
  removeNode: vi.fn().mockResolvedValue(true),
  saveNode: vi.fn().mockResolvedValue({ ok: true }),
  renderToolbarActions: () => null,
  renderNodeMenu: () => null,
  renderMissingObjects: () => null,
});

const platformManifest: ExtensionManifest = {
  name: '@tatrman/designer-authoring',
  version: '0.1.0',
  license: 'platform',
  requires: ['authoring'],
};
const openManifest: ExtensionManifest = {
  name: '@tatrman/example-open-ext',
  version: '1.0.0',
  license: 'none',
  requires: [],
};

const licenseClient = (grants: string[]): LicenseClient => ({
  fetchGrants: vi.fn().mockResolvedValue({ grants, expiresAt: '2027-01-01T00:00:00Z' }),
});

describe('core extension loader — license gate (FO §2)', () => {
  it('loads a license:"none" extension directly (no grant needed)', async () => {
    const ctx = fakeContext();
    const activate: ActivateExtension = vi.fn().mockReturnValue(ctx);
    const result = await loadExtension(openManifest, { activate });
    expect(result.loaded).toBe(true);
    if (result.loaded) expect(result.context).toBe(ctx);
    expect(activate).toHaveBeenCalledWith([]);
  });

  it('REFUSES a license:"platform" extension in the open Viewer build (no license client) — locked', async () => {
    const result = await loadExtension(platformManifest, {}); // open build: no client, no activate
    expect(result.loaded).toBe(false);
    if (!result.loaded) {
      expect(result.reason).toBe('no-license-client');
      expect(result.locked).toBe(true);
    }
  });

  it('never runs the extension when refused in the open build', async () => {
    const activate: ActivateExtension = vi.fn();
    // no license client → platform manifest must not reach activate
    const result = await loadExtension(platformManifest, { activate });
    expect(result.loaded).toBe(false);
    expect(activate).not.toHaveBeenCalled();
  });

  it('loads in the commercial build when the required grant is present', async () => {
    const ctx = fakeContext();
    const activate: ActivateExtension = vi.fn().mockReturnValue(ctx);
    const result = await loadExtension(platformManifest, {
      licenseClient: licenseClient(['authoring', 'operations']),
      activate,
    });
    expect(result.loaded).toBe(true);
    if (result.loaded) expect(result.context).toBe(ctx);
    expect(activate).toHaveBeenCalledWith(['authoring', 'operations']);
  });

  it('refuses (locked, grant-absent) when the grant is missing', async () => {
    const activate: ActivateExtension = vi.fn().mockReturnValue(fakeContext());
    const result = await loadExtension(platformManifest, {
      licenseClient: licenseClient(['operations']), // no 'authoring'
      activate,
    });
    expect(result.loaded).toBe(false);
    if (!result.loaded) {
      expect(result.reason).toBe('grant-absent');
      expect(result.locked).toBe(true);
    }
    expect(activate).not.toHaveBeenCalled();
  });

  it('fails closed (locked) when the license service is unreachable', async () => {
    const activate: ActivateExtension = vi.fn().mockReturnValue(fakeContext());
    const result = await loadExtension(platformManifest, {
      licenseClient: { fetchGrants: vi.fn().mockRejectedValue(new Error('ECONNREFUSED')) },
      activate,
    });
    expect(result.loaded).toBe(false);
    if (!result.loaded) expect(result.locked).toBe(true);
    expect(activate).not.toHaveBeenCalled();
  });
});

describe('LockedAuthoringAffordance', () => {
  it('renders a visible, locked affordance (never silently hidden)', () => {
    render(LockedAuthoringAffordance({ featureName: 'Authoring' }));
    // a lock glyph + an upgrade hint, discoverable by the user (FO-17 funnel)
    expect(screen.getByRole('button', { name: /authoring/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /authoring/i })).toBeDisabled();
    expect(screen.getByText(/🔒|locked|upgrade/i)).toBeInTheDocument();
  });
});
