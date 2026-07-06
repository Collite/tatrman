// Grounding Phase 1 (grammar 4.2) — the typed, validated result of a `semantics`
// block. Populated by the validator ONLY when the block is diagnostics-free for
// that element (degrade-don't-fail: a block with errors leaves the element
// without a ResolvedSemantics but the surrounding model still loads).

import type { EntityKind, AttributeRole } from './vocabulary.js';

/** A resolved cross-reference to another symbol (entity or sibling attribute). */
export interface SymbolRef {
  /** The reference text as written (opaque id, e.g. `AccountingPeriod`). */
  path: string;
  /** The resolved target's canonical qname, when resolution succeeded. */
  qname?: string;
}

/** The resolved `semantics` block on an entity or db table. */
export interface ResolvedEntitySemantics {
  kind: EntityKind;
}

/** The resolved `semantics` block on an attribute or db column. */
export interface ResolvedAttributeSemantics {
  role: AttributeRole;
  refs: {
    /** `period:` → the period-table entity (event/document/posting/due dates). */
    period?: SymbolRef;
    /** `currency:` → the sibling `currency_code` attribute (on `amount`). */
    currency?: SymbolRef;
  };
  params: {
    /** `code_format:` on `period_code` (default `"yyyyMM"`). */
    codeFormat?: string;
  };
}

/** Either shape, discriminated by the owning symbol's kind. */
export type ResolvedSemantics = ResolvedEntitySemantics | ResolvedAttributeSemantics;

export function isEntitySemantics(r: ResolvedSemantics): r is ResolvedEntitySemantics {
  return 'kind' in r;
}

export function isAttributeSemantics(r: ResolvedSemantics): r is ResolvedAttributeSemantics {
  return 'role' in r;
}
