// SPDX-License-Identifier: Apache-2.0
//
// @tatrman/deep-links — the Studio federation URL codec (FO contracts §3).
//
// One URL grammar both product families implement; deep links are the
// federation glue (P-2), v1 = links only (no embedding). This package is
// framework-free and dependency-free by discipline (PF F): a link is data, and
// every Studio surface builds/parses its address with it.
//
// Grammar (host-relative — the surface prepends its own host):
//   /s/viewer?object=<qname>&version=<v>
//   /s/lineage?cell=<cell-ref> | ?run=<run-id>
//   /s/process?program=<qname>&version=<v>
//   /p/round/<round-id>/form/<form-id>
//   /e/<table-qname>?filter=<enc>
//   /ask?context=<enc-json>   ({source:'studio', object?, cell?, run?})

/** Query params a deep link must never carry — auth rides the shared IdP session. */
export const FORBIDDEN_PARAMS: readonly string[] = [
  'token',
  'auth',
  'authorization',
  'access_token',
  'password',
  'secret',
  'bearer',
  'apikey',
  'api_key',
];

/** Unknown query params, preserved verbatim across a parse→build round-trip (forward compat). */
export type Extra = Record<string, string>;

/** "Ask about this" payload (Studio → Iris), FO contracts §3. */
export interface AskContext {
  source: 'studio';
  object?: string;
  cell?: string;
  run?: string;
}

/** A resolved Studio deep-link target. `extra` carries forward-compatible unknown params. */
export type DeepLink =
  | { kind: 'viewer'; object: string; version?: string; extra?: Extra }
  | { kind: 'lineage'; cell?: string; run?: string; extra?: Extra }
  | { kind: 'process'; program: string; version?: string; extra?: Extra }
  | { kind: 'planner-form'; roundId: string; formId: string; extra?: Extra }
  | { kind: 'entry'; table: string; filter?: string; extra?: Extra }
  | { kind: 'ask'; context: AskContext; extra?: Extra };

/** Thrown for an unroutable path, a missing required part, or a credential-bearing param. */
export class DeepLinkError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'DeepLinkError';
  }
}

// Any URL base works — the host is discarded; we only care about path + query.
const RELATIVE_BASE = 'https://deep-link.invalid';

function assertNotCredential(key: string): void {
  if (FORBIDDEN_PARAMS.includes(key.toLowerCase())) {
    throw new DeepLinkError(`a deep link must not carry the credential param "${key}"`);
  }
}

/** Build the host-relative path+query for a deep link (the surface prepends its own host). */
export function buildDeepLink(link: DeepLink): string {
  let path: string;
  const params = new URLSearchParams();

  switch (link.kind) {
    case 'viewer':
      if (!link.object) throw new DeepLinkError('viewer link requires object');
      path = '/s/viewer';
      params.set('object', link.object);
      if (link.version !== undefined) params.set('version', link.version);
      break;
    case 'lineage': {
      const hasCell = link.cell !== undefined;
      const hasRun = link.run !== undefined;
      if (hasCell === hasRun) {
        throw new DeepLinkError('lineage link requires exactly one of cell or run');
      }
      path = '/s/lineage';
      if (hasCell) params.set('cell', link.cell as string);
      else params.set('run', link.run as string);
      break;
    }
    case 'process':
      if (!link.program) throw new DeepLinkError('process link requires program');
      path = '/s/process';
      params.set('program', link.program);
      if (link.version !== undefined) params.set('version', link.version);
      break;
    case 'planner-form':
      if (!link.roundId || !link.formId) {
        throw new DeepLinkError('planner-form link requires roundId and formId');
      }
      path = `/p/round/${encodeURIComponent(link.roundId)}/form/${encodeURIComponent(link.formId)}`;
      break;
    case 'entry':
      if (!link.table) throw new DeepLinkError('entry link requires table');
      path = `/e/${encodeURIComponent(link.table)}`;
      if (link.filter !== undefined) params.set('filter', link.filter);
      break;
    case 'ask':
      path = '/ask';
      params.set('context', JSON.stringify(link.context));
      break;
    default: {
      const unreachable: never = link;
      throw new DeepLinkError(`unknown link kind: ${JSON.stringify(unreachable)}`);
    }
  }

  if (link.extra) {
    for (const [key, value] of Object.entries(link.extra)) {
      assertNotCredential(key);
      params.set(key, value);
    }
  }

  const query = params.toString();
  return query ? `${path}?${query}` : path;
}

/** Parse a deep link from a full URL or a host-relative path+query. */
export function parseDeepLink(input: string): DeepLink {
  const url = new URL(input, RELATIVE_BASE);
  const { pathname } = url;
  const sp = url.searchParams;

  // The credential rule is checked across every param before any routing.
  for (const key of sp.keys()) assertNotCredential(key);

  const attachExtra = <T extends DeepLink>(base: T, consumed: string[]): T => {
    const extra: Extra = {};
    for (const [key, value] of sp.entries()) {
      if (!consumed.includes(key)) extra[key] = value;
    }
    return Object.keys(extra).length > 0 ? { ...base, extra } : base;
  };

  if (pathname === '/s/viewer') {
    const object = sp.get('object');
    if (!object) throw new DeepLinkError('viewer link requires object');
    const link: DeepLink = { kind: 'viewer', object };
    const version = sp.get('version');
    if (version !== null) link.version = version;
    return attachExtra(link, ['object', 'version']);
  }

  if (pathname === '/s/lineage') {
    const cell = sp.get('cell');
    const run = sp.get('run');
    if ((cell === null) === (run === null)) {
      throw new DeepLinkError('lineage link requires exactly one of cell or run');
    }
    const link: DeepLink = cell !== null ? { kind: 'lineage', cell } : { kind: 'lineage', run: run as string };
    return attachExtra(link, ['cell', 'run']);
  }

  if (pathname === '/s/process') {
    const program = sp.get('program');
    if (!program) throw new DeepLinkError('process link requires program');
    const link: DeepLink = { kind: 'process', program };
    const version = sp.get('version');
    if (version !== null) link.version = version;
    return attachExtra(link, ['program', 'version']);
  }

  const planner = pathname.match(/^\/p\/round\/([^/]+)\/form\/([^/]+)$/);
  if (planner) {
    const link: DeepLink = {
      kind: 'planner-form',
      roundId: decodeURIComponent(planner[1]),
      formId: decodeURIComponent(planner[2]),
    };
    return attachExtra(link, []);
  }

  const entry = pathname.match(/^\/e\/([^/]+)$/);
  if (entry) {
    const link: DeepLink = { kind: 'entry', table: decodeURIComponent(entry[1]) };
    const filter = sp.get('filter');
    if (filter !== null) link.filter = filter;
    return attachExtra(link, ['filter']);
  }

  if (pathname === '/ask') {
    const raw = sp.get('context');
    if (!raw) throw new DeepLinkError('ask link requires context');
    let context: AskContext;
    try {
      context = JSON.parse(raw) as AskContext;
    } catch {
      throw new DeepLinkError('ask link context is not valid JSON');
    }
    if (context === null || typeof context !== 'object' || context.source !== 'studio') {
      throw new DeepLinkError('ask link context.source must be "studio"');
    }
    const link: DeepLink = { kind: 'ask', context };
    return attachExtra(link, ['context']);
  }

  throw new DeepLinkError(`unroutable deep-link path: ${pathname}`);
}
