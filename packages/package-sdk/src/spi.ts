// SPDX-License-Identifier: Apache-2.0
//
// FO-P4.S1.T2 — the SDK extension points, contracts only (no runtime). A domain package implements
// these against the published types alone (FO-23 certification lever): proposal-source parsers (§13)
// and canon-functions (§3). Everything here is deterministic + versioned (P-3) — the version is pinned
// in the entry record so every commit replays.

/** A §5 row-proposal batch — the deterministic boundary artifact a parser emits; the door consumes it. */
export interface RowBatch {
  batchId?: string;
  target: { ref: string };
  source: BatchSource;
  rows: RowEdit[];
}

export interface BatchSource {
  type: 'form' | 'import' | 'agent' | 'reconciliation';
  ref?: string;
  pluginId?: string;
  pluginVersion?: string;
}

export interface RowEdit {
  op: 'insert' | 'update' | 'delete';
  values: Record<string, unknown>;
  key?: Record<string, unknown>;
}

/** A per-row (or file-level, row 0) parser diagnostic — bad input becomes this, never a throw. */
export interface Diagnostic {
  row: number;
  code: string;
  detail: string;
}

/** What the parser is mapping onto — the writable target refs, opaque to the SDK. */
export interface ParseContext {
  readonly targets: readonly string[];
}

/**
 * §13 proposal-source parser: bytes (a broker statement, an Excel book, a SOAP response) → a §5 batch
 * plus diagnostics. Pure + versioned: same input + same version ⇒ same batch (P-3). MUST NOT throw on
 * bad input — surface a [Diagnostic] instead.
 */
export interface ProposalSourceParser {
  readonly id: string;
  readonly version: string;
  parse(input: Uint8Array, ctx: ParseContext): { batch: RowBatch; diagnostics: Diagnostic[] };
}

export interface TypedSignature {
  params: { name: string; type: string }[];
  returns: string;
}

/**
 * §3 canon-function: a pure, versioned function callable from TTR-P (e.g. TWR/MWR/FIFO where beyond the
 * language). No I/O, no clock — determinism is the contract; the version pins it in the entry record.
 */
export interface CanonFunction<A extends readonly unknown[] = readonly unknown[], R = unknown> {
  readonly id: string;
  readonly version: string;
  readonly signature: TypedSignature;
  eval(...args: A): R;
}

/** Connector SPI — stub in v1 (external sinks/sources); shape reserved so the manifest slot is typed. */
export interface Connector {
  readonly id: string;
  readonly version: string;
}
