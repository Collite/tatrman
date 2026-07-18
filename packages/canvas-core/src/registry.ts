// SPDX-License-Identifier: Apache-2.0
// SkinRegistry (contracts §1.4) with REGISTRATION-TIME enforcement (P-3 by construction —
// D-1). A skin that could hide semantics is rejected when registered, not caught in review.

import type { SkinDefinition, SkinId, ClaimableSlot } from './contract.js';
import { CLAIMABLE_SLOTS } from './contract.js';
import type { SchemaCode } from './types.js';

export type SkinRejectCode = 'DS-SKIN-001' | 'DS-SKIN-003' | 'duplicate-id';

export class SkinRegistrationError extends Error {
  constructor(public readonly code: SkinRejectCode, public readonly skinId: SkinId, message: string) {
    super(`[${code}] skin "${skinId}": ${message}`);
    this.name = 'SkinRegistrationError';
  }
}

/** Pinned E-3a defaults (contracts §1.4). Policy, not registration order — `defaultSkin`
 *  returns these regardless of what is registered. */
export const DEFAULT_SKINS = {
  processing: 'stage',
  db: 'db.table-classic',
  er: 'er.crow',
  md: 'md.star-glyph',
  cnc: 'cnc.bubbles',
} as const;

const PROBE_SIZE = { width: 160, height: 72 };

export class SkinRegistry {
  private readonly byId = new Map<SkinId, SkinDefinition>();
  private readonly order: SkinId[] = []; // registration order (roster ordering)

  register(skin: SkinDefinition): void {
    // (c) duplicate id
    if (this.byId.has(skin.id)) {
      throw new SkinRegistrationError('duplicate-id', skin.id, 'id already registered');
    }

    // (a) claims a never-claimable slot → DS-SKIN-001 (runtime, even if types were bypassed)
    if (skin.claims) {
      const allowed = new Set<string>(CLAIMABLE_SLOTS as readonly string[]);
      for (const key of Object.keys(skin.claims)) {
        if (!allowed.has(key)) {
          throw new SkinRegistrationError('DS-SKIN-001', skin.id, `claims never-claimable slot "${key}"`);
        }
      }
    }

    // (b) missing anchor for an unclaimed base slot → DS-SKIN-003
    const anchors = skin.declareAnchors(PROBE_SIZE);
    if (!anchors.chrome) {
      throw new SkinRegistrationError('DS-SKIN-003', skin.id, 'no anchor declared for "chrome" (never-claimable, always required)');
    }
    if (!skin.claims?.status && !anchors.status) {
      throw new SkinRegistrationError('DS-SKIN-003', skin.id, 'no anchor declared for unclaimed base slot "status"');
    }
    if (!skin.claims?.diagnostics && !anchors.diagnostics) {
      throw new SkinRegistrationError('DS-SKIN-003', skin.id, 'no anchor declared for unclaimed base slot "diagnostics"');
    }

    this.byId.set(skin.id, skin);
    this.order.push(skin.id);
  }

  /** unknown id ⇒ undefined; the caller applies the default + DS-SKIN-002 (contracts §8) */
  resolve(id: SkinId): SkinDefinition | undefined {
    return this.byId.get(id);
  }

  roster(face: 'processing'): SkinDefinition[];
  roster(face: 'modeling', kind: SchemaCode): SkinDefinition[];
  roster(face: 'processing' | 'modeling', kind?: SchemaCode): SkinDefinition[] {
    return this.order
      .map((id) => this.byId.get(id)!)
      .filter((s) => s.face === face && (face === 'processing' || s.modelKind === kind));
  }

  defaultSkin(face: 'processing'): SkinId;
  defaultSkin(face: 'modeling', kind: SchemaCode): SkinId;
  defaultSkin(face: 'processing' | 'modeling', kind?: SchemaCode): SkinId {
    if (face === 'processing') return DEFAULT_SKINS.processing;
    return DEFAULT_SKINS[kind as keyof typeof DEFAULT_SKINS] ?? DEFAULT_SKINS.er;
  }

  has(id: SkinId): boolean {
    return this.byId.has(id);
  }
}

export type { ClaimableSlot };
