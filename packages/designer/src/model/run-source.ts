// SPDX-License-Identifier: Apache-2.0
// DM-P4.S1: ported from modeler designer/src/model/run-source.ts (canvas-core namespace rewritten to @tatrman).
// The RunSource adapter (contracts §5 / §1.3) — run status + display results. The FIXTURE impl
// (scripted event walk + canned Arrow, base64-embedded so it works in the browser without fs or
// an asset loader) is a v1 deliverable and what component tests run on. The LIVE impl (ttrp/run
// shell-out + out/ Arrow watcher, C1-e/f arc) plugs in behind the SAME interface when the gate
// opens — the canvas/drawer never see the transport. A missing RunSource ⇒ available:false, which
// the UI renders as disabled-with-hint (DS-RUN-001), never hidden (P-3).

import { tableFromIPC } from 'apache-arrow';
import type { RunStatus, DiagnosticsState } from '@tatrman/canvas-core';

/** The table shape the drawer consumes — Arrow parsed to plain columns/rows (no Arrow types leak). */
export interface ArrowTable {
  columns: string[];
  rows: unknown[][];
  numRows: number;
}

/** A run status transition. status walks the FIXED §1.3 vocabulary — no invented states. */
export interface RunEvent {
  status: RunStatus;
  /** on 'done', the display sink whose Arrow result is now readable. */
  sinkRef?: string;
  diagnostics?: DiagnosticsState;
}

export interface RunSource {
  /** false ⇒ no backend; the UI renders run controls disabled-with-hint (DS-RUN-001). */
  available: boolean;
  run(programRef: string): AsyncIterable<RunEvent>;
  readDisplayResult(sinkRef: string): Promise<ArrowTable>;
}

/** Parse Arrow IPC bytes into the drawer's table shape (apache-arrow tableFromIPC, v21). */
export function parseArrowTable(bytes: Uint8Array): ArrowTable {
  const table = tableFromIPC(bytes);
  const columns = table.schema.fields.map((f) => f.name);
  const rows: unknown[][] = [];
  for (let i = 0; i < table.numRows; i++) {
    rows.push(columns.map((_, j) => table.getChildAt(j)?.get(i)));
  }
  return { columns, rows, numRows: table.numRows };
}

// base64-decode that works in both Node and the browser (no Buffer dependency in the bundle).
function decodeBase64(b64: string): Uint8Array {
  if (typeof atob === 'function') {
    const bin = atob(b64);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
  }
  // Node fallback
  return new Uint8Array(Buffer.from(b64, 'base64'));
}

