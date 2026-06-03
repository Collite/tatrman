import { describe, it, expect, vi, beforeEach } from 'vitest';
import { getCompletionConfig, invalidateCompletionConfig, loadCompletionConfig } from '../config-completion.js';

describe('config-completion', () => {
  beforeEach(() => {
    invalidateCompletionConfig();
  });

  it('getCompletionConfig returns merged config with defaults when workspace/configuration returns null', async () => {
    const mockConnection = {
      sendRequest: vi.fn().mockResolvedValue([undefined, undefined]),
    };

    const config = await loadCompletionConfig(mockConnection);
    expect(config.autoImport).toBe(true);
    expect(config.preselectFullyQualified).toBe(false);
  });

  it('getCompletionConfig returns cached config', () => {
    invalidateCompletionConfig();
    const config = getCompletionConfig();
    expect(config.autoImport).toBe(true);
    expect(config.preselectFullyQualified).toBe(false);
  });

  // NOTE: the end-to-end behaviour (autoImport:false ⇒ no additionalTextEdits on
  // a real completion) is covered by completion-config.test.ts. This case only
  // asserts that a `false` from workspace/configuration lands in the cache.
  it('loadCompletionConfig caches autoImport: false from workspace/configuration', async () => {
    const mockConnection = {
      sendRequest: vi.fn().mockResolvedValue([false]),
    };

    const config = await loadCompletionConfig(mockConnection);
    expect(config.autoImport).toBe(false);

    const cached = getCompletionConfig();
    expect(cached.autoImport).toBe(false);
  });

  it('invalidateCompletionConfig resets to defaults', async () => {
    const mockConnection = {
      sendRequest: vi.fn().mockResolvedValue([false]),
    };

    await loadCompletionConfig(mockConnection);
    expect(getCompletionConfig().autoImport).toBe(false);

    invalidateCompletionConfig();
    expect(getCompletionConfig().autoImport).toBe(true);
  });
});