# C.2 — Grammar-bump rehearsal + INDEX + PR

**Repo:** both (a throwaway modeler bump, then ai-platform). **Effort:** ~2 hours.

**Pre-flight:** C.1 done — `resolve/` holds only `ReferenceResolutionPass.kt` +
`DrillMapValidator.kt`; suite green against `0.3.0`.

**Goal:** prove the headline promise (Phase 2 DoD 2.8.8) — a grammar/version bump
in modeler is absorbed by ai-platform with **no source edits** beyond the version
ref.

---

- [x] **C.2.1 — Throwaway modeler bump.** In a scratch modeler branch, make a
      trivial additive grammar/version change (e.g. bump `@grammar-version` in
      `packages/grammar/src/TTR.g4` `2.2`→`2.3`, or any additive token), and
      `publishToMavenLocal` the whole stack at `0.3.1-LOCAL`:
      ```bash
      ./gradlew -Pversion=0.3.1-LOCAL \
          :packages:kotlin:ttr-parser:publishToMavenLocal \
          :packages:kotlin:ttr-writer:publishToMavenLocal \
          :packages:kotlin:ttr-semantics:publishToMavenLocal
      ```

- [x] **C.2.2 — Switch ai-platform's version ref only.** On a scratch
      ai-platform branch, set `tatrman-modeler = "0.3.1-LOCAL"` (with TEMP
      `mavenLocal()`), change **nothing else**, and run:
      ```bash
      ./gradlew :infra:metadata:test
      ```

- [x] **C.2.3 — Assert zero source edits.** The suite passes with only the
      version-ref change (and, if the bump introduced genuinely new syntax, new
      *fixtures* — never resolver/symbol-table source). `git diff --stat` on the
      ai-platform scratch branch shows only `libs.versions.toml` (+ TEMP
      settings). Record the diff in the PR description. Then discard both scratch
      branches.

- [x] **C.2.4 — Update the grammar-master task index (modeler).** In
      `docs/grammar-master/tasks/INDEX.md`, flip the Phase 2 stage **2.8** row to
      ☑ and remove the "resolver consolidation deferred" qualifier; tick the
      Phase 2 DoD items that are now satisfied (resolver/symbol-table deleted;
      next grammar bump needs no ai-platform semantics code).

- [x] **C.2.5 — Update this plan's index.** In
      `docs/grammar-master/resolver-consolidation/tasks/INDEX.md`, check A.1, B.1,
      B.2, B.3, C.1, C.2 and the Project DoD boxes.

- [x] **C.2.6 — CHANGELOG note (modeler).** Under the relevant version, note that
      ai-platform's resolver/symbol-table now consume `org.tatrman:ttr-semantics`
      (the consolidation is complete).

- [ ] **C.2.7 — Open the ai-platform PR.** Title:
      `grammar-master: consolidate resolver onto org.tatrman:ttr-semantics`. Body:
      summarise the adapter, link the parity evidence (the now-deleted
      `ResolverParitySpec` from the B-phase commits), and paste the C.2.3
      zero-edit rehearsal diff. Ensure CI is green against the **published**
      `0.3.0` (not the LOCAL throwaway).

**Stage DoD:**
- Seven boxes checked.
- Rehearsal proven: a modeler bump → ai-platform absorbs it with only a
  version-ref change.
- Both task indexes updated; ai-platform PR open and green.
- **Resolver consolidation COMPLETE** — Phase 2.8's deferred half delivered.
