// SPDX-License-Identifier: Apache-2.0
import type { Document } from '@tatrman/parser';
import type { Trivia } from '@tatrman/parser';
import type { RuleId } from './rule.js';

export interface SuppressionIndex {
  /** Whether `ruleId` is suppressed on `line` (1-indexed). Marks the directive used. */
  isSuppressed(ruleId: RuleId, line: number): boolean;
  /** Whether a directive *targets* `ruleId`/`line` without marking it used (for the cannot-suppress guard). */
  targets(ruleId: RuleId, line: number): boolean;
  /** Directives that matched nothing → `ttrlint/unused-suppression`. */
  unused(): Array<{ line: number; ruleId?: RuleId }>;
}

type DirectiveKind = 'next-line' | 'line' | 'file' | 'disable' | 'enable';

interface Directive {
  kind: DirectiveKind;
  /** Explicit rule ids, or null for "all rules". */
  ids: string[] | null;
  /** The comment's own line (for `unused()` reporting + range boundaries). */
  commentLine: number;
  /** The line a next-line/line directive suppresses (the owner node's line). */
  targetLine: number;
  // usage tracking
  usedAll: boolean;
  usedIds: Set<string>;
}

const DIRECTIVE_RE =
  /(?:\/\/|\/\*)\s*ttr-(disable-next-line|disable-line|disable-file|disable|enable)\b([^*]*)/;

function parseDirective(text: string): { kind: DirectiveKind; ids: string[] | null } | undefined {
  const m = DIRECTIVE_RE.exec(text);
  if (!m) return undefined;
  const raw = m[1];
  const kind: DirectiveKind =
    raw === 'disable-next-line'
      ? 'next-line'
      : raw === 'disable-line'
        ? 'line'
        : raw === 'disable-file'
          ? 'file'
          : (raw as 'disable' | 'enable');
  const ids = m[2]
    .split(/[\s,]+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
  return { kind, ids: ids.length ? ids : null };
}

function matchesId(d: Directive, ruleId: RuleId): boolean {
  return d.ids === null || d.ids.includes(ruleId);
}

function markUsed(d: Directive, ruleId: RuleId): void {
  if (d.ids === null) d.usedAll = true;
  else d.usedIds.add(ruleId);
}

class Index implements SuppressionIndex {
  constructor(private readonly directives: Directive[]) {}

  private active(ruleId: RuleId, line: number): Directive | undefined {
    // file-level
    for (const d of this.directives) {
      if (d.kind === 'file' && matchesId(d, ruleId)) return d;
    }
    // same-line / next-line
    for (const d of this.directives) {
      if ((d.kind === 'next-line' || d.kind === 'line') && d.targetLine === line && matchesId(d, ruleId)) {
        return d;
      }
    }
    // disable…enable ranges: the latest matching `disable` at/above `line` with
    // no matching `enable` between it and `line`.
    let best: Directive | undefined;
    for (const d of this.directives) {
      if (d.kind !== 'disable' || d.commentLine > line || !matchesId(d, ruleId)) continue;
      const closed = this.directives.some(
        (e) =>
          e.kind === 'enable' &&
          matchesId(e, ruleId) &&
          e.commentLine > d.commentLine &&
          e.commentLine <= line
      );
      if (!closed && (!best || d.commentLine > best.commentLine)) best = d;
    }
    return best;
  }

  isSuppressed(ruleId: RuleId, line: number): boolean {
    const d = this.active(ruleId, line);
    if (!d) return false;
    markUsed(d, ruleId);
    return true;
  }

  targets(ruleId: RuleId, line: number): boolean {
    return this.active(ruleId, line) !== undefined;
  }

  unused(): Array<{ line: number; ruleId?: RuleId }> {
    const out: Array<{ line: number; ruleId?: RuleId }> = [];
    for (const d of this.directives) {
      // `enable` directives are bookends, never "unused" on their own.
      if (d.kind === 'enable') continue;
      if (d.ids === null) {
        if (!d.usedAll) out.push({ line: d.commentLine });
      } else {
        for (const id of d.ids) if (!d.usedIds.has(id)) out.push({ line: d.commentLine, ruleId: id });
      }
    }
    return out;
  }
}

/**
 * Build a suppression index from the document's comment trivia (P0). Reads
 * `// ttr-disable-*` directives from `leadingTrivia`/`trailingTrivia`:
 *  - `disable-next-line` (a leading comment) → suppresses the owner node's line;
 *  - `disable-line` (a trailing comment) → suppresses the owner node's line;
 *  - `disable-file` → suppresses every line;
 *  - `disable` … `enable` → a line range.
 */
export function buildSuppressionIndex(ast: Document): SuppressionIndex {
  const directives: Directive[] = [];

  const visit = (value: unknown): void => {
    if (value === null || typeof value !== 'object') return;
    if (Array.isArray(value)) {
      for (const item of value) visit(item);
      return;
    }
    const obj = value as Record<string, unknown> & {
      source?: { line: number };
      leadingTrivia?: Trivia[];
      trailingTrivia?: Trivia[];
    };
    const ownerLine = obj.source?.line ?? 0;
    collect(obj.leadingTrivia, ownerLine, directives);
    collect(obj.trailingTrivia, ownerLine, directives);
    for (const key in obj) {
      if (key === 'source' || key === 'leadingTrivia' || key === 'trailingTrivia') continue;
      visit(obj[key]);
    }
  };
  visit(ast);

  return new Index(directives);
}

function collect(trivia: Trivia[] | undefined, ownerLine: number, out: Directive[]): void {
  for (const t of trivia ?? []) {
    if (t.kind !== 'line-comment' && t.kind !== 'block-comment') continue;
    const parsed = parseDirective(t.text);
    if (!parsed) continue;
    // next-line/line target the owner node's line; file/disable/enable use the
    // directive's own comment line.
    const targetLine = parsed.kind === 'next-line' || parsed.kind === 'line' ? ownerLine : t.source.line;
    out.push({
      kind: parsed.kind,
      ids: parsed.ids,
      commentLine: t.source.line,
      targetLine,
      usedAll: false,
      usedIds: new Set(),
    });
  }
}
