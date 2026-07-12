// SPDX-License-Identifier: Apache-2.0
/**
 * Project-wide index of **resolved** embedded-SQL references (embedded-sql §4.3),
 * keyed by the TTR `db` symbol qname they resolve to. Populated by the LSP during
 * the semantics pass (the LSP owns SQL parsing — desktop only) and queried by
 * find-references and rename. Mirrors the shape of the TTR `ReferenceIndex`:
 * per-document upsert/remove so a single edited file invalidates only its own
 * entries.
 *
 * Only unambiguously-resolved refs are recorded — an unresolved or ambiguous
 * usage can't be attributed to one symbol, so it is omitted (§4.3.2).
 */
export interface SqlRefRange {
  start: { line: number; character: number };
  end: { line: number; character: number };
}

export interface SqlRefLocation {
  uri: string;
  range: SqlRefRange;
}

export interface SqlRefEntry {
  qname: string;
  loc: SqlRefLocation;
}

export class SqlReferenceIndex {
  private byQname = new Map<string, SqlRefLocation[]>();
  private docQnames = new Map<string, Set<string>>();

  upsertDocument(uri: string, entries: ReadonlyArray<SqlRefEntry>): void {
    this.removeDocument(uri);
    const qnames = new Set<string>();
    for (const e of entries) {
      const list = this.byQname.get(e.qname) ?? [];
      list.push(e.loc);
      this.byQname.set(e.qname, list);
      qnames.add(e.qname);
    }
    if (qnames.size > 0) this.docQnames.set(uri, qnames);
  }

  removeDocument(uri: string): void {
    const qnames = this.docQnames.get(uri);
    if (!qnames) return;
    for (const q of qnames) {
      const filtered = (this.byQname.get(q) ?? []).filter((l) => l.uri !== uri);
      if (filtered.length === 0) this.byQname.delete(q);
      else this.byQname.set(q, filtered);
    }
    this.docQnames.delete(uri);
  }

  /** All recorded SQL usages of a symbol qname (empty for non-`db` qnames). */
  findByQname(qname: string): SqlRefLocation[] {
    return this.byQname.get(qname) ?? [];
  }
}
