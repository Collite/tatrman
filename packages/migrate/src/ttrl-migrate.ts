// SPDX-License-Identifier: Apache-2.0
// .ttrg → .ttrl sidecar migration (T2, TP-5). Extracts a .ttrg file's in-file
// `layout` property into a paired `.ttrl` sidecar (C1-c-iii schema) and strips
// the property from the source, leaving `objects:`/`schema:`/etc. untouched.
//
// Not to be confused with `index.ts`'s `convertTtrlToTtrg` (see
// `__tests__/ttrl-to-ttrg.test.ts`) — that's the OLDER, unrelated v1→v1.1
// migration going the OPPOSITE direction, off a project-wide JSON
// `.modeler/layout.ttrl` (no relation to the TTR-grammar per-document `.ttrl`
// this file produces beyond an unfortunate extension reuse across eras).
//
// `GraphLayout.nodes` keys are ALREADY full qnames (confirmed against
// `samples/v1.1-mini/graphs/all_er.ttrg`) — the same ζ-key identity T1's
// `TtrmLayoutService` reads (see its class doc). So this is a pure syntactic
// move, no registry/qname resolution needed: a node's in-file key becomes its
// sidecar node key unchanged.
//
// The `.ttrl` serializer below is a hand-kept TS port of the canonical Kotlin
// writer (`packages/kotlin/ttr-writer/.../TtrlWriter.kt`) — same determinism
// rules (canonical order, integral-coordinate formatting, 4-space indent, LF).
// Not a shared artifact (this is a one-shot CLI, not a published package), so
// what actually matters is that the real Kotlin `TtrlLoader` can parse this
// tool's output — proven by `ttrl-migrate.test.ts`'s fixture-based checks,
// mirrored against the grammar `TtrlLoader.kt` itself enforces.
//
// `viewport` (zoom/panX/panY/displayMode) has no home in `.ttrl` v1 (dropped,
// C1-c-iii, matching TTR-P's own layout) — it's a real, reported drop, not a
// silent one: `TtrlMigrationResult.viewportDropped` surfaces it.

import { parseString } from '@tatrman/parser';
import type { GraphBlock, GraphLayout } from '@tatrman/parser';
import { unifiedDiff } from './text-diff.js';

export interface TtrlMigrationResult {
  ttrgPath: string;
  sidecarPath: string;
  ttrgBefore: string;
  ttrgAfter: string;
  ttrl: string;
  diff: string;
  nodeCount: number;
  viewportDropped: boolean;
}

export type TtrlMigrationSkipReason = 'no-graph-block' | 'no-layout-property' | 'parse-error' | 'reparse-failed';

export interface TtrlMigrationSkip {
  ttrgPath: string;
  reason: TtrlMigrationSkipReason;
  detail?: string;
}

/** `x.ttrg` → `x.ttrl`, same directory. Mirrors the Kotlin `TtrmLayoutService.sidecarPath` pairing. */
export function sidecarPathFor(ttrgPath: string): string {
  const base = ttrgPath.endsWith('.ttrg') ? ttrgPath.slice(0, -'.ttrg'.length) : ttrgPath;
  return `${base}.ttrl`;
}

/**
 * Plan the migration for one `.ttrg` file's text (pure — no fs). Returns a
 * result to write, or a skip reason (idempotent: a file with no `layout`
 * property — including one already migrated — is a clean skip, not an error).
 */
export function planTtrlExtraction(ttrgPath: string, text: string): TtrlMigrationResult | TtrlMigrationSkip {
  const { ast, errors } = parseString(text, ttrgPath);
  if (!ast || errors.length > 0) {
    return { ttrgPath, reason: 'parse-error', detail: errors.map((e) => e.message).join('; ') };
  }
  const graph = ast.graph;
  if (!graph) return { ttrgPath, reason: 'no-graph-block' };
  if (!graph.layout) return { ttrgPath, reason: 'no-layout-property' };

  const span = findLayoutSpan(text, graph);
  if (!span) return { ttrgPath, reason: 'no-layout-property', detail: 'layout present in AST but its text span could not be located' };

  const ttrgAfter = text.slice(0, span.start) + text.slice(span.end);

  // Re-parse verification (this migration is structural, not a token rename —
  // hold it to a higher bar than qname-migrate's .ttrg handling): the result
  // must still parse, still carry the same objects, and no longer carry layout.
  const reparsed = parseString(ttrgAfter, ttrgPath);
  if (!reparsed.ast || reparsed.errors.length > 0) {
    return { ttrgPath, reason: 'reparse-failed', detail: reparsed.errors.map((e) => e.message).join('; ') };
  }
  if (reparsed.ast.graph?.layout) {
    return { ttrgPath, reason: 'reparse-failed', detail: 'layout property still present after strip' };
  }
  if (JSON.stringify(reparsed.ast.graph?.objects) !== JSON.stringify(graph.objects)) {
    return { ttrgPath, reason: 'reparse-failed', detail: 'objects list changed by the strip — refusing to write' };
  }

  const ttrl = writeTtrl(graph.name, graph.layout);
  const sidecarPath = sidecarPathFor(ttrgPath);
  return {
    ttrgPath,
    sidecarPath,
    ttrgBefore: text,
    ttrgAfter,
    ttrl,
    diff: unifiedDiff(ttrgPath, text, ttrgAfter),
    nodeCount: Object.keys(graph.layout.nodes).length,
    viewportDropped: graph.layout.viewport !== undefined,
  };
}

