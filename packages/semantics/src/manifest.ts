import { parse as parseToml } from 'smol-toml';

export interface ProjectManifest {
  project?: { name?: string; version?: string };
  language?: { preferred?: string };
  schemas?: { declared?: string[]; namespaces?: Record<string, string> };
  stock?: { load?: string[] };
  lint?: { strict?: boolean; requireDescriptions?: boolean };
}

export interface ResolvedManifest {
  name: string;
  projectRoot: string;
  preferredLanguage: string;
  declaredSchemas: string[];
  namespaces: Record<string, string>;
  stockVocabularies: string[];
  lint: { strict: boolean; requireDescriptions: boolean };
}

/**
 * Parse a `modeler.toml` source. Normalizes kebab-case keys the architecture
 * uses (`require-descriptions`) to the camelCase shape `ProjectManifest`
 * expects (`requireDescriptions`).
 */
export function parseManifest(content: string): ProjectManifest {
  const raw = parseToml(content) as Record<string, unknown> & {
    lint?: Record<string, unknown>;
  };
  if (raw.lint && typeof raw.lint === 'object') {
    const lint = raw.lint;
    if ('require-descriptions' in lint && !('requireDescriptions' in lint)) {
      lint.requireDescriptions = lint['require-descriptions'];
    }
  }
  return raw as ProjectManifest;
}

function basename(p: string): string {
  // works for both POSIX and Windows separators
  const norm = p.replace(/\\/g, '/');
  const trimmed = norm.endsWith('/') ? norm.slice(0, -1) : norm;
  const idx = trimmed.lastIndexOf('/');
  return idx === -1 ? trimmed : trimmed.slice(idx + 1);
}

export function resolveManifest(m: ProjectManifest | undefined, projectRoot: string): ResolvedManifest {
  return {
    name: m?.project?.name ?? (basename(projectRoot) || 'unnamed'),
    projectRoot,
    preferredLanguage: m?.language?.preferred ?? 'en',
    declaredSchemas: m?.schemas?.declared ?? ['db', 'er', 'map', 'query', 'cnc'],
    namespaces: m?.schemas?.namespaces ?? {},
    stockVocabularies: m?.stock?.load ?? ['cnc-roles'],
    lint: {
      strict: m?.lint?.strict ?? false,
      requireDescriptions: m?.lint?.requireDescriptions ?? false,
    },
  };
}