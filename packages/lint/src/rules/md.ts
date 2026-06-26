import { DiagnosticCode } from '@modeler/parser';
import type { CrossRef, Definition, SourceLocation } from '@modeler/parser';
import {
  defaultSchemaForKind,
  resolveMdRef,
  getCalcEntry,
  shapeSatisfied,
  validateCalcArgs,
  buildMdMapGraph,
  resolveLevelDomains,
  inferStep,
  grainReachable,
  type DomainShape,
} from '@modeler/semantics';
import type { Rule, DocumentRuleContext } from '../rule.js';

/** The six MD logical def kinds (schema md). */
const MD_LOGICAL_KINDS: ReadonlySet<string> = new Set([
  'mdDomain',
  'dimension',
  'mdMap',
  'hierarchy',
  'measure',
  'cubelet',
]);

/** The four MD binding def kinds (schema binding). */
const MD_BINDING_KINDS: ReadonlySet<string> = new Set([
  'md2dbCubelet',
  'md2dbDomain',
  'md2dbMap',
  'md2erCubelet',
]);

export function isMdKind(kind: string): boolean {
  return MD_LOGICAL_KINDS.has(kind) || MD_BINDING_KINDS.has(kind);
}

/** Every span-carrying cross-reference reachable from a top-level MD def. */
function mdRefsOf(def: Definition): CrossRef[] {
  const out: CrossRef[] = [];
  const push = (crs?: CrossRef[]) => {
    if (crs) out.push(...crs);
  };
  switch (def.kind) {
    case 'dimension':
      push(def.crossRefs); // hierarchies
      for (const attr of def.attributes) push(attr.crossRefs); // domain refs
      break;
    case 'cubelet':
      push(def.crossRefs); // grain + measure refs
      for (const m of def.measures) if (typeof m !== 'string') push(m.crossRefs); // inline measure domains
      break;
    case 'mdMap':
    case 'hierarchy':
    case 'measure':
      push(def.crossRefs);
      break;
    default:
      break;
  }
  return out;
}

const unknownRef: Rule = {
  id: 'md-unknown-ref',
  code: DiagnosticCode.MdUnknownRef,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: "An MD cross-reference (domain/map/dimension/measure/hierarchy/grain) doesn't resolve.",
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      for (const cr of mdRefsOf(def)) {
        if (cr.role === 'grain') continue; // cubelet grain → md/grain-ref-unknown
        if (!resolveMdRef(ctx.symbols, cr.path, cr.role)) {
          ctx.report({
            source: cr.source,
            message: `Unresolved ${cr.role} reference: '${cr.path}'`,
            data: { role: cr.role, path: cr.path },
          });
        }
      }
    }
  },
};

const unknownSchemaDef: Rule = {
  id: 'md-unknown-schema-def',
  code: DiagnosticCode.MdUnknownSchemaDef,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'An MD logical def appears outside `schema md`, or a binding def outside `schema binding`.',
  check(ctx: DocumentRuleContext) {
    if (ctx.scope !== 'document') return;
    const directive = ctx.ast.schemaDirective?.schemaCode;
    if (!directive) return; // schema-less files derive per-def; nothing to police
    for (const def of ctx.ast.definitions) {
      if (!isMdKind(def.kind)) continue;
      const expected = defaultSchemaForKind(def.kind); // 'md' (logical) | 'binding' (binding)
      if (expected !== directive) {
        ctx.report({
          source: def.source,
          message: `'${def.kind}' belongs in 'schema ${expected}', not 'schema ${directive}'`,
          data: { kind: def.kind, expected, actual: directive },
        });
      }
    }
  },
};

// ---------------------------------------------------------------------------
// 2C — domain / attribute / measure validators (contracts §3.1, §3.3, §3.6, §6.5)
// ---------------------------------------------------------------------------

/** Continuous value types — a member-set/`kind` is nonsensical on these (scalars). */
const CONTINUOUS_TYPES: ReadonlySet<string> = new Set(['decimal', 'float', 'double']);
/** Restrict clause names the validator understands. Others warn (open set). */
const KNOWN_RESTRICT_CLAUSES: ReadonlySet<string> = new Set(['range', 'members', 'pattern', 'length']);

function domainTypeName(def: Extract<Definition, { kind: 'mdDomain' }>): string | undefined {
  if (!def.type) return undefined;
  return def.type.kind === 'simple' ? def.type.name : def.type.typeName;
}

