// SPDX-License-Identifier: Apache-2.0
//
// FO-A1 P1 (task 1.2, contracts §6) — per-skin palette pins. These render each skin
// family's exemplar node and assert the load-bearing colours resolve to TODAY's hex
// values. Green by construction after the D1 token migration — their job is to catch a
// skin that later drifts to the WRONG token (a hue change slips past `no-raw-hex` because
// it's still "a token", but changes a pinned value here). Paired with the tokens-package
// palette-stability audit (which pins the token VALUES); this pins the token WIRING.

import { describe, it, expect, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { CanvasNode, NodeBaseState, NodeRenderProps } from '@tatrman/canvas-core';
import { canvas as palette } from '@tatrman/tokens';
import type { DesignerSkin } from '../../canvas/skin-component.js';
import { dbTableClassic } from '../db-table-classic.js';
import { erCrow } from '../er-crow.js';
import { cncCards } from '../cnc-cards.js';
import { cncBubbles } from '../cnc-bubbles.js';
import { mdErDialect } from '../md-er-dialect.js';
import { mdStarGlyph } from '../md-star-glyph.js';
import { installBrowserPolyfills, anchors } from '../../canvas/__tests__/test-utils.js';

beforeAll(() => installBrowserPolyfills());

const st: NodeBaseState = { selected: false, focused: false, readOnly: false, derived: false, orphanedLayout: false };
const renderBody = (skin: DesignerSkin, node: CanvasNode, theme: NodeRenderProps['theme'] = 'ice') => {
  const Body = skin.renderNode;
  return render(<Body node={node} state={st} anchors={anchors} theme={theme} />);
};

function rowNode(id: string, kind: string): CanvasNode {
  return {
    id, qname: `q.${id}`, kind, label: id,
    ports: [{ id: `${id}::out`, direction: 'out', role: 'data', connected: true }],
    slotData: { rows: [{ name: 'a', qname: `q.${id}.a`, kind: 'column', type: 'int', isKey: true }] },
  };
}
function cncNode(): CanvasNode {
  return {
    id: 'C', qname: 'q.cnc.entity.C', kind: 'entity', label: 'C',
    ports: [{ id: 'C::out', direction: 'out', role: 'data', connected: true }],
    slotData: { rows: [{ name: 'p', qname: 'q.cnc.entity.C.p', kind: 'attribute', type: 'text' }], role: 'master', synonyms: ['x'] },
  };
}
function mdNode(kind: 'cubelet' | 'dimension'): CanvasNode {
  return {
    id: kind, qname: `q.md.${kind}`, kind, label: kind,
    ports: [{ id: `${kind}::out`, direction: 'out', role: 'data', connected: true }],
    slotData: { rows: kind === 'cubelet'
      ? [{ name: 'm', qname: 'q.md.cubelet.m', kind: 'measure', type: 'num', isKey: false }]
      : [{ name: 'l', qname: 'q.md.dimension.l', kind: 'level', type: null, isKey: false }] },
  };
}

describe('skin palette pins (D1) — load-bearing colours resolve to the pinned tokens', () => {
  it('db.table-classic: navy header, white card, token border/rows', () => {
    renderBody(dbTableClassic, rowNode('T', 'table'));
    const card = screen.getByTestId('row-card');
    expect(card).toHaveStyle({ background: palette.nodeFill });
    expect(card).toHaveStyle({ border: `1.3px solid ${palette.nodeStroke}` });
    // header strip is the db accent (navy)
    expect(card.firstElementChild).toHaveStyle({ background: palette.ink });
    expect(screen.getByTestId('key-mark')).toHaveStyle({ color: palette.aliveDeep });
  });

  it('er.crow: deep-navy header', () => {
    renderBody(erCrow, rowNode('E', 'entity'));
    expect(screen.getByTestId('row-card').firstElementChild).toHaveStyle({ background: palette.accentDeep });
  });

  it('cnc.cards: slate header + role chip tokens', () => {
    renderBody(cncCards, cncNode());
    expect(screen.getByTestId('cnc-card').firstElementChild).toHaveStyle({ background: palette.slate });
    expect(screen.getByTestId('cnc-role')).toHaveStyle({ background: palette.chipRoleBg, color: palette.slate });
  });

  it('cnc.bubbles: star-tone border + ink label', () => {
    renderBody(cncBubbles, cncNode());
    expect(screen.getByTestId('cnc-bubble')).toHaveStyle({ border: `1.6px solid ${palette.edgeStar}` });
    expect(screen.getByTestId('node-label')).toHaveStyle({ color: palette.ink });
  });

  it('md.er-dialect: cube header deep-navy, card border token', () => {
    renderBody(mdErDialect, mdNode('cubelet'));
    expect(screen.getByTestId('md-er-card')).toHaveStyle({ border: `1.3px solid ${palette.nodeStroke}` });
    expect(screen.getByTestId('md-er-card').firstElementChild).toHaveStyle({ background: palette.accentDeep });
  });

  it('md.er-dialect: dimension header is the light tint', () => {
    renderBody(mdErDialect, mdNode('dimension'));
    expect(screen.getByTestId('md-er-card').firstElementChild).toHaveStyle({ background: palette.headerTint });
  });

  it('md.star-glyph: cube polygon fills deep-navy', () => {
    renderBody(mdStarGlyph, mdNode('cubelet'));
    expect(screen.getByTestId('star-cube')).toHaveStyle({ background: palette.accentDeep });
  });
});
