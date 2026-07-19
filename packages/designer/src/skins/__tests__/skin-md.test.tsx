import { describe, it, expect, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { CanvasNode, CanvasGraph, NodeBaseState } from '@tatrman/canvas-core';
import { mdStarGlyph, orbitLayout } from '../md-star-glyph.js';
import { mdErDialect } from '../md-er-dialect.js';
import { createSkinRegistry } from '../index.js';
import { installBrowserPolyfills, anchors } from '../../canvas/__tests__/test-utils.js';

beforeAll(() => installBrowserPolyfills());

const REST: NodeBaseState = { selected: false, focused: false, readOnly: false, derived: false, orphanedLayout: false };

function cube(): CanvasNode {
  return {
    id: 'Sales', qname: 'orders_hero.md.cubelet.Sales', kind: 'cubelet', label: 'Sales',
    ports: [{ id: 'Sales::out', direction: 'out', role: 'data', connected: true }],
    slotData: {
      rows: [
        { name: 'qty', qname: 'q', kind: 'measure', type: 'sum', isKey: false },
        { name: 'net_amount', qname: 'n', kind: 'measure', type: 'sum', isKey: false },
      ],
      calcs: ['margin_pct'], // fixture-filled derived measure
    },
  };
}
function dim(): CanvasNode {
  return {
    id: 'Time', qname: 'orders_hero.md.dimension.Time', kind: 'dimension', label: 'Time',
    ports: [{ id: 'Time::in', direction: 'in', role: 'data', connected: true }],
    slotData: {
      rows: [
        { name: 'day', qname: 'd', kind: 'level', type: null, isKey: true },
        { name: 'month', qname: 'm', kind: 'level', type: 'md.day_to_month', isKey: false },
        { name: 'year', qname: 'y', kind: 'level', type: null, isKey: false },
      ],
    },
  };
}
const renderBody = (skin: typeof mdStarGlyph, node: CanvasNode) => {
  const Body = skin.renderNode;
  return render(<Body node={node} state={REST} anchors={anchors} theme="ice" />);
};

describe('md.star-glyph', () => {
  it('renders the cube as a polygon carrying its measures + calc banner', () => {
    renderBody(mdStarGlyph, cube());
    expect(screen.getByTestId('star-cube')).toBeInTheDocument();
    expect(screen.getByTestId('node-label')).toHaveTextContent('Sales');
    expect(screen.getAllByTestId('md-measure').map((e) => e.textContent)).toEqual(['qty', 'net_amount']);
    expect(screen.getByTestId('md-calc')).toHaveTextContent('margin_pct');
  });

  it('renders a dimension as an orbiting level-stack in order', () => {
    renderBody(mdStarGlyph, dim());
    expect(screen.getByTestId('star-dim')).toBeInTheDocument();
    expect(screen.getAllByTestId('md-level-name').map((e) => e.textContent)).toEqual(['day', 'month', 'year']);
    // calc-driven level shows its via map
    expect(screen.getByText('via day_to_month')).toBeInTheDocument();
  });

  it('the orbit layout is deterministic (same graph ⇒ same positions)', () => {
    const graph: CanvasGraph = { id: 'g', face: 'modeling', kind: 'md', containers: [], edges: [], nodes: [cube(), dim(), { ...dim(), id: 'Customer', kind: 'dimension' }] };
    const size = () => ({ width: 150, height: 80 });
    const a = orbitLayout(graph, size);
    const b = orbitLayout(graph, size);
    expect(a).toEqual(b);
    expect(a['Sales']).toEqual({ x: 420, y: 320 }); // cube at centre
    expect(Object.keys(a).sort()).toEqual(['Customer', 'Sales', 'Time']);
  });

  it('is a registered modeling/md skin and the md default (E-3a)', () => {
    const reg = createSkinRegistry();
    expect(reg.roster('modeling', 'md').map((s) => s.id)).toContain('md.star-glyph');
    expect(reg.defaultSkin('modeling', 'md')).toBe('md.star-glyph');
  });
});

describe('md.er-dialect', () => {
  it('renders cube + dimension as er-style cards', () => {
    renderBody(mdErDialect, cube());
    expect(screen.getByTestId('md-er-card')).toBeInTheDocument();
    expect(screen.getByTestId('node-label')).toHaveTextContent('Sales');
  });

  it('is in the md roster (switchable via the same picker)', () => {
    const reg = createSkinRegistry();
    expect(reg.roster('modeling', 'md').map((s) => s.id).sort()).toEqual(['md.er-dialect', 'md.star-glyph']);
  });
});

describe('md P-1 parity — no datum is smuggled by either skin', () => {
  const names = (testid: string) => screen.queryAllByTestId(testid).map((e) => e.textContent);

  it('every measure + calc visible in star-glyph is present in er-dialect (cube)', () => {
    const { unmount } = renderBody(mdStarGlyph, cube());
    const star = { measures: names('md-measure'), calcs: screen.queryAllByTestId('md-calc').map((e) => e.textContent) };
    unmount();
    renderBody(mdErDialect, cube());
    expect(names('md-measure')).toEqual(star.measures);
    expect(screen.queryAllByTestId('md-calc').map((e) => e.textContent)).toEqual(star.calcs);
  });

  it('every dimension level (by name, in order) visible in star-glyph is present in er-dialect', () => {
    const { unmount } = renderBody(mdStarGlyph, dim());
    const starLevels = screen.getAllByTestId('md-level-name').map((e) => e.textContent);
    unmount();
    renderBody(mdErDialect, dim());
    expect(screen.getAllByTestId('md-level-name').map((e) => e.textContent)).toEqual(starLevels);
  });
});
