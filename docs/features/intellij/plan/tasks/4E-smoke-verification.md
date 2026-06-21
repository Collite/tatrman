# Stage 4.E — Smoke verification

Companion to [`../implementation-plan.md`](../implementation-plan.md) Stage 4.E. Goal: confirm parity with the VS Code extension on both IDEA editions. This is the feature's acceptance gate. **Check each box the moment it's done.**

Estimate: 1 day. Pre-flight: Stage 4.D done; a built `.zip` from `./gradlew :intellij-plugin:buildPlugin`.

> This stage is manual install-and-observe on clean IDEs. Full automated cross-host E2E (the shared `samples/`-based suite run for every host) belongs to the global Phase 5 integration flow, not here.

---

## Block 1 — Clean install on IDEA Community

- [ ] **1.1 — Install into a clean IntelliJ IDEA Community (2024.x).**
  Use a fresh IDE instance / clean config dir. Install LSP4IJ from Marketplace (or confirm the dependency prompt installs it), then install the built TTR Modeler `.zip` via *Settings → Plugins → Install Plugin from Disk*. Ensure Node 20+ is on `PATH`.
  **Verify:** both plugins enabled; no errors in `idea.log`.

- [ ] **1.2 — Open `samples/v1-metadata/` and walk the acceptance checklist.**
  Confirm each, comparing against VS Code behavior on the same files:
  - [ ] Syntax highlighting matches VS Code (same TextMate grammar).
  - [ ] Diagnostics appear inline (e.g. an unresolved reference is flagged).
  - [ ] Go to Declaration jumps to the definition.
  - [ ] Find Usages lists references.
  - [ ] Quick Documentation (hover) shows the definition's description/type/kind.
  - [ ] Code completion offers expected symbols.
  - [ ] Rename updates all references.
  - [ ] A `.ttrg` file is recognized and served.
  **Verify:** all checks pass; LSP4IJ console shows a clean session.

## Block 2 — Clean install on IDEA Ultimate

- [ ] **2.1 — Repeat the clean install on IntelliJ IDEA Ultimate (2024.x).**
  Same steps as 1.1 on Ultimate.
  **Verify:** both plugins enabled; no errors.

- [ ] **2.2 — Re-run the acceptance checklist on Ultimate.**
  Repeat every item from 1.2 on Ultimate.
  **Verify:** all checks pass; note any Ultimate-only differences (expected: none).

## Block 3 — Negative + upgrade paths

- [ ] **3.1 — Verify the missing-Node path on a clean install.**
  On one of the IDEs, remove `node` from `PATH` (and clear any override), restart, open a `.ttr` file.
  **Verify:** the actionable notification appears (not a stack trace); setting the Node path in settings and reopening starts the server. (Confirms Stage 4.D UX survives a real install.)

- [ ] **3.2 — Verify a plugin upgrade in place.**
  Install `0.1.0`, then install a bumped build over it; restart.
  **Verify:** the upgrade applies cleanly; the LSP still starts and features work.

## Block 4 — Record results

- [ ] **4.1 — Write a short results note.**
  Create `docs/intellij/implementation/smoke-4E.md` recording: IDE editions + exact build numbers tested, Node version used, the checklist outcomes, and any deltas vs VS Code (ideally none).
  **Verify:** the note exists and covers Community + Ultimate.

- [ ] **4.2 — Confirm the global Phase 4 acceptance statement.**
  Cross-check against the project plan: *install the plugin in a clean IntelliJ; open `samples/v1-metadata/`; same highlighting, navigation, hover, find-references behavior as VS Code* (`docs/v1/plan/implementation-plan.md` Phase 4 acceptance).
  **Verify:** the statement holds on both editions.

---

### Stage 4.E definition of DONE (feature acceptance)

- [ ] Acceptance checklist passes on IDEA Community and Ultimate (2024.x).
- [ ] Missing-Node and in-place-upgrade paths verified on a real install.
- [ ] Results note committed under `docs/intellij/implementation/`.
- [ ] Behavior matches VS Code on the `samples/v1-metadata/` bundle.

When all boxes are checked, tick **Stage 4.E** in [`index.md`](./index.md). The `intellij-plugin` feature is then ready to fold into the global Phase 5 packaging/distribution work (signing, Marketplace submission), which is tracked in the project plan, not here.

## Fast-follow (out of scope for this feature)

- [ ] Bundle per-platform Node (removes the PATH requirement) — design open question #4 / IJ-Q1.
- [ ] Widen targets to other JetBrains IDEs.
- [ ] JCEF-embedded Designer + custom `modeler/*` wiring.
