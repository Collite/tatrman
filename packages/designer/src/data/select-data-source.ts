// Explicit backend selection — never sniffed (P2).
//
// Precedence (also documented in packages/designer/README.md):
//   (1) `?server=ws://127.0.0.1:7270` → WsDesignerServerDataSource (the value is the
//       WS origin; the client appends `/ttrm`).
//   (2) otherwise → the worker LSP path (landing card / demo, exactly as today).
//
// A malformed or non-loopback `?server=` value is a visible error, never a
// guess-and-fallback (P2; S24 makes a loopback URL the only sane v1 value). `?demo=`
// keeps its current meaning (worker path); `?server=` + `?demo=` together is an
// error — they select different backends.

export type BackendSelection =
  | { kind: 'ws'; origin: string }
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

export function selectBackend(search: string | URLSearchParams): BackendSelection {
  const params = typeof search === 'string' ? new URLSearchParams(search) : search;
  const server = params.get('server');
  const demo = params.get('demo');

  if (server !== null) {
    if (demo !== null) {
      throw new BackendSelectionError(
        '`?server=` and `?demo=` select different backends — specify only one.',
      );
    }
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
