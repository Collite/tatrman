import { parse as parseToml } from 'smol-toml';

/**
 * The `[packages]` block of `modeler.toml` (contracts §13.1). Controls the
 * module-style root prefix and how strictly an in-file `package` declaration
 * must match its directory.
 */
export interface PackagesConfig {
  /** Module-style prefix prepended to directory-derived package names. "" = none. */
  root: string;
  /** Severity policy for a declaration that mismatches its directory. */
  layout: 'flexible' | 'strict' | 'off';
}

export const defaultPackagesConfig: PackagesConfig = { root: '', layout: 'flexible' };

/** A problem found while resolving the `[packages]` block. Surfaced by hosts. */
export interface PackagesConfigDiagnostic {
  field: 'root' | 'layout';
  message: string;
}

const VALID_LAYOUTS: ReadonlySet<string> = new Set(['flexible', 'strict', 'off']);

/**
 * Resolve the raw `[packages]` table into a {@link PackagesConfig}, validating
 * the `layout` value. Unknown `layout` values fall back to the default and yield
 * a config diagnostic (PD1.1). Pure — no filesystem.
 */
export function resolvePackagesConfig(
  raw: ProjectManifest['packages'] | undefined
): { config: PackagesConfig; diagnostics: PackagesConfigDiagnostic[] } {
  const diagnostics: PackagesConfigDiagnostic[] = [];
  const root = typeof raw?.root === 'string' ? raw.root : defaultPackagesConfig.root;

  let layout: PackagesConfig['layout'] = defaultPackagesConfig.layout;
  if (raw?.layout !== undefined) {
    if (VALID_LAYOUTS.has(raw.layout)) {
      layout = raw.layout as PackagesConfig['layout'];
    } else {
      diagnostics.push({
        field: 'layout',
        message: `Unknown [packages].layout value '${raw.layout}'; expected "flexible", "strict", or "off". Falling back to "${defaultPackagesConfig.layout}".`,
      });
    }
  }

  return { config: { root, layout }, diagnostics };
}

export interface ProjectManifest {
  project?: { name?: string; version?: string };
  language?: { preferred?: string };
  schemas?: { declared?: string[]; namespaces?: Record<string, string> };
  stock?: { load?: string[] };
  lint?: { strict?: boolean; requireDescriptions?: boolean };
  packages?: { root?: string; layout?: string };
}

export interface ResolvedManifest {
  name: string;
  projectRoot: string;
  preferredLanguage: string;
  declaredSchemas: string[];
  namespaces: Record<string, string>;
  stockVocabularies: string[];
  lint: { strict: boolean; requireDescriptions: boolean };
  packages: PackagesConfig;
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
    packages: resolvePackagesConfig(m?.packages).config,
  };
}