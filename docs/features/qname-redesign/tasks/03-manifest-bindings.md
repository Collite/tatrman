# 03 — Manifest: named schemas + package bindings (modeler / TS)

Goal: `modeler.toml` accepts `[schemas.<name>]`, `[packages.*] default-schema`,
`[defaults] schema`, `[lint] require-qualified-refs` → typed `ManifestConfig`. See
[`../contracts.md`](../contracts.md) §1–§2, §5.

**Pre-flight:** Phases 01–02 merged; baseline green. Read
`packages/semantics/src/manifest.ts` (current `resolveManifest`, `ResolvedManifest`
at lines 60/97; old `schemas.namespaces` at 54/103).

## Tasks

- [ ] **3.1 Tests first (manifest suite).** Parse `[schemas.dbo]` →
  `SchemaBinding{database,dbSchema,dialect}`; `[packages."shop.sales"].default-schema`;
  `[defaults].schema`; `[lint].require-qualified-refs`. No-collision (D9): a schema
  named like a package / model code / kind keyword → **error**. Equivalence with the
  embedded-SQL `[[sql.namespace-map]]` shape (contracts §1.1).
- [ ] **3.2 DTOs.** Add `SchemaBinding`, `PackageConfig`, `ManifestConfig` (contracts
  §2). Keep the old `schemas.namespaces` field *readable* (the migrator lifts it),
  marked deprecated.
- [ ] **3.3 `resolveManifest`.** Populate `schemas`, `packages`, `defaults`, `lint`;
  validate no-collision at load; surface diagnostics.
- [ ] **3.4 Vocab builder.** Expose `buildVocab(manifest, kinds)` → `Vocab`
  (contracts §4) so the resolver/parser get the registered schema + package + model +
  kind sets from one place.

## Done when

- [ ] Manifest suite green; collisions error; `resolveManifest` returns
  `ManifestConfig`.
- [ ] `pnpm --filter @modeler/semantics test` + `pnpm -r typecheck` green.

**Verify:** `pnpm --filter @modeler/semantics test -- manifest`
