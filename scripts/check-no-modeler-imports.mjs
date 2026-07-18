// SPDX-License-Identifier: Apache-2.0
// DM-PORT-001 guard: no `@modeler/*` import may survive in ported Designer-merge packages.
// (Designer Merge arc — see project/common/frontends-offering/designer/merge/.)
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
const ROOTS = ['packages/canvas-core/src', 'packages/perspectives/src'];
const bad = [];
function walk(dir) {
  for (const e of readdirSync(dir)) {
    const p = join(dir, e);
    if (statSync(p).isDirectory()) walk(p);
    else if (p.endsWith('.ts')) {
      const txt = readFileSync(p, 'utf8');
      txt.split('\n').forEach((line, i) => {
        if (line.includes('@modeler/')) bad.push(`${p}:${i + 1}: ${line.trim()}`);
      });
    }
  }
}
for (const r of ROOTS) walk(r);
if (bad.length) {
  console.error('DM-PORT-001: surviving @modeler/* references:\n' + bad.join('\n'));
  process.exit(1);
}
console.log('DM-PORT-001 ok: no @modeler/* references in ported packages.');
