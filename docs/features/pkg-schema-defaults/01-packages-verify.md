# Stage 1 — Item 1: verify optional packages + root default

**Goal:** prove the already-shipped behavior with tests; **no production code
changes**. If any test below fails, that is a real bug — stop and report it
rather than "fixing" the test to pass.

**Confirmed decision:** the default package is the empty-string **root package**
(`''`). We are *not* introducing a named `default` package.

**Behavior to lock in (from the current code):**
- `package` is optional: `packageDecl?` in `packages/grammar/src/TTR.g4:38`.
- Consumers read `ast.packageDecl?.name ?? ''`.
- Package is inferred from directory path by `inferPackageFromUri`
  (`packages/semantics/src/package-inference.ts`).
- A **non-root** file with no `package` line emits info diagnostic
  `MissingPackageDeclaration` (`packages/semantics/src/validator.ts:455–481`).
- **Root** files and `.ttrg` files are exempt (no diagnostic).
- A declared package that disagrees with the inferred one emits the **error**
  `PackageDeclarationMismatch`.

---

- [ ] **1.1 (TS) — Parser: a package-less file parses with no package.** In
  `packages/parser/src/__tests__/` add a test parsing a minimal file with no
  `package` line and assert `ast.packageDecl` is `undefined`/absent and
  `errors` is empty. Confirm a file *with* `package a.b.c` yields
  `ast.packageDecl.name === 'a.b.c'`.

- [ ] **1.2 (TS) — Semantics: root file, no package ⇒ no diagnostic.** In
  `packages/semantics/src/__tests__/`, drive `Validator.validatePackageDeclarations`
  with a URI that `inferPackageFromUri` classifies as a **root** file and no
  package decl. Assert **zero** diagnostics. (Read `package-inference.ts` to pick
  a URI that yields `isRootFile: true`.)

- [ ] **1.3 (TS) — Semantics: non-root file, no package ⇒ info
  `MissingPackageDeclaration`.** Same harness, a non-root URI, no package decl.
  Assert exactly one diagnostic with `code === DiagnosticCode.MissingPackageDeclaration`,
  `severity === 'info'`, and a message naming the inferred package.

- [ ] **1.4 (TS) — Semantics: `.ttrg` file is exempt.** A `.ttrg` URI with no
  package decl ⇒ zero package diagnostics (guard at `validator.ts:460`).

- [ ] **1.5 (TS) — Semantics: declared ≠ inferred ⇒ error
  `PackageDeclarationMismatch`.** Non-root URI whose inferred package is `x.y`
  but the file declares `package z`. Assert one diagnostic with
  `code === DiagnosticCode.PackageDeclarationMismatch`, `severity === 'error'`.

- [ ] **1.6 (Kotlin) — mirror 1.2/1.3/1.5 in
  `packages/kotlin/ttr-semantics/src/test/kotlin/org/tatrman/ttr/semantics/PackageInferenceSpec.kt`**
  (or `ValidatorSpec.kt` if the package-declaration checks live there — grep for
  `MissingPackageDeclaration` / `PackageDeclarationMismatch` first). Use the same
  three cases: root-no-package ⇒ none; non-root-no-package ⇒ info; mismatch ⇒
  error. Assert identical `DiagnosticCode` values to TS.

- [ ] **1.7 — Run and tick.** `pnpm --filter @modeler/parser test`,
  `pnpm --filter @modeler/semantics test`, and
  `./gradlew :packages:kotlin:ttr-semantics:test` all green. Then check every
  box above.

### Stage 1 DoD
- [ ] All seven tasks ticked; no production source files modified (only test
      files added). Confirm with `git status` — only `__tests__`/`*Spec.kt`
      files changed.
