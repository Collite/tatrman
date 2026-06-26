import type { CalcArg, PropertyValue } from '@modeler/parser';
import type { CatalogEntry, CatalogShape } from '@modeler/md-catalog';

/** Int-like TTR types that satisfy an `int{…}` catalog shape. */
const INT_TYPES: ReadonlySet<string> = new Set(['int', 'integer', 'number']);
/** Sub-day instant types. */
const INSTANT_TYPES: ReadonlySet<string> = new Set(['timestamp', 'datetime']);

export interface DomainShape {
  type?: string;
  range?: { lo: number; hi: number };
}

/**
 * Whether a resolved domain's `type` (+ optional `range`) satisfies a catalog
 * `input`/`output` shape (map-catalog §1). Range containment is checked only
 * when both the entry shape and the domain constrain it (lenient otherwise).
 */
export function shapeSatisfied(domain: DomainShape, shape: CatalogShape): boolean {
  const t = domain.type?.toLowerCase();
  if (!t) return false;
  if (shape === 'instant') return INSTANT_TYPES.has(t);
  if (shape === 'date') return t === 'date';
  if (shape === 'instant|date') return INSTANT_TYPES.has(t) || t === 'date';
  // IntShape
  if (!INT_TYPES.has(t)) return false;
  if (shape.lo !== undefined && shape.hi !== undefined && domain.range) {
    return shape.lo <= domain.range.lo && domain.range.hi <= shape.hi;
  }
  return true;
}

export interface CalcArgProblem {
  arg?: CalcArg;
  paramName: string;
  problem: 'unknown' | 'missing' | 'out-of-range';
}

function valueAsString(v: PropertyValue): string | undefined {
  if (v.kind === 'id') return v.path;
  if (v.kind === 'string' || v.kind === 'tripleString') return v.value;
  return undefined;
}
function valueAsNumber(v: PropertyValue): number | undefined {
  return v.kind === 'number' ? v.value : undefined;
}

/** Validate a calc map's args against the catalog entry's params (contracts §6.4). */
export function validateCalcArgs(entry: CatalogEntry, args: CalcArg[]): CalcArgProblem[] {
  const problems: CalcArgProblem[] = [];
  const byName = new Map(entry.params.map((p) => [p.name, p]));

  for (const arg of args) {
    const param = byName.get(arg.name);
    if (!param) {
      problems.push({ arg, paramName: arg.name, problem: 'unknown' });
      continue;
    }
    if (param.type === 'enum') {
      const s = valueAsString(arg.value);
      if (s === undefined || (param.values && !param.values.includes(s))) {
        problems.push({ arg, paramName: arg.name, problem: 'out-of-range' });
      }
    } else {
      const n = valueAsNumber(arg.value);
      const { lo, hi } = param.type;
      if (n === undefined || (lo !== undefined && n < lo) || (hi !== undefined && n > hi)) {
        problems.push({ arg, paramName: arg.name, problem: 'out-of-range' });
      }
    }
  }

  // Required params (no default) must be present.
  const provided = new Set(args.map((a) => a.name));
  for (const p of entry.params) {
    if (p.default === undefined && !provided.has(p.name)) {
      problems.push({ paramName: p.name, problem: 'missing' });
    }
  }
  return problems;
}
