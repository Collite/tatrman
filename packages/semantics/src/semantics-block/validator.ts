// SPDX-License-Identifier: Apache-2.0
// Grounding Phase 1 (grammar 4.2) — validate `semantics { … }` blocks against the
// closed vocabulary (vocabulary.ts, NORMATIVE) and produce the typed
// ResolvedSemantics for diagnostics-free elements.
//
// Pipeline per README §T3.4: shape (keys/values) → cross-ref resolution
// (`period:` entity ref, `currency:` sibling-attribute ref) → type-constraint
// check against the declared `type` → per-owner aggregation (completeness,
// event_date cardinality, geo/valid pairs). ResolvedSemantics is emitted for an
// element ONLY when that element is diagnostics-free (degrade, don't fail).

import { DiagnosticCode } from '@tatrman/parser';
import type {
  Document,
  Definition,
  EntityDef,
  TableDef,
  AttributeDef,
  ColumnDef,
  SemanticsBlock,
  SemanticsValue,
  DataType,
  SourceLocation,
} from '@tatrman/parser';
import type { ProjectSymbolTable } from '../project-symbols.js';
import {
  ATTRIBUTE_ROLES,
  ENTITY_KINDS,
  KIND_COMPLETENESS,
  ALL_ROLES,
  type EntityKind,
  type AttributeRole,
  type TypeConstraint,
} from './vocabulary.js';
import { nearestMatch } from './suggest.js';
import type {
  ResolvedSemantics,
  ResolvedEntitySemantics,
  ResolvedAttributeSemantics,
} from './model.js';

export interface SemanticsDiagnostic {
  code: DiagnosticCode;
  message: string;
  source: SourceLocation;
  /** Closed-vocabulary nearest match for 200/201/202, when one exists. */
  suggestion?: string;
}

export interface SemanticsAnalysis {
  diagnostics: SemanticsDiagnostic[];
  /** qname-free: resolved results keyed by the owning element's `source` node. */
  resolved: Map<SourceLocation, ResolvedSemantics>;
}

type OwnerDef = EntityDef | TableDef;

/** A member (attribute or column) of an owner, with its parsed semantics role. */
interface Member {
  name: string;
  role?: AttributeRole;
  rawRole?: string;
  type?: DataType;
  block: SemanticsBlock;
  source: SourceLocation;
  /** true once this member's block is proven diagnostics-free. */
  clean: boolean;
}

/** Map a declared TTR type name to a semantics type-constraint family. */
export function typeFamilyOf(dt: DataType | undefined): TypeConstraint | 'other' | undefined {
  if (!dt) return undefined;
  const raw = (dt.kind === 'simple' ? dt.name : dt.typeName).toLowerCase();
  if (['date', 'datetime', 'timestamp'].includes(raw)) return 'date';
  if (['text', 'varchar', 'char', 'string'].includes(raw)) return 'text';
  if (['decimal', 'number', 'numeric', 'int', 'integer', 'float', 'double', 'bigint', 'smallint'].includes(raw)) {
    return 'numeric';
  }
  return 'other';
}

const lastSeg = (path: string): string => path.split('.').pop() ?? path;

/**
 * Analyse every `semantics` block in `ast`. `symbols` (optional) enables
 * cross-document `period:` resolution; same-document targets resolve without it.
 */