const kindOnScalar: Rule = {
  id: 'md-kind-on-scalar',
  code: DiagnosticCode.MdKindOnScalar,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: '`kind:` on a scalar (continuous-typed) domain, which cannot form a member set.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind !== 'mdDomain' || !def.domainKind) continue;
      const t = domainTypeName(def)?.toLowerCase();
      if (t && CONTINUOUS_TYPES.has(t)) {
        ctx.report({
          source: def.source,
          message: `Domain '${def.name}' has 'kind: ${def.domainKind}' but a scalar '${t}' type; kind applies only to member-set/calc domains`,
        });
      }
    }
  },
};

const badRestrictValue: Rule = {
  id: 'md-bad-restrict-value',
  code: DiagnosticCode.MdBadRestrictValue,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'A restrict clause value has the wrong shape for its clause (or `members` on a non-discrete type).',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind !== 'mdDomain' || !def.restrict) continue;
      const t = domainTypeName(def)?.toLowerCase();
      for (const clause of def.restrict) {
        const v = clause.value;
        const isRange = !Array.isArray(v) && v.kind === 'rangeLiteral';
        const isMembers = Array.isArray(v);
        let bad: string | undefined;
        if (clause.clause === 'range' && !isRange) bad = 'expects a `lo..hi` range literal';
        else if (clause.clause === 'members' && !isMembers) bad = 'expects a labelled member set';
        else if (clause.clause === 'members' && isMembers && t && CONTINUOUS_TYPES.has(t))
          bad = `is only valid on a discrete type (not '${t}')`;
        else if (clause.clause === 'length' && (Array.isArray(v) || v.kind !== 'number')) bad = 'expects a number';
        else if (clause.clause === 'pattern' && (Array.isArray(v) || (v.kind !== 'string' && v.kind !== 'tripleString')))
          bad = 'expects a string';
        if (bad) ctx.report({ source: clause.source, message: `restrict '${clause.clause}' ${bad}` });
      }
    }
  },
};

const unknownRestrictClause: Rule = {
  id: 'md-unknown-restrict-clause',
  code: DiagnosticCode.MdUnknownRestrictClause,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'An unrecognised restrict clause name (the clause set is open; this is advisory).',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind !== 'mdDomain' || !def.restrict) continue;
      for (const clause of def.restrict) {
        if (!KNOWN_RESTRICT_CLAUSES.has(clause.clause)) {
          ctx.report({ source: clause.source, message: `Unknown restrict clause '${clause.clause}'` });
        }
      }
    }
  },
};

const attrNeedsDomain: Rule = {
  id: 'md-attr-needs-domain',
  code: DiagnosticCode.MdAttrNeedsDomain,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'An MD (dimension) attribute lacks the required `domain:`.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind !== 'dimension') continue;
      for (const attr of def.attributes) {
        if (!attr.domainRef) ctx.report({ source: attr.source, message: `Attribute '${attr.name}' needs a 'domain:'` });
      }
    }
  },
};

const attrTypeInMd: Rule = {
  id: 'md-attr-type-in-md',
  code: DiagnosticCode.MdAttrTypeInMd,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'An MD (dimension) attribute carries the ER-only `type:` (use `domain:`).',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind !== 'dimension') continue;
      for (const attr of def.attributes) {
        if (attr.type) ctx.report({ source: attr.source, message: `MD attribute '${attr.name}' must use 'domain:', not 'type:'` });
      }
    }
  },
};

const erAttrDomainInEr: Rule = {
  id: 'er-attr-domain-in-er',
  code: DiagnosticCode.ErAttrDomainInEr,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'An ER (entity) attribute carries the MD-only `domain:` (use `type:`).',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind !== 'entity' || !def.attributes) continue;
      for (const attr of def.attributes) {
        if (attr.domainRef) ctx.report({ source: attr.source, message: `ER attribute '${attr.name}' must use 'type:', not 'domain:'` });
      }
    }
  },
};

/** Whether an aggregation override names a latest-valid-style function (needs validBy). */
function hasLatestValidOverride(agg: { perDimension?: Record<string, string>; default?: string } | undefined): boolean {
  if (!agg) return false;
  const vals = [agg.default ?? '', ...Object.values(agg.perDimension ?? {})];
  return vals.some((v) => v.toLowerCase().includes('latestvalid') || v.toLowerCase().startsWith('latest'));
}

