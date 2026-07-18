import { describe, it, expect } from 'vitest';
import { parseString } from '@tatrman/parser';
import { buildModelGraph } from '../model-graph.js';

// DS-P3.S1.T1 — md (multidimensional) extraction. The cube renders as ONE node carrying its
// measures as rows; each dimension is a node carrying its hierarchy's ordered level-stack;
// the cube's grain yields cube→dimension edges. Grammar-truth only — the design's derived
// measure `margin_pct` is NOT expressible (MD-GAPS) and is fixture-filled on the designer
// side, never invented here; this suite asserts its absence.
const MD = `
package orders_hero
model md

def domain Money { type: decimal }
def domain Qty   { type: int }
def domain Region { type: string }
def domain Code   { type: string }
def domain Day    { type: date }
def domain Month  { type: int, kind: calc }
def domain Year   { type: int, kind: calc }

def dimension Customer { key: code, attributes: [
  def attribute code { domain: md.Code, isKey: true },
  def attribute region { domain: md.Region }
], hierarchies: [geo] }

def dimension Time { key: day, attributes: [
  def attribute day { domain: md.Day },
  def attribute month { domain: md.Month },
  def attribute year { domain: md.Year }
], hierarchies: [calendar] }

def map day_to_month { from: md.Day, to: md.Month, calc: monthOfDate }

def hierarchy geo { dimension: md.Customer, levels: [region, code] }
def hierarchy calendar { dimension: md.Time, levels: [day, month via md.day_to_month, year] }

def measure qty { domain: md.Qty, class: additive, aggregation: sum }
def measure net_amount { domain: md.Money, class: additive, aggregation: sum }

def cubelet Sales { grain: [Customer.code, Time.day], measures: [qty, net_amount] }
`;

function graph() {
  const parsed = parseString(MD, 'file:///md.ttrm');
  expect(parsed.errors.filter((e) => e.severity === 'error'), 'md fixture parses clean').toEqual([]);
  return buildModelGraph(parsed.ast!, 'md');
}

describe('buildModelGraph (md schema)', () => {
  it('reports schemaCode md', () => {
    expect(graph().schemaCode).toBe('md');
  });

  it('the cubelet is one node carrying its measures as rows (measures live IN the cube)', () => {
    const g = graph();
    const cube = g.nodes.find((n) => n.name === 'Sales');
    expect(cube, 'cube node present').toBeTruthy();
    expect(cube!.kind).toBe('cubelet');
    expect(cube!.rows.map((r) => r.name)).toEqual(['qty', 'net_amount']);
    expect(cube!.rows.every((r) => r.kind === 'measure')).toBe(true);
    // the derived measure margin_pct is a grammar gap — never fabricated by the LSP
    expect(cube!.rows.map((r) => r.name)).not.toContain('margin_pct');
  });

  it('each dimension is a node carrying its hierarchy level-stack in order', () => {
    const g = graph();
    const dims = g.nodes.filter((n) => n.kind === 'dimension').map((n) => n.name).sort();
    expect(dims).toEqual(['Customer', 'Time']);

    const customer = g.nodes.find((n) => n.name === 'Customer')!;
    expect(customer.rows.map((r) => r.name)).toEqual(['region', 'code']); // geo: coarse→fine
    expect(customer.rows.every((r) => r.kind === 'level')).toBe(true);

    const time = g.nodes.find((n) => n.name === 'Time')!;
    expect(time.rows.map((r) => r.name)).toEqual(['day', 'month', 'year']); // calendar order preserved
    // a calc-driven level carries its `via` map ref as the row type
    expect(time.rows.find((r) => r.name === 'month')!.type).toBe('md.day_to_month');
  });

  it('the cube grain yields one cube→dimension edge per distinct dimension', () => {
    const g = graph();
    const cube = g.nodes.find((n) => n.name === 'Sales')!;
    const grainEdges = g.edges.filter((e) => e.kind === 'grain');
    expect(grainEdges).toHaveLength(2);
    for (const e of grainEdges) expect(e.fromNode).toBe(cube.qname);
    const toNames = grainEdges.map((e) => g.nodes.find((n) => n.qname === e.toNode)!.name).sort();
    expect(toNames).toEqual(['Customer', 'Time']);
  });

  it('measures and dimensions carry accurate source locations (edit-synth invariant)', () => {
    const g = graph();
    for (const n of g.nodes) {
      expect(n.sourceUri).toBe('file:///md.ttrm');
      expect(n.sourceLocation.line).toBeGreaterThan(0);
    }
  });
});