export function isSkip(r: TtrlMigrationResult | TtrlMigrationSkip): r is TtrlMigrationSkip {
  return 'reason' in r;
}

/**
 * Locate the `layout: { … }` property's exact text span within [graph]'s own
 * source extent, brace-matched (string-literal-aware, since node ζ keys are
 * quoted qnames that may themselves contain `{`/`}`-free but arbitrary text).
 * Also absorbs the one separating comma — whichever side has it — so removal
 * never leaves a dangling `,` next to a sibling property or the closing `}`.
 */
function findLayoutSpan(text: string, graph: GraphBlock): { start: number; end: number } | null {
  const blockStart = graph.source.offsetStart;
  const blockEnd = graph.source.offsetEnd;
  const graphText = text.slice(blockStart, blockEnd);

  const keyMatch = /\blayout\s*:/.exec(graphText);
  if (!keyMatch) return null;
  let i = keyMatch.index + keyMatch[0].length;
  while (i < graphText.length && /\s/.test(graphText[i])) i++;
  if (graphText[i] !== '{') return null;

  let depth = 0;
  let inString = false;
  let j = i;
  for (; j < graphText.length; j++) {
    const c = graphText[j];
    if (inString) {
      if (c === '\\') {
        j++;
        continue;
      }
      if (c === '"') inString = false;
      continue;
    }
    if (c === '"') {
      inString = true;
      continue;
    }
    if (c === '{') depth++;
    else if (c === '}') {
      depth--;
      if (depth === 0) {
        j++;
        break;
      }
    }
  }
  if (depth !== 0) return null; // unbalanced — bail, caller treats as not-found rather than guessing

  let start = keyMatch.index;
  let end = j;

  // Prefer absorbing a LEADING comma (layout was last/middle property).
  let b = start - 1;
  while (b >= 0 && /\s/.test(graphText[b])) b--;
  if (b >= 0 && graphText[b] === ',') {
    start = b;
  } else {
    // Else absorb a TRAILING comma (layout was the first property).
    let f = end;
    while (f < graphText.length && /\s/.test(graphText[f])) f++;
    if (graphText[f] === ',') end = f + 1;
  }

  return { start: blockStart + start, end: blockStart + end };
}

const INDENT = '    ';

/** TS port of `TtrlWriter.write` for a single-canvas document (one `.ttrg` file = one canvas). */
function writeTtrl(canvasKey: string, layout: GraphLayout): string {
  const lines: string[] = [];
  lines.push('ttrl 1', '');
  lines.push(`canvas ${canvasKey} {`);
  lines.push(`${INDENT}mode: manual`);
  const entries = Object.entries(layout.nodes).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));
  if (entries.length > 0) {
    lines.push(`${INDENT}nodes: {`);
    for (const [zeta, pos] of entries) {
      lines.push(`${INDENT}${INDENT}${quote(zeta)}: { x: ${num(pos.x)}, y: ${num(pos.y)} }`);
    }
    lines.push(`${INDENT}}`);
  }
  lines.push('}');
  return lines.join('\n') + '\n';
}

function quote(s: string): string {
  return `"${s.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

/**
 * Matches `TtrlWriter.num`'s intent (integral coords print without a decimal).
 * `String()` already does this in JS (no `120.0` the way `Double.toString()`
 * prints in Kotlin) — kept as a named function so the parity with the Kotlin
 * writer is documented, not because the formatting needs help.
 */
function num(v: number): string {
  return String(v);
}
