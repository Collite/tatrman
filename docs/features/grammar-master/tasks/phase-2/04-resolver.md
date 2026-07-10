# Phase 2.4 — Resolver (6-step chain)

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1–2 days.

**Pre-flight:**
- Phase 2.3 DoD met. `Qname`, `SymbolTable`, `PackageInference`,
  `PackageGraph` are implemented and tested.
- Read [`../../contracts.md`](../../contracts.md) §4.3 — the
  `Resolver`/`ResolutionResult` shape.
- Reread `modeler/docs/v1-1/design/grammar-v1-1-changes.md` §4 (the resolver
  spec) — definitive on edge cases.

**Reference (port from):**
- `packages/semantics/src/resolver.ts` (canonical)
- ai-platform `infra/metadata/src/main/kotlin/infra/metadata/resolve/ReferenceResolver.kt`
  — the existing Kotlin impl, useful as a Kotlin-idiom reference but **the TS
  version is the source of truth** on behaviour (ai-platform's might lag the
  spec; verify case-by-case).

**Tasks:**

- [ ] **2.4.1 — Implement `ResolutionResult` sealed interface and
      `ResolutionStep` enum** per `contracts.md` §4.3.

- [ ] **2.4.2 — Implement step 6 (FQN) first.** Trivial case — if the
      reference is itself a Qname and `symbolTable.lookup(qname) != null`,
      return `Resolved(qname, FullyQualified)`. Sub-spec from
      `ResolverSpec.kt` for FQN should turn green.

- [ ] **2.4.3 — Implement step 1 (lexical).** If `lexicalScope` contains the
      bare name, return `Resolved(qname, Lexical)`. Spec → green.

- [ ] **2.4.4 — Implement step 2 (same-package).** If `currentPackage != null`
      and `symbolTable.lookup(currentPackage.append(bareName))` exists, return
      `Resolved(..., SamePackage)`. Add helper `Qname.append(segment)`.

- [ ] **2.4.5 — Implement step 5 (cnc.\* auto-import).** If the reference's
      bare name matches a `stockQnames` entry's last segment, return
      `Resolved(stockQname, AutoImport)`. (Step 5 implemented before 3/4
      because it's simpler and unblocks the wildcard test.)

- [ ] **2.4.6 — Implement step 3 (named imports).** For each
      `ImportStatement` where `wildcard == false`: if `import.target` ends
      with `.<bareName>`, that's a match. Multiple matches → `Ambiguous`.
      One match → `Resolved(import.target, NamedImport)`.
      **Bare-defName imports** (`import x` where `x` is a single segment) →
      this is a syntax error at parse time per spec §4; if the parser ever
      lets one through, resolver returns `Unresolved(UnimportedReference, ...)`.

- [ ] **2.4.7 — Implement step 4 (wildcard imports, non-recursive).** For each
      `ImportStatement` where `wildcard == true`: if there exists a symbol
      `<import.target>.<bareName>` (exactly one extra segment — NOT
      `<import.target>.x.<bareName>`), that's a match. Multiple wildcards
      matching same name → `Ambiguous`. Specifically test that
      `import x.y.*` does NOT expose `x.y.z.w`.

- [ ] **2.4.8 — Wire all steps in `Resolver.resolve()`.** First hit wins per
      spec §4. Diagnostic codes:
      - No match anywhere → `Unresolved(UnimportedReference, "<reason>")`.
      - Multiple matches in same step → `Ambiguous(candidates)`.
      - Verify `ResolverSpec` all cases green.

- [ ] **2.4.9 — Add a property-based test** (Kotest Property). For each
      generated `(packageName, imports, references)` tuple where all
      references resolve, assert `(resolver.resolve(...).step)` matches the
      expected step (e.g. FQN refs always resolve at step 6; non-FQN refs in
      same package resolve at step 2 if no shadowing).

**Stage DoD:**
- Nine tasks checked.
- `ResolverSpec` and the property-based test green.
- All other Phase 2 tests still green.
- Critical edge cases verified manually (non-recursion, ambiguity, FQN):
  ```bash
  ./gradlew :packages:kotlin:ttr-semantics:test --tests '*ResolverSpec*'
  ```
