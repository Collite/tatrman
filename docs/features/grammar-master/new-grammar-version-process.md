# New Grammar Version — Process Checklist (template)

Status: **living template** · Copy this list into the PR/issue for each grammar change and tick it off.

`packages/grammar/src/TTR.g4` is the **single canonical grammar**. It is **not vendored** anywhere.
Three parsers are generated from it and kept in lock-step by the conformance harness:

| Target | Generator | Lives in | Consumed as |
|---|---|---|---|
| TypeScript | `antlr-ng` | `packages/parser` | in-process (LSP, Designer) |
| Kotlin | ANTLR Gradle plugin (reads `.g4` directly) | `packages/kotlin/ttr-parser` (+ `ttr-semantics`) | `org.tatrman:ttr-parser` on **Maven** |
| Python | reference ANTLR jar (`-Dlanguage=Python3`) | `packages/python/ttr-parser` | `ttr-parser` wheel on **PyPI** |

Downstream repos (e.g. `ai-platform`) **bump the published-artifact version** — they never copy the
grammar. Drift between targets is caught by `conformance.yml`, not by any sync step.

> Fill in: **version** `____` · **kind** ☐ additive (minor `X.Y+1`) ☐ breaking (major `X+1.0`) ·
> **PR** `____`

---

## 0. Pre-flight

- [ ] Decide the version bump: **additive** (new optional construct / sugar) → minor `Y`;
  **breaking** (renamed/removed token, changed shape) → major `X`. Record the rationale in the PR.
- [ ] Confirm the change keeps `TTR.g4` **target-neutral**: no `@members`/`@header`, no embedded
  `{…}` actions, no semantic predicates, no `options { language=… }`. (All three targets generate
  from this one file — anything target-specific breaks Kotlin/Python generation.)

## 1. Grammar

- [ ] Edit `packages/grammar/src/TTR.g4`. New keywords used in cross-references must also be added to
  `idPart`.
- [ ] Update the `@grammar-version:` marker comment to the new version and add a CHANGELOG entry in
  the header comment block. (The parser prebuild extracts this into `@modeler/grammar`'s
  `TTR_GRAMMAR_VERSION`; update any test asserting the string.)

## 2. Regenerate + wire each target

- [ ] **TS:** `cd packages/parser && pnpm run prebuild` (regenerates `src/generated/` via
  `scripts/generate-typescript-parser.sh`). Update `walker.ts` / `ast.ts` for new nodes (keep
  source locations accurate — edit-synthesizer invariant) and any `@modeler/semantics` handling.
- [ ] **TextMate (VS Code):** `cd packages/vscode-ext && node scripts/generate-tm-grammar.ts`
  (regenerates `syntaxes/*.tmLanguage.json` — the only generated grammar file that **is** committed).
- [ ] **Kotlin:** runs the ANTLR Gradle plugin on the same `.g4`; update the Kotlin walker +
  `ttr-semantics` port to mirror the TS change. `./gradlew :packages:kotlin:ttr-parser:test
  :packages:kotlin:ttr-semantics:test`.
- [ ] **Python:** the Hatchling build hook regenerates via `packages/python/ttr-parser/scripts/
  generate-python-parser.sh` (needs Java/the antlr4 jar). Update the Python walker + semantics port;
  `cd packages/python/ttr-parser && pytest` (and `ruff`, `mypy --strict`).

> `generated/` / `_generated/` are gitignored on every target — never hand-edit them. Only `TTR.g4`,
> the generation scripts, and `vscode-ext/syntaxes/*.tmLanguage.json` are committed.

## 3. Conformance (the lock-step gate)

- [ ] Add/adjust fixtures under `tests/conformance/fixtures/` to exercise the new construct (plus a
  negative fixture if a token was removed).
- [ ] Refresh the committed TS baselines: `pnpm --filter @modeler/conformance dump-all`, commit
  `tests/conformance/out-ts/` and `out-ts-sem/`.
- [ ] Run cross-target diffs (mirrors `conformance.yml`): TS dumps vs Kotlin
  (`./gradlew … *ConformanceSpec*`, `*SemanticsConformanceSpec*`) and Python (`py-dump` /
  `py-sem-dump`); `pnpm --filter @modeler/conformance diff` + `diff-sem`. All three targets must agree.

## 4. Docs

- [ ] CHANGELOG: `packages/grammar/CHANGELOG.md` (+ repo `CHANGELOG.md` if present), with the
  breaking-change migration steps for a major bump.
- [ ] Update `CLAUDE.md` only if an **invariant** changed (new schema code, file kind, etc.).
- [ ] Update `docs/grammar-master/contracts.md` if data shapes changed.

## 5. Repo gates

- [ ] `pnpm -r typecheck && pnpm -r lint && pnpm -r build && pnpm -r test`
- [ ] `pnpm --filter @modeler/integration-tests test`

## 6. Publish (tag-driven — see `PUBLISHING.md`)

- [ ] Kotlin: push `kotlin/v<x.y.z>` (bundle) or the per-artifact tags — `publish.yml` publishes to
  Maven.
- [ ] Python: push `python/v<x.y.z>` — `publish-python.yml` builds + publishes the wheel to PyPI.
- [ ] Verify the artifacts are resolvable at the new version before notifying consumers.

## 7. Downstream

- [ ] In `ai-platform` (and any other consumer): bump the `ttr-parser` Maven / PyPI **dependency
  version** to the new release. **No grammar copy, no sync.** Run that repo's own test suite against
  the new artifact.
