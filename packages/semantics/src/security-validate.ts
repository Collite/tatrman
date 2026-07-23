// SPDX-License-Identifier: Apache-2.0
/**
 * PL-P4.S3.T6 (grammar 0.11, H-1/H-3) — advisory validation of the document-level
 * `security { }` block. **Warning-severity ONLY**: an unresolved object reference
 * is a lint, NEVER a compile block (H-3 — access declarations must not gate a
 * build; enforcement is Perun's, at bundle build). The library never mints TTRP-*
 * ids; the `security/*` code is the TTR-M editor surface and a cross-target
 * contract (mirrored in Kotlin `SecurityValidator.kt`).
 *
 * Only OBJECT references are checked. Role / classification tokens are verbatim
 * org-policy data (contracts §11), so they are not resolved against the model.
 */
import type { Document, SourceLocation } from '@tatrman/parser';

export type SecurityDiagnosticCode = 'security/unresolved-object';

export interface SecurityDiagnostic {
  code: SecurityDiagnosticCode;
  message: string;
  severity: 'warning';
  source: SourceLocation;
}

/**
 * Validate the `security { }` blocks of one document.
 *
 * @param doc           parsed document
 * @param knownObjects  project-wide resolvable object refs (names + dotted
 *                      `<object>.<member>` paths); when omitted, the set is derived
 *                      from THIS document only (single-file lint convenience —
 *                      cross-file callers inject the project-wide set).
 */
export function validateSecurityDocument(doc: Document, knownObjects?: ReadonlySet<string>): SecurityDiagnostic[] {
  if (!doc.securityBlocks || doc.securityBlocks.length === 0) return [];
  const objects = knownObjects ?? documentObjects(doc);
  const diagnostics: SecurityDiagnostic[] = [];
  for (const block of doc.securityBlocks) {
    for (const stmt of block.statements) {
      if (!resolves(stmt.objectRef, objects)) {
        diagnostics.push({
          code: 'security/unresolved-object',
          message:
            `\`${stmt.verb}\` references object '${stmt.objectRef}', which does not resolve to any known ` +
            `object; the grant is advisory until the reference resolves`,
          severity: 'warning',
          source: stmt.source,
        });
      }
    }
  }
  return diagnostics;
}

/** A ref resolves if it (or its head segment — the object owning a member) is known. */
function resolves(ref: string, objects: ReadonlySet<string>): boolean {
  if (objects.has(ref)) return true;
  const head = ref.split('.', 1)[0]!;
  return objects.has(head);
}

/** Names + dotted member paths declared in this document (the single-file default set). */
function documentObjects(doc: Document): ReadonlySet<string> {
  const out = new Set<string>();
  for (const def of doc.definitions) {
    out.add(def.name);
    if (def.kind === 'table') {
      for (const col of def.columns ?? []) out.add(`${def.name}.${col.name}`);
    } else if (def.kind === 'entity') {
      for (const attr of def.attributes ?? []) out.add(`${def.name}.${attr.name}`);
    }
  }
  return out;
}
