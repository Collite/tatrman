import { parseString } from '@modeler/parser';
import { formatDocument, DEFAULT_FORMAT_CONFIG, type FormatConfig } from './printer.js';

export { formatDocument, DEFAULT_FORMAT_CONFIG } from './printer.js';
export type { FormatConfig } from './printer.js';

/**
 * Parse `source` and print canonical layout. Throws on unparseable input.
 *
 * Thin wrapper over {@link formatDocument} that owns the parse; the LSP uses
 * {@link formatDocument} directly to avoid a double-parse.
 */
export function format(source: string, uri: string, config: FormatConfig = DEFAULT_FORMAT_CONFIG): string {
  const result = parseString(source, uri);
  if (!result.ast || result.errors.some((e) => e.severity === 'error')) {
    throw new Error(
      `Cannot format ${uri}: source has parse errors (${result.errors
        .filter((e) => e.severity === 'error')
        .slice(0, 2)
        .map((e) => e.message)
        .join('; ')})`
    );
  }
  return formatDocument(result.ast, source, config);
}
