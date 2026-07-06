// Crow's-foot cardinality glyphs.
//
// All glyphs live in a local frame anchored at the edge endpoint:
//   * origin (0,0) is on the entity boundary
//   * +x points OUTWARD from the entity, along the edge (away from this entity)
//   * +y is perpendicular (90° CCW from +x)
// The Canvas overlay rotates the glyph so that +x lines up with the outward
// direction at each endpoint, and `scale(zoom)` is applied so glyphs grow and
// shrink with the canvas.
//
// Stroke color is set explicitly (relation edge green) so the glyph doesn't
// depend on `currentColor` inheritance through the overlay <div>.

import type { Cardinality } from '@tatrman/lsp';

const STROKE = '#10b981';   // matches edge[kind = "relation"] color
const STROKE_WIDTH = 2;
const LINE_ATTRS = `stroke="${STROKE}" stroke-width="${STROKE_WIDTH}" stroke-linecap="round" fill="none"`;

const L = 24;   // glyph length along the edge (2× the previous 16 px)
const W = 8;    // tine spread perpendicular to the edge
const R = 6;    // 'zero-or-one' circle radius (was 3.5)
const D = 14;   // 'one' bar distance from the endpoint
const GAP = 4;  // gap between 'zero-or-one' bar and circle

export function glyphFor(card: Cardinality | null): string {
  switch (card) {
    case 'one':
      return `<g class="glyph-one" ${LINE_ATTRS}>`
        + `<line x1="${D}" y1="${-W}" x2="${D}" y2="${W}"/>`
        + `</g>`;
    case 'zero-or-one': {
      const cx = D + R + GAP;
      return `<g class="glyph-zero-or-one" ${LINE_ATTRS}>`
        + `<line x1="${D}" y1="${-W}" x2="${D}" y2="${W}"/>`
        + `<circle cx="${cx}" cy="0" r="${R}"/>`
        + `</g>`;
    }
    case 'many':
      // Convergence outward at (L, 0), wide end (the three tine tips) touches
      // the entity boundary at x = 0.
      return `<g class="glyph-many" ${LINE_ATTRS}>`
        + `<line x1="${L}" y1="0" x2="0" y2="${-W}"/>`
        + `<line x1="${L}" y1="0" x2="0" y2="0"/>`
        + `<line x1="${L}" y1="0" x2="0" y2="${W}"/>`
        + `</g>`;
    case 'one-or-many':
      // Crow's-foot opens toward the entity (fan at x = 0); bar sits at the
      // outer convergence (x = L), further from the entity.
      return `<g class="glyph-one-or-many" ${LINE_ATTRS}>`
        + `<line x1="${L}" y1="${-W}" x2="${L}" y2="${W}"/>`
        + `<line x1="${L}" y1="0" x2="0" y2="${-W}"/>`
        + `<line x1="${L}" y1="0" x2="0" y2="0"/>`
        + `<line x1="${L}" y1="0" x2="0" y2="${W}"/>`
        + `</g>`;
    case null:
      return '';
  }
}
