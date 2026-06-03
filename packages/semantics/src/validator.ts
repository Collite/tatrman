import type { SourceLocation, Document, SearchBlock, Definition } from '@modeler/parser';
import { DiagnosticCode } from '@modeler/parser';
import type { ResolvedManifest } from './manifest.js';
import { Resolver } from './resolver.js';
import { ProjectSymbolTable } from './project-symbols.js';
import { collectAllReferences, packageOfImport } from './references.js';
import type { PackageGraph } from './package-graph.js';
import { findCyclesOn } from './package-graph.js';
import { enclosingQnameOf } from './reference-index.js';
import { inferPackageFromUri } from './package-inference.js';

export interface ValidationDiagnostic {
  code: string;
  severity: 'error' | 'warning' | 'info';
  message: string;
  source: SourceLocation;
}

function hasSearch(def: Definition): def is Definition & { search?: SearchBlock } {
  return 'search' in def && def.kind !== 'column' && def.kind !== 'attribute';
}

function* searchBlocksOf(def: Definition): Iterable<SearchBlock> {
  if (hasSearch(def) && def.search) yield def.search;
  if (def.kind === 'table' || def.kind === 'view') {
    for (const m of def.columns ?? []) if (m.search) yield m.search;
  }
  if (def.kind === 'entity') {
    for (const m of def.attributes ?? []) if (m.search) yield m.search;
  }
}

export class Validator {
  constructor(
    private symbols: ProjectSymbolTable,
    private resolver: Resolver,
    private manifest: ResolvedManifest
  ) {}

  validateDocument(_uri: string, ast: Document): ValidationDiagnostic[] {
    const diagnostics: ValidationDiagnostic[] = [];

    for (const def of ast.definitions) {
      if (def.kind === 'entity') {
        if (!def.attributes || def.attributes.length === 0) {
          diagnostics.push({
            code: DiagnosticCode.RequiredPropertyMissing,
            severity: 'error',
            message: 'Entity must have at least one attribute',
            source: def.source,
          });
        }

        if (def.nameAttribute && def.attributes) {
          const last = def.nameAttribute.parts[def.nameAttribute.parts.length - 1];
          const exists = def.attributes.some((a) => a.name === last);
          if (!exists) {
            diagnostics.push({
              code: DiagnosticCode.EntityAttributeNotFound,
              severity: 'error',
              message: `nameAttribute '${def.nameAttribute.path}' not found on entity '${def.name}'`,
              source: def.nameAttribute.source,
            });
          }
        }

        if (def.codeAttribute && def.attributes) {
          const last = def.codeAttribute.parts[def.codeAttribute.parts.length - 1];
          const exists = def.attributes.some((a) => a.name === last);
          if (!exists) {
            diagnostics.push({
              code: DiagnosticCode.EntityAttributeNotFound,
              severity: 'error',
              message: `codeAttribute '${def.codeAttribute.path}' not found on entity '${def.name}'`,
              source: def.codeAttribute.source,
            });
          }
        }
      }

      if (def.kind === 'table') {
        if (!def.columns || def.columns.length === 0) {
          diagnostics.push({
            code: DiagnosticCode.RequiredPropertyMissing,
            severity: 'error',
            message: 'Table must have at least one column',
            source: def.source,
          });
        }

        if (def.primaryKey) {
          for (const pkCol of def.primaryKey) {
            const exists = def.columns?.some((c) => c.name === pkCol);
            if (!exists) {
              diagnostics.push({
                code: DiagnosticCode.PrimaryKeyColumnNotFound,
                severity: 'error',
                message: `Primary key column '${pkCol}' not found on table '${def.name}'`,
                source: def.source,
              });
            }
          }
        }
      }

      if (def.kind === 'column' && !def.type) {
        diagnostics.push({
          code: DiagnosticCode.RequiredPropertyMissing,
          severity: 'error',
          message: 'Column must have a type',
          source: def.source,
        });
      }

      if (def.kind === 'attribute' && !def.type) {
        diagnostics.push({
          code: DiagnosticCode.RequiredPropertyMissing,
          severity: 'error',
          message: 'Attribute must have a type',
          source: def.source,
        });
      }

      if (this.manifest.lint.requireDescriptions && !('description' in def && def.description)) {
        diagnostics.push({
          code: DiagnosticCode.RequiredPropertyMissing,
          severity: 'warning',
          message: 'Definition should have a description',
          source: def.source,
        });
      }

      for (const sb of searchBlocksOf(def)) {
        if (sb.fuzzy === true && sb.searchable !== true) {
          diagnostics.push({
            code: DiagnosticCode.FuzzyWithoutSearchable,
            severity: 'warning',
            message: 'fuzzy search is enabled but the element is not marked searchable; set searchable: true',
            source: sb.source,
          });
        }
        for (const dup of sb.duplicateProperties ?? []) {
          diagnostics.push({
            code: DiagnosticCode.DuplicateSearchProperty,
            severity: 'error',
            message: `Duplicate '${dup}' in search block`,
            source: sb.source,
          });
        }
      }
    }

    return diagnostics;
  }

