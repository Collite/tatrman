// SPDX-License-Identifier: Apache-2.0
//
// FO-A1 W4 (P4.S1, contracts §2). TtrpLspClient.validate: maps the server's flat wire shape →
// the §2 range/ok shape (derives ok = no error-severity), carries suggestedAlternative, and
// degrades to { supported:false } (A1-CAP-002) when the server lacks the method — never throws.

import { describe, it, expect } from 'vitest';
import { TtrpLspClient, METHOD_NOT_FOUND } from '../ws-client.js';

type Req = (method: string, params: unknown) => Promise<unknown>;

/** A client whose private rpc is replaced with a canned `request` (no socket). */
function clientWith(request: Req): TtrpLspClient {
  const c = new TtrpLspClient();
  (c as unknown as { rpc: { request: Req } }).rpc = { request };
  return c;
}

describe('TtrpLspClient.validate (W4, contracts §2)', () => {
  it('maps flat line/col → range, derives ok:false on an error, carries suggestedAlternative', async () => {
    let seen: [string, unknown] | null = null;
    const c = clientWith(async (m, p) => {
      seen = [m, p];
      return { diagnostics: [{ code: 'TTRP-PARSE-014', severity: 'error', message: "expected '}'", suggestedAlternative: 'add }', line: 2, column: 4, endLine: 2, endColumn: 9 }] };
    });
    const res = await c.validate('draft text', 'file:///p.ttrp');
    expect(seen).toEqual(['ttrp/validate', { source: 'draft text', uri: 'file:///p.ttrp' }]);
    expect(res).toEqual({
      supported: true,
      ok: false,
      diagnostics: [
        { severity: 'error', code: 'TTRP-PARSE-014', message: "expected '}'", range: { start: { line: 2, col: 4 }, end: { line: 2, col: 9 } }, suggestedAlternative: 'add }' },
      ],
    });
  });

  it('a clean program → ok:true, empty diagnostics', async () => {
    const res = await clientWith(async () => ({ diagnostics: [] })).validate('ok');
    expect(res).toEqual({ supported: true, ok: true, diagnostics: [] });
  });

  it('warnings/info do not flip ok to false (only error does)', async () => {
    const res = await clientWith(async () => ({ diagnostics: [{ code: 'W1', severity: 'warning', message: 'w', line: 0, column: 0, endLine: 0, endColumn: 1 }] })).validate('ok');
    expect(res).toMatchObject({ supported: true, ok: true });
  });

  it('server lacking the method → { supported:false } (A1-CAP-002), never throws', async () => {
    const res = await clientWith(async () => { throw { code: METHOD_NOT_FOUND, message: 'method not found' }; }).validate('x');
    expect(res).toEqual({ supported: false });
  });

  it('a non-capability RPC error still throws (not swallowed)', async () => {
    await expect(clientWith(async () => { throw new Error('socket closed'); }).validate('x')).rejects.toThrow(/socket closed/);
  });
});
