# Phase 1.7 — Publishing: maven-publish + GH Actions + first 0.1.0

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1 day
(mostly first-time-bootstrap fiddling with GitHub Packages).

**Pre-flight:**
- Phase 1.6 (conformance) DoD met; both Kotlin modules + conformance green.
- A GitHub repo at `Collite/modeler` exists (or rename target known).
- Read [`../../contracts.md`](../../contracts.md) §1 (coordinates) and §6
  (workflow contracts).
- Read ai-platform `PUBLISHING.md` — same pattern; copy what's useful.

**Tasks:**

- [x] **1.7.1 — Configure `maven-publish` in `ttr-parser/build.gradle.kts`.**
      Append:
      ```kotlin
      publishing {
          publications {
              create<MavenPublication>("maven") {
                  from(components["java"])
                  pom {
                      name.set("TTR Parser")
                      description.set("ANTLR-generated parser + typed AST for the TTR (Tatrman) modelling DSL.")
                      url.set("https://github.com/Collite/modeler")
                      licenses { /* fill in: license */ }
                      developers { /* fill in: maintainer */ }
                      scm {
                          connection.set("scm:git:https://github.com/Collite/modeler.git")
                          url.set("https://github.com/Collite/modeler")
                      }
                  }
              }
          }
          repositories {
              maven {
                  name = "GitHubPackages"
                  url = uri("https://maven.pkg.github.com/Collite/modeler")
                  credentials {
                      username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                      password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
                  }
              }
          }
      }
      ```

- [x] **1.7.2 — Repeat for `ttr-writer/build.gradle.kts`.** Same publishing
      block; `name` = "TTR Writer", description adjusted.

- [x] **1.7.3 — Smoke-test locally with `publishToMavenLocal`.**
      ```bash
      ./gradlew -Pversion=0.0.1-LOCAL \
          :packages:kotlin:ttr-parser:publishToMavenLocal \
          :packages:kotlin:ttr-writer:publishToMavenLocal
      ls ~/.m2/repository/org/tatrman/
      # Expect: ttr-parser/0.0.1-LOCAL/*.jar+pom, ttr-writer/0.0.1-LOCAL/*.jar+pom
      ```
      Inspect the `*.pom` files — verify groupId/artifactId/version are correct
      and `ttr-writer`'s POM lists `ttr-parser` as a dependency.

- [x] **1.7.4 — Create `.github/workflows/publish.yml`.** Use the skeleton
      from `contracts.md` §6.1; the tag→modules mapping is:
      ```bash
      TAG="${{ github.ref_name }}"
      VERSION="${TAG##*/v}"
      if [[ "$TAG" == kotlin-parser/v* ]]; then
        MODULES=":packages:kotlin:ttr-parser:publish"
      elif [[ "$TAG" == kotlin-semantics/v* ]]; then
        MODULES=":packages:kotlin:ttr-semantics:publish"
      else  # kotlin/v*
        MODULES=":packages:kotlin:ttr-parser:publish :packages:kotlin:ttr-writer:publish"
      fi
      ```
      Permissions block: `contents: read`, `packages: write`.

- [x] **1.7.5 — Write modeler-side `PUBLISHING.md`.** Top-level doc following
      ai-platform's pattern. Cover:
      - Tag conventions (`kotlin/v*`, `kotlin-parser/v*`, `kotlin-semantics/v*`).
      - Semver rules (per `contracts.md` §7).
      - Local-developer PAT setup (`gpr.user`/`gpr.token` in
        `~/.gradle/gradle.properties`, `read:packages` for consumers,
        `write:packages` for manual publishers).
      - The GitHub-Packages-requires-auth-for-reads gotcha (ai-platform's
        PUBLISHING.md #1).
      - First-time setup checklist (`publishToMavenLocal` smoke test → push
        a `kotlin/v0.0.1-test` tag → confirm the workflow runs green →
        delete the test package version).

- [x] **1.7.6 — Update `CLAUDE.md`.** Add a section "Kotlin artifacts" under
      Commands describing the publish lifecycle and the difference between
      pnpm (TS) and Gradle (Kotlin) build domains. Link to `PUBLISHING.md`
      and `docs/grammar-master/`.

- [x] **1.7.7 — Cut `0.0.1-test` and verify.** Push tag
      `kotlin/v0.0.1-test`. Wait for `publish.yml` to run green. Verify the
      packages appear under the repo's Packages tab. **Delete the test
      version** afterwards (per ai-platform's no-SNAPSHOT-but-no-test-leftovers
      policy).

- [x] **1.7.8 — Cut `0.1.0` real release.** `CHANGELOG.md` entry at modeler
      root: "0.1.0 — Phase 1 of grammar-master. ttr-parser + ttr-writer
      published from canonical TTR.g4 v2.2." Push tag `kotlin/v0.1.0`.
      Verify both artifacts at `org.tatrman:ttr-parser:0.1.0` and
      `org.tatrman:ttr-writer:0.1.0` are downloadable.

**Stage DoD:**
- All eight tasks checked.
- `publish.yml` green on the real `kotlin/v0.1.0` tag.
- `0.1.0` artifacts resolvable from a fresh Gradle project that adds the
  modeler GitHub Packages repo + the dep (test from a scratch dir).
- Modeler `PUBLISHING.md` exists and is accurate.
- Test version `0.0.1-test` deleted from GitHub Packages. **⚠ PENDING (manual):**
  the packages are owned by the `Collite` user account; deleting a user-owned
  package version requires a token belonging to `Collite` (collaborator
  `delete:packages` returns HTTP 403). Delete via the web UI
  (github.com/users/Collite → Packages → `org.tatrman.ttr-parser` /
  `ttr-writer` → version `0.0.1-test` → Delete) or with a Collite-owned PAT.
  Safe to do now that `0.1.0` exists (it is no longer the only version).
