/**
 * Built-in calc-map catalog types (map-catalog.md §4, contracts.md §8 — kept in
 * lockstep). A catalog entry is an **abstract function signature**, not an
 * implementation: it carries no SQL, only the dialect-agnostic semantics that
 * ai-platform lowers per `(name, dialect)`.
 */

/** A time-domain shape: a point/day, or either. Matches `timestamp`/`datetime`/`date`. */
export type TimeShape = 'instant' | 'date' | 'instant|date';

/** A (possibly bounded) integer shape. `lo`/`hi` absent ⇒ unbounded (e.g. `Year`). */
export interface IntShape {
  kind: 'int';
  lo?: number;
  hi?: number;
}

/** The type a map's `from`/`to` domain must satisfy. */
export type CatalogShape = TimeShape | IntShape;

/**
 * A configuration parameter of an entry. `type` is either a bounded integer or
 * a closed `enum`; an `enum` param lists its `values` and may carry a `default`.
 */
export interface CatalogParam {
  name: string;
  type: IntShape | 'enum';
  values?: string[];
  default?: string | number;
}

export interface CatalogEntry {
  name: string;
  category: 'truncation' | 'extraction' | 'rollup' | 'fiscal';
  params: CatalogParam[];
  input: CatalogShape;
  output: CatalogShape;
  /** Every time map is a function and coarsening ⇒ N:1 (map-catalog.md §1). */
  cardinality: 'N:1';
  /** Abstract meaning ai-platform lowers; the catalog contains no SQL. */
  semantics: string;
}
