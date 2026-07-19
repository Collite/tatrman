// SPDX-License-Identifier: Apache-2.0
import { defineConfig } from 'vitest/config';

// The integration suite boots real LSP servers (child processes / workers) and
// parses full sample trees. Under concurrent `pnpm -r test` CI load — every
// package suite competing for 2–4 runner cores — these bounded-but-heavy
// operations intermittently race vitest's default hook (10s) and test (5s)
// timeouts, surfacing as flaky "Hook/Test timed out" failures (seen in
// v1.1-samples and symbol-indexing-extended). The work is not hanging, just
// starved; give the whole suite a generous budget so CI contention can't trip
// it. A genuine hang still fails, only later.
export default defineConfig({
  test: {
    hookTimeout: 30_000,
    testTimeout: 30_000,
  },
});
