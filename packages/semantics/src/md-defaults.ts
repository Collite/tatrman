// SPDX-License-Identifier: Apache-2.0
import type { CubeletDef, MeasureDef } from '@tatrman/parser';

/**
 * MD Layer-A defaults — the TS twin of Kotlin `ttr-semantics` `Defaults.kt` (review-071 T-P2 / S1-B5).
 * Kotlin is canonical (the runtime path); this mirrors it so the parity harness can byte-compare the
 * two. Layer-A default only: the `nonAdditive`-must-not-be-blind-summed GATE lives in the resolver
 * (Kotlin-only), not here — `defaultAgg` is the raw ttr-semantics fallback, matching `MdMeasure.defaultAgg`.
 */
export type AggKind = 'sum' | 'avg' | 'min' | 'max' | 'count';

/** Mirror of `aggKindOf`: the closed R5 set (`sum/avg/min/max/count`) + `latestValid ⇒ max`; else undefined. */
export function aggKindOf(spelling: string): AggKind | undefined {
  switch (spelling.toLowerCase()) {
    case 'sum':
      return 'sum';
    case 'avg':
      return 'avg';
    case 'min':
      return 'min';
    case 'max':
      return 'max';
    case 'count':
      return 'count';
    case 'latestvalid':
      return 'max'; // "latest valid" ⇒ MAX (D26 coarse stand-in; real lowering derives from valid roles)
    default:
      return undefined;
  }
}

/** A cubelet's default measure = its first declared measure (by simple name), or undefined if none. */
export function defaultMeasure(cubelet: CubeletDef): string | undefined {
  const first = cubelet.measures[0];
  if (first === undefined) return undefined;
  return typeof first === 'string' ? (first.split('.').pop() as string) : first.name;
}

/** A measure's default aggregation: its declared `aggregation`, else `sum` (the additive fallback). */
export function defaultAgg(measure: MeasureDef): AggKind {
  const declared = measure.aggregation?.default;
  return (declared ? aggKindOf(declared) : undefined) ?? 'sum';
}
