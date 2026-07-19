// SPDX-License-Identifier: Apache-2.0
// Pop-out (A-2 γ / S-7). Pinning a perspective/view pops it out as its own PREVIEW tab
// (transient italic → pin promotes). In P2 the only poppable view is a canvas snapshot
// placeholder; real perspectives (DS-P4) reuse the PerspectivePopoutHost seam untouched. The
// pop-out tab's URL is the contracts §6 format of its state (a shareable link).

import type { ShellState, Subject, SubjectTab, UrlState } from './types.js';
import { openSubject } from './shell-state.js';
import { formatPath } from './url.js';

export interface PopoutView {
  subject: Subject;
  perspective?: 'binding' | 'lineage';
}

/** The seam DS-P4 binding/lineage generators pop out through. */
export interface PerspectivePopoutHost {
  popOut(view: PopoutView): void;
}

/** pop a view out as a preview tab carrying its full state (S-7 preview discipline). */
export function popOut(state: ShellState, view: PopoutView): ShellState {
  return openSubject(state, view.subject, { preview: true, perspective: view.perspective });
}

/** the pop-out tab's shareable URL (contracts §6). */
export function tabUrl(workspace: string, tab: SubjectTab): string {
  const state: UrlState = tab.perspective
    ? { kind: 'perspective', workspace, perspective: tab.perspective, params: { subject: tab.subject.ref } }
    : { kind: 'subject', workspace, subjectRef: tab.subject.ref, ...(tab.skin ? { skin: tab.skin } : {}) };
  return formatPath(state);
}
