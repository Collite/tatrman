// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { insertPackageDecl } from '../index.js';

describe('insertPackageDecl', () => {
  it('inserts package line at the very top when no leading comments', () => {
    const input = `model er schema entity
def entity artikl { }`;
    const result = insertPackageDecl(input, 'billing.invoicing');
    expect(result).toBe('package billing.invoicing\nmodel er schema entity\ndef entity artikl { }');
  });

  it('is idempotent — no-op when package already present', () => {
    const input = `package billing.invoicing
model er schema entity`;
    const result = insertPackageDecl(input, 'billing.invoicing');
    expect(result).toBe(input);
  });

  it('inserts package after leading comment block', () => {
    const input = `// This is a license header
// SPDX-License-Identifier: MIT

model er schema entity`;
    const result = insertPackageDecl(input, 'foo.bar');
    expect(result).toBe(`// This is a license header
// SPDX-License-Identifier: MIT

package foo.bar
model er schema entity`);
  });

  it('inserts package after leading blank-line-surrounded comments', () => {
    const input = `// (c) 2026
//

model er schema entity`;
    const result = insertPackageDecl(input, 'baz');
    expect(result).toBe(`// (c) 2026
//

package baz
model er schema entity`);
  });

  it('leaves content unchanged for default package (empty string)', () => {
    const input = `model er schema entity
def entity artikl { }`;
    const result = insertPackageDecl(input, '');
    expect(result).toBe(input);
  });
});