const semiadditiveNoValidby: Rule = {
  id: 'md-semiadditive-no-validby',
  code: DiagnosticCode.MdSemiadditiveNoValidby,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'A semi-additive measure with a latest-valid aggregation override but no `validBy`.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    const measures = ctx.ast.definitions.flatMap((d) =>
      d.kind === 'measure' ? [d] : d.kind === 'cubelet' ? d.measures.filter((m): m is Extract<Definition, { kind: "measure" }> => typeof m !== "string") : []
    );
    for (const m of measures) {
      if (m.measureClass === 'semiAdditive' && hasLatestValidOverride(m.aggregation) && !m.validBy) {
        ctx.report({ source: m.source, message: `Semi-additive measure '${m.name}' uses a latest-valid override but has no 'validBy'` });
      }
    }
  },
};

const nonadditiveRecompute: Rule = {
  id: 'md-nonadditive-recompute-unsupported',
  code: DiagnosticCode.MdNonadditiveRecomputeUnsupported,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'A non-additive measure declares a per-dimension recompute (a v1.1 feature; v1 marks only).',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    const measures = ctx.ast.definitions.flatMap((d) =>
      d.kind === 'measure' ? [d] : d.kind === 'cubelet' ? d.measures.filter((m): m is Extract<Definition, { kind: "measure" }> => typeof m !== "string") : []
    );
    for (const m of measures) {
      if (m.measureClass === 'nonAdditive' && m.aggregation?.perDimension && Object.keys(m.aggregation.perDimension).length > 0) {
        ctx.report({
          source: m.source,
          message: `Non-additive measure '${m.name}' declares a per-dimension recompute; v1 marks non-additive only (v1.1 feature)`,
        });
      }
    }
  },
};

// ---------------------------------------------------------------------------
// 2D — map + calc-catalog validation (contracts §3.4, §6.4; map-catalog §1)
// ---------------------------------------------------------------------------

type MdMap = Extract<Definition, { kind: 'mdMap' }>;
type Ctx = DocumentRuleContext;

function mapDefs(ctx: Ctx): MdMap[] {
  return ctx.ast.definitions.filter((d): d is MdMap => d.kind === 'mdMap');
}

/** The crossRef span for a from/to domain path on a map, or the map's own source. */
function refSourceFor(def: MdMap, path: string): SourceLocation {
  return def.crossRefs?.find((c) => c.path === path)?.source ?? def.source;
}

/** Resolve a from/to ref to its {type, range} shape via the project symbols. */
function domainShape(ctx: Ctx, path: string): DomainShape | undefined {
  const sym = resolveMdRef(ctx.symbols, path, 'domain');
  if (!sym) return undefined;
  return { type: sym.domainType, range: sym.domainRange };
}

const unknownCalcMap: Rule = {
  id: 'md-unknown-calc-map',
  code: DiagnosticCode.MdUnknownCalcMap,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'A `calc:` references a name not in the built-in catalog.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of mapDefs(ctx)) {
      if (def.calc && !getCalcEntry(def.calc.name)) {
        ctx.report({ source: def.calc.source, message: `Unknown calc map '${def.calc.name}'` });
      }
    }
  },
};

const badCalcArgs: Rule = {
  id: 'md-bad-calc-args',
  code: DiagnosticCode.MdBadCalcArgs,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'A calc argument is unknown, missing (required), or out of range.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of mapDefs(ctx)) {
      if (!def.calc) continue;
      const entry = getCalcEntry(def.calc.name);
      if (!entry) continue; // md/unknown-calc-map handles this
      for (const p of validateCalcArgs(entry, def.calc.args)) {
        ctx.report({
          source: p.arg?.source ?? def.calc.source,
          message: `calc '${def.calc.name}' arg '${p.paramName}' is ${p.problem}`,
        });
      }
    }
  },
};

