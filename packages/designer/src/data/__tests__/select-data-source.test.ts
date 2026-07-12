// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { selectBackend, BackendSelectionError } from '../select-data-source.js';

describe('selectBackend', () => {
  it('selects the WS backend for a loopback ?server= origin', () => {
    expect(selectBackend('?server=ws://127.0.0.1:7270')).toEqual({ kind: 'ws', origin: 'ws://127.0.0.1:7270' });
    expect(selectBackend('?server=ws://localhost:7270')).toEqual({ kind: 'ws', origin: 'ws://localhost:7270' });
  });

  it('strips a trailing slash from the origin', () => {
    expect(selectBackend('?server=ws://127.0.0.1:7270/')).toEqual({ kind: 'ws', origin: 'ws://127.0.0.1:7270' });
  });

  it('defaults to the worker path with no params', () => {
    expect(selectBackend('')).toEqual({ kind: 'worker', demo: null });
  });

  it('keeps ?demo= on the worker path', () => {
    expect(selectBackend('?demo=v1.1-mini')).toEqual({ kind: 'worker', demo: 'v1.1-mini' });
  });

  it('rejects a non-loopback ?server= host (no guessing fallback)', () => {
    expect(() => selectBackend('?server=ws://evil.example.com:7270')).toThrow(BackendSelectionError);
  });

  it('rejects a ?server= value carrying a path', () => {
    expect(() => selectBackend('?server=ws://127.0.0.1:7270/ttrm')).toThrow(BackendSelectionError);
  });

  it('rejects a malformed ?server= value', () => {
    expect(() => selectBackend('?server=not-a-url')).toThrow(BackendSelectionError);
  });

  it('rejects ?server= and ?demo= together (conflicting backends)', () => {
    expect(() => selectBackend('?server=ws://127.0.0.1:7270&demo=v1.1-mini')).toThrow(BackendSelectionError);
  });
});
