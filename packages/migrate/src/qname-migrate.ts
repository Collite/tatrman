// Qname migrator (qname-redesign Phase 7, key-remap core).
//
// The v4.0 canonical key is uniform + package-first:
//   <package> . <model> . <schema?> . <kind> . <name>(.<sub>)*
// vs the legacy key, which omitted the kind segment for db (`db.dbo.Orders`),
// kept a separate `query` model, and doubled the stock-cnc segment
// (`cnc.cnc.role.fact`).
//
// Short references inside defs keep working via slot-filling and need NO rewrite.
// Only the FULL canonical keys stored in graph `objects: [...]` lists and `.ttrg`
// layout node keys change — and the kind segment they gain is NOT derivable from
// the qname string (db.dbo.QFOO could be a table, view, fk, …). So we recover
// each symbol's kind from the symbol table and build the old→new key map, then
// rewrite occurrences token-wise.

import { modelForKind, namespaceForKind, type ProjectSymbolTable, type SymbolEntry } from '@modeler/semantics';

/** Walk the parent chain to recover the full name path (ancestors + self). */
function nameParts(entry: SymbolEntry, symbols: ProjectSymbolTable): string[] {
  const parts = [entry.name];
  let cur: SymbolEntry | undefined = entry;
  const seen = new Set<string>();
  while (cur?.parent && !seen.has(cur.parent)) {
    seen.add(cur.parent);
    const p = symbols.get(cur.parent);
    if (!p) break;
    parts.unshift(p.name);
    cur = p;
  }
  return parts;
}

/** The top-level ancestor of an entry (itself when it has no parent). */
function rootAncestor(entry: SymbolEntry, symbols: ProjectSymbolTable): SymbolEntry {
  let cur = entry;
  const seen = new Set<string>();
  while (cur.parent && !seen.has(cur.parent)) {
    seen.add(cur.parent);
    const p = symbols.get(cur.parent);
    if (!p) break;
    cur = p;
  }
  return cur;
}

/** The legacy db schema handle for an entry, recovered from its old key. */
function legacyDbSchema(entry: SymbolEntry): string {
  const rest = entry.packageName && entry.qname.startsWith(entry.packageName + '.')
    ? entry.qname.slice(entry.packageName.length + 1)
    : entry.qname;
  const segs = rest.split('.');
  // segs[0] is the legacy model code (db | query | …). For a folded `query`
  // model the new schema is the default `dbo` (D14); else the schema is segs[1].
  if (segs[0] === 'query') return 'dbo';
  return segs[1] ?? 'dbo';
}

/**
 * Build the new, uniform canonical key for a legacy symbol entry. The
 * model/schema/kind segment come from the entry's ROOT ancestor (children are
 * grouped under their top-level def's kind, e.g. `…db.dbo.table.Orders.id`);
 * only the name path grows down the chain.
 */
export function newKeyForEntry(entry: SymbolEntry, symbols: ProjectSymbolTable): string {
  const root = rootAncestor(entry, symbols);
  const model = modelForKind(root.kind);
  const segs: string[] = [];
  if (entry.packageName) segs.push(entry.packageName);
  segs.push(model);
  if (model === 'db') segs.push(legacyDbSchema(root));
  segs.push(namespaceForKind(root.kind) || root.kind);
  segs.push(...nameParts(entry, symbols));
  return segs.join('.');
}

/**
 * old canonical key → new canonical key, for every symbol whose key actually
 * changes (db gains the kind segment, query folds into db, stock-cnc loses the
 * doubled segment). Unchanged keys (er/md/binding/non-stock-cnc) are omitted.
 */
export function computeKeyMap(symbols: ProjectSymbolTable): Map<string, string> {
  const map = new Map<string, string>();
  for (const entry of symbols.all()) {
    const next = newKeyForEntry(entry, symbols);
    if (next !== entry.qname) map.set(entry.qname, next);
  }
  return map;
}

const IDENT_CHAR = /[A-Za-z0-9_À-ɏ]/;

/** True if `text[i..i+len]` is a whole dotted-id token (not a sub-segment). */
function isWholeToken(text: string, start: number, len: number): boolean {
  const before = start > 0 ? text[start - 1] : '';
  const after = start + len < text.length ? text[start + len] : '';
  if (before && (IDENT_CHAR.test(before) || before === '.')) return false;
  if (after && (IDENT_CHAR.test(after) || after === '.')) return false;
  return true;
}

/** Replace every whole-token occurrence of `from` with `to` in `text`. */
function replaceToken(text: string, from: string, to: string): string {
  let out = '';
  let i = 0;
  while (i < text.length) {
    const idx = text.indexOf(from, i);
    if (idx === -1) { out += text.slice(i); break; }
    if (isWholeToken(text, idx, from.length)) {
      out += text.slice(i, idx) + to;
      i = idx + from.length;
    } else {
      out += text.slice(i, idx + from.length);
      i = idx + from.length;
    }
  }
  return out;
}

/**
 * Rewrite one file's canonical-key occurrences (graph objects + layout keys)
 * from legacy to uniform, applying the key map longest-first so a column key is
 * rewritten before its table prefix.
 */
export function rewriteCanonicalKeys(text: string, keyMap: Map<string, string>): string {
  const olds = [...keyMap.keys()].sort((a, b) => b.length - a.length);
  let out = text;
  for (const old of olds) out = replaceToken(out, old, keyMap.get(old)!);
  return out;
}