const calcTypeMismatch: Rule = {
  id: 'md-calc-type-mismatch',
  code: DiagnosticCode.MdCalcTypeMismatch,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: "A map's `from`/`to` domain types don't satisfy the calc signature.",
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of mapDefs(ctx)) {
      if (!def.calc) continue;
      const entry = getCalcEntry(def.calc.name);
      if (!entry) continue;
      const fromPath = def.from[0];
      const toPath = def.to[0];
      const fromShape = fromPath ? domainShape(ctx, fromPath) : undefined;
      const toShape = toPath ? domainShape(ctx, toPath) : undefined;
      // Only flag a mismatch when the domain resolves (else md/unknown-ref fires).
      if (fromShape && !shapeSatisfied(fromShape, entry.input)) {
        ctx.report({ source: refSourceFor(def, fromPath), message: `'from' domain does not satisfy calc '${def.calc.name}' input` });
      }
      if (toShape && !shapeSatisfied(toShape, entry.output)) {
        ctx.report({ source: refSourceFor(def, toPath), message: `'to' domain does not satisfy calc '${def.calc.name}' output` });
      }
    }
  },
};

const calcCardinalityConflict: Rule = {
  id: 'md-calc-cardinality-conflict',
  code: DiagnosticCode.MdCalcCardinalityConflict,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'An explicit `cardinality: 1:1` on a calc (implicitly N:1) map.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of mapDefs(ctx)) {
      if (def.calc && def.cardinality === '1:1') {
        ctx.report({ source: def.source, message: `calc map '${def.name}' is implicitly N:1; remove the explicit '1:1'` });
      }
    }
  },
};

/** Last dotted segment of a ref (the bare name). */
function leaf(path: string): string {
  const parts = path.split('.');
  return parts[parts.length - 1];
}

const tableMapNoBinding: Rule = {
  id: 'md-table-map-no-binding',
  code: DiagnosticCode.MdTableMapNoBinding,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'A table-backed map (no `calc:`) with no `md2db_map` binding in this file (Phase 3 refines cross-file).',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    const boundMapNames = new Set(
      ctx.ast.definitions.filter((d) => d.kind === 'md2dbMap').map((d) => leaf((d as Extract<Definition, { kind: 'md2dbMap' }>).mapRef))
    );
    for (const def of mapDefs(ctx)) {
      if (!def.calc && !boundMapNames.has(def.name)) {
        ctx.report({ source: def.source, message: `table-backed map '${def.name}' has no md2db_map binding` });
      }
    }
  },
};

// ---------------------------------------------------------------------------
// 2E — hierarchy step inference (contracts §6.3)
// ---------------------------------------------------------------------------

const levelNotInDim: Rule = {
  id: 'md-level-not-in-dim',
  code: DiagnosticCode.MdLevelNotInDim,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: "A hierarchy level attribute isn't a member of the hierarchy's dimension.",
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind !== 'hierarchy') continue;
      const levelInfos = resolveLevelDomains(ctx.symbols, def.dimensionRef, def.levels.map((l) => l.attribute));
      def.levels.forEach((lvl, i) => {
        if (!levelInfos[i].inDim) {
          ctx.report({ source: lvl.source, message: `Level '${lvl.attribute}' is not in dimension '${def.dimensionRef ?? '?'}'` });
        }
      });
    }
  },
};

/** Shared step inference for the two step-error rules; returns one result per consecutive pair. */
function hierarchySteps(ctx: Ctx, def: Extract<Definition, { kind: 'hierarchy' }>) {
  const maps = ctx.ast.definitions.filter((d): d is MdMap => d.kind === 'mdMap');
  const graph = buildMdMapGraph(ctx.symbols, maps);
  const infos = resolveLevelDomains(ctx.symbols, def.dimensionRef, def.levels.map((l) => l.attribute));
  const out: { upper: typeof def.levels[number]; result: 'ok' | 'none' | 'ambiguous' }[] = [];
  for (let i = 0; i + 1 < def.levels.length; i++) {
    const lower = infos[i];
    const upper = infos[i + 1];
    const upperLevel = def.levels[i + 1];
    if (!lower.inDim || !upper.inDim || !lower.domain || !upper.domain) continue; // covered elsewhere
    const via = upperLevel.via;
    if (via) {
      const viaName = leaf(via);
      const ok = graph.edges.some(
        (e) => !e.oneToOne && e.from === lower.domain && e.to === upper.domain && e.mapName === viaName
      );
      out.push({ upper: upperLevel, result: ok ? 'ok' : 'none' });
      continue;
    }
    const step = inferStep(graph.edges, lower.domain, upper.domain);
    out.push({ upper: upperLevel, result: 'ok' in step ? 'ok' : step.error });
  }
  return out;
}

