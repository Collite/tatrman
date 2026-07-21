// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it, vi } from 'vitest';
import type { DesignerExtension, ExtensionContext } from '../designer-extensions.js';
import { mountDesignerPanels, resolveExtensionUrl } from '../panels-host.js';

function ctx(): ExtensionContext {
  return {
    dataSource: {} as never,
    // `worker` backend ⇒ discover() returns the bundled roster only (no network).
    backend: { kind: 'worker', baseUrl: null, capabilities: { edit: false } as never },
    auth: { token: async () => null },
  };
}

/** A fake extension contributing two panels that record their mount/dispose lifecycle. */
function fakeModule(log: string[]): { default: DesignerExtension } {
  const panel = (id: string) => ({
    id,
    title: id.toUpperCase(),
    mount(el: HTMLElement) {
      el.textContent = `${id}-mounted`;
      log.push(`mount:${id}`);
      return () => log.push(`dispose:${id}`);
    },
  });
  return {
    default: {
      id: 'cz.test.ext',
      version: '0.1.0',
      contributes: { panels: [panel('runs'), panel('lineage')] },
      activate() {},
    },
  };
}

describe('mountDesignerPanels', () => {
  it('loads advertised extensions and mounts their panels as a tabbed dock', async () => {
    const log: string[] = [];
    const host = document.createElement('div');
    const res = await mountDesignerPanels(host, ctx(), {
      bundled: [{ id: 'cz.test.ext', version: '0.1.0', moduleUrl: '/x' }],
      importModule: vi.fn(async () => fakeModule(log)),
    });

    expect(res.loaded).toContain('cz.test.ext');
    expect(host.querySelectorAll('.ttr-panels__tab').length).toBe(2);
    // The first panel is mounted; the second is not until its tab is clicked.
    expect(host.querySelector('.ttr-panels__panel')?.getAttribute('data-panel')).toBe('runs');
    expect(log).toEqual(['mount:runs']);

    const lineageTab = Array.from(host.querySelectorAll<HTMLButtonElement>('.ttr-panels__tab')).find(
      (b) => b.dataset.panel === 'lineage',
    );
    lineageTab?.click();
    // Switching disposes the previous panel and mounts the next.
    expect(log).toEqual(['mount:runs', 'dispose:runs', 'mount:lineage']);
    expect(host.querySelector('.ttr-panels__panel')?.getAttribute('data-panel')).toBe('lineage');
  });

  it('dispose tears down the active panel and clears the host', async () => {
    const log: string[] = [];
    const host = document.createElement('div');
    const res = await mountDesignerPanels(host, ctx(), {
      bundled: [{ id: 'cz.test.ext', version: '0.1.0', moduleUrl: '/x' }],
      importModule: vi.fn(async () => fakeModule(log)),
    });
    res.dispose();
    expect(log).toEqual(['mount:runs', 'dispose:runs']);
    expect(host.querySelector('.ttr-panels__tabs')).toBeNull();
  });

  it('a host with no advertised panels is left empty (no dock chrome)', async () => {
    const host = document.createElement('div');
    const res = await mountDesignerPanels(host, ctx(), { bundled: [] });
    expect(res.loaded).toEqual([]);
    expect(host.children.length).toBe(0);
  });

  it('R2-10: a panel whose mount() throws degrades to an inline error and keeps the other tabs alive', async () => {
    const host = document.createElement('div');
    const mod: { default: DesignerExtension } = {
      default: {
        id: 'cz.test.ext',
        version: '0.1.0',
        contributes: {
          panels: [
            {
              id: 'boom',
              title: 'BOOM',
              mount() {
                throw new Error('kaboom');
              },
            },
            { id: 'ok', title: 'OK', mount: (el: HTMLElement) => ((el.textContent = 'ok'), () => {}) },
          ],
        },
        activate() {},
      },
    };
    // The first (throwing) panel is shown by default — the mount must NOT reject the whole host.
    const res = await mountDesignerPanels(host, ctx(), {
      bundled: [{ id: 'cz.test.ext', version: '0.1.0', moduleUrl: '/x' }],
      importModule: vi.fn(async () => mod),
    });
    expect(res.loaded).toContain('cz.test.ext');
    expect(host.querySelector('.ttr-panels__panel')?.textContent).toContain('failed to render');
    expect(host.querySelectorAll('.ttr-panels__tab').length).toBe(2); // both tabs still present
  });
});

describe('resolveExtensionUrl (R2-2 same-origin guard)', () => {
  it('allows a same-origin absolute moduleUrl and resolves a relative one', () => {
    expect(resolveExtensionUrl('/v1/designer/extensions/x', 'https://veles.local')).toBe(
      'https://veles.local/v1/designer/extensions/x',
    );
    expect(resolveExtensionUrl('https://veles.local/x.js', 'https://veles.local')).toBe('https://veles.local/x.js');
  });

  it('refuses a cross-origin moduleUrl so the bearer is never sent elsewhere', () => {
    expect(() => resolveExtensionUrl('https://evil.example/x.js', 'https://veles.local')).toThrow(/cross-origin/);
  });

  it('refuses an absolute moduleUrl when there is no backend origin to check against', () => {
    expect(() => resolveExtensionUrl('https://evil.example/x.js', undefined)).toThrow(/no backend origin/);
    expect(resolveExtensionUrl('/x.js', undefined)).toBe('/x.js'); // relative is fine
  });
});
