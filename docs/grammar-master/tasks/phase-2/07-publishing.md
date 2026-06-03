# Phase 2.7 — Publishing: ttr-semantics 0.1.0

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 2 hours.

**Pre-flight:**
- Phase 2.6 DoD met. Conformance with semantics is green.
- `publish.yml` already updated in 2.1.5 to publish three modules on `kotlin/v*`.

**Tasks:**

- [ ] **2.7.1 — Smoke-test locally.**
      ```bash
      ./gradlew -Pversion=0.0.1-LOCAL :packages:kotlin:ttr-semantics:publishToMavenLocal
      ls ~/.m2/repository/org/tatrman/ttr-semantics/0.0.1-LOCAL/
      ```
      Inspect the POM — verify `<dependencies>` includes `ttr-parser` with
      scope `compile` (since the build uses `api(...)`).

- [ ] **2.7.2 — Verify the bundled stock resource ships in the jar.**
      ```bash
      jar tf ~/.m2/repository/org/tatrman/ttr-semantics/0.0.1-LOCAL/ttr-semantics-0.0.1-LOCAL.jar \
          | grep builtin
      # Expect: builtin/cnc-stock-roles.ttr
      ```

- [ ] **2.7.3 — Push a test tag.** `git tag kotlin-semantics/v0.0.1-test &&
      git push origin kotlin-semantics/v0.0.1-test`. Watch `publish.yml` —
      should publish only `ttr-semantics`. Delete the test package version
      after verification.

- [ ] **2.7.4 — Update `PUBLISHING.md`.** Add `ttr-semantics` to the
      published-modules table.

- [ ] **2.7.5 — Cut `0.1.0`.** Tag `kotlin-semantics/v0.1.0` (or bundle
      `kotlin/v0.2.0` if it makes sense to bump all three together — decide
      based on what's changed since last `kotlin/v*`). Verify
      `org.tatrman:ttr-semantics:0.1.0` resolvable from a scratch project.

- [ ] **2.7.6 — Update modeler `CHANGELOG.md`.** Entry: "0.2.0 — Phase 2 of
      grammar-master. ttr-semantics (resolver, symbol table, validator, stock
      vocab) published; replaces ai-platform's hand-rolled equivalent."

**Stage DoD:**
- Six tasks checked.
- `org.tatrman:ttr-semantics:0.1.0` (or chosen version) published and
  resolvable.
- Stock vocab resource included in the jar.
- `PUBLISHING.md` updated.
