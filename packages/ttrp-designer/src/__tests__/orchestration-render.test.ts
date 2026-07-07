import { describe, it, expect } from 'vitest';
import hero from './fixtures/hero-getGraph.json';
import type { GetGraphResult } from '../graph/types.js';
import { deriveOrchestration, deriveContainer } from '../graph/derive-orchestration.js';
import { renderCanvas } from '../cy/adapter.js';
import { alteryxKnime } from '../skins/alteryx-knime.js';

const result = hero as unknown as GetGraphResult;

/**
 * Orchestration render (T5.3.2): the top-level canvas shows collapsed containers +
 * program leaves + the synthesized transfer edge, and NO inner op nodes (C1-a β).
 */
describe('orchestration render', () => {
  it('shows both containers + program leaves, no inner op nodes', () => {
    const { nodes } = deriveOrchestration(result);
    const containerZetas = nodes.filter((n) => n.isContainer).map((n) => n.zeta);
    expect(containerZetas).toContain('acc_prep');
    expect(containerZetas).toContain('crunch');
    // Inner op nodes (crunch/sales#1, crunch/j#1, …) must NOT appear at the top level.
    expect(nodes.some((n) => n.zeta.includes('/'))).toBe(false);
    // The synthesized transfer is a program leaf.
    expect(nodes.some((n) => n.kind === 'Transfer' && n.synthesized)).toBe(true);
    // Displays/stores present.
    expect(nodes.some((n) => n.kind === 'Display')).toBe(true);
  });

  it('cross-container edge carries its synthesized transfer via id', () => {
    const { edges } = deriveOrchestration(result);
    const crossing = edges.find((e) => e.from === 'acc_prep' && e.to === 'crunch');
    expect(crossing).toBeDefined();
    expect(crossing!.via).toBe('x0~transfer');
  });

  it('mounts a headless Cytoscape core with the orchestration elements', () => {
    const cy = renderCanvas({
      elements: deriveOrchestration(result),
      skin: alteryxKnime,
      autoLayout: result.autoLayout.program,
    });
    // 2 containers + 4 program leaves = 6 nodes.
    expect(cy.nodes().length).toBe(6);
    expect(cy.edges().length).toBe(4);
    cy.destroy();
  });
});

/** Drill-in render (T5.3.2): entering `crunch` shows its 5 op nodes incl. the Branch. */
describe('drill-in render', () => {
  it('crunch drill-in shows the authored op nodes (Branch, not the polars lowering)', () => {
    const { nodes } = deriveContainer(result, 'crunch');
    const kinds = nodes.map((n) => n.kind);
    expect(nodes.map((n) => n.zeta)).toContain('crunch/sales#1');
    expect(kinds).toContain('Branch'); // authored surface — not branch→filter
    expect(kinds).toContain('Join');
    expect(kinds).toContain('Aggregate');
    // All members carry the container path.
    expect(nodes.every((n) => n.containerPath === 'crunch')).toBe(true);
  });
});