// The canned display Arrow for `display top_customers` (5 rows), mirroring
// samples/orders-hero/processing/out/top_customers.arrow (DS-P0.S2.T7). Regenerate via
// samples/orders-hero/processing/gen-arrow.mjs and re-embed if the fixture data changes.
const TOP_CUSTOMERS_ARROW_B64 = 'QVJST1cxAAD/////sAAAABQAAAAAAAAADAAcABoAEwAUAAQADAAAAEAAAAAAAAAAAAAAAAAAAAIQAAAAAAAEAAgACgAAAAQACAAAABAAAAAAAAoAGAAMAAgABAAKAAAAFAAAAEgAAAAFAAAAAAAAAAAAAAADAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAGAAAAAAAAAAYAAAAAAAAACgAAAAAAAAAAAAAAAEAAAAFAAAAAAAAAAAAAAAAAAAAAAAAAAkAAAAPAAAAFgAAAB4AAAAlAAAAQWNtZSBDb3JwR2xvYmV4SW5pdGVjaFVtYnJlbGxhU295bGVudAAAAP////+4AAAAFAAAAAAAAAAMABwAGgATABQABAAMAAAAMAAAAAAAAAAAAAAAAAAAAhAAAAAAAAQACAASAAgABAAIAAAAGAAAAAEAAAAAAAAAAAAKABgADAAIAAQACgAAABQAAABIAAAABAAAAAAAAAAAAAAAAwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABgAAAAAAAAAGAAAAAAAAAAYAAAAAAAAAAAAAAABAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAFAAAACgAAAA4AAAASAAAAAAAAAE5vcnRoU291dGhFYXN0V2VzdAAAAAAAAP////8YAQAAFAAAAAAAAAAMABYAFAAPABAABAAMAAAAcAAAAAAAAAAAAAADEAAAAAQACgAYAAwACAAEAAoAAAAUAAAAmAAAAAUAAAAAAAAAAAAAAAgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAYAAAAAAAAABgAAAAAAAAAAAAAAAAAAAAYAAAAAAAAABgAAAAAAAAAMAAAAAAAAAAAAAAAAAAAADAAAAAAAAAAKAAAAAAAAABYAAAAAAAAAAAAAAAAAAAAWAAAAAAAAAAYAAAAAAAAAAAAAAAEAAAABQAAAAAAAAAAAAAAAAAAAAUAAAAAAAAAAAAAAAAAAAAFAAAAAAAAAAAAAAAAAAAABQAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAgAAAAMAAAAEAAAAAAAAAAAAAAABAAAAAAAAAAIAAAADAAAAAAAAAAAAAACURQZBAAAAAGAUA0EAAAAABlMBQQAAAACEHPhAAAAAAOBQ9UDYBAAAUQQAANQDAACAAgAATgIAAAAAAAD/////AAAAABAAAAAMABQADgAQAAQACAAMAAAAEAAAAEQAAAAAAAQAWAAAAAIAAAAIAAAAAAAAALgAAAAAAAAAQAAAAAAAAAAAAQAAAAAAAMAAAAAAAAAAMAAAAAAAAAAAAAAAAQAAAPABAAAAAAAAIAEAAAAAAABwAAAAAAAAANT+//8EAAAABAAAAOgAAACAAAAARAAAAAQAAADU////EAAAABQAAAAAAAACEAAAAAMAAABxdHkAAAAAAAD///8AAAABIAAAABAAFAAEAAAADwAQAAAACAAQAAAAEAAAABwAAAAAAAADIAAAAAoAAABuZXRfYW1vdW50AAAAAAAAAAAGAAgABgAGAAAAAAACAKz///80AAAAFAAAAAAAAAEYAAAAAAAABRQAAAAGAAAAcmVnaW9uAAAAAAAAmP///wgAEAAIAAQACAAAAAwAAAABAAAAAAAAAJz///8AAAABIAAAABAAHAAIAA8AFwAYAAQAEAAQAAAAPAAAABQAAAAAAAABHAAAAAAAAAUcAAAACAAAAGN1c3RvbWVyAAAAAAAAAAAEAAQABAAAAAgACAAAAAQACAAAAAwAAAAIAAwACAAHAAgAAAAAAAABIAAAAMgBAABBUlJPVzE=';

const CANNED: Record<string, string> = { top_customers: TOP_CUSTOMERS_ARROW_B64 };

export interface FixtureRunOptions {
  /** terminal status of the scripted walk (contracts §1.3). Default 'done'. */
  outcome?: 'done' | 'failed';
  /** diagnostics reported on a 'failed' terminal event. */
  diagnostics?: DiagnosticsState;
  /** the display sink the run produces (readable via readDisplayResult on 'done'). */
  sinkRef?: string;
}

/** Fixture/replay RunSource: a scripted idle→running→(done|failed) walk + canned Arrow. */
export function fixtureRunSource(opts: FixtureRunOptions = {}): RunSource {
  const outcome = opts.outcome ?? 'done';
  const sinkRef = opts.sinkRef ?? 'top_customers';
  return {
    available: true,
    async *run(): AsyncIterable<RunEvent> {
      yield { status: 'idle' };
      yield { status: 'running' };
      if (outcome === 'failed') {
        yield { status: 'failed', diagnostics: opts.diagnostics ?? { errorCount: 1, warnCount: 0 } };
      } else {
        yield { status: 'done', sinkRef };
      }
    },
    async readDisplayResult(sink: string): Promise<ArrowTable> {
      const b64 = CANNED[sink];
      if (!b64) throw new Error(`no canned Arrow for sink "${sink}"`);
      return parseArrowTable(decodeBase64(b64));
    },
  };
}

/** The absent RunSource — no backend wired (the gate is closed). available:false ⇒ DS-RUN-001. */
export function absentRunSource(): RunSource {
  return {
    available: false,
    async *run(): AsyncIterable<RunEvent> {
      // no backend — callers must gate on `available` before iterating; this makes misuse loud.
      throw new Error('no run backend (DS-RUN-001)');
    },
    async readDisplayResult(): Promise<ArrowTable> {
      throw new Error('no run backend (DS-RUN-001)');
    },
  };
}
