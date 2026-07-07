import { alteryxKnime } from './alteryx-knime.js';
import { enso } from './enso.js';
import type { Skin, SkinId } from './types.js';

export const SKINS: Record<SkinId, Skin> = {
  'alteryx-knime': alteryxKnime,
  enso,
};

export const DEFAULT_SKIN: SkinId = 'alteryx-knime';

export function skinFor(id: string | null | undefined): Skin {
  return SKINS[(id as SkinId) ?? DEFAULT_SKIN] ?? SKINS[DEFAULT_SKIN];
}

export type { Skin, SkinId } from './types.js';
