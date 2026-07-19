// SPDX-License-Identifier: Apache-2.0
// FO-P0.S4.T3 — the visible-but-locked affordance for a refused commercial extension (FO contracts §2,
// FO-17 funnel). When the license loader refuses a `license:'platform'` extension in the open Viewer,
// the capability is shown LOCKED, never hidden — the user sees the feature exists and where to get it.
// This carries no edit code (FO-21): it is a disabled control + an upgrade pointer, nothing more.

import type { ReactNode } from 'react';

export interface LockedAuthoringAffordanceProps {
  /** the capability the user is seeing locked, e.g. `"Authoring"`. */
  featureName: string;
  /** optional upgrade entry point (FO-17 funnel); omitted → just the locked indicator. */
  onUpgrade?: () => void;
  className?: string;
}

/**
 * A disabled, discoverable indicator for a commercial capability absent from the open build. Rendered
 * where the edit affordance would otherwise mount. The lock glyph + accessible label make the locked
 * state visible to both sighted and assistive-tech users.
 */
export function LockedAuthoringAffordance({
  featureName,
  onUpgrade,
  className,
}: LockedAuthoringAffordanceProps): ReactNode {
  return (
    <span className={className} data-testid="locked-authoring-affordance">
      <button
        type="button"
        disabled
        aria-label={`${featureName} — locked; available in Tatrman Studio (commercial). Upgrade to enable.`}
        title="Available in Tatrman Studio (commercial)"
      >
        🔒 {featureName}
      </button>
      {onUpgrade ? (
        <button type="button" onClick={onUpgrade}>
          Upgrade…
        </button>
      ) : null}
    </span>
  );
}
