import type { Document, ImportDecl } from '@modeler/parser';
import { ProjectSymbolTable } from './project-symbols.js';
import { packageOfImport } from './references.js';

export interface PackageNode {
  name: string;
  documentUris: string[];
}

export interface PackageEdge {
  from: string;
  to: string;
  citedBy: string[];
}

export interface PackageGraph {
  nodes: PackageNode[];
  edges: PackageEdge[];
}

export function findCyclesOn(graph: PackageGraph): string[][] {
  const packages = graph.nodes.map((n) => n.name);
  const indexByPkg = new Map<string, number>();
  for (let i = 0; i < packages.length; i++) indexByPkg.set(packages[i], i);
  const n = packages.length;

  const adjacency: number[][] = Array.from({ length: n }, () => []);
  for (const edge of graph.edges) {
    const fromIdx = indexByPkg.get(edge.from);
    const toIdx = indexByPkg.get(edge.to);
    if (fromIdx !== undefined && toIdx !== undefined) {
      adjacency[fromIdx].push(toIdx);
    }
  }

  let idx = 0;
  const stack: number[] = [];
  const onStack = new Set<number>();
  const tarjanNodes: TarjanNode[] = Array.from({ length: n }, () => ({
    index: -1,
    lowlink: 0,
    onStack: false,
  }));

  function strongConnect(v: number, sccs: number[][]): void {
    tarjanNodes[v].index = idx;
    tarjanNodes[v].lowlink = idx;
    idx++;
    stack.push(v);
    onStack.add(v);

    for (const w of adjacency[v]) {
      if (tarjanNodes[w].index === -1) {
        strongConnect(w, sccs);
        tarjanNodes[v].lowlink = Math.min(tarjanNodes[v].lowlink, tarjanNodes[w].lowlink);
      } else if (onStack.has(w)) {
        tarjanNodes[v].lowlink = Math.min(tarjanNodes[v].lowlink, tarjanNodes[w].index);
      }
    }

    if (tarjanNodes[v].lowlink === tarjanNodes[v].index) {
      const scc: number[] = [];
      let w: number;
      do {
        w = stack.pop()!;
        onStack.delete(w);
        scc.push(w);
      } while (w !== v);
      sccs.push(scc);
    }
  }

  const sccs: number[][] = [];
  for (let v = 0; v < n; v++) {
    if (tarjanNodes[v].index === -1) strongConnect(v, sccs);
  }

  return sccs
    .filter((scc) => scc.length > 1)
    .map((scc) => scc.map((i) => packages[i]));
}

interface TarjanNode {
  index: number;
  lowlink: number;
  onStack: boolean;
}

export class PackageGraphBuilder {
  constructor(
    private projectSymbols: ProjectSymbolTable,
    private documents: Map<string, Document>
  ) {}

  build(): PackageGraph {
    const packageToUris = new Map<string, Set<string>>();
    const allUris = new Set<string>();

    for (const entry of this.projectSymbols.all()) {
      const pkg = entry.packageName;
      const uri = entry.documentUri;
      allUris.add(uri);
      if (!packageToUris.has(pkg)) packageToUris.set(pkg, new Set());
      packageToUris.get(pkg)!.add(uri);
    }

    const nodes: PackageNode[] = [];
    for (const [name, uris] of packageToUris) {
      nodes.push({ name, documentUris: Array.from(uris) });
    }

    const edgeMap = new Map<string, Map<string, Set<string>>>();

    for (const uri of allUris) {
      const doc = this.documents.get(uri);
      if (!doc) continue;
      const srcPackage = this.packageOf(uri);
      if (!srcPackage) continue;
      for (const imp of doc.imports ?? []) {
        const tgtPackage = this.resolvePackageOfImport(imp);
        if (!tgtPackage || tgtPackage === srcPackage) continue;
        if (!edgeMap.has(srcPackage)) edgeMap.set(srcPackage, new Map());
        if (!edgeMap.get(srcPackage)!.has(tgtPackage)) {
          edgeMap.get(srcPackage)!.set(tgtPackage, new Set());
        }
        edgeMap.get(srcPackage)!.get(tgtPackage)!.add(uri);
      }
    }

    const edges: PackageEdge[] = [];
    for (const [from, targets] of edgeMap) {
      for (const [to, citedBySet] of targets) {
        edges.push({ from, to, citedBy: Array.from(citedBySet) });
      }
    }

    return { nodes, edges };
  }

