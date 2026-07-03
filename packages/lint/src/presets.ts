import type { RuleId, Severity } from './rule.js';

export type PresetName = 'recommended' | 'strict' | 'all' | 'none';

export const PRESET_NAMES: ReadonlySet<string> = new Set<PresetName>([
  'recommended',
  'strict',
  'all',
  'none',
]);

/**
 * The severity a preset assigns to a rule, or `undefined` when the preset leaves
 * the rule at its `defaultSeverity` (design §5.4). Modeling presets as partial
 * maps is what keeps `recommended` from force-clamping correctness rules whose
 * default is below `error` (e.g. `fuzzy-without-searchable`): the preset simply
 * doesn't touch them, so the correctness floor (§6.5) never engages.
 */
export function presetSeverity(preset: PresetName, ruleId: RuleId): Severity | undefined {
  switch (preset) {
    case 'recommended':
      return ruleId === 'missing-description' ? 'off' : undefined;
    case 'strict':
      if (ruleId === 'missing-description') return 'warning';
      if (ruleId === 'unresolved-reference') return 'error';
      return undefined;
    case 'all':
      return 'error';
    case 'none':
      return 'off';
  }
}
