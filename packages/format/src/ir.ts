// SPDX-License-Identifier: Apache-2.0
// Formatter intermediate representation — a small Wadler/Prettier-style "doc"
// tree. Build a Doc with the helpers, then render() it (see render.ts).
//
//   text("x")              literal text (must not contain newlines except via verbatim)
//   concat([a, b])         sequence
//   line                   space when the enclosing group is flat, newline when broken
//   softline               nothing when flat, newline when broken
//   hardline               always a newline (forces the enclosing group to break)
//   indent(d)              increase indentation by one level for newlines inside d
//   group(d)               render d flat if it fits the width, else broken

export type Doc =
  | { t: 'text'; s: string }
  | { t: 'verbatim'; s: string }
  | { t: 'concat'; parts: Doc[] }
  | { t: 'line' }
  | { t: 'softline' }
  | { t: 'hardline' }
  | { t: 'indent'; doc: Doc }
  | { t: 'group'; doc: Doc };

export function text(s: string): Doc {
  return { t: 'text', s };
}
/** Emit `s` exactly — including any internal newlines, which are NOT re-indented.
 *  Used for source-sliced values (triple-strings etc.); a multi-line verbatim
 *  forces the enclosing group to break. */
export function verbatim(s: string): Doc {
  return { t: 'verbatim', s };
}
export function concat(parts: Doc[]): Doc {
  return { t: 'concat', parts };
}
export const line: Doc = { t: 'line' };
export const softline: Doc = { t: 'softline' };
export const hardline: Doc = { t: 'hardline' };
export function indent(doc: Doc): Doc {
  return { t: 'indent', doc };
}
export function group(doc: Doc): Doc {
  return { t: 'group', doc };
}

/** Join `docs` with `sep` between each. */
export function join(docs: Doc[], sep: Doc): Doc {
  const parts: Doc[] = [];
  docs.forEach((d, i) => {
    if (i > 0) parts.push(sep);
    parts.push(d);
  });
  return concat(parts);
}
