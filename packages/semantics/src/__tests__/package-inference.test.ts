// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { inferPackageFromUri } from '../package-inference.js';

describe('inferPackageFromUri', () => {
  it('plain path: /proj/pkg_a/sub/file.ttrm, projectRoot /proj/ → inferred pkg_a.sub', () => {
    const result = inferPackageFromUri('/proj/pkg_a/sub/file.ttrm', '/proj/');
    expect(result.inferred).toBe('pkg_a.sub');
    expect(result.isRootFile).toBe(false);
  });

  it('file:// URI: file:///proj/pkg_a/file.ttrm, projectRoot /proj/ → inferred pkg_a', () => {
    const result = inferPackageFromUri('file:///proj/pkg_a/file.ttrm', '/proj/');
    expect(result.inferred).toBe('pkg_a');
    expect(result.isRootFile).toBe(false);
  });

  it('root file: /proj/main.ttrm, projectRoot /proj/ → isRootFile true, inferred empty', () => {
    const result = inferPackageFromUri('/proj/main.ttrm', '/proj/');
    expect(result.inferred).toBe('');
    expect(result.isRootFile).toBe(true);
  });

  it('.ttrg file: /proj/pkg_a/graphs/main.ttrg, projectRoot /proj/ → inferred pkg_a.graphs', () => {
    const result = inferPackageFromUri('/proj/pkg_a/graphs/main.ttrg', '/proj/');
    expect(result.inferred).toBe('pkg_a.graphs');
    expect(result.isRootFile).toBe(false);
  });

  it('file:// root: file:///proj/main.ttrm, projectRoot /proj/ → isRootFile true', () => {
    const result = inferPackageFromUri('file:///proj/main.ttrm', '/proj/');
    expect(result.inferred).toBe('');
    expect(result.isRootFile).toBe(true);
  });
});