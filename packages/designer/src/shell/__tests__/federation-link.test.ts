// SPDX-License-Identifier: Apache-2.0
//
// FO-P1.S5.T3 — the federation bridge: shell state ⇄ the §3 deep-link grammar.

import { describe, it, expect } from 'vitest';
import { parseDeepLink } from '@tatrman/deep-links';
import {
  federationLinkForTab,
  federationUrlForTab,
  openIntentForFederationLink,
  federationIntentFromPath,
  askUrlForTab,
} from '../federation-link.js';
import type { SubjectKind, SubjectTab } from '../types.js';

function tab(ref: string, kind: SubjectKind, perspective?: 'binding' | 'lineage'): SubjectTab {
  return { id: ref, subject: { ref, kind, label: ref }, drillPath: [], preview: false, perspective };
}

describe('federationLinkForTab (shell → §3 projection)', () => {
  it('projects a schema subject to a viewer link', () => {
    expect(federationLinkForTab(tab('sales.Order', 'schema'))).toEqual({ kind: 'viewer', object: 'sales.Order' });
  });

  it('projects a program subject to a process link', () => {
    expect(federationLinkForTab(tab('etl.LoadSales', 'program'))).toEqual({ kind: 'process', program: 'etl.LoadSales' });
  });

  it('projects a lineage perspective to a cell= lineage link', () => {
    expect(federationLinkForTab(tab('sales.Order', 'schema', 'lineage'))).toEqual({ kind: 'lineage', cell: 'sales.Order' });
  });

  it('has no §3 row for the binding perspective in v1', () => {
    expect(federationLinkForTab(tab('sales.Order', 'schema', 'binding'))).toBeNull();
  });

  it('is null for no tab', () => {
    expect(federationLinkForTab(null)).toBeNull();
    expect(federationLinkForTab(undefined)).toBeNull();
  });
});

describe('federationUrlForTab (absolute, copyable)', () => {
  it('prefixes the surface origin onto the §3 path', () => {
    expect(federationUrlForTab(tab('sales.Order', 'schema'), 'https://studio.example.com')).toBe(
      'https://studio.example.com/s/viewer?object=sales.Order',
    );
  });

  it('is null when the tab has no shareable projection', () => {
    expect(federationUrlForTab(tab('x', 'schema', 'binding'), 'https://studio.example.com')).toBeNull();
  });
});

describe('openIntentForFederationLink (§3 inbound → shell intent)', () => {
  it('resolves a viewer link to a subject open', () => {
    expect(openIntentForFederationLink({ kind: 'viewer', object: 'sales.Order' })).toEqual({ kind: 'subject', ref: 'sales.Order' });
  });

  it('resolves a lineage cell to the rooting object (strips the cell suffix)', () => {
    expect(openIntentForFederationLink({ kind: 'lineage', cell: 'sales.Order:42/total' })).toEqual({ kind: 'lineage', root: 'sales.Order' });
  });

  it('cannot open a run= lineage drill in the Viewer (no schema root)', () => {
    expect(openIntentForFederationLink({ kind: 'lineage', run: 'run_1' })).toBeNull();
  });

  it('resolves a process link to a process open', () => {
    expect(openIntentForFederationLink({ kind: 'process', program: 'etl.LoadSales' })).toEqual({ kind: 'process', program: 'etl.LoadSales' });
  });

  it('returns null for other apps’ surfaces (planner/entry/ask)', () => {
    expect(openIntentForFederationLink({ kind: 'entry', table: 'hr.Employee' })).toBeNull();
    expect(openIntentForFederationLink({ kind: 'planner-form', roundId: 'r', formId: 'f' })).toBeNull();
    expect(openIntentForFederationLink({ kind: 'ask', context: { source: 'studio' } })).toBeNull();
  });
});

describe('askUrlForTab (§3 Studio → Iris ask-about-this)', () => {
  it('builds an Iris ask URL whose §3 context round-trips the active object', () => {
    const url = askUrlForTab(tab('sales.Order', 'schema'), 'https://iris.example.com');
    expect(url).not.toBeNull();
    expect(url!.startsWith('https://iris.example.com/ask?context=')).toBe(true);
    expect(parseDeepLink(url!.slice('https://iris.example.com'.length))).toEqual({
      kind: 'ask',
      context: { source: 'studio', object: 'sales.Order' },
    });
  });

  it('normalizes a trailing slash on the base URL', () => {
    expect(askUrlForTab(tab('x', 'schema'), 'https://iris.example.com/')).toMatch(
      /^https:\/\/iris\.example\.com\/ask\?context=/,
    );
  });

  it('is null when there is no tab to ask about', () => {
    expect(askUrlForTab(null, 'https://iris.example.com')).toBeNull();
  });
});

describe('federationIntentFromPath (parse + resolve, never throws)', () => {
  it('reads a federation viewer path', () => {
    expect(federationIntentFromPath('/s/viewer?object=sales.Order')).toEqual({ kind: 'subject', ref: 'sales.Order' });
  });

  it('is null for an internal §6 path (not a federation link)', () => {
    expect(federationIntentFromPath('/w/ws/s/sales.Order')).toBeNull();
  });

  it('is null for another app’s surface', () => {
    expect(federationIntentFromPath('/e/hr.Employee?filter=x')).toBeNull();
  });

  it('is null (not a throw) for a credential-bearing or malformed path', () => {
    expect(federationIntentFromPath('/s/viewer?object=x&token=secret')).toBeNull();
    expect(federationIntentFromPath('::::')).toBeNull();
  });
});
