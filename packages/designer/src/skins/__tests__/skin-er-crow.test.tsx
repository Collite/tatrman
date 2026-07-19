import { describe, it, expect, beforeAll } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { erCrow } from '../er-crow.js';
import { createSkinRegistry } from '../index.js';
import { CanvasKernel } from '../../canvas/Kernel.js';
import { installBrowserPolyfills, heroErGraph, erNode, anchors } from '../../canvas/__tests__/test-utils.js';

beforeAll(() => installBrowserPolyfills());

describe('er.crow skin — mandatory slots (contracts §1.1)', () => {
  it('renders kind mark, label, and attribute rows with key marks', () => {
    const node = erNode('Customer', [{ name: 'id', isKey: true, type: 'int' }, { name: 'name', type: 'text' }]);
    const Body = erCrow.renderNode;
    render(<Body node={node} state={{ selected: false, focused: false, readOnly: false, derived: false, orphanedLayout: false }} anchors={anchors} theme="ice" />);
    expect(screen.getByTestId('kind-mark')).toHaveTextContent('◇');
    expect(screen.getByTestId('node-label')).toHaveTextContent('Customer');
    const rows = screen.getAllByTestId('card-row');
    expect(rows).toHaveLength(2);
    // the key attribute carries a key mark
    expect(within(rows[0]).getByTestId('key-mark')).toHaveTextContent('⚿');
  });

  it('is a registered modeling/er skin and the er default', () => {
    const reg = createSkinRegistry();
    expect(reg.roster('modeling', 'er').map((s) => s.id)).toContain('er.crow');
    expect(reg.defaultSkin('modeling', 'er')).toBe('er.crow');
  });

  it('EVERY port is visible in the kernel (D-2) — a Handle per CanvasPort', () => {
    const reg = createSkinRegistry();
    const graph = heroErGraph();
    const totalPorts = graph.nodes.reduce((n, node) => n + node.ports.length, 0);
    const { container } = render(
      <div style={{ width: 800, height: 600 }}>
        <CanvasKernel graph={graph} registry={reg} skinId="er.crow" positions={{ Customer: { x: 0, y: 0 }, Order: { x: 320, y: 0 } }} />
      </div>,
    );
    const ports = container.querySelectorAll('[data-testid^="port-"]');
    expect(ports.length).toBe(totalPorts);
  });

  it('slot output structure is stable (structural snapshot, not pixels)', () => {
    const node = erNode('Customer', [{ name: 'id', isKey: true, type: 'int' }]);
    const Body = erCrow.renderNode;
    const { container } = render(<Body node={node} state={{ selected: false, focused: false, readOnly: false, derived: false, orphanedLayout: false }} anchors={anchors} theme="ice" />);
    const testids = Array.from(container.querySelectorAll('[data-testid]')).map((el) => el.getAttribute('data-testid'));
    expect(testids).toEqual(['row-card', 'kind-mark', 'node-label', 'card-row', 'key-mark']);
  });
});
