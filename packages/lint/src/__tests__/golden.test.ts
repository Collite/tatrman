// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { lintDocument, lintProject } from '../runner.js';
import { recommendedConfig, buildProject } from './helpers.js';
import { CORPUS, PROJECT_ROOT, diagKey } from './golden-corpus.js';
import snapshot from './golden-snapshot.json';

// Golden parity: under a `recommended`-equivalent config the new rule runner must
// reproduce the old Validator's diagnostics (code + severity + exact source +
// message), per uri. The snapshot was frozen from the old Validator pipeline (the
// LSP orchestration) before that class was deleted — this is the permanent
// regression net authorising the deletion.

const SNAP = snapshot as Record<string, string[]>;

/**
 * Codes introduced AFTER the snapshot was frozen.
 *
 * The snapshot is a record of what the old Validator produced, and it has to keep
 * meaning exactly that — writing a row into it for a rule that class never had would
 * quietly turn the regression net into a rubber stamp. So a deliberately-added rule is
 * allowed through here, BY NAME, and the file stays honest.
 *
 * `onlyOld` is never filtered: losing a diagnostic the old Validator produced is the
 * regression this test exists to catch, and no new rule can excuse one.
 *
 * - `TTR-SEM-218` — `semantics-legacy-mention-deprecated` (MS, vocabulary v3). Fires on
 *   `entity.ttrm`'s `nameAttribute: ghost`: contracts §1.2 row 1, legacy-only ⇒ WARN.
 *   ⚑ It is the first warning-level semantics rule, and it fires on EVERY model still
 *   using `nameAttribute:`/`codeAttribute:` — which is the point of a deprecation, but
 *   it does mean estates see new warnings the moment they take this release.
 */
const POST_FREEZE_CODES = new Set(['TTR-SEM-218']);

/** `diagKey` joins with U+0001; the code is the first field. */
const codeOf = (key: string): string => key.split('\u0001')[0];

describe('golden parity: new runner == frozen Validator snapshot (recommended)', () => {
  it('produces byte-identical diagnostic sets per uri', () => {
    const project = buildProject(CORPUS, PROJECT_ROOT);
    const config = recommendedConfig();
    const projectByUri = lintProject(project.documents, project.graph, project.deps, config);

    for (const [uri, ast] of project.documents) {
      const docNew = lintDocument(uri, ast, project.deps, config);
      const projNew = projectByUri.get(uri) ?? [];
      const newSet = new Set([...docNew, ...projNew].map(diagKey));
      const oldSet = new Set(SNAP[uri] ?? []);

      const onlyOld = [...oldSet].filter((k) => !newSet.has(k));
      const onlyNew = [...newSet].filter((k) => !oldSet.has(k) && !POST_FREEZE_CODES.has(codeOf(k)));
      expect(onlyOld, `diagnostics only in the frozen snapshot for ${uri}`).toEqual([]);
      expect(onlyNew, `diagnostics only the new runner produced for ${uri}`).toEqual([]);
    }
  });
});