  validateReferences(_uri: string, ast: Document): ValidationDiagnostic[] {
    const diagnostics: ValidationDiagnostic[] = [];
    const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
    const namespace = ast.schemaDirective?.namespace ?? '';
    const packageName = ast.packageDecl?.name ?? '';

    for (const { ref, ownerDef } of collectAllReferences(ast)) {
      const enclosingQname = enclosingQnameOf(ownerDef, schemaCode, namespace, packageName);
      const res = this.resolver.resolveReference(
        { path: ref.path, parts: ref.parts },
        { schemaCode, namespace, enclosingQname, imports: ast.imports, packageName }
      );

      if (!res.resolved) {
        if (res.reason === 'ambiguous') {
          const loc = res.candidates?.[0]?.source ?? ref.source;
          diagnostics.push({
            code: DiagnosticCode.AmbiguousReference,
            severity: 'error',
            message: `Ambiguous reference: '${ref.path}' matches ${res.candidates?.length ?? 0} definitions via wildcard imports`,
            source: loc,
          });
        } else {
          diagnostics.push({
            code: DiagnosticCode.UnresolvedReference,
            severity: this.manifest.lint.strict ? 'error' : 'warning',
            message: `Unresolved reference: '${ref.path}' (tried ${res.tried.map((a) => a.candidate).join(', ')})`,
            source: ref.source,
          });
        }
      } else if (res.viaStep === 'fully-qualified' && packageName) {
        const resolvedPackage = res.symbol.packageName;
        if (resolvedPackage && resolvedPackage !== packageName) {
          const importedPkgs = new Set(
            (ast.imports ?? []).map((imp) => packageOfImport(imp))
          );
          if (!importedPkgs.has(resolvedPackage)) {
            diagnostics.push({
              code: DiagnosticCode.UnimportedReference,
              severity: 'info',
              message: `Reference to '${res.symbol.qname}' resolves via package search; consider adding an import`,
              source: ref.source,
            });
          }
        }
      }
    }

    return diagnostics;
  }

  validateProject(): ValidationDiagnostic[] {
    const diagnostics: ValidationDiagnostic[] = [];

    for (const dup of this.symbols.duplicates()) {
      const hasInline = dup.entries.some((e) => e.mappingSource === 'inline');
      if (hasInline) {
        const isEr2db =
          dup.entries[0].kind === 'er2dbEntity' ||
          dup.entries[0].kind === 'er2dbAttribute' ||
          dup.entries[0].kind === 'er2dbRelation';
        if (isEr2db) continue;
      }
      for (const entry of dup.entries) {
        const others = dup.entries
          .filter((e) => !(e.documentUri === entry.documentUri && e.source.line === entry.source.line))
          .map((e) => `${e.documentUri}:${e.source.line}`)
          .join(', ');
        diagnostics.push({
          code: DiagnosticCode.DuplicateDefinition,
          severity: 'error',
          message: `Duplicate definition of '${dup.qname}' (also at ${others})`,
          source: entry.source,
        });
      }
    }

    diagnostics.push(...this.validateDuplicateMappings());

    return diagnostics;
  }

  private validateDuplicateMappings(): ValidationDiagnostic[] {
    const diagnostics: ValidationDiagnostic[] = [];

    for (const qname of this.symbols.allQnames()) {
      const entries = this.symbols.getAll(qname);
      if (entries.length < 2) continue;
      const firstKind = entries[0].kind;
      const isEr2db =
        firstKind === 'er2dbEntity' ||
        firstKind === 'er2dbAttribute' ||
        firstKind === 'er2dbRelation';
      if (!isEr2db) continue;
      const sources = new Set(entries.map((e) => e.mappingSource ?? 'explicit'));
      if (!sources.has('inline')) continue;
      for (const e of entries) {
        const others = entries
          .filter((other) => !(other.documentUri === e.documentUri && other.source.line === e.source.line))
          .map((o) => `${o.documentUri}:${o.source.line}`)
          .join(', ');
        diagnostics.push({
          code: DiagnosticCode.DuplicateMapping,
          severity: 'error',
          message: `Duplicate mapping for "${qname}" — declared in ${entries.length} places: ${others}`,
          source: e.source,
        });
      }
    }

    return diagnostics;
  }

