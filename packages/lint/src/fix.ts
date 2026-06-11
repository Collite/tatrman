import type { WorkspaceEdit, TextEdit } from '@modeler/edit';
import type { DiagnosticCode } from '@modeler/parser';
import type { LintDiagnostic, RuleContext } from './rule.js';
import { ruleForCode } from './registry.js';

export interface FixResult {
  /** Merged, non-overlapping edit for all included safe fixes. */
  edit: WorkspaceEdit;
  /** Diagnostics whose safe fix was included this pass. */
  applied: LintDiagnostic[];
  /** Overlapping fixes deferred to the next pass. */
  deferred: LintDiagnostic[];
}

interface OffsetEdit {
  start: number;
  end: number;
  newText: string;
}

/**
 * Collect the `safe` fixes for one document and merge their non-overlapping
 * edits. Overlapping fixes are dropped into `deferred` for the next `--fix`
 * pass (eslint's model). `suggestion` fixes are never included.
 */
export function collectSafeFixes(diags: LintDiagnostic[], ctx: RuleContext): FixResult {
  const text = ctx.scope === 'document' ? ctx.text ?? '' : '';
  const lineStarts = computeLineStarts(text);
  const uri = ctx.scope === 'document' ? ctx.uri : '';

  const candidates: Array<{ diag: LintDiagnostic; edits: OffsetEdit[] }> = [];
  for (const diag of diags) {
    if (diag.code.startsWith('ttrlint/')) continue; // tool diagnostics have no rule fix
    const rule = ruleForCode(diag.code as DiagnosticCode);
    if (!rule?.fix || rule.fix.kind !== 'safe') continue;
    const ws = rule.fix.build(ctx, diag);
    const edits = offsetEditsFor(ws, uri, lineStarts, text.length);
    if (edits.length === 0) continue;
    candidates.push({ diag, edits });
  }

  // Sort by start so overlap detection is a single sweep.
  candidates.sort((a, b) => a.edits[0].start - b.edits[0].start);

  const merged: OffsetEdit[] = [];
  const applied: LintDiagnostic[] = [];
  const deferred: LintDiagnostic[] = [];
  let lastEnd = -1;
  for (const c of candidates) {
    const minStart = Math.min(...c.edits.map((e) => e.start));
    const maxEnd = Math.max(...c.edits.map((e) => e.end));
    if (minStart < lastEnd) {
      deferred.push(c.diag);
      continue;
    }
    merged.push(...c.edits);
    applied.push(c.diag);
    lastEnd = maxEnd;
  }

  return { edit: toWorkspaceEdit(uri, merged, lineStarts, text), applied, deferred };
}

function offsetEditsFor(ws: WorkspaceEdit, uri: string, lineStarts: number[], max: number): OffsetEdit[] {
  const out: OffsetEdit[] = [];
  for (const change of ws.documentChanges ?? []) {
    if (!('textDocument' in change)) continue;
    if (uri && change.textDocument.uri !== uri) continue;
    for (const e of change.edits as TextEdit[]) {
      out.push({
        start: offsetOf(lineStarts, e.range.start.line, e.range.start.character, max),
        end: offsetOf(lineStarts, e.range.end.line, e.range.end.character, max),
        newText: e.newText,
      });
    }
  }
  return out;
}

function toWorkspaceEdit(uri: string, edits: OffsetEdit[], lineStarts: number[], text: string): WorkspaceEdit {
  if (edits.length === 0) return { documentChanges: [] };
  const textEdits: TextEdit[] = edits.map((e) => ({
    range: { start: positionAt(text, e.start), end: positionAt(text, e.end) },
    newText: e.newText,
  }));
  return { documentChanges: [{ textDocument: { uri, version: null }, edits: textEdits }] };
}

function computeLineStarts(text: string): number[] {
  const starts = [0];
  for (let i = 0; i < text.length; i++) if (text[i] === '\n') starts.push(i + 1);
  return starts;
}

function offsetOf(lineStarts: number[], line: number, character: number, max: number): number {
  if (line >= lineStarts.length) return max;
  return Math.min(lineStarts[line] + character, max);
}

function positionAt(text: string, offset: number): { line: number; character: number } {
  const clamped = Math.max(0, Math.min(offset, text.length));
  let line = 0;
  let lineStart = 0;
  for (let i = 0; i < clamped; i++) {
    if (text[i] === '\n') {
      line++;
      lineStart = i + 1;
    }
  }
  return { line, character: clamped - lineStart };
}
