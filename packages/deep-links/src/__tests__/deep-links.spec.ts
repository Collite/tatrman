// SPDX-License-Identifier: Apache-2.0
//
// FO-P1.S5.T1 — the deep-link codec contract (FO contracts §3). Red-first.

import { describe, expect, it } from 'vitest';
import {
  buildDeepLink,
  parseDeepLink,
  DeepLinkError,
  type DeepLink,
} from '../index.js';

/** parse(build(x)) must equal x for every grammar row. */
function roundTrips(link: DeepLink): void {
  const url = buildDeepLink(link);
  expect(parseDeepLink(url)).toEqual(link);
}

describe('deep-link grammar (FO contracts §3)', () => {
  describe('/s/viewer', () => {
    it('builds and round-trips object + version', () => {
      const link: DeepLink = { kind: 'viewer', object: 'sales.Order', version: '3' };
      expect(buildDeepLink(link)).toBe('/s/viewer?object=sales.Order&version=3');
      roundTrips(link);
    });

    it('round-trips the object-only form', () => {
      roundTrips({ kind: 'viewer', object: 'sales.Order' });
    });

    it('rejects a viewer link with no object', () => {
      expect(() => parseDeepLink('/s/viewer')).toThrow(DeepLinkError);
    });
  });

  describe('/s/lineage (both drill forms)', () => {
    it('round-trips the cell= form', () => {
      roundTrips({ kind: 'lineage', cell: 'sales.Order:42/total' });
    });

    it('round-trips a member-qname cell (W2 member-lineage entry, contracts §5)', () => {
      // a member entry serialises its member qname as the lineage cell ref — no new grammar.
      roundTrips({ kind: 'lineage', cell: 'er.entity.customer.region' });
      roundTrips({ kind: 'lineage', cell: 'acme.sales.core.v1.db.dbo.table.products.price' });
    });

    it('round-trips the run= form', () => {
      roundTrips({ kind: 'lineage', run: 'run_2026_07_19_abc' });
    });

    it('rejects a lineage link carrying both cell and run', () => {
      expect(() => buildDeepLink({ kind: 'lineage', cell: 'a', run: 'b' })).toThrow(DeepLinkError);
    });

    it('rejects a lineage link carrying neither cell nor run', () => {
      expect(() => buildDeepLink({ kind: 'lineage' })).toThrow(DeepLinkError);
      expect(() => parseDeepLink('/s/lineage')).toThrow(DeepLinkError);
    });
  });

  describe('/s/process', () => {
    it('round-trips program + version', () => {
      const link: DeepLink = { kind: 'process', program: 'etl.LoadSales', version: '2' };
      expect(buildDeepLink(link)).toBe('/s/process?program=etl.LoadSales&version=2');
      roundTrips(link);
    });
  });

  describe('/p/round/:roundId/form/:formId (Studio Planner)', () => {
    it('builds path segments and round-trips', () => {
      const link: DeepLink = { kind: 'planner-form', roundId: 'r-2026Q3', formId: 'capex' };
      expect(buildDeepLink(link)).toBe('/p/round/r-2026Q3/form/capex');
      roundTrips(link);
    });
  });

  describe('/e/:table?filter= (Studio Data Entry)', () => {
    it('round-trips a table with an encoded filter', () => {
      const link: DeepLink = { kind: 'entry', table: 'hr.Employee', filter: "dept='RnD'" };
      roundTrips(link);
      // filter value is URL-encoded, not raw
      expect(buildDeepLink(link)).toContain('/e/hr.Employee?filter=');
      expect(buildDeepLink(link)).not.toContain("dept='RnD'");
    });

    it('round-trips a table with no filter', () => {
      roundTrips({ kind: 'entry', table: 'hr.Employee' });
    });
  });

  describe('/ask?context= (Studio → Iris)', () => {
    it('round-trips a JSON context', () => {
      roundTrips({
        kind: 'ask',
        context: { source: 'studio', object: 'sales.Order', cell: 'sales.Order:42/total' },
      });
    });

    it('emits the context as encoded JSON', () => {
      const url = buildDeepLink({ kind: 'ask', context: { source: 'studio', run: 'run_1' } });
      expect(url.startsWith('/ask?context=')).toBe(true);
      const parsed = parseDeepLink(url);
      expect(parsed.kind).toBe('ask');
      if (parsed.kind === 'ask') expect(parsed.context).toEqual({ source: 'studio', run: 'run_1' });
    });
  });

  describe('forward compatibility (FO §3: unknown params ignored)', () => {
    it('preserves an unknown param across a round-trip', () => {
      const link: DeepLink = { kind: 'viewer', object: 'sales.Order', extra: { perspective: 'graph' } };
      const parsed = parseDeepLink(buildDeepLink(link));
      expect(parsed).toEqual(link);
    });

    it('carries an unknown param through parse from a raw URL', () => {
      const parsed = parseDeepLink('/s/viewer?object=sales.Order&future=xyz');
      expect(parsed).toEqual({ kind: 'viewer', object: 'sales.Order', extra: { future: 'xyz' } });
    });
  });

  describe('no-credentials rule (FO §3: a deep link never carries credentials)', () => {
    it('rejects a token param on parse', () => {
      expect(() => parseDeepLink('/s/viewer?object=x&token=secret')).toThrow(DeepLinkError);
    });

    it('rejects an auth param on parse (case-insensitive)', () => {
      expect(() => parseDeepLink('/s/viewer?object=x&AUTH=secret')).toThrow(DeepLinkError);
    });

    it('rejects a credential smuggled through extra on build', () => {
      expect(() =>
        buildDeepLink({ kind: 'viewer', object: 'x', extra: { token: 'secret' } }),
      ).toThrow(DeepLinkError);
    });
  });

  describe('routing', () => {
    it('rejects an unknown path', () => {
      expect(() => parseDeepLink('/x/nope?a=1')).toThrow(DeepLinkError);
    });

    it('accepts a full absolute URL, not only a path', () => {
      const parsed = parseDeepLink('https://studio.example.com/s/viewer?object=sales.Order');
      expect(parsed).toEqual({ kind: 'viewer', object: 'sales.Order' });
    });
  });
});
