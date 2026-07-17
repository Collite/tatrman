// SPDX-License-Identifier: Apache-2.0
// Explicit backend selection — never sniffed (P2).
//
// Precedence (also documented in packages/designer/README.md):
//   (1) `?server=ws://127.0.0.1:7270` → WsDesignerServerDataSource (the value is the
//       WS origin; the client appends `/ttrm`). Loopback-only (a modeling-time server).
//   (2) `?veles=<base>` → VelesDataSource, the read-only catalog view over the Veles
//       JSON read API (SV-P4·S2·T5). `<base>` is EITHER a same-origin absolute path
//       prefix (the in-chart deployment shape, e.g. `/veles`, so the browser stays
//       same-origin behind the viewer's ingress — no CORS) OR a full http(s) origin
//       (dev / cross-origin, e.g. `http://localhost:7260`). Read-only: no edit.
//   (3) otherwise → the worker LSP path (landing card / demo, exactly as today).
//
// A malformed value is a visible error, never a guess-and-fallback (P2). `?demo=`
// keeps its current meaning (worker path); combining `?server=`/`?veles=`/`?demo=`
// is an error — they select different backends.
//
// Why `?veles=` allows a non-loopback origin where `?server=` does not: `?server=`
// targets a *modeling-time* server that owns a local repo, so loopback is the only
// sane value; `?veles=` targets the *deployed* catalog service, which is inherently
// remote. The same-origin path-prefix form keeps the common (in-chart) case CORS-free;
// the full-origin form is an explicit operator/dev choice, CORS- and auth-gated by
// Veles itself (see T5 decisions doc).

export type BackendSelection =
  | { kind: 'ws'; origin: string }
  | { kind: 'veles'; base: string }
  | { kind: 'worker'; demo: string | null };

export class BackendSelectionError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'BackendSelectionError';
  }
}

function isLoopbackWsOrigin(value: string): boolean {
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    return false;
  }
  if (url.protocol !== 'ws:' && url.protocol !== 'wss:') return false;
  if (url.hostname !== '127.0.0.1' && url.hostname !== 'localhost' && url.hostname !== '[::1]') return false;
  // Origin only — no path/query/fragment (the client owns `/ttrm`).
  return url.pathname === '/' || url.pathname === '';
}

/**
 * Normalize a `?veles=` value to a base the VelesDataSource concatenates endpoint
 * paths onto. Two accepted forms:
 *   - a same-origin absolute path prefix: starts with `/`, no query/fragment
 *     (e.g. `/veles` → `/veles`; `/` → ``). The browser resolves it against its
 *     own origin, so no CORS.
 *   - a full http(s) ORIGIN: scheme + host [+ port], no path/query/fragment
 *     (e.g. `http://localhost:7260`). Trailing slash stripped.
 * Anything else throws.
 */
function normalizeVelesBase(value: string): string {
  if (value.startsWith('/')) {
    if (/[?#]/.test(value)) {
      throw new BackendSelectionError(
        `Invalid \`?veles=\` value: ${JSON.stringify(value)}. ` +
          'A same-origin path prefix must carry no query or fragment (e.g. /veles).',
      );
    }
    return value.replace(/\/$/, '');
  }
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new BackendSelectionError(
      `Invalid \`?veles=\` value: ${JSON.stringify(value)}. ` +
        'Expected a same-origin path prefix (e.g. /veles) or an http(s) origin (e.g. http://localhost:7260).',
    );
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new BackendSelectionError(
      `Invalid \`?veles=\` value: ${JSON.stringify(value)}. Expected an http(s) origin.`,
    );
  }
  if ((url.pathname !== '/' && url.pathname !== '') || url.search || url.hash) {
    throw new BackendSelectionError(
      `Invalid \`?veles=\` value: ${JSON.stringify(value)}. ` +
        'Expected an ORIGIN only (no path/query/fragment), e.g. http://localhost:7260.',
    );
  }
  return value.replace(/\/$/, '');
}

export function selectBackend(search: string | URLSearchParams): BackendSelection {
  const params = typeof search === 'string' ? new URLSearchParams(search) : search;
  const server = params.get('server');
  const veles = params.get('veles');
  const demo = params.get('demo');

  const selectors = [server, veles].filter((v) => v !== null).length;
  if (selectors > 1 || (selectors === 1 && demo !== null)) {
    throw new BackendSelectionError(
      '`?server=`, `?veles=`, and `?demo=` select different backends — specify only one.',
    );
  }

  if (veles !== null) {
    return { kind: 'veles', base: normalizeVelesBase(veles) };
  }

  if (server !== null) {
    if (!isLoopbackWsOrigin(server)) {
      throw new BackendSelectionError(
        `Invalid \`?server=\` value: ${JSON.stringify(server)}. ` +
          'Expected a loopback WS origin, e.g. ws://127.0.0.1:7270 (no path).',
      );
    }
    return { kind: 'ws', origin: server.replace(/\/$/, '') };
  }

  return { kind: 'worker', demo };
}
