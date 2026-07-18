// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import type { DataSourceCapabilities } from '../model-data-source.js';
import {
  perspectiveHint,
  modelKindHint,
  layoutPersistHint,
  backendFailureHint,
} from '../capability-hints.js';

const WORKER: DataSourceCapabilities = {
  edit: true, modelKinds: ['db', 'er', 'md', 'cnc'], bindings: true, perspectives: true, layoutPersist: 'in-file',
};
const WS: DataSourceCapabilities = {
  edit: false, modelKinds: ['db', 'er', 'cnc'], bindings: false, perspectives: false, layoutPersist: 'sidecar',
};
const VELES: DataSourceCapabilities = {
  edit: false, modelKinds: ['db', 'er'], bindings: false, perspectives: false, layoutPersist: 'none',
};

describe('DM-CAP-* capability hints', () => {
  it('DM-CAP-001: perspectives ok on Worker, hinted on WS/Veles', () => {
    expect(perspectiveHint(WORKER)).toBeNull();
    expect(perspectiveHint(WS)?.code).toBe('DM-CAP-001');
    expect(perspectiveHint(VELES)?.code).toBe('DM-CAP-001');
    expect(perspectiveHint(WS)?.severity).toBe('hint');
  });

  it('DM-CAP-002: md served on Worker, hinted on WS/Veles; cnc served on WS not Veles', () => {
    expect(modelKindHint(WORKER, 'md')).toBeNull();
    expect(modelKindHint(WS, 'md')?.code).toBe('DM-CAP-002');
    expect(modelKindHint(VELES, 'md')?.code).toBe('DM-CAP-002');
    expect(modelKindHint(WS, 'cnc')).toBeNull();
    expect(modelKindHint(VELES, 'cnc')?.code).toBe('DM-CAP-002');
    expect(modelKindHint(WORKER, 'db')).toBeNull();
  });

  it('DM-CAP-003: layout saved on Worker (in-file) + WS (sidecar), auto-only on Veles', () => {
    expect(layoutPersistHint(WORKER)).toBeNull();
    expect(layoutPersistHint(WS)).toBeNull();
    expect(layoutPersistHint(VELES)?.code).toBe('DM-CAP-003');
    expect(layoutPersistHint(VELES)?.severity).toBe('info');
  });

  it('DM-CAP-004: a failed advertised capability surfaces, never swallowed', () => {
    const h = backendFailureHint('getBindings', 'timeout');
    expect(h.code).toBe('DM-CAP-004');
    expect(h.severity).toBe('warning');
    expect(h.message).toContain('getBindings');
    expect(h.message).toContain('timeout');
  });
});
