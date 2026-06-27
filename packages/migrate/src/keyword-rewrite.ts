// v4.0 keyword rewrite (qname-redesign D1–D3, D13 hard cut).
//
// The breaking grammar-4.0 rename, applied to source text:
//   • `def model <id>`            → `def project <id>`        (D1)
//   • directive `schema <code>`   → `model <code>`            (D2)
//   • graph `schema: <code>`      → `model: <code>`           (D2)
//   • `namespace <id>`            → `schema <id>`             (D3)
//
// This is the single source for the rename, shared by the grammar-3.0 migrator
// (`phase0`) and the `migrate-qnames` codemod, so both emit identical v4.0
// surface. It is **idempotent**: already-migrated text contains no `def model`,
// no directive/`schema:` followed by a bare model code, and no `namespace`
// keyword, so a second pass is a no-op (proven in keyword-rewrite.test.ts).
//
// Why regex rather than a full CST rebuild: these are keyword-position renames on
// a small, closed vocabulary; the model-code list is matched with a trailing
// word boundary so `schema dbo` (a *namespace* value, not a model code) is left
// untouched and only genuine directives flip. The whitespace captures preserve
// surrounding trivia, so comments and formatting are kept verbatim.

/** The reserved model codes a directive may carry (incl. the retired `query`,
 * which still appears in pre-cut files and must migrate). */
const MODEL_CODE = '(?:db|er|binding|query|cnc|md)';

/**
 * Rewrite the three keyword changes in one file's text. Pure, deterministic, and
 * idempotent. Preserves all surrounding whitespace and comments.
 */
export function rewriteV4Keywords(text: string): string {
  return text
    // `def model <id>` → `def project <id>` (the whole-artifact header, D1).
    .replace(/\bdef model\b/g, 'def project')
    // Directive `schema <code>` → `model <code>` (line-anchored; D2). The model
    // code is bounded by `\b` so a namespace value (`schema dbo`) never matches.
    .replace(new RegExp(`(^|\\n)([ \\t]*)schema([ \\t]+)(${MODEL_CODE})\\b`, 'g'), '$1$2model$3$4')
    // Graph `schema: <code>` property → `model: <code>` (D2).
    .replace(new RegExp(`\\bschema([ \\t]*:[ \\t]*)(${MODEL_CODE})\\b`, 'g'), 'model$1$2')
    // `namespace <id>` → `schema <id>` (D3) — runs last so a migrated directive
    // (`model db namespace dbo`) ends as `model db schema dbo`.
    .replace(/\bnamespace([ \t]+)(?=[A-Za-z_])/g, 'schema$1');
}
