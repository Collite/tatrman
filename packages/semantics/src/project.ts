import type { ResolvedManifest } from './manifest.js';

export interface Project {
  root: string;
  manifest: ResolvedManifest;
  ttrFiles: string[];
}

export function loadProjectFromOpenDocuments(
  documents: Array<{ uri: string }>,
  rootUri: string,
  manifest: ResolvedManifest
): Project {
  const ttrFiles = documents
    .filter((d) => d.uri.endsWith('.ttrm'))
    .map((d) => {
      const path = d.uri.startsWith('file://') ? d.uri.slice(7) : d.uri;
      return path;
    });

  return { root: rootUri, manifest, ttrFiles };
}