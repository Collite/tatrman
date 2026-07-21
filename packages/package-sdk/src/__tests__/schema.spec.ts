// SPDX-License-Identifier: Apache-2.0
//
// FO-P4.S1.T1 — the published JSON artifact must never drift from the TS source of truth. A non-TS
// package author validates against `schema/package-manifest.schema.json`; this proves it equals what
// the SDK's `validateManifest` actually compiles.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { packageManifestSchema } from '../schema.js';

describe('published manifest schema artifact', () => {
  it('the .json copy is byte-in-sync with the TS source', () => {
    const jsonPath = fileURLToPath(new URL('../schema/package-manifest.schema.json', import.meta.url));
    const published = JSON.parse(readFileSync(jsonPath, 'utf8'));
    expect(published).toEqual(JSON.parse(JSON.stringify(packageManifestSchema)));
  });
});