  validateImports(uri: string, ast: Document): ValidationDiagnostic[] {
    const diagnostics: ValidationDiagnostic[] = [];
    const imports = ast.imports ?? [];
    const seenTargets = new Set<string>();
    const wildcardTargets = new Set<string>();

    for (const imp of imports) {
      if (imp.wildcard) {
        wildcardTargets.add(imp.target);
        const defs = this.symbols.getByPackage(imp.target);
        if (defs.length === 0) {
          diagnostics.push({
            code: DiagnosticCode.WildcardWithNoMatches,
            severity: 'warning',
            message: `Wildcard import '${imp.target}.*' has no matching definitions`,
            source: imp.source,
          });
        }
      }

      if (seenTargets.has(imp.target)) {
        diagnostics.push({
          code: DiagnosticCode.DuplicateImport,
          severity: 'warning',
          message: `Duplicate import of '${imp.target}'`,
          source: imp.source,
        });
      } else {
        seenTargets.add(imp.target);
      }
    }

    const usedTargets = new Set<string>();
    const schemaCode = ast.schemaDirective?.schemaCode ?? 'db';
    const namespace = ast.schemaDirective?.namespace ?? '';
    const packageName = ast.packageDecl?.name ?? '';

    for (const { ref } of collectAllReferences(ast)) {
      const res = this.resolver.resolveReference(
        { path: ref.path, parts: ref.parts },
        { schemaCode, namespace, imports, packageName }
      );
      if (res.resolved && res.viaStep === 'named-import') {
        const lastDot = res.symbol.qname.lastIndexOf('.');
        const prefix = res.symbol.qname.slice(0, lastDot);
        usedTargets.add(prefix);
      }
      if (res.resolved && res.viaStep === 'wildcard-import') {
        const lastDot = res.symbol.qname.lastIndexOf('.');
        const prefix = res.symbol.qname.slice(0, lastDot);
        usedTargets.add(prefix);
      }
    }

    for (const imp of imports) {
      if (!imp.wildcard) {
        const pkg = packageOfImport(imp);
        if (pkg && !usedTargets.has(pkg)) {
          diagnostics.push({
            code: DiagnosticCode.UnusedImport,
            severity: 'warning',
            message: `Import '${imp.target}' is not referenced`,
            source: imp.source,
          });
        }
      }
    }

    return diagnostics;
  }

  validateFileOrdering(_uri: string, ast: Document): ValidationDiagnostic[] {
    const diagnostics: ValidationDiagnostic[] = [];
    const schemaLine = ast.schemaDirective?.source?.line ?? Infinity;
    const graphLine = ast.graph?.source?.line ?? Infinity;
    const pkgLine = ast.packageDecl?.source?.line ?? Infinity;
    const firstImportLine = ast.imports?.[0]?.source?.line ?? Infinity;
    const firstDefLine = ast.definitions?.[0]?.source?.line ?? Infinity;

    const pushOrdering = (msg: string, src: SourceLocation) =>
      diagnostics.push({ code: DiagnosticCode.FileOrdering, severity: 'warning', message: msg, source: src });

    if (pkgLine !== Infinity && firstImportLine !== Infinity && pkgLine > firstImportLine) {
      pushOrdering('package declaration must appear before import declarations', ast.imports[0].source);
    }
    if (firstImportLine !== Infinity && schemaLine !== Infinity && firstImportLine > schemaLine) {
      pushOrdering('import declarations must appear before schema directive', ast.schemaDirective!.source);
    }
    if (firstImportLine !== Infinity && graphLine !== Infinity && firstImportLine > graphLine) {
      pushOrdering('import declarations must appear before graph block', ast.graph!.source);
    }
    if (schemaLine !== Infinity && firstDefLine !== Infinity && schemaLine > firstDefLine) {
      pushOrdering('schema directive must appear before definitions', ast.definitions[0].source);
    }
    if (graphLine !== Infinity && firstDefLine !== Infinity && graphLine > firstDefLine) {
      pushOrdering('graph block must appear before definitions', ast.definitions[0].source);
    }

    return diagnostics;
  }

