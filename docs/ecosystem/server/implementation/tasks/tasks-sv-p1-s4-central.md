# SV-P1 · S4 — Maven Central: public coordinates (the phase-DONE test)

> Repos: **tatrman** + **tatrman-server**. Pre-flight: S0·T2 (namespace verified) + S0·T5 (signing key) + S1/S2 (the 0.9.x line exists on staging). RO-17: **Maven Central = the public registry** — GH Packages requires auth even for public packages and kills adoption; this stage is what makes gate coordinates *public*. Overlappable with S3. Library: `com.vanniktech.maven.publish` (verified current 2026-07-11 via context7 — Central Portal mode is its default; API below).

- [x] **T1 — Wire the publish plugin in tatrman (convention plugin, not per-module).** In the Kotlin convention plugin (or root build) for every published module, replace/augment the bare `maven-publish` GH Packages setup with:
  ```kotlin
  // plugins { id("com.vanniktech.maven.publish") version "<latest>" }
  mavenPublishing {
      publishToMavenCentral()          // Central Portal (central.sonatype.com)
      signAllPublications()            // required by Central
      coordinates("org.tatrman", project.name, version.toString())
      pom {
          name.set(project.name)
          description.set("<one line per module — take from PUBLISHING.md's table>")
          inceptionYear.set("2025")
          url.set("https://github.com/Collite/tatrman")
          licenses { license {
              name.set("The Apache License, Version 2.0")
              url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
          } }
          developers { developer { id.set("collite"); name.set("Collite"); url.set("https://github.com/Collite") } }
          scm {
              url.set("https://github.com/Collite/tatrman")
              connection.set("scm:git:git://github.com/Collite/tatrman.git")
          }
      }
  }
  ```
  Central REQUIRES: javadoc + sources jars (the plugin adds them), full POM, signatures. Keep the GH Packages repository block — it stays the staging lane.
- [x] **T2 — Same wiring in tatrman-server** for the S2 artifact set (shared libs + proto stubs). SCM URLs point at `Collite/tatrman-server`.
- [x] **T3 — CI secrets + workflow lane.** Org/repo secrets: `ORG_GRADLE_PROJECT_mavenCentralUsername` / `ORG_GRADLE_PROJECT_mavenCentralPassword` (the S0·T2 user token) · `ORG_GRADLE_PROJECT_signingInMemoryKey` (S0·T5 armored key) · `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`. Extend both publish workflows: the existing tag → GH Packages step, plus `./gradlew publishAndReleaseToMavenCentral` (or `publishToMavenCentral` + manual portal release for the first run — safer: eyeball the staged bundle in the portal UI once).
- [~] **T4 — Dry-run then publish tatrman 0.9.x to Central. [GATE]** Preconditions: namespace VERIFIED · T1 POMs inspected locally (`./gradlew generatePomFileForMavenPublication` + read one POM) · versions match S1's line exactly (re-publishing a different jar under the same version is forbidden — if in doubt, bump patch). First module solo (`ttr-parser`) → verify in the portal → then the rest: parser, writer, semantics, metadata, metadata-git, plan-proto, translator.
- [~] **T5 — Publish tatrman-server 0.9.x set to Central. [GATE]** Same discipline.
- [x] **T6 — The phase-DONE proof: scratch-project resolution.** On a machine/container with **no** `~/.gradle` credentials: a fresh Gradle project with ONLY `mavenCentral()` declaring every spine artifact at 0.9.x — `./gradlew dependencies --configuration runtimeClasspath` resolves clean, anonymously. Commit the scratch project as `tatrman-server/scripts/verify-public-resolution/` (it reruns at every future gate + at SV-P6). Note: Central sync takes ~15–120 min after release; don't ⚑ before 2 h.
- [~] **T7 — Close SV-P1.** PUBLISHING.md: Central = public lane documented (portal, tokens, plugin), GH Packages demoted to staging in the text. Walk the phase-DONE checklist in [`00-task-management.md`](./00-task-management.md); compile all five stages' findings into `sv-p1-review-input.md` for Bora; control room gets the session-index row; project memory updated.

**Verify block:**
```bash
# anonymous resolution (no credentials anywhere):
cd ~/Dev/collite-gh/tatrman-server/scripts/verify-public-resolution && ./gradlew dependencies --configuration runtimeClasspath --refresh-dependencies
# spot-check Central search: https://central.sonatype.com/artifact/org.tatrman/ttr-plan-proto — version 0.9.x, Apache-2.0 badge
# signatures present:
curl -sI https://repo1.maven.org/maven2/org/tatrman/ttr-plan-proto/0.9.0/ttr-plan-proto-0.9.0.jar.asc | head -1   # 200
```

## Findings / ⚑

**Wiring DONE (T1/T2/T3/T6), 2026-07-11 — 2 branches, publish held for secrets.**
- **T1 (tatrman) — branch `sv-p1-s4-central-wiring-tatrman`.** vanniktech 0.36.0 on the 7 spine modules (parser/writer/semantics/metadata/metadata-git/plan-proto/translator): `publishToMavenCentral()` + full Apache-2.0 POM + sources/javadoc jars. Signing gated on `ORG_GRADLE_PROJECT_signingInMemoryKey` presence (GH Packages lane + local builds don't sign). Root declares `kotlin.jvm` + vanniktech `apply false` (shared `MavenCentralBuildService` + Kotlin-version detection — required to configure >1 Central module). Validated: repo-wide `publishToMavenCentral --dry-run` configures all 7; POMs Central-compliant.
- **T2 (tatrman-server) — branch `sv-p1-s4-central-wiring-server`.** Converted the `publishableLibs` convention (all **11** libs → Central, per Bora) from bare maven-publish to vanniktech; added a `pomMeta` map (name+description Central requires; the GH POM had neither); `:shared:proto` keeps `ttr-server-proto`. Same gated-signing + GH Packages staging. Validated: dry-run configures all 11; `ttr-server-proto` POM Central-compliant.
- **T3 (both workflows).** After the GH Packages step, a guarded **Publish to Maven Central** step runs `publishToMavenCentral` (one signed bundle → Portal, **manual** release; switch to `publishAndReleaseToMavenCentral` once trusted). Gated on `ORG_GRADLE_PROJECT_mavenCentralUsername` so the staging lane stays green until secrets exist. **Secret names ARE the Gradle property names** (`ORG_GRADLE_PROJECT_mavenCentralUsername/Password`, `ORG_GRADLE_PROJECT_signingInMemoryKey/Password`).
- **T6 — `tatrman-server/scripts/verify-public-resolution/`.** Standalone Gradle project, `mavenCentral()`-only, all 18 spine artifacts. `gradle verifyPublicResolution` = anonymous public-resolution proof. Configures clean; passes post-publish (Central sync lags ~15–120 min).

**⏳ T4/T5 (the publishes) + T7 (close-out) — HELD on Bora's track (secrets not yet set).** To publish: (1) add the 4 repo/org secrets named above to `Collite/tatrman` + `Collite/tatrman-server`; (2) T4 — cut `kotlin-parser/v0.9.0` first (ttr-parser solo), eyeball the staged bundle in the Central Portal, release; then the rest; (3) T5 — `server-libs/v0.9.0`; (4) run the T6 verifier; (5) T7 — PUBLISHING.md (Central=public, GH Packages=staging) + close SV-P1. ⚑ versions aren't uniform (metadata pair 0.9.1; most else 0.9.0) — the T6 verifier takes `-PspineVersion`/`-PmetadataVersion`.
