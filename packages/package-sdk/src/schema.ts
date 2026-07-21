// SPDX-License-Identifier: Apache-2.0
//
// FO-P4.S1.T1 — the package-manifest JSON Schema (draft 2020-12), FO contracts §15. This TS module is
// the single source of truth; `./schema/package-manifest.schema.json` is the published, language-agnostic
// copy (a non-TS package author validates against it directly) and is kept byte-in-sync by schema.spec.ts.

export const packageManifestSchema = {
  $schema: 'https://json-schema.org/draft/2020-12/schema',
  $id: 'https://tatrman.dev/schema/package-manifest.schema.json',
  title: 'Tatrman domain-package manifest',
  description:
    'FO contracts §15. The open (Apache) manifest a domain package declares. A package is authorable from this schema + the SPIs alone (FO-23 certification lever). Write-path plugins are deterministic (P-3); anything agentic lives in the golem slot, never in `plugins`.',
  type: 'object',
  required: ['package', 'version', 'model', 'canon'],
  additionalProperties: false,
  properties: {
    package: { type: 'string', description: 'Plain domain noun (FO-18).', pattern: '^[a-z][a-z0-9-]*$' },
    version: { type: 'string', pattern: '^\\d+\\.\\d+\\.\\d+$' },
    model: { $ref: '#/$defs/dirPath', description: 'TTR-M model.' },
    canon: { $ref: '#/$defs/dirPath', description: 'TTR-P canon incl. <table>-entry-apply programs.' },
    forms: { $ref: '#/$defs/dirPath', description: 'ttrl authored forms (optional overrides).' },
    plugins: {
      type: 'array',
      description:
        'P-3 write-path plugins: deterministic, versioned, pinned in the entry record. Agentic behaviour is NOT a plugin — it lives in the golem slot.',
      items: { $ref: '#/$defs/plugin' },
    },
    connectors: { type: 'array', items: { $ref: '#/$defs/plugin' } },
    golem: {
      type: 'object',
      description:
        'Agentic-face slot. FO validates presence + version; the config CONTENTS schema is Kantheon-owned (seam C-3).',
      required: ['config', 'schemaVersion'],
      additionalProperties: false,
      properties: {
        config: { $ref: '#/$defs/filePath' },
        schemaVersion: { type: 'integer', minimum: 1 },
      },
    },
    reconciliation: { $ref: '#/$defs/filePath', description: '§14 per-package reconciliation config.' },
  },
  $defs: {
    dirPath: {
      type: 'string',
      pattern: '^\\./.*/$',
      description: 'Package-relative directory (leading ./, trailing /).',
    },
    filePath: {
      type: 'string',
      pattern: '^\\./.*[^/]$',
      description: 'Package-relative file (leading ./, no trailing /).',
    },
    plugin: {
      type: 'object',
      required: ['id', 'type', 'artifact'],
      additionalProperties: false,
      properties: {
        id: { type: 'string', pattern: '^[a-z][a-z0-9-]*$' },
        type: {
          description:
            'Write-path plugin kinds only — all deterministic (P-3). `agent`/`golem` are deliberately absent: agentic behaviour is not a manifest plugin.',
          enum: ['proposal-source', 'canon-function', 'connector'],
        },
        artifact: { type: 'string', description: 'Published artifact ref (scope per ⚑1).' },
        determinism: {
          description: 'Write-path plugins must be pure (P-3). Present for explicitness; only `pure` is admissible.',
          const: 'pure',
        },
      },
    },
  },
} as const;
