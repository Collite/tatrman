// SPDX-License-Identifier: Apache-2.0
import type { Definition, SearchBlock } from '@tatrman/parser';

function hasSearch(def: Definition): def is Definition & { search?: SearchBlock } {
  return 'search' in def && def.kind !== 'column' && def.kind !== 'attribute';
}

/**
 * Yields every search block reachable from a definition: the def's own block
 * plus per-column / per-attribute blocks. Ported verbatim from the old
 * `Validator.searchBlocksOf`.
 */
export function* searchBlocksOf(def: Definition): Iterable<SearchBlock> {
  if (hasSearch(def) && def.search) yield def.search;
  if (def.kind === 'table' || def.kind === 'view') {
    for (const m of def.columns ?? []) if (m.search) yield m.search;
  }
  if (def.kind === 'entity') {
    for (const m of def.attributes ?? []) if (m.search) yield m.search;
  }
}
