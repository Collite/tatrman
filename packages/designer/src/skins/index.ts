// The app-owned SkinRegistry (contracts §1.4 — v1 built-ins compiled in). DS-P1 registers
// the two modeling skins; DS-P3/P5 add md/cnc/processing skins to this list only.

import { SkinRegistry } from '@tatrman/canvas-core';
import { asSkinDefinition, type DesignerSkin } from '../canvas/skin-component.js';
import { erCrow } from './er-crow.js';
import { dbTableClassic } from './db-table-classic.js';
import { mdStarGlyph } from './md-star-glyph.js';
import { mdErDialect } from './md-er-dialect.js';
import { cncBubbles } from './cnc-bubbles.js';
import { cncCards } from './cnc-cards.js';
import { stage } from './stage.js';
import { script } from './script.js';

// DS-P1 registered db/er; DS-P3 adds the md + cnc rosters; DS-P5 adds the processing pair
// (stage default per E-3a, live in canvas-core's DEFAULT_SKINS).
export const BUILTIN_SKINS: DesignerSkin[] = [
  dbTableClassic, erCrow,
  mdStarGlyph, mdErDialect,
  cncBubbles, cncCards,
  stage, script,
];

export function createSkinRegistry(): SkinRegistry {
  const registry = new SkinRegistry();
  for (const skin of BUILTIN_SKINS) registry.register(asSkinDefinition(skin));
  return registry;
}

export { erCrow, dbTableClassic, mdStarGlyph, mdErDialect, cncBubbles, cncCards, stage, script };
