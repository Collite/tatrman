# Publishing — Kotlin artifacts

This repo publishes the modeler-owned Kotlin libraries to **GitHub Packages**
(Maven feed at `https://maven.pkg.github.com/Collite/tatrman`) so `ai-platform`
— and any future constellation repo — can consume them as ordinary Maven
dependencies under the `org.tatrman:*` group.

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
`.github/workflows/publish.yml`:

| Tag | Modules published |
|---|---|
| `kotlin/v<x.y.z>` | **bundle**: `ttr-parser` + `ttr-writer` + `ttr-semantics` |
| `kotlin-parser/v<x.y.z>` | `ttr-parser` only (rare; parser-only patch) |
| `kotlin-semantics/v<x.y.z>` | `ttr-semantics` only (Phase 2 cadence) |
| `kotlin-metadata/v<x.y.z>` | **both** `ttr-metadata` + `ttr-metadata-git` (lockstep; contracts §1). First real tag `kotlin-metadata/v0.1.0` is cut at **M2.2**, not M1 |
| `kotlin-translator/v<x.y.z>` | **both** `ttr-plan-proto` + `ttr-translator` (lockstep; ttr-translator arc). First real tag `kotlin-translator/v0.8.0`. Wire-format changes follow `docs/ttr-translator/architecture/contracts.md` §2 (append-only within `v1`) |
| `ttrp/v<x.y.z>` | bundle: all `org.tatrman:ttrp-*` modules (first cut in TTR-P Phase 3; workflow wiring lands there) |

```bash
git tag kotlin/v0.1.0       && git push origin kotlin/v0.1.0          # bundle
git tag kotlin-parser/v0.1.1 && git push origin kotlin-parser/v0.1.1  # parser-only
```

The workflow runs `./gradlew -Pversion=<x.y.z> <modules>:publish` with the
auto-provisioned `GITHUB_TOKEN` (no PAT needed in CI).

### Python wheels (PyPI)

Pure-Python wheels ship to **public PyPI** via Trusted Publishing (OIDC — no
token), driven by [`.github/workflows/publish-python.yml`](.github/workflows/publish-python.yml):

| Tag | Wheel (PyPI project) |
|---|---|
| `python/v<x.y.z>` | `ttr-parser` — the ANTLR parser + stock vocab (pure-Python) |
| `python-plan/v<x.y.z>` | `ttr-plan-proto` — pre-generated `plan.v1`/`transdsl.v1`/`dfdsl.v1` `*_pb2.py` |

```bash
git tag python-plan/v0.8.0 && git push origin python-plan/v0.8.0   # ttr-plan-proto wheel
```

Each PyPI project registers this repo + `publish-python.yml` + the `pypi`
environment as its Trusted Publisher **before** the first tag is pushed (PyPI UI
→ project → Publishing). The `ttr-plan-proto` wheel carries the same version as
the Kotlin `kotlin-translator/v*` pair by convention.

## Semver discipline

Per [`contracts.md`](docs/grammar-master/contracts.md) §7:

- **Patch** — bug fix only; no public API surface change.
- **Minor** — additive: new def kinds, new properties, new diagnostic codes.
  While version `< 1.0.0`, minor bumps **may** contain breaking changes —
  document them in `CHANGELOG.md`.
- **Major** — removing/renaming any public type, removing a `DiagnosticCode`,
  or changing the conformance JSON dump schema (§5). Post-1.0 this is strict.
- **No SNAPSHOTs.** They consume package storage with no auto-cleanup. Cut real
  versions even for early iteration. Local builds without `-Pversion` get
  `0.0.1-LOCAL` (see below).

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

## Gotchas

1. **GitHub Packages requires authentication even for "public" packages** — the
   most-cited quirk. Always supply a PAT (`gpr.token`) or `GITHUB_TOKEN`, even
   for a read. Anonymous pulls fail.
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

## First-time setup checklist

- [ ] Verify the artifacts build locally:
      `./gradlew -Pversion=0.0.1-LOCAL :packages:kotlin:ttr-parser:publishToMavenLocal :packages:kotlin:ttr-writer:publishToMavenLocal`
      — produces `~/.m2/repository/org/tatrman/*` jars + POMs; confirm
      `ttr-writer`'s POM references `org.tatrman:ttr-parser`. ✅ (done 2026-06-03)
- [ ] Push `kotlin/v0.0.1-test` to exercise `.github/workflows/publish.yml`;
      confirm it runs green and the packages appear under the repo's Packages
      tab; **delete the test version** afterwards.
- [ ] Configure the repo's GitHub package settings (access inheritance) if the
      first publish needs it.
- [ ] Cut the real `kotlin/v0.1.0` and confirm `org.tatrman:ttr-parser:0.1.0` +
      `org.tatrman:ttr-writer:0.1.0` resolve from a fresh Gradle project.

---

# Publishing — Editor extensions (VS Code / IntelliJ)

The VS Code `.vsix` and IntelliJ plugin `.zip` are released as **GitHub
Releases** (download the asset and install it), tag-driven like the Kotlin
artifacts above. Unlike the Kotlin version — which lives only in the tag — each
extension's version lives in a tracked file: `packages/vscode-ext/package.json`
(`version`) and `intellij-plugin/gradle.properties` (`pluginVersion`).

## How to release

Run the recipe; it bumps the version, builds the version-stamped artifact,
commits the bump, and pushes the branch + a `<kind>/v<x.y.z>` tag (with a confirm
prompt before pushing). The tag push triggers
[`.github/workflows/release-extensions.yml`](.github/workflows/release-extensions.yml),
which rebuilds the artifact in a clean runner and attaches it to a Release.

```bash
just vscode              # patch bump (0.1.0 -> 0.1.1)
just vscode minor        # 0.1.0 -> 0.2.0
just vscode major        # 0.1.0 -> 1.0.0
just vscode set 0.3.0    # explicit version
just intellij            # same four forms for the IntelliJ plugin
```

| Tag | Built + released asset |
|---|---|
| `vscode/v<x.y.z>` | `ttr-modeler-vsc-<x.y.z>.vsix` |
| `intellij/v<x.y.z>` | `intellij-plugin-<x.y.z>.zip` |

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
- **No Marketplace publish** here — these are Release assets only. Publishing to
  the VS Code Marketplace (`vsce publish`, publisher `collite`) is a separate,
  not-yet-wired step.
- **Branch protection:** the recipe does `git push origin <branch>` for the bump
  commit. If the default branch requires PRs, release from a feature branch (the
  recipe warns when you're not on `master`).
- Smoke-test the workflow with a throwaway `vscode/v0.0.1-test` tag (it builds +
  creates a real Release); **delete that Release + tag** afterwards.
