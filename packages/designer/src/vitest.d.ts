// SPDX-License-Identifier: Apache-2.0
// Augment vitest's `expect` with jest-dom matcher TYPES (DM-P2.S4). The runtime registration lives
// in `test-setup.ts` (`expect.extend(matchers)`); this makes tsc aware of `toBeInTheDocument`,
// `toHaveTextContent`, `toHaveAttribute`, … so the ported component suites type-check. Kept separate
// from the `/vitest` entry deliberately — that entry binds matchers to a second `expect` under the
// multi-vitest workspace (the DM-P2 jest-dom bug); we want its types without its runtime.
import 'vitest';
import type { TestingLibraryMatchers } from '@testing-library/jest-dom/matchers';

declare module 'vitest' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type -- the jest-dom augmentation is a pure `extends`
  interface Assertion<T = unknown> extends TestingLibraryMatchers<T, void> {}
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type -- ditto
  interface AsymmetricMatchersContaining extends TestingLibraryMatchers<unknown, void> {}
}
