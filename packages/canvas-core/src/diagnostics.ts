// SPDX-License-Identifier: Apache-2.0
// The DS-* diagnostics registry (contracts §8). House convention: named, stable,
// fixture-backed — every id carries user-facing text now, even when its triggering feature
// lands in a later phase (`pending: true`, `phase` names when it goes live). A diagnostic
// without renderable text is not done.

export type DiagnosticSeverity = 'error' | 'warning' | 'info' | 'hint' | 'badge';

export type DsId =
  | 'DS-SKIN-001'
  | 'DS-SKIN-002'
  | 'DS-SKIN-003'
  | 'DS-CANV-001'
  | 'DS-CANV-002'
  | 'DS-PERSP-001'
  | 'DS-PERSP-002'
  | 'DS-SHELL-001'
  | 'DS-RUN-001'
  | 'DS-EDIT-001';

export interface DiagnosticDef {
  id: DsId;
  severity: DiagnosticSeverity;
  meaning: string;
  /** the phase whose feature makes this diagnostic fire for real */
  phase: string;
  /** no live trigger yet (P0 scaffold): the id + text exist; the feature arrives in `phase` */
  pending: boolean;
  /** the fixture stub — renders the user-facing text (given optional context) */
  text: (ctx?: Record<string, string>) => string;
}

export const DIAGNOSTICS: Record<DsId, DiagnosticDef> = {
  'DS-SKIN-001': {
    id: 'DS-SKIN-001', severity: 'error', phase: 'DS-P1', pending: false,
    meaning: 'skin claims a never-claimable slot — registration rejected (P-3 by construction)',
    text: (c) => `Skin "${c?.skin ?? '<skin>'}" claims the never-claimable slot "${c?.slot ?? '<slot>'}" — registration rejected.`,
  },
  'DS-SKIN-002': {
    id: 'DS-SKIN-002', severity: 'warning', phase: 'DS-P1', pending: false,
    meaning: 'unknown skin id in view-state — default applied, truth chip shows the substitution',
    text: (c) => `Unknown skin "${c?.skin ?? '<skin>'}" — falling back to default "${c?.fallback ?? '<default>'}".`,
  },
  'DS-SKIN-003': {
    id: 'DS-SKIN-003', severity: 'error', phase: 'DS-P1', pending: false,
    meaning: 'skin missing anchor declaration for an unclaimed base slot — registration rejected',
    text: (c) => `Skin "${c?.skin ?? '<skin>'}" declares no anchor for unclaimed base slot "${c?.slot ?? '<slot>'}" — registration rejected.`,
  },
  'DS-CANV-001': {
    id: 'DS-CANV-001', severity: 'badge', phase: 'DS-P1', pending: true,
    meaning: 'orphaned layout entry (node in view-state lost its model object) — the orphan mark',
    text: (c) => `Layout entry "${c?.node ?? '<node>'}" has no model object — shown with the orphan mark.`,
  },
  'DS-CANV-002': {
    id: 'DS-CANV-002', severity: 'info', phase: 'DS-P4', pending: false,
    meaning: 'derived canvas — read-only + auto-layout banner (fragments, perspectives)',
    text: () => 'Derived canvas — read-only, auto-layout only.',
  },
  'DS-PERSP-001': {
    id: 'DS-PERSP-001', severity: 'hint', phase: 'DS-P4', pending: false,
    meaning: 'lineage fullPath degraded to neighborhood — "runs need a platform backend" (G-5 honesty)',
    text: () => 'Full-path lineage degraded to neighborhood — runs need a platform backend.',
  },
  'DS-PERSP-002': {
    id: 'DS-PERSP-002', severity: 'warning', phase: 'DS-P4', pending: false,
    meaning: 'binding target unresolved (dangling bind) — rendered on the ribbon, not hidden',
    text: (c) => `Binding target "${c?.target ?? '<target>'}" is unresolved (dangling bind).`,
  },
  'DS-SHELL-001': {
    id: 'DS-SHELL-001', severity: 'warning', phase: 'DS-P2', pending: false,
    meaning: 'URL references a subject/object not in this workspace',
    text: (c) => `"${c?.ref ?? '<ref>'}" is not in this workspace.`,
  },
  'DS-RUN-001': {
    id: 'DS-RUN-001', severity: 'hint', phase: 'DS-P5', pending: false,
    meaning: 'run requested / run controls shown without a run-capable backend — disabled-with-hint',
    text: () => 'Run controls need a run-capable backend — disabled here.',
  },
  'DS-EDIT-001': {
    id: 'DS-EDIT-001', severity: 'info', phase: 'DS-P6', pending: false,
    meaning: 'edit gesture in read-only mode — routed to peek + open-in-IDE (A-3 read-only behavior)',
    text: () => 'Read-only — opening a text peek; edit in your IDE.',
  },
};

export const ALL_DIAGNOSTICS: DiagnosticDef[] = Object.values(DIAGNOSTICS);
