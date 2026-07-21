// SPDX-License-Identifier: Apache-2.0
//
// @tatrman/package-sdk — the open (Apache) contracts for Tatrman domain packages (FO contracts §13/§15).
// The certification lever (FO-23): a domain package is authorable from this package alone — the manifest
// schema + the parser / canon-function SPIs — with no commercial (loader/organ/golem-runtime) code.
//
// FO-P4.S1. Published to the internal/managed registry first; the public cut is ⚑2-held (Bora's go).

export {
  validateManifest,
  packageManifestSchema,
  type PackageManifest,
  type PluginRef,
  type PluginType,
  type GolemSlot,
  type ManifestValidation,
} from './manifest.js';

export {
  type ProposalSourceParser,
  type CanonFunction,
  type Connector,
  type RowBatch,
  type BatchSource,
  type RowEdit,
  type Diagnostic,
  type ParseContext,
  type TypedSignature,
} from './spi.js';
