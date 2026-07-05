import { MD_CALC_CATALOG, MD_CATALOG_VERSION, type CatalogEntry } from '@tatrman/md-catalog';

/**
 * The built-in calc-map catalog, pre-loaded as a read-only `calc:` symbol source
 * (contracts §5 / §8) — exactly as the stock CNC vocab is pre-loaded. It is not
 * user-overridable in v1; `@tatrman/lint` resolves a `def map`'s `calc:` against
 * it and the LSP completes catalog names from it. Re-exported through the
 * semantics barrel so consumers depend only on `@tatrman/semantics`.
 */
export { MD_CALC_CATALOG, MD_CATALOG_VERSION };
export type { CatalogEntry };

/** Whether `name` is a known built-in calc map. */
export function isKnownCalc(name: string): boolean {
  return MD_CALC_CATALOG.has(name);
}

/** The catalog entry for `name`, or undefined if unknown. */
export function getCalcEntry(name: string): CatalogEntry | undefined {
  return MD_CALC_CATALOG.get(name);
}

/** All known calc-map names (for completion). */
export function calcNames(): string[] {
  return [...MD_CALC_CATALOG.keys()];
}
