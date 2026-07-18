// SPDX-License-Identifier: Apache-2.0
// DM-CAP-* honest-degradation helpers (Designer Merge, DM-P1 / contracts §1.2, §6).
//
// The shell renders capabilities, never assumes them. When the active backend cannot serve a
// read the user reached for, these helpers return a disabled-with-hint signal (never a dead
// control, never silent absence — the DS "nothing invisible" P-3 invariant, extended to the
// backend matrix). The shell (DM-P2) turns a non-null hint into a visible, disabled affordance.

import type { DataSourceCapabilities, ModelKind } from './model-data-source.js';

export type CapCode = 'DM-CAP-001' | 'DM-CAP-002' | 'DM-CAP-003' | 'DM-CAP-004';

export interface CapabilityHint {
  readonly code: CapCode;
  readonly severity: 'hint' | 'info' | 'warning';
  readonly message: string;
}

/** DM-CAP-001 — a perspective (binding/lineage) was reached for on a backend that can't generate it. */
export function perspectiveHint(caps: DataSourceCapabilities): CapabilityHint | null {
  if (caps.perspectives) return null;
  return {
    code: 'DM-CAP-001',
    severity: 'hint',
    message: caps.bindings
      ? 'Perspectives are unavailable on this backend.'
      : 'Perspectives need a bindings-capable backend (open a project locally to use them).',
  };
}

/**
 * DM-CAP-002 — a model kind the active backend does not serve at all.
 * (The second, structural-only case of §1.1a — a kind served without slot bodies — lands with the
 * DS shell's rich-graph consumer in DM-P2, which is where the shape axis becomes observable.)
 */
export function modelKindHint(caps: DataSourceCapabilities, kind: ModelKind): CapabilityHint | null {
  if (caps.modelKinds.includes(kind)) return null;
  return {
    code: 'DM-CAP-002',
    severity: 'hint',
    message: `This backend serves ${caps.modelKinds.join('/')} — not ${kind}.`,
  };
}

/** DM-CAP-003 — the active backend does not persist layout (auto-layout only); say so, never silently. */
export function layoutPersistHint(caps: DataSourceCapabilities): CapabilityHint | null {
  if (caps.layoutPersist !== 'none') return null;
  return {
    code: 'DM-CAP-003',
    severity: 'info',
    message: 'Layout is auto-arranged on this backend and is not saved.',
  };
}

/** DM-CAP-004 — a capability was advertised but the call failed; surface it, never swallow it. */
export function backendFailureHint(capability: string, detail: string): CapabilityHint {
  return {
    code: 'DM-CAP-004',
    severity: 'warning',
    message: `${capability} was expected on this backend but failed: ${detail}`,
  };
}