export function analyzeSemantics(ast: Document, symbols?: ProjectSymbolTable): SemanticsAnalysis {
  const diagnostics: SemanticsDiagnostic[] = [];
  const resolved = new Map<SourceLocation, ResolvedSemantics>();
  const emit = (code: DiagnosticCode, source: SourceLocation, message: string, suggestion?: string): void => {
    diagnostics.push({ code, source, message, suggestion });
  };

  // Document-local index of declared entity/table kinds (raw), for `period:`.
  const localKinds = new Map<string, string>();
  for (const def of ast.definitions) {
    if ((def.kind === 'entity' || def.kind === 'table') && def.semantics) {
      const k = def.semantics.entries.kind;
      if (typeof k === 'string') localKinds.set(def.name, k);
    }
  }

  for (const def of ast.definitions) {
    if (def.kind === 'entity' || def.kind === 'table') {
      validateOwner(def, def.kind === 'entity' ? def.attributes ?? [] : def.columns ?? []);
    } else if (def.kind === 'attribute') {
      // Standalone attribute: shape/type/period checks only (no owner aggregation,
      // no sibling for `currency:`).
      if (def.semantics) validateStandaloneAttribute(def);
    }
  }

  function validateOwner(owner: OwnerDef, rawMembers: ReadonlyArray<AttributeDef | ColumnDef>): void {
    // --- entity/table-level block ---
    let ownerKind: EntityKind | undefined;
    let ownerClean = true;
    if (owner.semantics) {
      const r = validateEntityBlock(owner.semantics);
      ownerKind = r.kind;
      ownerClean = r.clean;
      if (r.clean && r.kind) {
        resolved.set(owner.semantics.source, { kind: r.kind } satisfies ResolvedEntitySemantics);
      }
    }

    // --- member blocks ---
    const members: Member[] = [];
    for (const m of rawMembers) {
      if (!m.semantics) continue;
      const parsed = validateAttributeBlock(m.semantics, m.type, owner, rawMembers);
      members.push({
        name: m.name,
        role: parsed.role,
        rawRole: parsed.rawRole,
        type: m.type,
        block: m.semantics,
        source: m.semantics.source,
        clean: parsed.clean,
      });
      if (parsed.clean && parsed.resolved) resolved.set(m.semantics.source, parsed.resolved);
    }

    // --- per-owner aggregation (runs regardless of kind for 207/210/211) ---
    aggregate(owner, ownerKind, ownerClean, members);
  }

  function validateStandaloneAttribute(attr: AttributeDef): void {
    const parsed = validateAttributeBlock(attr.semantics!, attr.type, undefined, []);
    if (parsed.clean && parsed.resolved) resolved.set(attr.semantics!.source, parsed.resolved);
  }

  // ---- entity/table block shape ----
  function validateEntityBlock(block: SemanticsBlock): { kind?: EntityKind; clean: boolean } {
    let clean = true;
    for (const dup of block.duplicateProperties ?? []) {
      emit(DiagnosticCode.SemDuplicateKey, block.source, `duplicate semantics key '${dup}'`);
      clean = false;
    }
    let kind: EntityKind | undefined;
    for (const [key, value] of Object.entries(block.entries)) {
      if (key === 'kind') {
        if (typeof value === 'string' && (ENTITY_KINDS as ReadonlyArray<string>).includes(value)) {
          kind = value as EntityKind;
        } else {
          const s = typeof value === 'string' ? nearestMatch(value, ENTITY_KINDS) : undefined;
          emit(DiagnosticCode.SemUnknownKind, block.source, `unknown entity/table kind '${String(value)}'${didYouMean(s)}`, s);
          clean = false;
        }
      } else if (key === 'role' || ALL_ROLES.includes(String(value)) || isAttributeOnlyKey(key)) {
        // `role:` (and role-only extras) belong on an attribute/column, not here.
        emit(DiagnosticCode.SemMisplacedKeyword, block.source, `'${key}' is an attribute/column key; entity/table blocks carry only 'kind'`);
        clean = false;
      } else {
        const s = nearestMatch(key, ['kind']);
        emit(DiagnosticCode.SemUnknownKey, block.source, `unknown semantics key '${key}'${didYouMean(s)}`, s);
        clean = false;
      }
    }
    return { kind, clean };
  }

  // ---- attribute/column block shape + cross-refs + type ----
  function validateAttributeBlock(
    block: SemanticsBlock,
    memberType: DataType | undefined,
    owner: OwnerDef | undefined,
    siblings: ReadonlyArray<AttributeDef | ColumnDef>,
  ): { role?: AttributeRole; rawRole?: string; clean: boolean; resolved?: ResolvedAttributeSemantics } {
    let clean = true;
    for (const dup of block.duplicateProperties ?? []) {
      emit(DiagnosticCode.SemDuplicateKey, block.source, `duplicate semantics key '${dup}'`);
      clean = false;
    }

    // `kind` on an attribute/column is misplaced.
    if ('kind' in block.entries) {
      emit(DiagnosticCode.SemMisplacedKeyword, block.source, `'kind' is an entity/table key; attribute/column blocks carry 'role'`);
      clean = false;
    }

    const rawRole = block.entries.role;
    let role: AttributeRole | undefined;
    if (typeof rawRole === 'string' && rawRole in ATTRIBUTE_ROLES) {
      role = rawRole as AttributeRole;
    } else if (rawRole !== undefined) {
      const s = typeof rawRole === 'string' ? nearestMatch(rawRole, ALL_ROLES) : undefined;
      emit(DiagnosticCode.SemUnknownRole, block.source, `unknown semantics role '${String(rawRole)}'${didYouMean(s)}`, s);
      clean = false;
    }

    // Allowed keys for this role.
    const spec = role ? ATTRIBUTE_ROLES[role] : undefined;
    const allowed = new Set<string>(['role', ...(spec ? spec.extraKeys.map((k) => k.key) : [])]);
    for (const key of Object.keys(block.entries)) {
      if (key === 'kind') continue; // already handled (204)
      if (allowed.has(key)) continue;
      if (role) {
        const s = nearestMatch(key, [...allowed]);
        emit(DiagnosticCode.SemUnknownKey, block.source, `key '${key}' is not valid for role '${role}'${didYouMean(s)}`, s);
        clean = false;
      }
      // when role is unknown we already reported 201; don't pile on per-key noise
    }

    // Type constraint.
    if (role && spec?.typeConstraint) {
      const fam = typeFamilyOf(memberType);
      if (fam && fam !== spec.typeConstraint) {
        emit(
          DiagnosticCode.SemTypeConstraint,
          block.source,
          `role '${role}' requires a ${spec.typeConstraint} type, but the declared type is '${typeName(memberType)}'`,
        );
        clean = false;
      }
    }

    // Cross-refs.
    const refs: ResolvedAttributeSemantics['refs'] = {};
    if (role) {
      // period: → entity ref of kind period_table (event/document/posting/due dates).
      const periodVal = block.entries.period;
      if (periodVal !== undefined && spec?.extraKeys.some((k) => k.key === 'period')) {
        const ref = resolvePeriodRef(String(periodVal), block.source);
        if (ref.ok) refs.period = { path: String(periodVal), qname: ref.qname };
        else clean = false;
      }
      // currency: → sibling attribute of role currency_code (on `amount`).
      const currencyVal = block.entries.currency;
      if (currencyVal !== undefined && spec?.extraKeys.some((k) => k.key === 'currency')) {
        const ref = resolveCurrencyRef(String(currencyVal), siblings, block.source);
        if (ref.ok) refs.currency = { path: String(currencyVal) };
        else clean = false;
      }
    }

    if (!role || !clean) return { role, rawRole: typeof rawRole === 'string' ? rawRole : undefined, clean };
    const params: ResolvedAttributeSemantics['params'] = {};
    if (role === 'period_code') {
      const cf = block.entries.code_format;
      params.codeFormat = typeof cf === 'string' ? cf : 'yyyyMM';
    }
    return { role, rawRole: role, clean: true, resolved: { role, refs, params } };
  }

  // period: resolution — same-doc kind index first, then project symbols.
  function resolvePeriodRef(path: string, source: SourceLocation): { ok: boolean; qname?: string } {
    const name = lastSeg(path);
    // same document
    const localKind = localKinds.get(name);
    if (localKind !== undefined) {
      if (localKind === 'period_table') return { ok: true };
      emit(DiagnosticCode.SemBadPeriodRef, source, `period: '${path}' refers to '${name}', which is not a 'period_table' kind`);
      return { ok: false };
    }
    // cross document via the project symbol table
    if (symbols) {
      const cands = symbols.findByName(name).filter((s) => s.kind === 'entity' || s.kind === 'table');
      if (cands.length > 0) {
        if (cands.some((s) => s.semanticsKind === 'period_table')) return { ok: true, qname: cands[0].qname };
        emit(DiagnosticCode.SemBadPeriodRef, source, `period: '${path}' refers to an entity/table that is not a 'period_table' kind`);
        return { ok: false };
      }
    }
    emit(DiagnosticCode.SemBadPeriodRef, source, `period: '${path}' does not resolve to any entity/table`);
    return { ok: false };
  }

  // currency: resolution — a sibling member with role currency_code.
  function resolveCurrencyRef(
    path: string,
    siblings: ReadonlyArray<AttributeDef | ColumnDef>,
    source: SourceLocation,
  ): { ok: boolean } {
    const name = lastSeg(path);
    const sib = siblings.find((m) => m.name === name);
    if (!sib) {
      emit(DiagnosticCode.SemBadCurrencyRef, source, `currency: '${path}' does not resolve to a sibling attribute/column`);
      return { ok: false };
    }
    if (sib.semantics?.entries.role !== 'currency_code') {
      emit(DiagnosticCode.SemBadCurrencyRef, source, `currency: '${path}' must reference a sibling with role 'currency_code'`);
      return { ok: false };
    }
    return { ok: true };
  }

  // ---- per-owner aggregation ----
  function aggregate(owner: OwnerDef, ownerKind: EntityKind | undefined, ownerClean: boolean, members: Member[]): void {
    const roleCount = (r: AttributeRole): number => members.filter((m) => m.role === r).length;

    // 207 — at most one event_date per owner.
    if (roleCount('event_date') > 1) {
      emit(DiagnosticCode.SemMultipleEventDate, owner.source, `entity/table '${owner.name}' has more than one 'event_date' — exactly one is the default query date`);
    }

    // 210 — geo_lat/geo_lon pair required together.
    const hasLat = roleCount('geo_lat') > 0;
    const hasLon = roleCount('geo_lon') > 0;
    if (hasLat !== hasLon) {
      emit(DiagnosticCode.SemGeoPair, owner.source, `'${owner.name}' has ${hasLat ? 'geo_lat without geo_lon' : 'geo_lon without geo_lat'} — the pair is required together`);
    }

    // 211 — valid_from/valid_to both-or-neither.
    const hasFrom = roleCount('valid_from') > 0;
    const hasTo = roleCount('valid_to') > 0;
    if (hasFrom !== hasTo) {
      emit(DiagnosticCode.SemValidPair, owner.source, `'${owner.name}' has ${hasFrom ? 'valid_from without valid_to' : 'valid_to without valid_from'} — the validity pair is both-or-neither`);
    }

    if (!ownerKind || !ownerClean) return;

    // Kind completeness.
    if (ownerKind === 'poi') {
      const point = roleCount('geo_point');
      const pair = hasLat && hasLon ? 1 : 0;
      if (!((point === 1 && pair === 0) || (point === 0 && pair === 1))) {
        emit(DiagnosticCode.SemGeoPair, owner.source, `poi '${owner.name}' must have exactly one 'geo_point' XOR one 'geo_lat' + one 'geo_lon'`);
      }
    } else {
      for (const clause of KIND_COMPLETENESS[ownerKind]) {
        const n = roleCount(clause.role);
        if (n !== clause.count) {
          emit(DiagnosticCode.SemCompleteness, owner.source, `${ownerKind} '${owner.name}' requires exactly ${clause.count} '${clause.role}' (found ${n})`);
        }
      }
    }
  }

  return { diagnostics, resolved };
}

function isAttributeOnlyKey(key: string): boolean {
  for (const spec of Object.values(ATTRIBUTE_ROLES)) {
    if (spec.extraKeys.some((k) => k.key === key)) return true;
  }
  return key === 'code_format' || key === 'period' || key === 'currency';
}

function typeName(dt: DataType | undefined): string {
  if (!dt) return '<none>';
  return dt.kind === 'simple' ? dt.name : dt.typeName;
}

function didYouMean(s: string | undefined): string {
  return s ? `; did you mean '${s}'?` : '';
}

// Re-exports so consumers import the whole surface from the validator module.
export type { SemanticsValue, Definition };
