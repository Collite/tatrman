// SPDX-License-Identifier: Apache-2.0
/**
 * Maps an ANTLR SQL token to an LSP semantic-token type (embedded-sql contracts
 * §7 legend). Driven off the token's **names**, never hard-coded type integers,
 * so it survives grammar regeneration. `null` = no semantic token (plain
 * identifiers — which become `class`(table)/`property`(column) in Phase 3 — and
 * whitespace).
 *
 * `parameter`, `class`, and `property` are NOT produced here: `parameter` comes
 * from the `maskPlaceholders` spans (stage 2.4), and table/column classification
 * needs the Phase 3 resolver.
 */
export type SqlSemanticType = 'keyword' | 'string' | 'number' | 'comment' | 'operator' | 'variable';

export function classifyToken(
  typeName: string | null,
  literalName: string | null,
): SqlSemanticType | null {
  // Fixed-text tokens (keywords + punctuation/operators) carry a literal name,
  // e.g. "'SELECT'" or "'='". Alphabetic literal → keyword; symbolic → operator.
  if (literalName) {
    const lit = literalName.replace(/^'|'$/g, '');
    return /^[A-Za-z]/.test(lit) ? 'keyword' : 'operator';
  }
  // Symbolic-only tokens (STRING, DECIMAL, Integral, LineComment, LOCAL_ID, …)
  // classify by symbolic-name pattern. Both dialects' names are covered.
  const name = typeName ?? '';
  if (/comment/i.test(name)) return 'comment';
  if (/string|charconst|nchar_string|squote|dquote/i.test(name)) return 'string';
  if (/local_id|variable|temp/i.test(name)) return 'variable';
  if (/decimal|integ|numeric|float|real|double|^hex|binary|money|number/i.test(name)) return 'number';
  return null;
}
