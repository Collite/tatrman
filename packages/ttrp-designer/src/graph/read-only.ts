// SPDX-License-Identifier: Apache-2.0
import type { ContainerView, GetGraphResult } from './types.js';

/**
 * A container is read-only on canvas (no edit affordances, even after Stage 5.4) when it
 * is a fragment container (`"""sql`/`"""pandas`/`"""ttrb`) or a derived bare-fragment
 * sub-graph (C2-f, C1-b-iv): fragment interiors are formatter-untouchable and auto-only.
 */
export function isContainerReadOnly(c: ContainerView): boolean {
  return c.derived || c.fragment != null;
}

/** The dialect banner text for a read-only container drill-in, or null if editable. */
export function readOnlyBanner(c: ContainerView): string | null {
  if (c.fragment != null) return `derived from ${c.fragment} fragment — read-only`;
  if (c.derived) return 'derived sub-graph — read-only';
  return null;
}

export function containerByPath(result: GetGraphResult, path: string): ContainerView | undefined {
  return result.graph.containers.find((c) => c.path === path);
}
