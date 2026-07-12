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
      const onlyNew = [...newSet].filter((k) => !oldSet.has(k));
      expect(onlyOld, `diagnostics only in the frozen snapshot for ${uri}`).toEqual([]);
      expect(onlyNew, `diagnostics only the new runner produced for ${uri}`).toEqual([]);
    }
  });
});
