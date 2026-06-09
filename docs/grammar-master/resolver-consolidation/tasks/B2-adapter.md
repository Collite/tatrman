# B.2 — `PublishedResolverAdapter` (build + resolve + maps)

**Repo:** ai-platform. **Effort:** ~half day.

**Pre-flight:**
- B.1 done (`ResolverParitySpec` red on the missing adapter).
- Read [`../contracts.md`](../contracts.md) §2, §2.1, §2.3.

**File:** new `infra/metadata/src/main/kotlin/infra/metadata/resolve/PublishedResolverAdapter.kt`.

This stage implements everything **except** the body of `toProtoQName`, which is
B.3. Stub `toProtoQName` to return a minimal `QualifiedName` (e.g. name only) so
the class compiles and `resolve` wires end-to-end; the parity edge cases stay red
until B.3.

---

- [ ] **B.2.1 — Create the class skeleton** per contracts §2: the `private`
      constructor, the `build(files)` companion that calls
      `TtrSymbolTable().upsertDocument(...)` per file with
      `packageName = f.declaredPackage ?: ""` (contracts §2.1), and the
      `byQname = table.all().associateBy { it.qname }` index.

- [ ] **B.2.2 — Import-alias the published types** to avoid clashes with
      ai-platform's `ResolutionContext`/`Resolution`:
      ```kotlin
      import org.tatrman.ttr.semantics.ResolutionContext as TtrCtx
      import org.tatrman.ttr.semantics.ResolutionResult as TtrResult
      import org.tatrman.ttr.semantics.ResolutionStep
      import org.tatrman.ttr.semantics.Resolver as TtrResolver
      import org.tatrman.ttr.semantics.SymbolEntry
      import org.tatrman.ttr.semantics.SymbolTable as TtrSymbolTable
      ```

- [ ] **B.2.3 — Implement the bare-import guard** (contracts §2, step 1): for a
      non-wildcard `imp` with `imp.target.split(".").size < 3`, return
      `unimported(ref)` before touching the published resolver. Move the legacy
      `unimported(ref)` / `ambiguous(ref)` helpers (and the diagnostic message
      strings — keep them byte-identical) into the adapter or a shared file.

- [ ] **B.2.4 — Implement the context map** (contracts §2): build `TtrCtx` from
      ai-platform's `ResolutionContext` — `schemaCode = ctx.schemaCode ?: "db"`,
      `namespace = ctx.resolvedNamespace ?: ""`, `imports = ctx.imports`,
      `packageName = ctx.packageName`, `enclosingQname = null`.

- [ ] **B.2.5 — Implement the result map** (contracts §2): `TtrResult.Resolved`
      → `Resolution.Resolved(toProtoQName(symbol, viaStep))`; `Unresolved` with
      `Reason.Ambiguous` → `ambiguous(ref)`; `Reason.NotFound` →
      `unimported(ref)`. Stub `toProtoQName` for now (B.3 fills it).

- [ ] **B.2.6 — Compile.**
      ```bash
      ./gradlew :infra:metadata:compileTestKotlin
      ```
      The class compiles; `ResolverParitySpec` now compiles too (turns from
      compile-red to runtime-red).

- [ ] **B.2.7 — Run parity; expect the resolved/diagnostic *kind* to match.**
      ```bash
      ./gradlew :infra:metadata:test --tests '*ResolverParitySpec*'
      ```
      Diagnostic-only cases (ambiguous, unknown) should already pass (no
      `toProtoQName` involved). Resolved cases fail on `QualifiedName` inequality
      — expected; B.3 fixes them. If a *diagnostic* case fails, fix the
      guard/result map here.

**Stage DoD:**
- Seven boxes checked.
- Adapter compiles; `resolve` wired end-to-end through the published resolver.
- Parity passes for all diagnostic (unresolved/ambiguous) cases; resolved cases
  remain red pending B.3.
