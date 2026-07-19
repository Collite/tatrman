import { describe, it, expect, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import { TOKENS, type CanvasNode, type RenderContext } from '@tatrman/canvas-core';
import { script } from '../script.js';
import { createSkinRegistry } from '../index.js';
import { installBrowserPolyfills, anchors } from '../../canvas/__tests__/test-utils.js';

beforeAll(() => installBrowserPolyfills());

const ctx = { skin: script, theme: 'stage-navy' } as unknown as RenderContext;
const state = { selected: false, focused: false, readOnly: false, derived: false, orphanedLayout: false } as const;

const opNode = (over: Partial<CanvasNode>): CanvasNode => ({
  id: 'filter', qname: 'p.filter', kind: 'op', label: 'filter cancelled', ports: [], slotData: {}, ...over,
});

describe('script skin — the text-forward extreme (E-3)', () => {
  it('is a registered processing skin on the Stage-Navy dark canvas, TD flow', () => {
    const reg = createSkinRegistry();
    expect(reg.roster('processing').map((s) => s.id)).toContain('script');
    expect(script.flow.orientation).toBe('TD');
    expect(script.canvas.background).toBe(TOKENS.stageNavy);
  });

  it('renders a text-forward pill showing the description (bodyText)', () => {
    const Body = script.renderNode;
    render(<Body node={opNode({ bodyText: 'drop rows where status = cancelled' })} state={state} anchors={anchors} theme="stage-navy" />);
    expect(screen.getByTestId('body-text')).toHaveTextContent('drop rows where status = cancelled');
  });

  it('falls back to the code fragment when the node has no description (T4 fallback)', () => {
    const Body = script.renderNode;
    render(<Body node={opNode({ bodyText: undefined, slotData: { code: 'df.filter(status != "cancelled")' } })} state={state} anchors={anchors} theme="stage-navy" />);
    expect(screen.getByTestId('body-text')).toHaveTextContent('df.filter(status != "cancelled")');
  });

  it('data edges are non-dashed; the control edge is dashed YELLOW (D-4)', () => {
    const data = script.edgeStyle({ id: 'e', from: { node: 'a', port: 'p' }, to: { node: 'b', port: 'q' }, role: 'data' }, ctx);
    expect(data.dash).toBeUndefined();
    const control = script.edgeStyle({ id: 'c', from: { node: 'a', port: 'p' }, to: { node: 'b', port: 'q' }, role: 'control' }, ctx);
    expect(control.dash).toBeTruthy();
    expect(control.stroke).toBe(TOKENS.yellow);
  });

  it('D-4 proof holds in TD too: control on cross axis, data on flow axis', () => {
    expect(script.portGeometry({ id: 'p', direction: 'in', role: 'data', connected: true }, opNode({})).placement).toBe('flow-in');
    expect(script.portGeometry({ id: 'c', direction: 'in', role: 'control', connected: true }, opNode({})).placement).toBe('cross-in');
  });

  it('renders the unconnected rejects stub in the port strip (D-2)', () => {
    const Body = script.renderNode;
    const node = opNode({ slotData: { ports: [
      { id: 'filter.out', direction: 'out', role: 'data', connected: true, label: 'kept' },
      { id: 'filter.rejects', direction: 'out', role: 'rejects', connected: false, label: 'rejects ∅' },
    ] } });
    render(<Body node={node} state={state} anchors={anchors} theme="stage-navy" />);
    const reject = screen.getByTestId('port-strip').querySelector('[data-role="rejects"]');
    expect(reject).not.toBeNull();
    expect(reject).toHaveAttribute('data-connected', 'false');
  });
});
