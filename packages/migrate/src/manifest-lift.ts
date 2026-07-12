// SPDX-License-Identifier: Apache-2.0
// modeler.toml manifest lift (qname-redesign Phase 7, step 2).
//
// The legacy manifest declared model→namespace defaults inline:
//   [schemas]
//   declared   = ["db", "er", "map"]
//   namespaces = { db = "dbo", er = "entity", map = "er2db" }
// v4.0 promotes the db namespace to a NAMED schema binding (db-only, D6) and adds
// a project-wide default:
//   [schemas.dbo]
//   db-schema = "dbo"
//   dialect   = "tsql"
//   [defaults]
//   schema = "dbo"
//
// Only the `db` namespace becomes a binding — `er`/`map` are model namespaces with
// no physical schema (D6), so they are simply dropped. When the project carries an
// embedded-SQL `[[sql.namespace-map]]`, each entry's database/db-schema/dialect is
// pulled into its binding (contracts §1.1); otherwise db-schema defaults to the
// handle and the dialect to `tsql`. Idempotent: a manifest with no legacy
// `[schemas].namespaces`/`declared` keys is returned unchanged.

import { parseManifest } from '@tatrman/semantics';

export interface SchemaBindingLift {
  handle: string;
  database?: string;
  dbSchema: string;
  dialect: string;
}

export interface ManifestLiftResult {
  text: string;
  changed: boolean;
  /** Schema handles created (for reporting). */
  schemas: string[];
}

interface SqlNamespaceEntry {
  namespace?: string;
  database?: string;
  schema?: string;
  dialect?: string;
}

/** Best-effort parse; a malformed manifest yields `undefined` (no lift). */
function safeParse(text: string): ReturnType<typeof parseManifest> | undefined {
  try {
    return parseManifest(text);
  } catch {
    return undefined;
  }
}

/**
 * Lift a legacy `[schemas].namespaces`/`declared` manifest to v4.0 named bindings.
 * Pure and deterministic. Preserves all other tables, comments, and formatting.
 */
export function liftManifest(tomlText: string, opts: { defaultDialect?: string } = {}): ManifestLiftResult {
  const m = safeParse(tomlText);
  const schemas = m?.schemas as (Record<string, unknown> & { namespaces?: Record<string, string>; declared?: string[] }) | undefined;
  const hasLegacy = schemas !== undefined && ('namespaces' in schemas || 'declared' in schemas);
  const dbHandle = schemas?.namespaces?.db;
  if (!hasLegacy || !dbHandle) {
    return { text: tomlText, changed: false, schemas: [] };
  }

  // Enrich from any embedded-SQL namespace map (contracts §1.1).
  const sqlMap = ((m as Record<string, unknown>)?.sql as { 'namespace-map'?: SqlNamespaceEntry[] } | undefined)?.['namespace-map'] ?? [];
  const byNamespace = new Map<string, SqlNamespaceEntry>();
  for (const e of sqlMap) if (e.namespace) byNamespace.set(e.namespace, e);

  const fallbackDialect = opts.defaultDialect ?? 'tsql';
  const enriched = byNamespace.get(dbHandle);
  const bindings: SchemaBindingLift[] = [
    {
      handle: dbHandle,
      database: enriched?.database,
      dbSchema: enriched?.schema ?? dbHandle,
      dialect: enriched?.dialect ?? fallbackDialect,
    },
  ];

  const hasDefaultsSchema = (m as { defaults?: { schema?: string } })?.defaults?.schema !== undefined;

  // Render the new tables.
  const blocks: string[] = [];
  for (const b of bindings) {
    blocks.push(`[schemas.${b.handle}]`);
    if (b.database !== undefined) blocks.push(`database  = "${b.database}"`);
    blocks.push(`db-schema = "${b.dbSchema}"`);
    blocks.push(`dialect   = "${b.dialect}"`);
    blocks.push('');
  }
  if (!hasDefaultsSchema) {
    blocks.push('[defaults]', `schema = "${dbHandle}"`, '');
  }

  const text = spliceSchemasSection(tomlText, blocks);
  return { text, changed: text !== tomlText, schemas: bindings.map((b) => b.handle) };
}

/**
 * Remove the legacy `declared`/`namespaces` keys from the `[schemas]` table and
 * insert the new blocks in its place. If `[schemas]` keeps other keys, they are
 * preserved under the header; if it becomes empty, the header is dropped.
 */
function spliceSchemasSection(text: string, blocks: string[]): string {
  const lines = text.split('\n');
  const headerIdx = lines.findIndex((l) => l.trim() === '[schemas]');
  if (headerIdx === -1) return text;

  // Section body: from after the header to the next top-level table header.
  let end = lines.length;
  for (let i = headerIdx + 1; i < lines.length; i++) {
    if (/^\s*\[/.test(lines[i])) { end = i; break; }
  }

  const body = lines.slice(headerIdx + 1, end);
  const kept: string[] = [];
  let keptRealKey = false;
  for (const line of body) {
    const t = line.trim();
    if (t.startsWith('declared') || t.startsWith('namespaces')) continue; // dropped
    kept.push(line);
    if (t !== '' && !t.startsWith('#') && t.includes('=')) keptRealKey = true;
  }

  // Trim trailing blank lines from the kept body (we re-add spacing).
  while (kept.length > 0 && kept[kept.length - 1].trim() === '') kept.pop();

  const replacement: string[] = [];
  if (keptRealKey) {
    replacement.push('[schemas]', ...kept, '');
  }
  replacement.push(...blocks);
  // Normalize to exactly one blank line before the following content (if any), so
  // the new tables don't run into the next section and blanks don't accumulate.
  while (replacement.length > 0 && replacement[replacement.length - 1] === '') replacement.pop();
  const tail = lines.slice(end);
  if (tail.some((l) => l.trim() !== '')) replacement.push('');

  const out = [...lines.slice(0, headerIdx), ...replacement, ...tail];
  return out.join('\n');
}
