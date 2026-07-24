// SPDX-License-Identifier: Apache-2.0
// FO-A1 P5 (5.1, contracts §7 — FO-33 naming). The shell chrome names the suite "Tatrman Studio";
// the OPEN build (no editContext) identifies as "Studio Viewer" and NEVER names a commercial module
// (Studio Modeler / Studio Designer live only in @tatrman/designer-authoring). Product strings only —
// package names / routes / test-ids are unchanged (§7 rule).
import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { ShellFrame } from '../ShellFrame.js';
import type { CatalogGroup } from '../types.js';
import { fakeDataSource } from './fake-data-source.js';

const CATALOG: CatalogGroup[] = [
  { kind: 'schema', label: 'Schemas', items: [{ ref: 'er_sales', qname: 'er_sales', kind: 'schema', schemaCode: 'er', label: 'er_sales' }] },
];

afterEach(() => { cleanup(); window.history.replaceState(null, '', '/'); });

describe('FO-33 naming — shell chrome', () => {
  it('the shell chrome names the suite "Tatrman Studio"', () => {
    render(<ShellFrame dataSource={fakeDataSource()} workspace="ws" catalog={CATALOG} files={[]} displayMode="just-names" />);
    expect(screen.getByTestId('suite-brand')).toHaveTextContent('Tatrman Studio');
  });

  it('the OPEN build (no editContext) identifies as "Studio Viewer" and names no commercial module', () => {
    render(<ShellFrame dataSource={fakeDataSource()} workspace="ws" catalog={CATALOG} files={[]} displayMode="just-names" />);
    expect(screen.getByTestId('build-name')).toHaveTextContent('Studio Viewer');
    expect(screen.queryByText('Studio Modeler')).toBeNull();
    expect(screen.queryByText('Studio Designer')).toBeNull();
  });
});
