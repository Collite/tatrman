export interface Qname {
  schemaCode: string;
  namespace: string;
  parts: string[];
}

const KNOWN_SCHEMA_CODES = ['db', 'er', 'binding', 'query', 'cnc'] as const;

export function qnameToString(q: Qname): string {
  const segments: string[] = [q.schemaCode];
  if (q.namespace) segments.push(q.namespace);
  segments.push(...q.parts);
  return segments.join('.');
}

/**
 * Parse a dotted qname into structured form.
 *
 * Convention: `<schemaCode>.<namespace>.<part>(.<part>)*`. The namespace is
 * always the second segment when at least two segments follow the schemaCode;
 * when only one trailing segment is present, namespace is empty and that
 * segment is `parts[0]`. Returns null if the first segment is not a known
 * schema code.
 */
export function parseQname(text: string): Qname | null {
  const segments = text.split('.');
  if (segments.length < 2) return null;
  const schemaCode = segments[0];
  if (!(KNOWN_SCHEMA_CODES as readonly string[]).includes(schemaCode)) return null;

  if (segments.length === 2) {
    return { schemaCode, namespace: '', parts: [segments[1]] };
  }

  return {
    schemaCode,
    namespace: segments[1],
    parts: segments.slice(2),
  };
}

export function buildQname(schemaCode: string, namespace: string, parts: string[]): Qname {
  return { schemaCode, namespace, parts };
}
