import { describe, it, expect } from 'vitest';
import { parseString } from '../index.js';

describe('ast v1.1 — packageDecl / importDecl / graphBlock', () => {
  it('package billing.invoicing → packageDecl with correct name, parts, and source', () => {
    const result = parseString(
      'package billing.invoicing\n' +
      'model er schema entity\n' +
      'def entity X {}\n'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast?.packageDecl).toBeDefined();
    const pkg = result.ast!.packageDecl!;
    expect(pkg.kind).toBe('packageDecl');
    expect(pkg.name).toBe('billing.invoicing');
    expect(pkg.parts).toEqual(['billing', 'invoicing']);
    expect(pkg.source.line).toBe(1);
    expect(pkg.source.endLine).toBe(1);
  });

  it('no package line → packageDecl undefined and imports empty', () => {
    const result = parseString('model er\ndef entity X {}');
    expect(result.errors).toEqual([]);
    expect(result.ast?.packageDecl).toBeUndefined();
    expect(result.ast?.imports).toEqual([]);
  });

  it('import x.y.* → imports[0] with wildcard: true', () => {
    const result = parseString(
      'package a.b\n' +
      'import x.y.*\n' +
      'model er schema entity\n'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast?.imports).toHaveLength(1);
    const imp = result.ast!.imports[0]!;
    expect(imp.kind).toBe('importDecl');
    expect(imp.target).toBe('x.y');
    expect(imp.targetParts).toEqual(['x', 'y']);
    expect(imp.wildcard).toBe(true);
  });

  it('import p.q.r.S → imports[0] with wildcard: false', () => {
    const result = parseString(
      'package a.b\n' +
      'import p.q.r.S\n' +
      'model er schema entity\n'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast?.imports).toHaveLength(1);
    const imp = result.ast!.imports[0]!;
    expect(imp.kind).toBe('importDecl');
    expect(imp.target).toBe('p.q.r.S');
    expect(imp.targetParts).toEqual(['p', 'q', 'r', 'S']);
    expect(imp.wildcard).toBe(false);
  });

  it('graph view { model: er, objects: [...] } → graph populated, definitions empty, no schemaDirective', () => {
    const result = parseString(
      'package a.b\n' +
      'graph artikl_overview { model: er, objects: [a.b.er.entity.X] }\n'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast?.graph).toBeDefined();
    const graph = result.ast!.graph!;
    expect(graph.kind).toBe('graphBlock');
    expect(graph.name).toBe('artikl_overview');
    expect(graph.schema).toBe('er');
    expect(graph.objects).toEqual(['a.b.er.entity.X']);
    expect(graph.source.line).toBe(2);
    expect(graph.source.endLine).toBe(2);
    expect(result.ast?.definitions).toHaveLength(0);
    expect(result.ast?.schemaDirective).toBeUndefined();
  });

  it('source-location accuracy — packageDecl line/endLine', () => {
    const result = parseString(
      'package billing.invoicing\n' +
      'model er schema entity\n'
    );
    const pkg = result.ast!.packageDecl!;
    const src = pkg.source;
    expect(src.line).toBe(1);
    expect(src.endLine).toBe(1);
    expect(src.column).toBe(0);
    expect(src.endColumn).toBe('package billing.invoicing'.length);
  });

  it('source-location accuracy — importDecl line/endLine', () => {
    const result = parseString(
      'package a\n' +
      'import x.y.*\n'
    );
    const src = result.ast!.imports[0].source;
    expect(src.line).toBe(2);
    expect(src.endLine).toBe(2);
  });

  it('source-location accuracy — graphBlock line/endLine', () => {
    const result = parseString(
      'graph artikl_overview {\n' +
      '  model: er,\n' +
      '  objects: [a.b.er.entity.X]\n' +
      '}\n'
    );
    const src = result.ast!.graph!.source;
    expect(src.line).toBe(1);
    expect(src.endLine).toBe(4);
  });

  it('wrong-file-kind: graph + definitions → ttr/wrong-file-kind error', () => {
    const result = parseString(
      'graph my_view { model: er, objects: [er.entity.X] }\n' +
      'def entity Y {}'
    );
    const wrongFileKind = result.errors.find((e) => e.code === 'ttr/wrong-file-kind');
    expect(wrongFileKind).toBeDefined();
    expect(wrongFileKind!.severity).toBe('error');
  });

  it('graph with layout → layout node positions parsed', () => {
    const result = parseString(
      'package a\n' +
      'graph v { model: er, objects: [a.er.entity.X], layout: {\n' +
      '  nodes: { a_er_entity_X: { x: 100, y: 200 } }\n' +
      '} }'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast!.graph!.layout).toBeDefined();
    expect(result.ast!.graph!.layout!.nodes['a_er_entity_X']).toEqual({ x: 100, y: 200 });
  });

  it('graph with viewport → viewport fields parsed', () => {
    const result = parseString(
      'package a\n' +
      'graph v { model: er, objects: [], layout: {\n' +
      '  viewport: { zoom: 1.5, panX: 10, panY: 20, displayMode: withTypes }\n' +
      '} }'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast!.graph!.layout!.viewport).toBeDefined();
    expect(result.ast!.graph!.layout!.viewport!.zoom).toBe(1.5);
    expect(result.ast!.graph!.layout!.viewport!.panX).toBe(10);
    expect(result.ast!.graph!.layout!.viewport!.panY).toBe(20);
    expect(result.ast!.graph!.layout!.viewport!.displayMode).toBe('withTypes');
  });

  it('viewport displayMode is accepted as any identifier (validation deferred to semantics)', () => {
    const result = parseString(
      'package a\n' +
      'graph v { model: er, objects: [], layout: {\n' +
      '  viewport: { zoom: 1.0, panX: 0, panY: 0, displayMode: nonsense_value }\n' +
      '} }'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast!.graph!.layout!.viewport!.displayMode).toBe('nonsense_value');
  });

  it('graph with edges → bendPoints parsed as [number, number][]', () => {
    const result = parseString(
      'package a\n' +
      'graph v { model: er, objects: [a.er.entity.X, a.er.entity.Y], layout: {\n' +
      '  nodes: { a_er_entity_X: { x: 0, y: 0 }, a_er_entity_Y: { x: 100, y: 100 } },\n' +
      '  edges: { rel_1: { bendPoints: [[10, 20], [30, 40]] } }\n' +
      '} }'
    );
    expect(result.errors).toEqual([]);
    const edges = result.ast!.graph!.layout!.edges;
    expect(edges['rel_1']).toEqual({ bendPoints: [[10, 20], [30, 40]] });
  });

  it('graph with edges but no bendPoints → entry present, bendPoints undefined', () => {
    const result = parseString(
      'package a\n' +
      'graph v { model: er, objects: [a.er.entity.X], layout: {\n' +
      '  edges: { rel_2: {} }\n' +
      '} }'
    );
    expect(result.errors).toEqual([]);
    const edges = result.ast!.graph!.layout!.edges;
    expect(edges['rel_2']).toEqual({});
  });

  it('graph description with escape sequence is unescaped', () => {
    const result = parseString(
      'package a\n' +
      'graph v { model: er, description: "say \\"hi\\"", objects: [a.er.entity.X] }'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast!.graph!.description).toBe('say "hi"');
  });

  it('graph description with triple-quoted string preserves content', () => {
    const result = parseString(
      'package a\n' +
      'graph v {\n' +
      '  model: er,\n' +
      '  description: """\nmulti\nline""",\n' +
      '  objects: [a.er.entity.X]\n' +
      '}'
    );
    expect(result.errors).toEqual([]);
    expect(result.ast!.graph!.description).toBe('multi\nline');
  });
});