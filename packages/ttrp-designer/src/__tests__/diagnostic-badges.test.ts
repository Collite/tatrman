import { describe, it, expect } from 'vitest';
import hero from './fixtures/hero-getGraph.json';
import type { GetGraphResult } from '../graph/types.js';
import type { PublishedDiagnostic } from '../lsp/ws-client.js';
import { aggregateToContainers, badgeNodes } from '../graph/diagnostics-map.js';

const result = hero as unknown as GetGraphResult;

function diagAt(line: number, message: string, severity = 1): PublishedDiagnostic {
  return { range: { start: { line, character: 0 }, end: { line, character: 5 } }, severity, message };
}

/** Diagnostics → node badges at both levels (T5.4.7). */
describe('diagnostic badges', () => {
  it('maps a diagnostic on a crunch member to a per-node badge', () => {
    // crunch/sales#1 (Load) is on line ~20 (0-based range in the fixture).
    const load = result.graph.containers.find((c) => c.path === 'crunch')!.nodes.find((n) => n.zeta === 'crunch/sales#1')!;
    const line = load.range!.line;
    const badges = badgeNodes(result, [diagAt(line, 'TTRP-EQ-001 use =')]);
    expect(badges.get('crunch/sales#1')?.count).toBe(1);
    expect(badges.get('crunch/sales#1')?.maxSeverity).toBe(1);
  });

  it('aggregates node badges to a per-container badge for the orchestration view', () => {
    const crunch = result.graph.containers.find((c) => c.path === 'crunch')!;
    const lines = crunch.nodes.filter((n) => n.range).map((n) => n.range!.line);
    const diags = lines.map((l, i) => diagAt(l, `err${i}`, i === 0 ? 1 : 2));
    const nodeBadges = badgeNodes(result, diags);
    const containerBadges = aggregateToContainers(result, nodeBadges);
    const badge = containerBadges.get('crunch')!;
    expect(badge.count).toBeGreaterThanOrEqual(2);
    expect(badge.maxSeverity).toBe(1); // an Error dominates the aggregate severity
  });

  it('a diagnostic outside any node range produces no badge', () => {
    const badges = badgeNodes(result, [diagAt(9999, 'somewhere else')]);
    expect(badges.size).toBe(0);
  });
});
