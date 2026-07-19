// SPDX-License-Identifier: Apache-2.0
// Extend the designer's OWN vitest `expect` with jest-dom matchers. We use the `/matchers`
// subpath + an explicit `expect.extend` (rather than `@testing-library/jest-dom/vitest`,
// which imports its own `vitest` and can bind matchers to a different `expect` instance when
// more than one vitest version is resolved in the workspace — the cause of the "Invalid Chai
// property: toBeInTheDocument" failures the DM-P2 port inherited; see DM-P2.S5 / dm-p1-review.md).
import { expect } from 'vitest';
import * as matchers from '@testing-library/jest-dom/matchers';

expect.extend(matchers);

// React Flow (the canvas kernel engine, DM-P2.S2) measures nodes via ResizeObserver + reads
// matchMedia; jsdom provides neither. Polyfill globally so any test that mounts the canvas works.
class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}
if (!globalThis.ResizeObserver) {
  (globalThis as unknown as { ResizeObserver: typeof ResizeObserverStub }).ResizeObserver = ResizeObserverStub;
}
if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false, media: query, onchange: null,
    addListener() {}, removeListener() {}, addEventListener() {}, removeEventListener() {}, dispatchEvent() { return false; },
  })) as unknown as typeof window.matchMedia;
}
