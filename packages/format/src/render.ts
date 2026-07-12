// SPDX-License-Identifier: Apache-2.0
import type { Doc } from './ir.js';

type Mode = 'flat' | 'break';

// Does `doc`, rendered flat starting at column `col`, stay within `width`?
// `hardline` makes it impossible (returns false) — that forces a break.
function fits(doc: Doc, col: number, width: number): boolean {
  const stack: Doc[] = [doc];
  let pos = col;
  while (stack.length > 0) {
    const d = stack.pop()!;
    switch (d.t) {
      case 'text':
        pos += d.s.length;
        if (pos > width) return false;
        break;
      case 'verbatim':
        if (d.s.includes('\n')) return false; // multi-line value can't be flat
        pos += d.s.length;
        if (pos > width) return false;
        break;
      case 'line': // flat → single space
        pos += 1;
        if (pos > width) return false;
        break;
      case 'softline':
        break;
      case 'hardline':
        return false;
      case 'indent':
        stack.push(d.doc);
        break;
      case 'group':
        stack.push(d.doc);
        break;
      case 'concat':
        for (let i = d.parts.length - 1; i >= 0; i--) stack.push(d.parts[i]);
        break;
    }
  }
  return true;
}

export interface RenderOptions {
  width: number;
  indentSpaces: number;
}

export function render(doc: Doc, opts: RenderOptions): string {
  const { width, indentSpaces } = opts;
  let out = '';
  let col = 0;
  // Work stack of (indentLevel, mode, doc), processed LIFO.
  const stack: Array<{ indent: number; mode: Mode; doc: Doc }> = [{ indent: 0, mode: 'break', doc }];

  while (stack.length > 0) {
    const { indent: ind, mode, doc: d } = stack.pop()!;
    switch (d.t) {
      case 'text':
        out += d.s;
        col += d.s.length;
        break;
      case 'verbatim': {
        out += d.s;
        const nl = d.s.lastIndexOf('\n');
        col = nl === -1 ? col + d.s.length : d.s.length - nl - 1;
        break;
      }
      case 'concat':
        for (let i = d.parts.length - 1; i >= 0; i--) stack.push({ indent: ind, mode, doc: d.parts[i] });
        break;
      case 'indent':
        stack.push({ indent: ind + 1, mode, doc: d.doc });
        break;
      case 'group': {
        // A group renders flat if it fits on the remaining line; else broken.
        const flatMode: Mode = fits(d.doc, col, width) ? 'flat' : 'break';
        stack.push({ indent: ind, mode: flatMode, doc: d.doc });
        break;
      }
      case 'line':
        if (mode === 'flat') {
          out += ' ';
          col += 1;
        } else {
          out += '\n' + ' '.repeat(ind * indentSpaces);
          col = ind * indentSpaces;
        }
        break;
      case 'softline':
        if (mode === 'break') {
          out += '\n' + ' '.repeat(ind * indentSpaces);
          col = ind * indentSpaces;
        }
        break;
      case 'hardline':
        out += '\n' + ' '.repeat(ind * indentSpaces);
        col = ind * indentSpaces;
        break;
    }
  }
  return out;
}
