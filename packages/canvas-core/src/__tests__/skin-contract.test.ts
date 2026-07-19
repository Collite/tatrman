// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { SkinRegistry } from '../registry.js';
import type { RunStatus, DiagnosticsState, NodeBaseState } from '../types.js';
import { fakeSkin } from './fake-skin.js';

describe('skin render contract (contracts §1)', () => {
  it('a minimal valid SkinDefinition type-checks and registers', () => {
    const reg = new SkinRegistry();
    expect(() => reg.register(fakeSkin({ id: 'test.skin' }))).not.toThrow();
    expect(reg.resolve('test.skin')?.displayName).toBe('Test');
  });

  it('both mandatory slot renderers + a full AnchorDeclaration are present on a valid skin', () => {
    const skin = fakeSkin();
    expect(skin.renderNode).toBeTruthy(); // opaque token (kindMark + label + ports live in it)
    const a = skin.declareAnchors({ width: 160, height: 72 });
    expect(a.chrome).toBeDefined();
    expect(a.status).toBeDefined();
    expect(a.diagnostics).toBeDefined();
  });

  it('the fixed RunStatus vocabulary is exactly idle|running|done|failed (exhaustive)', () => {
    const all: RunStatus[] = ['idle', 'running', 'done', 'failed'];
    // compile-time exhaustiveness: a switch over RunStatus with no default must cover all
    const label = (s: RunStatus): string => {
      switch (s) {
        case 'idle': return 'idle';
        case 'running': return 'running';
        case 'done': return 'done';
        case 'failed': return 'failed';
        // no default — if RunStatus grows, this stops compiling
      }
    };
    expect(all.map(label)).toEqual(['idle', 'running', 'done', 'failed']);
  });

  it('DiagnosticsState is exactly {errorCount, warnCount}', () => {
    const d: DiagnosticsState = { errorCount: 2, warnCount: 1 };
    expect(Object.keys(d).sort()).toEqual(['errorCount', 'warnCount']);
  });

  it('NodeBaseState carries the never-claimable chrome flags', () => {
    const s: NodeBaseState = {
      selected: true, focused: false, readOnly: true, derived: false, orphanedLayout: true,
    };
    expect(s.selected).toBe(true);
    expect(s.readOnly).toBe(true);
    expect(s.orphanedLayout).toBe(true);
  });
});
