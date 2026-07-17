# Contributing to Tatrman

Thank you for your interest in Tatrman. This document covers **how contributions
land** — the legal sign-off, where different kinds of change go, and how to get a
development environment running. By participating you agree to abide by our
[Code of Conduct](./CODE_OF_CONDUCT.md).

## Developer Certificate of Origin (DCO)

Tatrman uses the **[Developer Certificate of Origin 1.1](https://developercertificate.org/)**,
not a CLA. The DCO is a lightweight statement that you have the right to submit
your contribution under the project's license (Apache-2.0). You assert it by
signing off every commit:

```bash
git commit -s -m "your message"
```

This appends a `Signed-off-by: Your Name <you@example.com>` trailer. Commits
without a sign-off will be flagged by CI and cannot be merged.

**Agent-authored patches are welcome.** If you used an AI assistant to write a
change, submit it as your own: your `Signed-off-by` carries the DCO, and you take
responsibility for the contribution as if you had typed every line. "Robots write
through git" applies to external contributors exactly as it does to us.

## Where changes go

Tatrman distinguishes the **edges** from the **core**:

- **Edges** — SPI-shaped additions with a small blast radius: workers, connectors,
  emit plugins, IDE-side niceties, docs, examples. These land through the normal
  **PR → review → conformance** path. A first contribution is best shaped as *your*
  worker or connector.
- **Core** — the language (grammar, semantics), the published contracts, and the
  plan hub. Changes here go through the **public RFC process** — see
  [GOVERNANCE.md](./GOVERNANCE.md). Open a design discussion before a large PR; a
  core change that arrives as a surprise PR will be asked to start as an RFC.

If you are unsure which bucket your idea falls in, open an issue and ask.

### Docs: the site vs. the engineering record

Two kinds of documentation live in this repo, and they are not interchangeable:

- **`docs-site/`** — the **public docs site** ([tatrman.org](https://tatrman.org),
  MkDocs Material). Product documentation: what Tatrman does and how to use it.
  Organised as four goal-shaped tracks — *Get running · Model · Connect ·
  Operate* — one per reader's job. A new page belongs to the track matching the
  reader's **job**, not the subsystem it describes.
- **`docs/features/` and `docs/ecosystem/`** — the **engineering record**: design
  decisions, architecture rationale, and how we got here. These are not site
  content and are not published.

The boundary is enforced by CI (`scripts/check-docs-site-boundary.sh`): a site
page may **distill** the engineering record into an explanation, but must never
mirror it or relative-link into it — such a link resolves locally and 404s once
the site is served standalone. Link outward to a GitHub URL instead.

Working on the site locally:

```bash
cd docs-site
pip install -r requirements.txt
mkdocs serve          # http://127.0.0.1:8000, live reload
mkdocs build --strict # what CI gates on
```

## Development quickstart

This repo is a pnpm (TypeScript) + Gradle (Kotlin) monorepo. See
[CLAUDE.md](./CLAUDE.md) for the full command reference and architecture.

```bash
pnpm install          # install TS workspace deps (pnpm 11, Node 20+)
pnpm -r build         # build every package
pnpm -r test          # run all Vitest suites
pnpm -r typecheck     # type-check without emitting
./gradlew build       # build + test the Kotlin modules
```

Convenience recipes live in the `justfile` where present. Before opening a PR,
run the relevant test suite and `bash scripts/check-spdx.sh` (every source file
carries an `SPDX-License-Identifier: Apache-2.0` header — run
`bash scripts/add-spdx.sh` to add it to new files).

### Grammar changes

`packages/grammar/src/TTR.g4` is the canonical grammar; the generated parsers are
gitignored and rebuilt at build time. After editing it, follow the regeneration
steps in [CLAUDE.md](./CLAUDE.md). Cross-target drift (TS ↔ Kotlin ↔ Python) is
caught by the conformance harness, not by hand.

## Commit and PR hygiene

- Sign off every commit (`git commit -s`).
- Keep unrelated changes in separate commits/PRs.
- Reference the issue or RFC your change implements.
- CI must be green: build, test, lint, the SPDX header gate, and (for the
  published artifacts) the conformance and no-retired-persona gates.

## Communication

- **Bugs** → GitHub Issues (use the templates).
- **Questions / ideas** → GitHub Discussions.
- **Security** → do **not** open a public issue; see [SECURITY.md](./SECURITY.md).
- **Chat** → <!-- TODO(G2): pick ONE community chat (Discord or Zulip) and link it here. Do not add a second. -->
  a real-time channel will be announced with the 1.0 launch.
