// SPDX-License-Identifier: Apache-2.0
/**
 * Given a document URI and the project root, infers the expected package name
 * from the file path. Handles both plain filesystem paths and `file://` URIs.
 */
export function inferPackageFromUri(uri: string, projectRoot: string): { inferred: string; isRootFile: boolean } {
  const path = uri.startsWith('file://') ? new URL(uri).pathname : uri;

  const relativePath = path.startsWith(projectRoot) ? path.slice(projectRoot.length) : path;
  const segments = relativePath.split('/').filter(Boolean);

  const isRootFile = segments.length === 1 || (segments.length === 2 && segments[1].startsWith('.'));

  let inferred = '';
  if (segments.length >= 2) {
    const withoutFile = segments.slice(0, -1);
    const withoutExt = withoutFile.map((s) => s.replace(/\.(ttr|ttrg)$/, ''));
    inferred = withoutExt.join('.');
  }

  return { inferred, isRootFile };
}