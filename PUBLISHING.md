# Publishing — Kotlin artifacts

This repo publishes the modeler-owned Kotlin libraries under the `org.tatrman:*`
group to **two lanes** (SV-P1 S4, 2026-07-12; lane-gating polarity flipped
2026-07-16 — justfile sync):

- **Maven Central** (Central Portal, `central.sonatype.com`) — the **public
  lane** (RO-17). Anonymous, no auth to consume; this is what external readers
  and `ai-platform`/`kantheon` resolve. Signed + full POMs + sources/javadoc,
  via the `com.vanniktech.maven.publish` plugin. **Only a tag explicitly marked
  `-RELEASE` reaches here** — see [§ Release lanes](#release-lanes--internal-vs-release-2026-07-16).
- **GitHub Packages** (`https://maven.pkg.github.com/Collite/tatrman`) — the
  **staging lane**. Every release lands here first (it needs auth even for
  public reads — Gotcha 1 — which is exactly why it can't be the public lane).
  **Every tag** lands here, `-RELEASE`-marked or not.

See [§ Maven Central — the public lane](#maven-central--the-public-lane-sv-p1-s4) below.

> Two build domains coexist in this repo. The **pnpm/TypeScript** workspace
> (`packages/*`, built with `pnpm -r build`) ships the LSP, VS Code extension,
> and Designer. The **Gradle/Kotlin** build (`packages/kotlin/*`) ships the
> published Maven artifacts described here. They share only the canonical
> grammar at `packages/grammar/src/TTR.g4`; nothing else crosses between them.

## What is published

| Module (Gradle path) | Coordinate | Phase | Why it's published |
|---|---|---|---|
| `:packages:kotlin:ttr-parser` | `org.tatrman:ttr-parser` | 1 | ANTLR parser + typed AST; ai-platform's `infra/metadata` consumes the model types |
| `:packages:kotlin:ttr-writer` | `org.tatrman:ttr-writer` | 1 | Deterministic AST → TTR-source renderer; depends on `ttr-parser` (api) |
| `:packages:kotlin:ttr-semantics` | `org.tatrman:ttr-semantics` | 2 | Symbol table + resolver + per-kind validator + bundled stock CNC vocab; depends on `ttr-parser` (api) |
| `:packages:kotlin:ttr-metadata` | `org.tatrman:ttr-metadata` | 1 (M1) | Typed model, storage SPI (+ fs/classpath), reconciler, resolver, graph, search, registry, refresher mechanism, export, world resolution (M2). Pins the in-repo `ttr-parser`/`ttr-writer`/`ttr-semantics` and re-exports them as `api` deps (contracts §1) |
| `:packages:kotlin:ttr-metadata-git` | `org.tatrman:ttr-metadata-git` | 1 (M1) | `GitArchiveStorage` (jgit) behind the core `ModelStorage` SPI — Ariadne only; keeps jgit off the compiler/Designer-server classpath (MD3) |
| `:packages:kotlin:ttr-plan-proto` | `org.tatrman:ttr-plan-proto` | ttr-translator arc | Canonical `plan.v1`/`transdsl.v1`/`dfdsl.v1` wire formats (+ the `proteus.v1` enum stub and `plan.v1.SchemaCodes`), with the `.proto` files bundled as jar resources for the protoc include-path contract. tatrman owns the wire format (TR-3); consumed by kantheon `:shared:proto` + the translator |
| `:packages:kotlin:ttr-translator` | `org.tatrman:ttr-translator` | ttr-translator arc | Calcite-backed translation core (island ↔ RelNode ↔ SQL / `plan.v1`); consumed by tatrman `ttrp-emit` and kantheon Proteus/Ariadne. `api` deps: `ttr-plan-proto` + `calcite-core`. Ships an `InMemoryModelHandle` test-fixtures jar (ModelHandle SPI double, contracts §3) |
| `:packages:kotlin:ttrp-frontend` | `org.tatrman:ttrp-frontend` | TTR-P P3 | compiler front-half: parse → resolve → typecheck; serves `ttrp check`/`validate`/`authoringContext` (contracts §10) |
| `:packages:kotlin:ttrp-graph`    | `org.tatrman:ttrp-graph`    | TTR-P P3 | graph construction + normalizer (T8 rewrites) |
| `:packages:kotlin:ttrp-emit`     | `org.tatrman:ttrp-emit`     | TTR-P P3 | island codegen, movement synthesis, bundle assembly |
| `:packages:kotlin:ttrp-lsp`      | `org.tatrman:ttrp-lsp`      | TTR-P P4 | one TTR-P LSP; stdio + WS transports |
| `:packages:kotlin:ttrp-cli`      | `org.tatrman:ttrp-cli`      | TTR-P P3 | the `ttrp` binary (S2): build/run/explain/conform |
| `:packages:kotlin:ttrp-conform`  | `org.tatrman:ttrp-conform`  | TTR-P P3 | Q9 conformance harness (S3) |

Coordinates and public API surfaces are normative in
[`docs/grammar-master/contracts.md`](docs/grammar-master/contracts.md) §1.

### Not published (internal tooling)

| Module (Gradle path) | Why it's internal |
|---|---|
| `:packages:kotlin:ttr-designer-server` | Repo-attached Designer backend (`ttrm/…` WS-JSON-RPC over loopback, MD8); consumes `ttr-metadata` as a normal project dep. Ships as an application, not a library artifact — no `maven-publish`, no publication block, no release tag. |

## How to publish (release)

Versioning is **tag-driven** (consistent with the constellation's
`<name>/v<x.y.z>` convention). Pushing one of these tags triggers
`.github/workflows/publish.yml`. Tag prefixes were renamed 2026-07-16 (justfile
sync) from `kotlin*` to the module's own directory name or an explicit bundle
name — nobody pins a git tag name for Maven consumption, only the published
coordinate/version, so this rename is not a breaking change for consumers:

| Tag | Modules published | `just publish` |
|---|---|---|
| `grammar/v<x.y.z>[-RELEASE]` | **bundle**: `ttr-parser` + `ttr-writer` + `ttr-semantics` (grammar toolchain only). `ttr-metadata(-git)` is **not** in this bundle — it has one publisher, `metadata/v*` (RO-24: one tag per module family) | `just publish bundle grammar` |
| `ttr-parser/v<x.y.z>[-RELEASE]` | `ttr-parser` only (rare; parser-only patch) | `just publish ttr-parser` |
| `ttr-semantics/v<x.y.z>[-RELEASE]` | `ttr-semantics` only (Phase 2 cadence) | `just publish ttr-semantics` |
| `metadata/v<x.y.z>[-RELEASE]` | **both** `ttr-metadata` + `ttr-metadata-git` (lockstep; contracts §1). First real tag `metadata/v0.1.0` is cut at **M2.2**, not M1 | `just publish bundle metadata` |
| `translator/v<x.y.z>[-RELEASE]` | **both** `ttr-plan-proto` + `ttr-translator` (lockstep; ttr-translator arc). First real tag `translator/v0.8.0`. Wire-format changes follow `docs/ttr-translator/architecture/contracts.md` §2 (append-only within `v1`) | `just publish bundle translator` |
| `ttrp/v<x.y.z>[-RELEASE]` | bundle: all `org.tatrman:ttrp-*` modules (first cut in TTR-P Phase 3; workflow wiring lands there) | *(not yet wired into `just publish`)* |

```bash
just publish bundle grammar            # internal only, patch bump
just publish ttr-parser release        # + Maven Central, patch bump
```

The workflow publishes in two steps: first the **GitHub Packages** staging lane
(`<modules>:publishAllPublicationsToGitHubPackagesRepository` with the
auto-provisioned `GITHUB_TOKEN` — no PAT in CI, runs for every tag), then the
**Maven Central** lane (`<modules>:publishToMavenCentral`, guarded on both the
Central secrets AND the tag's `-RELEASE` marker — see below).

> **Use the GH-Packages-*specific* task, never the generic `:module:publish`.**
> Since vanniktech registers a `mavenCentral` repository, the aggregate `publish`
> now also pulls in `prepareMavenCentralPublishing`, which fails the GH-Packages
> step with "mavenCentralUsername not found" (that step carries only the
> `GITHUB_TOKEN`). Fixed 2026-07-12 — see the workflow comments.

### Release lanes — internal vs RELEASE (2026-07-16)

Maven Central's free tier is quota-limited **per namespace per month, on a
3-month rolling average**: roughly **1,167 files / 78 MB / 7 releases**
([publishing limits](https://central.sonatype.org/publish/maven-central-publishing-limits/)).
Every published module contributes jar + sources + javadoc + POM + Gradle module
metadata, each multiplied by signatures and checksums (~25–40 files per module),
so a multi-module tag burns hundreds of files and one of the ~7 releases in a
single push. Fast iteration through Central is therefore not viable — three days
of it consumed 70% of a month's quota (2026-07-13).

The rule (**flipped 2026-07-16** from the original version-string-picks-the-lane
scheme): **a tag reaches Central only when explicitly marked `-RELEASE`.**
Internal patches now vastly outnumber real releases, so the default (a bare,
un-marked tag) had to become internal-only — inferring "public" from the mere
*absence* of a prerelease suffix meant every routine patch was one `just publish`
away from burning Central quota.

| Tag | Example | Lanes |
|---|---|---|
| Bare `x.y.z` (default — `just publish X`) | `ttr-parser/v0.9.5` | GH Packages **only** (staging/internal) |
| `-RELEASE`-marked (`just publish X release`) | `ttr-parser/v0.9.6-RELEASE` | GH Packages **and** Maven Central — published to **both** as bare `0.9.6` (the marker is stripped before it ever reaches a registry) |

`publish.yml` implements this by only running the Central step when
`github.ref_name` ends in `-RELEASE`. Practically:

- **Iterating?** Cut bare patches as fast as you like:
  `just publish ttr-parser` (or `just publish ttr-parser set 0.9.5`). Internal
  consumers (ai-platform, kantheon) pin whichever internal version they need —
  they already resolve the GH Packages repo (see
  [§ Consumer setup](#consumer-setup-ai-platform-and-other-repos)).
- **Going public?** `just publish ttr-parser release` (or `release minor` /
  `release set X.Y.Z`) — mints a **brand-new** version number (never reuses one
  already spent by a prior internal tag, so the stripped Central/GH-Packages
  version never collides with anything already published) and pushes it to both
  lanes. Prefer **batching** families into one coordinated release — the release
  *count* is a quota metric too.
- **Same-machine loop?** Skip publishing entirely — `publishToMavenLocal`
  (below).

This also composes with RO-24's version freeze: every published version
(internal or RELEASE) is immutable once cut — the recipe refuses to reuse a
number, in either form, for the same module.

### Python wheels (PyPI)

Pure-Python wheels ship to **public PyPI** via Trusted Publishing (OIDC — no
token), driven by [`.github/workflows/publish-python.yml`](.github/workflows/publish-python.yml).
PyPI has no internal-registry equivalent in this repo, so — unlike the Maven
lane above — a bare tag doesn't publish anywhere at all; **only a `-RELEASE`-marked
tag builds and publishes** (2026-07-16, justfile sync):

| Tag | Wheel (PyPI project) |
|---|---|
| `python/v<x.y.z>-RELEASE` | `ttr-parser` — the ANTLR parser + stock vocab (pure-Python) |
| `python-plan/v<x.y.z>-RELEASE` | `ttr-plan-proto` — pre-generated `plan.v1`/`transdsl.v1`/`dfdsl.v1` `*_pb2.py` |

```bash
just publish packages/python/ttr-plan-proto release   # ttr-plan-proto wheel (path form — bare
                                                        # "ttr-plan-proto" is unambiguous, but
                                                        # "ttr-parser" collides with the Kotlin
                                                        # module of the same name and needs the path)
```

Each PyPI project registers this repo + `publish-python.yml` + the `pypi`
environment as its Trusted Publisher **before** the first tag is pushed (PyPI UI
→ project → Publishing). The `ttr-plan-proto` wheel carries the same version as
the Kotlin `kotlin-translator/v*` pair by convention.

### TypeScript grammar (GitHub Packages / npm) — `@collite/ttr-grammar`

The canonical grammar ships to **GitHub Packages (npm)** so external TypeScript
consumers depend on a published, versioned package instead of vendoring a frozen
copy of `TTR.g4` — the same story Kotlin gets via Maven and Python via PyPI.
Driven by [`.github/workflows/publish-ts.yml`](.github/workflows/publish-ts.yml):

| Tag | Package (registry) |
|---|---|
| `ts-grammar/v<x.y.z>` | `@collite/ttr-grammar` — raw `TTR.g4` + built `PROPERTY_MAP` / `TTR_GRAMMAR_VERSION` |

```bash
just publish ts-grammar set 0.9.0   # publishes @collite/ttr-grammar@0.9.0 (GH Packages only —
                                  # no external npm lane exists, so `release` is accepted for
                                  # interface consistency but changes nothing here)
```

Two things the workflow rewrites at publish (nothing is committed with these
values — mirrors the Python `0.0.0`→tag injection):

- **Scope.** GitHub Packages ties an npm scope to the owning account (`Collite`),
  so it cannot host `@tatrman/*`. The workspace-internal name `@tatrman/grammar`
  is rewritten to **`@collite/ttr-grammar`** (`npm pkg set name=…`; note `pnpm pkg`
  is unimplemented in pnpm 11). Maven/PyPI have no such restriction, which is why
  `org.tatrman:*` and the PyPI projects keep their names.
- **Version.** The `0.0.0` placeholder in `packages/grammar/package.json` is
  replaced by the tag version. **Align the published minor with the grammar
  version** (grammar `@grammar-version: 0.9` → `ts-grammar/v0.9.0`) so a consumer's
  `package.json` shows at a glance which grammar line it tracks.

The published tarball is guarded to be **self-contained** — it must carry both
`src/TTR.g4` (consumers regenerate their own parser) and the built `dist/` that
re-exports `TTR_GRAMMAR_VERSION` (the workflow greps the tarball and fails
otherwise, mirroring the Python wheel's `unzip -l | grep` check). Only the
**grammar** is published, not `@tatrman/parser`: consumers run their own ANTLR
generation from the shipped `.g4` (exactly what modeler does).

In-repo TS consumers (Designer, VS Code ext) build from `packages/parser`
directly and need no publish — this lane is for **external** repos only.

## Semver discipline

Per [`contracts.md`](docs/grammar-master/contracts.md) §7:

- **Patch** — bug fix only; no public API surface change.
- **Minor** — additive: new def kinds, new properties, new diagnostic codes.
  While version `< 1.0.0`, minor bumps **may** contain breaking changes —
  document them in `CHANGELOG.md`.
- **Major** — removing/renaming any public type, removing a `DiagnosticCode`,
  or changing the conformance JSON dump schema (§5). Post-1.0 this is strict.
- **No SNAPSHOTs.** They consume package storage with no auto-cleanup. Cut real
  versions even for early iteration — but WIP iteration versions carry a
  **prerelease suffix** (`-rc.N`, `-dev.N`) so they stay on the GH Packages
  staging lane and off the Central quota (§ Release lanes). Local builds
  without `-Pversion` get `0.0.1-LOCAL` (see below).

## Local iteration — `publishToMavenLocal`

Tight cross-repo iteration uses Maven Local instead of a real publish:

```bash
./gradlew -Pversion=0.0.1-LOCAL \
    :packages:kotlin:ttr-parser:publishToMavenLocal \
    :packages:kotlin:ttr-writer:publishToMavenLocal \
    :packages:kotlin:ttr-semantics:publishToMavenLocal
# → ~/.m2/repository/org/tatrman/{ttr-parser,ttr-writer,ttr-semantics}/0.0.1-LOCAL/
```

Point ai-platform at `mavenLocal()` for that window, then switch back to a real
version for anything that leaves your machine.

## Consumer setup (ai-platform and other repos)

In the consuming repo's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            name = "ColliteModeler"
            url = uri("https://maven.pkg.github.com/Collite/tatrman")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

Then add a `gradle/libs.versions.toml` entry + `implementation(...)` line, e.g.
`implementation("org.tatrman:ttr-parser:0.1.0")`. The same `gpr.user`/`gpr.token`
keys already used for ai-platform's own `AiPlatformPackages` repo work here.

### Local-developer authentication

Each developer who consumes or manually publishes needs a GitHub Personal
Access Token (classic): `read:packages` to consume, `write:packages` to publish
by hand. Put it in `~/.gradle/gradle.properties` (**never committed**):

```
gpr.user=<github-username>
gpr.token=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

CI does not need this — `GITHUB_TOKEN` is auto-provisioned in Actions.

### Consumer setup — TypeScript / npm (`@collite/ttr-grammar`)

A TS consumer (modeler, and future in-repo Designer splits) adds an `.npmrc` that
scopes `@collite` to GitHub Packages and supplies a token:

```
# .npmrc (committed — the token comes from the environment, not this file)
@collite:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${NODE_AUTH_TOKEN}
```

Then depend on the grammar and resolve the raw `.g4` from it:

```jsonc
// packages/{parser,lsp}/package.json
"dependencies": { "@collite/ttr-grammar": "^0.9.0" }
```
```bash
# regenerate the parser from the dependency's grammar, not a vendored copy:
node -p "require.resolve('@collite/ttr-grammar/grammar')"   # → …/node_modules/@collite/ttr-grammar/src/TTR.g4
```

Auth token: a GitHub PAT (classic) with `read:packages` locally
(`export NODE_AUTH_TOKEN=ghp_…`); in CI the auto-provisioned `GITHUB_TOKEN`
(with `permissions: packages: read`) works. GitHub Packages **requires auth even
for reads** (Gotcha 1), so the token is not optional. The named exports
(`PROPERTY_MAP`, `SEARCH_SUB_PROPERTIES`, `TTR_GRAMMAR_VERSION`, types
`DefinitionKind` / `PropertyInfo`) import from `@collite/ttr-grammar` directly.

## Maven Central — the public lane (SV-P1 S4)

The `org.tatrman:*` coordinates are published to **Maven Central** via the
**Central Portal** (`central.sonatype.com`), which is what makes them anonymously
resolvable (RO-17). Wiring landed at SV-P1 S4 (2026-07-12); the Central debut
line is **0.9.4**.

**Plugin.** Each published module applies `com.vanniktech.maven.publish` (Central
Portal mode is its default). The convention adds `publishToMavenCentral()`,
`signAllPublications()` (gated on the signing key's presence so the GH Packages
lane + local builds don't fail unsigned), the full Apache-2.0 POM, and the
sources + javadoc jars Central requires. The root build declares `kotlin.jvm` +
the plugin `apply false` so the shared `MavenCentralBuildService` registers once
across the multi-module build.

**Namespace.** `org.tatrman` is verified to Collite on the Portal via a DNS TXT
record on `tatrman.org` (S0·T2).

**CI secrets** (repo or org secrets on `Collite/tatrman` + `Collite/tatrman-server`;
the names ARE the Gradle property names, so they pass straight through as
`ORG_GRADLE_PROJECT_*` env):

| Secret | Value |
|---|---|
| `ORG_GRADLE_PROJECT_mavenCentralUsername` | Portal **User Token** username (Account → Generate User Token — **not** the login) |
| `ORG_GRADLE_PROJECT_mavenCentralPassword` | Portal User Token password |
| `ORG_GRADLE_PROJECT_signingInMemoryKey` | ASCII-armored GPG **secret** key (`gpg --export-secret-keys --armor <id>`) |
| `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` | that key's passphrase |

The Portal auth is `Authorization: Bearer base64(username:password)`. To check the
secrets without publishing, run the **Validate Maven Central token** workflow
(Actions → Run workflow): HTTP 401 = bad token, 400/404 = valid.

**Release flow (manual, first-run-safe).** A tag push runs
`publishToMavenCentral`, which uploads one signed bundle to the Portal where it
sits **VALIDATED** awaiting a manual **Publish** click (central.sonatype.com →
Deployments). Once trusted, switch the workflow task to
`publishAndReleaseToMavenCentral` for one-click. Central sync (search + CDN) lags
a release by ~15–120 min.

**Proof.** `tatrman-server/scripts/verify-public-resolution/` resolves the whole
spine from `mavenCentral()` only, anonymously — the standing public-access test.

## Gotchas

1. **GitHub Packages requires authentication even for "public" packages** — the
   most-cited quirk, and the reason **Maven Central (above), not GH Packages, is
   the public lane**. Always supply a PAT (`gpr.token`) or `GITHUB_TOKEN` for the
   staging lane, even for a read. Anonymous pulls fail.
2. **No automatic version cleanup** — old versions stay until manually deleted.
   Delete test versions (e.g. `kotlin/v0.0.1-test`) after exercising the
   workflow; sweep stale versions periodically.
3. **PAT rotation** — classic PATs expire (default 90 days). Refresh in
   `~/.gradle/gradle.properties` when consumers start failing auth.
4. **First-time publish bootstrap** — the first publish may need the repo's
   package settings configured (Settings → Packages → "Inherit access from
   source repository"). Confirm on the first tag push.
5. **The ANTLR-tool leak** — `ttr-parser` strips the `antlr` configuration from
   its `api` so only `antlr4-runtime` (not the full `antlr4` code-gen tool)
   appears in the POM. If you touch the parser build, re-inspect the published
   POM. Likewise the test-only conformance dumper lives in `src/test/` so
   `kotlinx-serialization` stays off the runtime classpath.
6. **One tag per module family (RO-24) — no module has two publishers.** The
   `grammar/v*` bundle (was `kotlin/v*` before the 2026-07-16 justfile-sync
   rename) ships exactly the three grammar-toolchain modules
   (`ttr-parser` + `ttr-writer` + `ttr-semantics`); `ttr-metadata(-git)` is
   published **only** by `metadata/v*` (was `kotlin-metadata/v*`), and
   `ttr-plan-proto` + `ttr-translator` **only** by `translator/v*` (was
   `kotlin-translator/v*`). Historically (through the 0.9.1 grounding release)
   the `else` branch also ran `:ttr-metadata(-git):publish`, so a `grammar/v*`
   and a same-version `metadata/v*` would race to `PUT` the same immutable jar
   and the loser went **409 Conflict** (red run, harmless but noisy). Reconciled
   at **SV-P1·S1·T2** (2026-07-11) by trimming the `else` branch — this table,
   the workflow header, and the workflow logic now agree. Because RO-24 also
   freezes versions once public, **never re-cut an existing `<family>/v<x.y.z>`**
   — bump the version instead. The `just publish` recipe enforces this: it
   refuses a version number already used by either the bare or `-RELEASE` tag
   for that family.

## First-time setup checklist

- [ ] Verify the artifacts build locally:
      `./gradlew -Pversion=0.0.1-LOCAL :packages:kotlin:ttr-parser:publishToMavenLocal :packages:kotlin:ttr-writer:publishToMavenLocal`
      — produces `~/.m2/repository/org/tatrman/*` jars + POMs; confirm
      `ttr-writer`'s POM references `org.tatrman:ttr-parser`. ✅ (done 2026-06-03)
- [ ] Push `grammar/v0.0.1-test` (internal-only — no `release`) to exercise
      `.github/workflows/publish.yml`; confirm it runs green and the packages
      appear under the repo's Packages tab; **delete the test version** afterwards.
- [ ] Configure the repo's GitHub package settings (access inheritance) if the
      first publish needs it.
- [ ] Cut the real `just publish bundle grammar release set 0.1.0` and confirm
      `org.tatrman:ttr-parser:0.1.0` + `org.tatrman:ttr-writer:0.1.0` resolve
      from a fresh Gradle project (both GH Packages and Maven Central).

---

# Publishing — Editor extensions (VS Code / IntelliJ)

The VS Code `.vsix` and IntelliJ plugin `.zip` are released as **GitHub
Releases** (download the asset and install it), tag-driven like the Kotlin
artifacts above. The VS Code extension is **also published to the VS Code
Marketplace** (`collite.ttr-modeler-vsc`) by the same tag. Unlike the Kotlin
version — which lives only in the tag — each extension's version lives in a
tracked file: `packages/vscode-ext/package.json` (`version`) and
`intellij-plugin/gradle.properties` (`pluginVersion`).

> **Canonical publisher (2026-07-17).** tatrman owns the grammar + IDE support,
> so the `collite.ttr-modeler-vsc` Marketplace listing is published from **this
> repo**. The frozen `modeler` fork no longer publishes to the Marketplace (a
> single id must have one publisher) — its release workflow builds a `.vsix`
> GitHub asset only. tatrman's extension version was reconciled to resume the
> series **above** the last modeler-published `0.9.8`.

## How to release

Run the recipe; it bumps the version, builds the version-stamped artifact,
commits the bump, and pushes the branch + a `<kind>/v<x.y.z>` tag (with a confirm
prompt before pushing). The tag push triggers
[`.github/workflows/release-extensions.yml`](.github/workflows/release-extensions.yml),
which rebuilds the artifact in a clean runner and attaches it to a Release. No
`-RELEASE` marker applies here — every tag already produces a public GitHub
Release, there's no internal-only lane to opt out of (`just publish vscode release`
is refused with an explanatory error).

```bash
just publish vscode              # patch bump (0.1.0 -> 0.1.1)
just publish vscode minor        # 0.1.0 -> 0.2.0
just publish vscode major        # 0.1.0 -> 1.0.0
just publish vscode set 0.3.0    # explicit version
just publish intellij            # same four forms for the IntelliJ plugin
```

| Tag | Built + released asset | Marketplace |
|---|---|---|
| `vscode/v<x.y.z>` | `ttr-modeler-vsc-<x.y.z>.vsix` | ✅ VS Code Marketplace (`collite.ttr-modeler-vsc`) |
| `intellij/v<x.y.z>` | `intellij-plugin-<x.y.z>.zip` | — (GitHub Release only) |

Each Release lands at `https://github.com/Collite/tatrman/releases` (or
`gh release download <kind>/v<x.y.z>`); its notes carry the install instructions.

## Notes

- **One build path.** The workflow runs the *same* private `just` recipes
  (`_build-vsix` / `_build-intellij`) the release recipes use locally, so CI and
  local builds never drift. `just _build-vsix <x.y.z>` builds a `.vsix` with no
  version bump or git side-effects.
- **`vsce` is a dev dependency** (`@vscode/vsce` in `packages/vscode-ext`),
  invoked via `pnpm exec vsce` — no global install needed. Its native deps
  (`@vscode/vsce-sign`, `keytar`) are marked `false` under `allowBuilds` in
  `pnpm-workspace.yaml`; their build scripts aren't needed for `vsce package`.
- **VS Code Marketplace publish** (`vsce publish`, publisher `collite`) runs in
  the same `vscode` job, publishing the *same* `.vsix` just built (via
  `--packagePath`) so the Marketplace and the GitHub Release asset are byte-identical.
  It is gated on the `VSCE_PAT` repo secret — an Azure DevOps PAT scoped to
  *Marketplace → Manage* on the `collite` publisher; if the secret is absent the
  step is skipped and only the GitHub Release is produced. ⚠️ Microsoft retires
  all-accessible-orgs classic PATs in **December 2026** — before then this step
  must move to `vsce publish --azure-credential` (Entra ID / OIDC, no PAT). The
  IntelliJ plugin is **not** published to the JetBrains Marketplace (GitHub
  Release asset only).
- **Branch protection:** the recipe does `git push origin <branch>` for the bump
  commit. If the default branch requires PRs, release from a feature branch (the
  recipe warns when you're not on `master`).
- Smoke-test the workflow with a throwaway `vscode/v0.0.1-test` tag (it builds +
  creates a real Release); **delete that Release + tag** afterwards.