  validateTtrgGraph(_uri: string, ast: Document): ValidationDiagnostic[] {
    const diagnostics: ValidationDiagnostic[] = [];
    const graph = ast.graph;
    if (!graph) return diagnostics;

    const fileName = _uri.replace(/^.*[\/\\]/, '').replace(/\.ttrg$/, '');

    if (!graph.schema) {
      diagnostics.push({
        code: DiagnosticCode.RequiredPropertyMissing,
        severity: 'error',
        message: "graph requires a 'schema' property (e.g. schema: er)",
        source: graph.source,
      });
    }

    if (graph.objects && graph.objects.length === 0) {
      diagnostics.push({
        code: DiagnosticCode.GraphObjectsEmpty,
        severity: 'warning',
        message: 'Graph objects list is empty; the graph will render nothing',
        source: graph.source,
      });
    }

    if (graph.name && graph.name !== fileName) {
      diagnostics.push({
        code: DiagnosticCode.GraphNameMismatch,
        severity: 'warning',
        message: `Graph name '${graph.name}' does not match file name '${fileName}'`,
        source: graph.source,
      });
    }

    if (graph.objects) {
      for (const qname of graph.objects) {
        if (!this.symbols.get(qname)) {
          diagnostics.push({
            code: DiagnosticCode.GraphObjectNotFound,
            severity: 'warning',
            message: `Graph object '${qname}' not found in project`,
            source: graph.source,
          });
        }
      }
    }

    if (graph.layout?.nodes) {
      const objectQnames = new Set(graph.objects ?? []);
      for (const qname of Object.keys(graph.layout.nodes)) {
        if (!objectQnames.has(qname)) {
          diagnostics.push({
            code: DiagnosticCode.GraphLayoutStaleNode,
            severity: 'warning',
            message: `Layout references '${qname}' which is not in objects list`,
            source: graph.source,
          });
        }
      }
    }

    return diagnostics;
  }

validateCircularDependencies(packageGraph: PackageGraph): ValidationDiagnostic[] {
    const diagnostics: ValidationDiagnostic[] = [];
    const cycles = findCyclesOn(packageGraph);
    const packageToUris = new Map<string, string[]>();
    for (const entry of this.symbols.all()) {
      const arr = packageToUris.get(entry.packageName) ?? [];
      arr.push(entry.documentUri);
      packageToUris.set(entry.packageName, arr);
    }
    for (const cycle of cycles) {
      const uri = packageToUris.get(cycle[0])?.[0] ?? '';
      diagnostics.push({
        code: DiagnosticCode.CircularPackageDependency,
        severity: 'warning',
        message: `Package '${cycle[0]}' is part of a cycle: ${cycle.join(' → ')} → ${cycle[0]}. Cycles parse cleanly but make dependency reasoning harder.`,
        source: { file: uri, line: 1, column: 0, endLine: 1, endColumn: 0, offsetStart: 0, offsetEnd: 0 },
      });
    }
    return diagnostics;
  }

  validatePackageDeclarations(uri: string, ast: Document): ValidationDiagnostic[] {
    const diagnostics: ValidationDiagnostic[] = [];
    // Graph files (.ttrg) are project-level definitions that reference
    // fully-qualified names; they are not package members and never carry a
    // package declaration, so package-inference rules don't apply to them.
    if (uri.endsWith('.ttrg')) return diagnostics;
    const declaredPackage = ast.packageDecl?.name ?? '';
    const { inferred, isRootFile } = inferPackageFromUri(uri, this.manifest.projectRoot);

    if (!declaredPackage && !isRootFile) {
      diagnostics.push({
        code: DiagnosticCode.MissingPackageDeclaration,
        severity: 'info',
        message: `File is in package '${inferred}' but has no package declaration`,
        source: ast.source,
      });
    } else if (declaredPackage && inferred && declaredPackage !== inferred) {
      diagnostics.push({
        code: DiagnosticCode.PackageDeclarationMismatch,
        severity: 'error',
        message: `Declared package '${declaredPackage}' does not match inferred package '${inferred}'`,
        source: ast.packageDecl!.source,
      });
    }

    return diagnostics;
  }
}
