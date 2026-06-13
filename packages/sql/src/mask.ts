/**
 * `maskPlaceholders` — span-preserving pre-pass (embedded-sql contracts §3a,
 * SPIKE S0.2). TTR embeds `{param}` placeholders in SQL (e.g.
 * `WHERE name = {nazev_produktu}`); the `{`/`}` are not valid SQL and break raw
 * lexing — on the project T-SQL corpus, **every** raw lex error was a brace.
 * Masking blanks ONLY the two brace chars of a balanced `{ident}` to spaces, so
 * the inner text lexes as a bare identifier and `masked.length === value.length`
 * keeps the §8 uniform source map intact. Runs before the SQL lexer.
 */

/** Offsets index into the ORIGINAL `value` (lengths/offsets cover `{name}` incl. braces). */
export interface MaskedSpan {
  offset: number;
  length: number;
  name: string;
}

export interface MaskResult {
  masked: string;
  placeholders: MaskedSpan[];
}

/**
 * Placeholder identifier — mirrors the TTR `IDENT` lexer rule in `TTR.g4`
 * exactly (`[a-zA-ZÀ-ɏ_][a-zA-Z0-9_À-ɏ]*`), so accented
 * param names (e.g. `{název_produktu}`) mask too. This is intentionally wider
 * than contracts §3a's ASCII sketch `[A-Za-z_][A-Za-z0-9_]*`: the grammar is
 * the ground truth, and TTR identifiers admit Latin-1/Latin-Extended letters.
 */
const PLACEHOLDER = /\{([a-zA-ZÀ-ɏ_][a-zA-Z0-9_À-ɏ]*)\}/g;

/**
 * Replace each balanced `{ident}` with `·ident·` (braces → spaces; same length).
 * Unbalanced `{`/`}`, or a `{` not immediately followed by an identifier, are
 * left untouched (and surface as real SQL lex errors, which is correct).
 */
export function maskPlaceholders(value: string): MaskResult {
  const placeholders: MaskedSpan[] = [];
  const masked = value.replace(PLACEHOLDER, (full, name: string, offset: number) => {
    placeholders.push({ offset, length: full.length, name });
    return ` ${name} `; // 2 braces → 2 spaces; inner text unchanged ⇒ length preserved
  });
  return { masked, placeholders };
}
