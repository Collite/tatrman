import type { GraphEdit } from './graph-edits.js';

export type ApplyOutcome = { ok: true } | { ok: false; stale: boolean; message?: string };

export interface EditContext {
  /** The version to stamp on the next applyGraphEdit. */
  version(): number;
  /** Send edits at [version]; a stale document ⇒ `{ ok: false, stale: true }` (TTRP-EDIT-001). */
  apply(edits: GraphEdit[], version: number): Promise<ApplyOutcome>;
  /** Re-pull graph + layout after a stale error; returns the fresh version. */
  refresh(): Promise<number>;
}

export type SubmitResult = { ok: true } | { ok: false; message: string };

/**
 * Submit β edits with the versioned stale-reject → replay discipline (C1-d-iii): stamp the
 * current document version; on a stale error re-pull graph+layout and replay the SAME edits
 * against the new version, bounded to [maxAttempts] (default 3) before surfacing an error.
 */
export async function submitEdits(
  edits: GraphEdit[],
  ctx: EditContext,
  maxAttempts = 3,
): Promise<SubmitResult> {
  let version = ctx.version();
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const outcome = await ctx.apply(edits, version);
    if (outcome.ok) return { ok: true };
    if (!outcome.stale) return { ok: false, message: outcome.message ?? 'edit rejected' };
    version = await ctx.refresh();
  }
  return { ok: false, message: `edit still stale after ${maxAttempts} attempts — reload the graph` };
}
