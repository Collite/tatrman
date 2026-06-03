# Phase 1.9 — Retire sync scripts + update modeler docs

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1–2 hours.

**Pre-flight:**
- Phase 1.8 DoD met — ai-platform is consuming
  `org.tatrman:ttr-parser:0.1.0` cleanly.

**Tasks:**

- [ ] **1.9.1 — Delete the sync scripts.** Remove:
      - `packages/grammar/scripts/sync-to-ai-platform.sh`
      - `packages/grammar/scripts/check-sync.sh`
      Keep `packages/grammar/scripts/generate-typescript-parser.sh` and
      `packages/grammar/scripts/extract-property-map.ts` — they're still
      needed by the TS parser build.

- [ ] **1.9.2 — Update `packages/grammar/README.md`.** Remove the
      `sync-to-ai-platform.sh` and `check-sync.sh` sections. Replace with a
      "Consumers" section listing the published Kotlin artifacts and a one-line
      pointer to `docs/grammar-master/` for the publishing pipeline.

- [ ] **1.9.3 — Update `CLAUDE.md`.** Edit the "Grammar regeneration" section:
      - Remove step 3 (the sync-to-ai-platform paragraph) entirely.
      - Add a new bullet about the Kotlin Gradle build path:
        `cd packages/kotlin/ttr-parser && ./gradlew test` regenerates the
        Java parser via the ANTLR Gradle plugin reading the canonical .g4.
      - Add the note: "The grammar is consumed cross-repo via published
        Maven artifacts (`org.tatrman:ttr-parser`); see
        `docs/grammar-master/architecture.md` and root `PUBLISHING.md`."

- [ ] **1.9.4 — Update `docs/v1/design/architecture.md` §9** ("Relationship to
      ai-platform"). Replace the "shares the grammar via the sync script"
      bullet with "publishes the Kotlin parser and writer as Maven artifacts
      (`org.tatrman:*`); ai-platform consumes them via GitHub Packages."
      Update §8.5 (CI) to mention the new `publish.yml` and `conformance.yml`
      workflows.

- [ ] **1.9.5 — Update `docs/ai-platform-upgrade.md` status header.** Add a
      preamble noting that Section 0 (grammar sync) is obsoleted by the
      grammar-master migration as of `kotlin/v0.1.0`; Section B's
      `searchable` fix is folded into Phase 1.8.6 of the new plan. Leave the
      rest of the document for historical reference (it's a clear illustration
      of *why* we did this refactor).

- [ ] **1.9.6 — Add a CHANGELOG entry to `packages/grammar/CHANGELOG.md`.**
      Note: "Grammar canonical source unchanged; consumer model changed —
      ai-platform now resolves `org.tatrman:ttr-parser` from GitHub Packages
      instead of vendoring TTR.g4. The sync scripts have been removed."

- [ ] **1.9.7 — Final verification.** Run:
      ```bash
      ./gradlew build
      pnpm -r test
      pnpm -r build
      pnpm -r typecheck
      pnpm -r lint
      grep -rn "sync-to-ai-platform\|check-sync" --exclude-dir=node_modules --exclude-dir=.git . || echo "OK: no leftover references"
      ```
      All green; final command prints "OK".

- [ ] **1.9.8 — Update `docs/grammar-master/tasks/INDEX.md`** ticking off the
      Phase 1 DoD checkboxes and committing.

**Stage DoD:**
- All eight tasks checked.
- `sync-to-ai-platform.sh` and `check-sync.sh` deleted.
- All docs that referenced them updated.
- Both build systems (Gradle + pnpm) green.
- `docs/grammar-master/tasks/INDEX.md` Phase 1 DoD list fully checked.
- ai-platform's `grep -rn "shared.ttr.parser"` returns nothing (verified in
  1.8.8) — but worth one more cross-repo grep here as a final safety net.

**Phase 1 COMPLETE when this stage's DoD is met.**
