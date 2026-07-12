// SPDX-License-Identifier: Apache-2.0
import type { Document } from '@tatrman/parser';
import type { PackagesConfig } from './manifest.js';
import { inferPackageFromUri } from './package-inference.js';

/**
 * Directory-derived package name for a file, including the configured root
 * prefix (contracts §13.1, design §14.1):
 *
 *   derivedPackage(P) = join(".", filter-nonempty([cfg.root, ...relDirSegments]))
 *
 * `relDirSegments` is the directory path from the project root to the file's
 * parent. This is the **no-cascade** derivation: it reads only the file's own
 * path, never an ancestor file's `package` declaration.
 */
export function derivedPackage(fileUri: string, projectRoot: string, cfg: PackagesConfig): string {
  // Without a known project root we cannot derive path segments — deriving from
  // an absolute path would invent garbage packages (e.g. `Users.bora.…`). Fall
  // back to just the configured prefix (often ""). This keeps undeclared files
  // in the default package until a real root is known (e.g. browser hosts call
  // setProjectRoot after init).
  const path = fileUri.startsWith('file://') ? new URL(fileUri).pathname : fileUri;
  const inferred = projectRoot && path.startsWith(projectRoot)
    ? inferPackageFromUri(fileUri, projectRoot).inferred
    : '';
  return [cfg.root, inferred].filter((s) => s !== '').join('.');
}

/**
 * The `IDENT` shape from `TTR.g4` — letters/digits/underscore, **no hyphen**.
 * A package segment must match this to be a legal name (B24).
 */
const IDENT_RE = /^[a-zA-ZÀ-ɏ_][a-zA-Z0-9_À-ɏ]*$/;

/** Whether a single package segment is a valid `IDENT` (B24, design §14.1). */
export function isValidPackageSegment(segment: string): boolean {
  return IDENT_RE.test(segment);
}

/**
 * Directory-derived path segments that are **not** valid `IDENT`s (B24). Reads
 * only the file's own directory-derived package (never the configured `root`,
 * which is author config assumed valid), and applies **no** `-`→`_`
 * normalization — a hyphenated folder is reported, not silently rewritten.
 *
 * Empty when every directory segment is a valid `IDENT`, when there is no
 * directory-derived package (a root-level file), or when the project root is
 * unknown / the file lies outside it.
 */
export function invalidPackageSegments(fileUri: string, projectRoot: string): string[] {
  const path = fileUri.startsWith('file://') ? new URL(fileUri).pathname : fileUri;
  if (!projectRoot || !path.startsWith(projectRoot)) return [];
  const inferred = inferPackageFromUri(fileUri, projectRoot).inferred;
  if (inferred === '') return [];
  return inferred.split('.').filter((seg) => !isValidPackageSegment(seg));
}

/**
 * Effective package for a file: the in-file `package` declaration if present
 * (authoritative, B15), else {@link derivedPackage}. The declaration is taken
 * verbatim — it may elide the configured `root` (B17); the resolver normalises
 * both forms (see {@link Resolver}).
 */
export function effectivePackage(
  doc: Document,
  fileUri: string,
  projectRoot: string,
  cfg: PackagesConfig
): string {
  const declared = doc.packageDecl?.name;
  if (declared) return declared;
  return derivedPackage(fileUri, projectRoot, cfg);
}

/**
 * Strip the configured `root` prefix from a (possibly root-elided) dotted name,
 * yielding the path-relative form used for mismatch comparison. A name that
 * already omits the root is returned unchanged.
 */
export function elideRoot(name: string, root: string): string {
  if (!root) return name;
  if (name === root) return '';
  if (name.startsWith(`${root}.`)) return name.slice(root.length + 1);
  return name;
}

/**
 * Classification of how a file's `package` declaration relates to its directory
 * (PD1.6). Compared on the **root-elided** (path-relative) forms so a
 * declaration that omits the configured `root` is not spuriously flagged.
 *
 * - `none`   — declaration matches the derived path (or no declaration).
 * - `leaf`   — only the final segment differs (a harmless rename of the leaf).
 * - `prefix` — a non-leaf segment differs, or the segment count differs; the
 *              file is orphaned from anything resolving through its path
 *              (`ttr/package-prefix-divergence`).
 */
export type PackageMismatchKind = 'none' | 'leaf' | 'prefix';

export function classifyPackageMismatch(
  doc: Document,
  fileUri: string,
  projectRoot: string,
  cfg: PackagesConfig
): PackageMismatchKind {
  const declared = doc.packageDecl?.name;
  if (!declared) return 'none';

  // Without a known project root (or for a file outside it) we cannot derive the
  // directory package, so there is nothing to compare — never invent a mismatch
  // from an absolute path (the browser host sets the root after init).
  const path = fileUri.startsWith('file://') ? new URL(fileUri).pathname : fileUri;
  if (!projectRoot || !path.startsWith(projectRoot)) return 'none';

  const declaredRel = elideRoot(declared, cfg.root);
  const derivedRel = inferPackageFromUri(fileUri, projectRoot).inferred;

  // No directory-derived package (e.g. a root-level file): nothing to compare.
  if (derivedRel === '') return 'none';
  if (declaredRel === derivedRel) return 'none';

  const declParts = declaredRel.split('.');
  const pathParts = derivedRel.split('.');

  const sameLength = declParts.length === pathParts.length;
  const samePrefix =
    sameLength && declParts.slice(0, -1).every((seg, i) => seg === pathParts[i]);

  return samePrefix ? 'leaf' : 'prefix';
}
