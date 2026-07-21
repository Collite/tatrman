// SPDX-License-Identifier: Apache-2.0
//
// FO-P4.S1 — the domain-package manifest contract (FO contracts §15). The manifest schema is the
// published, Apache-licensed artifact a third party validates against; a package is authorable from
// this + the SPIs alone (FO-23 certification lever). Validation is over a parsed manifest object —
// YAML→object parsing is the loader's concern (this SDK stays parser-agnostic).

import type { ValidateFunction } from 'ajv';
import Ajv2020Module from 'ajv/dist/2020.js';
import { packageManifestSchema } from './schema.js';

// ajv ships a CJS default under an ESM wrapper; unwrap it the same way `packages/lsp` does (Node16).
type AjvCtor = new (opts?: { allErrors?: boolean; strict?: boolean }) => {
  compile(schema: object): ValidateFunction;
};
const AjvClass = ((Ajv2020Module as unknown as { default?: AjvCtor }).default ??
  (Ajv2020Module as unknown as AjvCtor)) as AjvCtor;

/** The write-path plugin kinds a manifest may declare — all deterministic (P-3). Agentic ≠ plugin. */
export type PluginType = 'proposal-source' | 'canon-function' | 'connector';

export interface PluginRef {
  id: string;
  type: PluginType;
  artifact: string;
  determinism?: 'pure';
}

export interface GolemSlot {
  /** Slot only — the config CONTENTS schema is Kantheon-owned (seam C-3). */
  config: string;
  schemaVersion: number;
}

export interface PackageManifest {
  package: string;
  version: string;
  model: string;
  canon: string;
  forms?: string;
  plugins?: PluginRef[];
  connectors?: PluginRef[];
  golem?: GolemSlot;
  reconciliation?: string;
}

/** The published JSON Schema (draft 2020-12) — re-exported so the loader/tests share one source. */
export { packageManifestSchema } from './schema.js';

export interface ManifestValidation {
  valid: boolean;
  /** Human-readable `instancePath message` lines; empty when valid. */
  errors: string[];
}

let _validate: ValidateFunction | undefined;
function compiled(): ValidateFunction {
  if (!_validate) {
    const ajv = new AjvClass({ allErrors: true, strict: false });
    _validate = ajv.compile(packageManifestSchema as object);
  }
  return _validate;
}

/**
 * Validate a parsed manifest object against the §15 schema. Never throws — a malformed manifest comes
 * back as `{valid:false, errors}`. A manifest that declares an agentic plugin in the write path
 * (`type` outside the deterministic enum) is rejected here — the P-3 boundary, enforced by the contract.
 */
export function validateManifest(manifest: unknown): ManifestValidation {
  const validate = compiled();
  const valid = validate(manifest) as boolean;
  const errors = valid
    ? []
    : (validate.errors ?? []).map((e) => `${e.instancePath || '/'} ${e.message ?? 'invalid'}`.trim());
  return { valid, errors };
}
