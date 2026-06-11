# A.1 — `SymbolEntry.namespace` + publish 0.3.0

**Repo:** modeler. **Effort:** ~2 hours.

**Pre-flight:**
- Phase 2 complete; `org.tatrman:*:0.2.1` published.
- Branch off `master` (or continue on the current grammar-master branch).
- Read [`../contracts.md`](../contracts.md) §1.

**File:** `packages/kotlin/ttr-semantics/src/main/kotlin/org/tatrman/ttr/semantics/SymbolTable.kt`.

---

- [x] **A.1.1 — Add the field to `SymbolEntry`.** In the `data class SymbolEntry`,
      add `val namespace: String` immediately after `name`:
      ```kotlin
      data class SymbolEntry(
          val qname: String,
          val kind: String,
          val name: String,
          val namespace: String,   // NEW — file namespace ("" when none declared)
          val source: SourceLocation,
          val documentUri: String,
          val parent: String?,
          val packageName: String,
          val schemaCode: String,
          val mappingSource: MappingSource? = null,
      )
      ```

- [x] **A.1.2 — Populate it in `DocumentSymbols`.** `DocumentSymbols` already
      holds `private val namespace`. Pass it to every `SymbolEntry(...)`
      construction: the top-level entry in `addEntry` and the child entry in
      `addChild`. Set `namespace = namespace` (the file namespace — **not**
      `nsOrKind`; pass the raw `this.namespace`, which may be `""`).

- [x] **A.1.3 — Fix any `SymbolEntry` literals.** Grep for direct constructions:
      ```bash
      grep -rn 'SymbolEntry(' packages/kotlin/ttr-semantics/src
      ```
      Entries built via `upsertDocument` need no change. Add `namespace = …` to
      any test/production literal the grep finds (expected: none outside
      `DocumentSymbols`).

- [x] **A.1.4 — Module tests + ktlint.**
      ```bash
      ./gradlew :packages:kotlin:ttr-semantics:test :packages:kotlin:ttr-semantics:ktlintCheck
      ```
      All green. If a spec asserts `SymbolEntry` shape, extend it to check
      `namespace` (e.g. in `SymbolTableSpec`: an `er namespace entity` entity has
      `namespace == "entity"`; a `schema db` table has `namespace == ""`).

- [x] **A.1.5 — Re-verify both conformance harnesses.** The dumps must be
      unchanged (the semantics dump never serialised `namespace`):
      ```bash
      pnpm --filter @modeler/parser --filter @modeler/semantics build
      pnpm --filter @modeler/conformance dump && pnpm --filter @modeler/conformance dump-sem
      ./gradlew :packages:kotlin:ttr-parser:test --tests '*ConformanceSpec*' \
                :packages:kotlin:ttr-semantics:test --tests '*SemanticsConformanceSpec*'
      pnpm --filter @modeler/conformance diff && pnpm --filter @modeler/conformance diff-sem
      ```
      Both report "All 30 fixtures match".

- [x] **A.1.6 — CHANGELOG + smoke publish.** Add a `0.3.0` entry to
      `CHANGELOG.md` ("adds `SymbolEntry.namespace` for downstream proto
      adapters; no resolver/qname change"). Smoke-test:
      ```bash
      ./gradlew -Pversion=0.3.0 :packages:kotlin:ttr-semantics:publishToMavenLocal
      javap -cp ~/.m2/repository/org/tatrman/ttr-semantics/0.3.0/ttr-semantics-0.3.0.jar \
            org.tatrman.ttr.semantics.SymbolEntry | grep namespace   # field present
      ```

- [ ] **A.1.7 — Commit, push, release.** Commit + push. Then publish the real
      `0.3.0` (outward-facing — confirm with the owner): `git tag kotlin/v0.3.0
      && git push origin kotlin/v0.3.0`. Verify `org.tatrman:ttr-semantics:0.3.0`
      resolves from GitHub Packages.

**Stage DoD:**
- Seven boxes checked.
- `./gradlew build` + `pnpm -r test` green; both conformance diffs green.
- `org.tatrman:*:0.3.0` published; `SymbolEntry` carries `namespace`.
