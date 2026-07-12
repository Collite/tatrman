// SPDX-License-Identifier: Apache-2.0
// Grounding Phase 1 (grammar 4.2) — the closed `semantics { … }` vocabulary.
//
// This table is NORMATIVE and mirrors `docs/features/semantics-block/README.md`
// §Vocabulary exactly. It is the cross-repo sync key with ai-platform's closed
// proto enums (`cz.dfpartner.metadata.v1` AttributeSemantics / EntitySemantics,
// feature-grounding-contracts.md §4): the vocabulary here and the proto enums
// version TOGETHER via SEMANTICS_VOCABULARY_VERSION (the md-catalog
// MD_CATALOG_VERSION precedent). Bump it whenever a role/kind is added or a
// signature changes, and cut the matching proto release in lock-step.

/** Cross-repo sync key — bumps in lock-step with ai-platform's proto enums. */
export const SEMANTICS_VOCABULARY_VERSION = 1 as const;

/** The type-family a role's attribute/column must declare. */
export type TypeConstraint = 'date' | 'text' | 'numeric';

/** Entity/table kinds (`kind:`). */
export const ENTITY_KINDS = ['period_table', 'calendar', 'poi', 'fx_rate'] as const;
export type EntityKind = (typeof ENTITY_KINDS)[number];

/** Cross-reference keys a role may carry beyond `role:` itself. */
export interface RoleSpec {
  /** Optional extra keys (besides `role`) this role accepts, with their kind. */
  readonly extraKeys: ReadonlyArray<{ key: string; kind: 'entityRef' | 'attrRef' | 'string'; required: boolean }>;
  /** The declared-type family the attribute/column must have (undefined = any). */
  readonly typeConstraint?: TypeConstraint;
}

/**
 * Attribute/column roles (`role:`) → their extra keys + type constraint. Mirrors
 * README §Vocabulary's role table 1:1.
 */
export const ATTRIBUTE_ROLES: Readonly<Record<string, RoleSpec>> = {
  period_start: { extraKeys: [], typeConstraint: 'date' },
  period_end: { extraKeys: [], typeConstraint: 'date' },
  period_code: { extraKeys: [{ key: 'code_format', kind: 'string', required: false }], typeConstraint: 'text' },
  event_date: { extraKeys: [{ key: 'period', kind: 'entityRef', required: false }], typeConstraint: 'date' },
  document_date: { extraKeys: [{ key: 'period', kind: 'entityRef', required: false }], typeConstraint: 'date' },
  posting_date: { extraKeys: [{ key: 'period', kind: 'entityRef', required: false }], typeConstraint: 'date' },
  due_date: { extraKeys: [{ key: 'period', kind: 'entityRef', required: false }], typeConstraint: 'date' },
  valid_from: { extraKeys: [], typeConstraint: 'date' },
  valid_to: { extraKeys: [], typeConstraint: 'date' },
  calendar_date: { extraKeys: [], typeConstraint: 'date' },
  geo_lat: { extraKeys: [], typeConstraint: 'numeric' },
  geo_lon: { extraKeys: [], typeConstraint: 'numeric' },
  geo_point: { extraKeys: [], typeConstraint: 'text' },
  amount: { extraKeys: [{ key: 'currency', kind: 'attrRef', required: false }], typeConstraint: 'numeric' },
  amount_domestic: { extraKeys: [], typeConstraint: 'numeric' },
  currency_code: { extraKeys: [], typeConstraint: 'text' },
  fx_from_currency: { extraKeys: [], typeConstraint: 'text' },
  fx_to_currency: { extraKeys: [], typeConstraint: 'text' },
  fx_rate: { extraKeys: [], typeConstraint: 'numeric' },
} as const;

export type AttributeRole = keyof typeof ATTRIBUTE_ROLES;

/**
 * A single required-role clause in a kind's completeness rule: the role must
 * appear exactly `count` times among the owner's attributes/columns.
 */
export interface CompletenessClause {
  readonly role: AttributeRole;
  readonly count: 1;
}

/**
 * Per-kind completeness rules (validated on the owning entity/table). `poi` is
 * special (geo_point XOR lat/lon pair) — handled in the validator, not as a flat
 * clause list — so it maps to an empty list here and is documented as such.
 */
export const KIND_COMPLETENESS: Readonly<Record<EntityKind, ReadonlyArray<CompletenessClause>>> = {
  period_table: [
    { role: 'period_start', count: 1 },
    { role: 'period_end', count: 1 },
    { role: 'period_code', count: 1 },
  ],
  calendar: [{ role: 'calendar_date', count: 1 }],
  // poi: exactly one geo_point XOR (one geo_lat AND one geo_lon) — the XOR is not
  // expressible as a flat count list, so the validator special-cases it (210).
  poi: [],
  fx_rate: [
    { role: 'fx_from_currency', count: 1 },
    { role: 'fx_to_currency', count: 1 },
    { role: 'fx_rate', count: 1 },
    // valid_from/valid_to are optional as a pair (211), not required — so absent here.
  ],
} as const;

export const ALL_ROLES: ReadonlyArray<string> = Object.keys(ATTRIBUTE_ROLES);

/** The keys legal at all: `role` plus every role's extra keys, deduped. */
export const ALL_ATTRIBUTE_KEYS: ReadonlyArray<string> = [
  'role',
  ...new Set(Object.values(ATTRIBUTE_ROLES).flatMap((r) => r.extraKeys.map((k) => k.key))),
];

/** The keys legal on an entity/table `semantics` block. */
export const ALL_ENTITY_KEYS: ReadonlyArray<string> = ['kind'];
