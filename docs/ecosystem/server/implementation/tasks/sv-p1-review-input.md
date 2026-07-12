# SV-P1 — Phase review input (for Bora)

> Compiled at S4 close (2026-07-12). Source: the five stage lists' findings sections
> + the phase-DONE checklist in [`00-task-management.md`](./00-task-management.md).
> SV-P1 is the **publish** phase: it takes the SV-P0 fork (read spine in the open
> Apache-2.0 `tatrman-server`, personas off the wire) and makes the artifacts real —
> `translate.v1` public, the server libraries + spine images published, the pilot
> repointed off the SV-P0 pin, and the `org.tatrman:*` coordinates made **public on
> Maven Central**.

## Outcome: all five stages DONE

| Stage | Result | Where |
|---|---|---|
| S0 · pre-flight | ✅ RO-13 review; Central namespace **verified** (`tatrman.org` DNS TXT); signing key generated (`097C71…EA63`); SV-P0 branches folded | Bora's external track + `master` |
| S1 · tatrman gates (1+2) | ✅ `translate.v1` public — `kotlin-translator/v0.9.0` + `python-plan/v0.9.0` (proteus.v1 retired); metadata already 0.9.1; persona grep-gate added to `ci.yml` | tatrman `master` |
| S2 · server artifacts (gate 3a) | ✅ `server-libs/v0.9.0` — 11 `org.tatrman:*` libs to GH Packages; kantheon retired `mavenLocal()`, clean-machine build proof green | tatrman-server + kantheon `master` |
| S3 · images + repoint (gate 3b) | ✅ 17 images `ghcr.io/collite/*:0.9.0`; `ttr-service` chart lib; olymp bp-dsk repointed; **namespace split** (spine → `ttr-server`, personas → `kantheon`); pilot all green. Plus T6 prose sweep + persona-gate hardening | tatrman-server + kantheon + olymp `master` |
| S4 · Maven Central | ✅ vanniktech wiring (tatrman 7 modules + tatrman-server 11 libs); CI Central lanes; **public debut line `0.9.4`** released via the Portal; anonymous-resolution proof project | tatrman + tatrman-server `master` |

**Phase-DONE checklist:** 4/5 met; the 5th is *this* review (⚑ disposition by Bora).

## What happened at S4 (the publish itself)

- **Plugin**: `com.vanniktech.maven.publish` 0.36.0 (Central Portal mode). Per-module
  in tatrman; a `publishableLibs` convention → `pomMeta` map in tatrman-server. Signing
  gated on the key so the GH Packages staging lane + local builds don't fail unsigned.
  Root builds declare `kotlin.jvm` + the plugin `apply false` (shared build service).
- **Debut version `0.9.4`**, not the planned 0.9.0: the GH-Packages line was non-uniform
  (parser/writer/semantics 0.9.1; metadata/translator/server-libs 0.9.0) and two debug
  runs burned parser 0.9.2 + 0.9.3 on GH Packages. `0.9.4` was free across the whole
  spine → clean uniform public line.
- **Two bugs fixed during the first real publish** (both folded):
  1. The GH-Packages step ran the generic `:module:publish`; because vanniktech
     registers a `mavenCentral` repository, that pulled in `prepareMavenCentralPublishing`
     and failed on the missing `mavenCentralUsername` (the step only has `GITHUB_TOKEN`).
     → switched to `publishAllPublicationsToGitHubPackagesRepository`.
  2. A paste error in the `mavenCentral*` secrets → `401 Invalid token`. Added a
     **Validate Maven Central token** `workflow_dispatch` to both repos to check the
     secrets without publishing.

## What needs Bora's attention (⚑)

1. **Finish the release sweep + verify.** `ttr-parser` released green; confirm
   `kotlin-writer / -semantics / -metadata / -translator` + `server-libs` all reach
   RELEASED in the Portal, then run the T6 proof:
   `gradle -PspineVersion=0.9.4 -PmetadataVersion=0.9.4 verifyPublicResolution --refresh-dependencies`
   (allow ~15–120 min for Central sync before treating a failure as real).
2. **Consumer repoint (post-SV-P1).** kantheon/ai-platform still resolve the spine from
   GitHub Packages at mixed 0.9.0/0.9.1; they can move to `mavenCentral()` at `0.9.4`
   when convenient. Not gating.
3. **Non-uniform pre-Central versions.** GH Packages keeps the old 0.9.0/0.9.1 (+ burned
   parser 0.9.2/0.9.3) artifacts as harmless staging cruft; Central is clean at 0.9.4.
   Optionally delete the dead `kotlin-parser/v0.9.2` + `/v0.9.3` tags.
4. **Auto-release toggle.** The Portal flow is manual (`publishToMavenCentral` → click
   Publish). Flip both workflows to `publishAndReleaseToMavenCentral` once trusted.
5. **Deferred follow-ups from S3** (out of publish scope): k8s deploy-descriptor cards
   still show `ghcr.io/boraperusic/*` chart-default registries olymp overrides; meta-mcp
   chart/app is `veles-mcp` while its image is `ttr-meta-mcp`; kantheon's "permanent PAT
   for `org.tatrman:ttr-*`" may be stale post-extraction; `Collite/modeler`→`Collite/tatrman`
   lingers in a few older docs.

## Dispositions (Bora, 2026-07-12) — SV-P1 CLOSED (phase-DONE 5/5)

1. **Verify — DONE.** The T6 proof ran green: 375 files resolved anonymously from
   Maven Central, all 18 `org.tatrman:*` spine artifacts present at 0.9.4 (after a
   harness fix — `java-library` for the JVM variant attributes, branch
   `fix/verify-resolution-jvm-attrs`).
2. **Consumer repoint — DO NOW.** kantheon repointed to Maven Central at 0.9.4:
   the four version pins bumped, the two `Collite/tatrman{,-server}` GitHub Packages
   repositories + the `gpr.*` PAT removed from `settings.gradle.kts` (this also
   retires ⚑5's stale-PAT concern), compile-verified (`:shared:proto:build`,
   `:agents:golem:compileKotlin`) + the AGENTS.md/CLAUDE.md PAT/`Collite/modeler`
   prose cleaned up. Branch: kantheon `s4-followup-repoint-central`. *(ai-platform
   is a separate repo on Bora's track.)*
3. **Non-uniform pre-Central versions — ACCEPTABLE.** No action; GH Packages keeps
   the old + burned-parser versions as harmless staging cruft, Central is clean at 0.9.4.
4. **Auto-release toggle — DO NOW.** Both publish workflows flipped from
   `publishToMavenCentral` (manual portal release) to `publishAndReleaseToMavenCentral`
   (upload + auto-release). Branches: `s4-followups-autorelease` (tatrman + tatrman-server).
   *NB: the next tag now publishes straight to Central with no manual click.*
5. **S3 deferred follow-ups — TIDY NOW.** (a/b) All 17 chart-default
   `image.repository` set to `ghcr.io/collite/<image>`; meta-mcp's image default is the
   functional `ttr-meta-mcp` while the `veles-mcp` chart/service name is kept (survivor
   persona; matches the olymp app — renaming the Service would churn in-cluster DNS).
   Branch: tatrman-server `s4-followup-chart-image-defaults`. (c) stale-PAT prose folded
   into ⚑2's doc cleanup. (d) `Collite/modeler`→`Collite/tatrman` updated in living docs
   (dated provenance keeps the old name).

## Next: SV-P2

Apache/SPDX/governance (LICENSE headers, NOTICE, CONTRIBUTING, external-reader docs).
Lists exist under `tasks-sv-p2-*`.
