# B.3 — `toProtoQName` — parity green

**Repo:** ai-platform. **Effort:** ~half day.

**Pre-flight:**
- B.2 done (adapter wired; diagnostic cases green, resolved cases red).
- Read [`../contracts.md`](../contracts.md) §2.2 (the conversion contract) — it is
  normative; implement it verbatim.

**File:** `infra/metadata/src/main/kotlin/infra/metadata/resolve/PublishedResolverAdapter.kt`.

---

- [ ] **B.3.1 — Implement `toProtoQName(e, step)` per contracts §2.2.**
      ```kotlin
      private fun toProtoQName(e: SymbolEntry, step: ResolutionStep): QualifiedName {
          val pkg = if (step == ResolutionStep.AutoImport) "cnc" else ""
          val protoName = e.parent
              ?.let { "${byQname[it]?.name ?: it.substringAfterLast('.')}.${e.name}" }
              ?: e.name
          return QualifiedName.newBuilder()
              .setPackage(pkg)
              .setSchemaCode(
                  runCatching { SchemaCode.valueOf(e.schemaCode.uppercase()) }
                      .getOrDefault(SchemaCode.SCHEMA_CODE_UNSPECIFIED),
              )
              .setNamespace(e.namespace)
              .setName(protoName)
              .build()
      }
      ```
      Note `e.namespace` requires `org.tatrman:ttr-semantics:0.3.0` (Phase A).

- [ ] **B.3.2 — Verify the stock rule.** Run the auto-import parity case: a bare
      `fact` must come back as `QualifiedName{package="cnc", schemaCode=CNC,
      namespace="role", name="fact"}` — identical to the legacy resolver's step-5
      output. (`StockRoleResolutionSpec` covers the same end-to-end.)

- [ ] **B.3.3 — Verify the nested rule.** Run the nested-ref parity case (relation
      `join` or `er2db_attribute` target referencing `er.entity.artikl.id`): the
      proto `name` must be `"artikl.id"` (parent.child), `package=""`,
      `namespace="entity"`.

- [ ] **B.3.4 — Verify the package-drop rule.** A user def in a declared package
      (`package billing.invoicing`) resolved via same-package/FQN must come back
      `package=""` (ai-platform identity drops the package) — matching the legacy
      `Def.fqn`.

- [ ] **B.3.5 — Green the whole parity spec.**
      ```bash
      ./gradlew :infra:metadata:test --tests '*ResolverParitySpec*'
      ```
      Every corpus + edge case passes. Investigate any residual diff case by case
      (the *resolved target* must be identical even when the resolution *step*
      differs between the two implementations — see architecture §Risks).

- [ ] **B.3.6 — Full suite + lint (legacy still wired).**
      ```bash
      ./gradlew :infra:metadata:test :infra:metadata:ktlintMainSourceSetCheck
      ```
      247+ tests green — the pass still uses the legacy resolver, so the existing
      behaviour is untouched; only the new adapter + parity spec are exercised.

- [ ] **B.3.7 — Commit (do not delete legacy yet).** Revert the TEMP
      `mavenLocal()` from `settings.gradle.kts`, confirm `:infra:metadata`
      compiles against the published `0.3.0`
      (`./gradlew :infra:metadata:compileKotlin --refresh-dependencies`), commit
      + push. The legacy resolver + parity spec stay in the tree for Phase C.

**Stage DoD:**
- Seven boxes checked.
- `ResolverParitySpec` fully green — the adapter matches the legacy resolver on
  every case.
- Full `:infra:metadata:test` green; ktlint clean; builds against published
  `0.3.0`.