const noHierarchyStep: Rule = {
  id: 'md-no-hierarchy-step',
  code: DiagnosticCode.MdNoHierarchyStep,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'No N:1 map connects two consecutive hierarchy levels.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind !== 'hierarchy') continue;
      for (const s of hierarchySteps(ctx, def)) {
        if (s.result === 'none') {
          ctx.report({ source: s.upper.source, message: `No map connects to level '${s.upper.attribute}'` });
        }
      }
    }
  },
};

const ambiguousHierarchyStep: Rule = {
  id: 'md-ambiguous-hierarchy-step',
  code: DiagnosticCode.MdAmbiguousHierarchyStep,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: 'More than one N:1 map connects two consecutive levels and no `via:` disambiguates.',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind !== 'hierarchy') continue;
      for (const s of hierarchySteps(ctx, def)) {
        if (s.result === 'ambiguous') {
          ctx.report({ source: s.upper.source, message: `Ambiguous step to level '${s.upper.attribute}'; add 'via <map>'` });
        }
      }
    }
  },
};

// ---------------------------------------------------------------------------
// 2F — cubelet validator (contracts §3.7, §6.1)
// ---------------------------------------------------------------------------

type Cubelet = Extract<Definition, { kind: 'cubelet' }>;

const grainRefUnknown: Rule = {
  id: 'md-grain-ref-unknown',
  code: DiagnosticCode.MdGrainRefUnknown,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'error',
  docs: "A cubelet `grain` ref doesn't resolve to a real Dimension.attribute.",
  check(ctx) {
    if (ctx.scope !== 'document') return;
    for (const def of ctx.ast.definitions) {
      if (def.kind !== 'cubelet') continue;
      for (const cr of def.crossRefs ?? []) {
        if (cr.role === 'grain' && !resolveMdRef(ctx.symbols, cr.path, 'grain')) {
          ctx.report({ source: cr.source, message: `Unknown grain attribute '${cr.path}'` });
        }
      }
    }
  },
};

/** The underlying domain qname of a grain attribute ref, or undefined. */
function grainDomain(ctx: Ctx, path: string): string | undefined {
  const attr = resolveMdRef(ctx.symbols, path, 'grain');
  if (!attr?.domainRef) return undefined;
  return resolveMdRef(ctx.symbols, attr.domainRef, 'domain')?.qname;
}

const grainNotLeaf: Rule = {
  id: 'md-grain-not-leaf',
  code: DiagnosticCode.MdGrainNotLeaf,
  category: 'md',
  scope: 'document',
  defaultSeverity: 'warning',
  docs: 'A grain attribute is strictly coarser than another in the same grain (advisory).',
  check(ctx) {
    if (ctx.scope !== 'document') return;
    const maps = ctx.ast.definitions.filter((d): d is MdMap => d.kind === 'mdMap');
    const graph = buildMdMapGraph(ctx.symbols, maps);
    const reach = grainReachable(graph.edges);
    for (const def of ctx.ast.definitions) {
      if (def.kind !== 'cubelet') continue;
      const cubelet = def as Cubelet;
      const grainCrs = (cubelet.crossRefs ?? []).filter((c) => c.role === 'grain');
      const domains = grainCrs.map((c) => grainDomain(ctx, c.path));
      for (let i = 0; i < grainCrs.length; i++) {
        for (let j = 0; j < grainCrs.length; j++) {
          if (i === j) continue;
          const a = domains[i];
          const b = domains[j];
          // a (finer) coarsens to b (coarser) ⇒ b is redundant/coarser in the grain.
          if (a && b && a !== b && reach(a, b)) {
            ctx.report({
              source: grainCrs[j].source,
              message: `Grain attribute '${grainCrs[j].path}' is coarser than '${grainCrs[i].path}'`,
            });
          }
        }
      }
    }
  },
};

export const MD_RULES: Rule[] = [
  unknownRef,
  unknownSchemaDef,
  kindOnScalar,
  badRestrictValue,
  unknownRestrictClause,
  attrNeedsDomain,
  attrTypeInMd,
  erAttrDomainInEr,
  semiadditiveNoValidby,
  nonadditiveRecompute,
  unknownCalcMap,
  badCalcArgs,
  calcTypeMismatch,
  calcCardinalityConflict,
  tableMapNoBinding,
  levelNotInDim,
  noHierarchyStep,
  ambiguousHierarchyStep,
  grainRefUnknown,
  grainNotLeaf,
];