  findCycles(): string[][] {
    const packages = new Set<string>();
    for (const entry of this.projectSymbols.all()) {
      packages.add(entry.packageName);
    }
    const pkgArray = Array.from(packages);
    const indexByPkg = new Map<string, number>();
    for (let i = 0; i < pkgArray.length; i++) indexByPkg.set(pkgArray[i], i);
    const n = pkgArray.length;

    const adjacency: number[][] = Array.from({ length: n }, () => []);
    for (const edge of this.build().edges) {
      const fromIdx = indexByPkg.get(edge.from);
      const toIdx = indexByPkg.get(edge.to);
      if (fromIdx !== undefined && toIdx !== undefined) {
        adjacency[fromIdx].push(toIdx);
      }
    }

    let idx = 0;
    const stack: number[] = [];
    const onStack = new Set<number>();
    const tarjanNodes: TarjanNode[] = Array.from({ length: n }, () => ({
      index: -1,
      lowlink: 0,
      onStack: false,
    }));

    function strongConnect(v: number, sccs: number[][]): void {
      tarjanNodes[v].index = idx;
      tarjanNodes[v].lowlink = idx;
      idx++;
      stack.push(v);
      onStack.add(v);

      for (const w of adjacency[v]) {
        if (tarjanNodes[w].index === -1) {
          strongConnect(w, sccs);
          tarjanNodes[v].lowlink = Math.min(tarjanNodes[v].lowlink, tarjanNodes[w].lowlink);
        } else if (onStack.has(w)) {
          tarjanNodes[v].lowlink = Math.min(tarjanNodes[v].lowlink, tarjanNodes[w].index);
        }
      }

      if (tarjanNodes[v].lowlink === tarjanNodes[v].index) {
        const scc: number[] = [];
        let w: number;
        do {
          w = stack.pop()!;
          onStack.delete(w);
          scc.push(w);
        } while (w !== v);
        sccs.push(scc);
      }
    }

    const sccs: number[][] = [];
    for (let v = 0; v < n; v++) {
      if (tarjanNodes[v].index === -1) strongConnect(v, sccs);
    }

    return sccs
      .filter((scc) => scc.length > 1)
      .map((scc) => scc.map((i) => pkgArray[i]));
  }

  getDependents(pkg: string): string[] {
    const graph = this.build();
    const dependentSet = new Set<string>();
    const queue: string[] = [pkg];
    while (queue.length > 0) {
      const current = queue.shift()!;
      for (const edge of graph.edges) {
        if (edge.to === current && !dependentSet.has(edge.from)) {
          dependentSet.add(edge.from);
          queue.push(edge.from);
        }
      }
    }
    return Array.from(dependentSet);
  }

  getDependencies(pkg: string): string[] {
    const graph = this.build();
    const dependencySet = new Set<string>();
    const queue: string[] = [pkg];
    while (queue.length > 0) {
      const current = queue.shift()!;
      for (const edge of graph.edges) {
        if (edge.from === current && !dependencySet.has(edge.to)) {
          dependencySet.add(edge.to);
          queue.push(edge.to);
        }
      }
    }
    return Array.from(dependencySet);
  }

  private packageOf(uri: string): string | undefined {
    for (const entry of this.projectSymbols.all()) {
      if (entry.documentUri === uri) return entry.packageName;
    }
    return undefined;
  }

  private resolvePackageOfImport(imp: ImportDecl): string | undefined {
    return packageOfImport(imp) || undefined;
  }
}