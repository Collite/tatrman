import { describe, it, expect, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { CanvasNode, NodeBaseState } from '@tatrman/canvas-core';
import { cncBubbles } from '../cnc-bubbles.js';
import { cncCards } from '../cnc-cards.js';
import { createSkinRegistry } from '../index.js';
import { installBrowserPolyfills, anchors } from '../../canvas/__tests__/test-utils.js';

beforeAll(() => installBrowserPolyfills());

const state = (focused: boolean): NodeBaseState => ({ selected: focused, focused, readOnly: false, derived: false, orphanedLayout: false });

function concept(): CanvasNode {
  return {
    id: 'Customer', qname: 'orders_hero.cnc.entity.Customer', kind: 'entity', label: 'Customer',
    ports: [{ id: 'Customer::out', direction: 'out', role: 'data', connected: true }],
    slotData: {
      rows: [
        { name: 'name', qname: 'orders_hero.cnc.entity.Customer.name', kind: 'attribute', type: 'text' },
      ],
      role: 'master',        // fixture-filled
      synonyms: ['client'],  // fixture-filled
    },
  };
}
const renderBody = (skin: typeof cncBubbles, node: CanvasNode, focused = false) => {
  const Body = skin.renderNode;
  return render(<Body node={node} state={state(focused)} anchors={anchors} theme="ice" />);
};

describe('cnc.bubbles', () => {
  it('renders a concept ellipse with its role chip at rest, properties HIDDEN', () => {
    renderBody(cncBubbles, concept(), false);
    expect(screen.getByTestId('cnc-bubble')).toBeInTheDocument();
    expect(screen.getByTestId('node-label')).toHaveTextContent('Customer');
    expect(screen.getByTestId('cnc-role')).toHaveTextContent('master');
    expect(screen.queryByTestId('cnc-prop')).toBeNull(); // collapsed at rest
  });

  it('expands properties as chips ON FOCUS (P-2: driven by kernel focus state)', () => {
    renderBody(cncBubbles, concept(), true);
    expect(screen.getByTestId('cnc-prop')).toHaveTextContent('name');
    expect(screen.getByTestId('cnc-synonym')).toHaveTextContent('client');
  });

  it('is a registered modeling/cnc skin and the cnc default (E-3a)', () => {
    const reg = createSkinRegistry();
    expect(reg.roster('modeling', 'cnc').map((s) => s.id)).toContain('cnc.bubbles');
    expect(reg.defaultSkin('modeling', 'cnc')).toBe('cnc.bubbles');
  });

  it('labeled relations use an arrow marker (directed)', () => {
    expect(cncBubbles.edgeStyle({ id: 'e', from: { node: 'a', port: 'a' }, to: { node: 'b', port: 'b' }, role: 'data', label: 'places' }, { skin: cncBubbles as never, theme: 'ice' }).marker).toBe('arrow');
  });
});

describe('cnc.cards', () => {
  it('renders a concept card with role + properties AT REST', () => {
    renderBody(cncCards, concept(), false);
    expect(screen.getByTestId('cnc-card')).toBeInTheDocument();
    expect(screen.getByTestId('cnc-role')).toHaveTextContent('master');
    expect(screen.getByTestId('cnc-prop')).toHaveTextContent('name'); // shown at rest, no focus gate
  });

  it('is in the cnc roster (switchable via the same picker)', () => {
    const reg = createSkinRegistry();
    expect(reg.roster('modeling', 'cnc').map((s) => s.id).sort()).toEqual(['cnc.bubbles', 'cnc.cards']);
  });
});

describe('cnc P-1 parity — no datum is smuggled by either skin', () => {
  it('every property/role/synonym in focused bubbles is present in cards', () => {
    const { unmount } = renderBody(cncBubbles, concept(), true);
    const bubble = {
      props: screen.getAllByTestId('cnc-prop').map((e) => e.textContent).sort(),
      role: screen.getByTestId('cnc-role').textContent,
      synonyms: screen.getAllByTestId('cnc-synonym').map((e) => e.textContent).sort(),
    };
    unmount();
    renderBody(cncCards, concept(), false);
    expect(screen.getAllByTestId('cnc-prop').map((e) => e.textContent).sort()).toEqual(bubble.props);
    expect(screen.getByTestId('cnc-role').textContent).toEqual(bubble.role);
    expect(screen.getAllByTestId('cnc-synonym').map((e) => e.textContent).sort()).toEqual(bubble.synonyms);
  });
});
