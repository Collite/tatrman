/**
 * v4.1 world-model validators (ttr-metadata M0). Warning-severity only — the
 * hard-error twins (staging conflict is a compile error) live in M2's
 * `WorldResolver`, per MD5 (ttr-metadata is mechanism; the compiler is policy).
 * The library never mints TTRP-* diagnostic ids; these `world/*` codes are the
 * TTR-M editor surface and are a cross-target contract (mirrored in Kotlin
 * `Validator.kt`).
 */
import type { Document, SourceLocation, WorldDef } from '@tatrman/parser';

export type WorldDiagnosticCode = 'world/duplicate-staging' | 'world/hosts-unknown-package' | 'world/wrong-model-kind';

export interface WorldDiagnostic {
  code: WorldDiagnosticCode;
  message: string;
  severity: 'warning';
  source: SourceLocation;
}

/**
 * Validate the world-model layer of one document.
 *
 * @param doc            parsed document
 * @param knownPackages  package names known to the project (for `hosts:` checks);
 *                       when omitted, `hosts:` entries are not flagged.
 */
export function validateWorldDocument(doc: Document, knownPackages?: ReadonlySet<string>): WorldDiagnostic[] {
  const diagnostics: WorldDiagnostic[] = [];
  const modelCode = doc.modelDirective?.modelCode;
  const isWorldFile = modelCode === 'world';

  for (const def of doc.definitions) {
    if (def.kind === 'world') {
      // A `def world` in a non-`model world` file is misplaced.
      if (!isWorldFile) {
        diagnostics.push({
          code: 'world/wrong-model-kind',
          message: `'def world ${def.name}' is only valid in a 'model world' file`,
          severity: 'warning',
          source: def.source,
        });
      }
      validateWorld(def, knownPackages, diagnostics);
    } else if (isWorldFile) {
      // A non-world def in a `model world` file is misplaced.
      diagnostics.push({
        code: 'world/wrong-model-kind',
        message: `'def ${def.kind} ${def.name}' is not valid in a 'model world' file`,
        severity: 'warning',
        source: def.source,
      });
    }
  }

  return diagnostics;
}

function validateWorld(world: WorldDef, knownPackages: ReadonlySet<string> | undefined, out: WorldDiagnostic[]): void {
  // D-f: exactly one storage may be `staging: true`. Two+ is a warning here
  // (hard error in WorldResolver).
  const stagingStorages = world.storages.filter((s) => s.staging === true);
  if (stagingStorages.length > 1) {
    out.push({
      code: 'world/duplicate-staging',
      message: `world '${world.name}' declares ${stagingStorages.length} staging storages (${stagingStorages
        .map((s) => s.name)
        .join(', ')}); exactly one is allowed`,
      severity: 'warning',
      source: world.source,
    });
  }

  // D-d-i: every `hosts:` entry should name a known model package.
  if (knownPackages) {
    for (const storage of world.storages) {
      for (const pkg of storage.hosts) {
        if (!knownPackages.has(pkg)) {
          out.push({
            code: 'world/hosts-unknown-package',
            message: `storage '${storage.name}' hosts unknown package '${pkg}'`,
            severity: 'warning',
            source: storage.source,
          });
        }
      }
    }
  }
}
