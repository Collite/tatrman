# SV-P2 · S3 — External-reader verification (the lawyer and the skeptic)

> Repos: both, read-only + fixes. Pre-flight: S1 + S2 merged. The phase's DONE is a *reader test*: "both open repos are legally coherent for an external reader" — G1's own bar is "a lawyer and a skeptic both leave calm." This stage runs those two readers deliberately, plus the one external legal action (OQ-6). Repos are already public — this is overdue hygiene, prioritize accordingly.

- [ ] **T1 — Trademark sanity check executes (Bora, ~1 h — OQ-6/RO-16).** TMview + ÚPV + EUIPO search for "Tatrman" (word mark; TATRA adjacency is THE thing to look at: Tatra trucks, Tatra banka, Tatry marks — assess classes 9/42 specifically) → if clear, file EUTM (applicant Collite, classes 9 + 42; 41 stays open per RO-16). Record the clearance result on the [stewardship checklist](../../../stewardship-checklist-260710.md) + findings here. **Before Aricoma diligence reads the repo.**
- [ ] **T2 — Client-name / secrets sweep (the skeptic, part 1).** Both public repos: `grep -rniE 'dfpartner|df.partner|dolphin|aricoma|kkcg' --exclude-dir={.git,node_modules,build,graphify-out} .` — every hit is either (a) sanctioned public framing (the docs deliberately say "validated at a production pilot" — fine, keep), or (b) client-confidential leakage (internal hostnames, engagement terms, people's names in code comments) → remove/generalize. Also: `git log` scan is NOT required (public history was sanctioned at the fork), but run `gitleaks detect` (or `trufflehog filesystem .`) on both worktrees for credentials/tokens; ⚑ ANY hit immediately to Bora — a leaked secret means rotation, not just deletion.
- [ ] **T3 — License-coherence read (the lawyer).** Walk as an outside counsel would: LICENSE = Apache-2.0 text intact · NOTICE present + minimal · SPDX check green in CI (S1) · third-party licenses: run a dependency license report (Gradle `licensee` or `./gradlew dependencies` + spot-check; pnpm `licenses list` for the TS workspace) — flag anything GPL/AGPL-family in a *distributed* artifact (test-only deps are fine); confirm ANTLR (BSD), Calcite (Apache-2.0), jgit (EDL/BSD) attributions need no NOTICE additions · vendored code: anything copied-in (protos adopted from ai-platform per contracts §3 — DFP-authored, verify the fork sanction covers it; ⚑ if provenance is undocumented, feeds OQ-3's pattern).
- [ ] **T4 — The G1 checklist walk, formally.** [`../../../open-source-plan.md`](../../../open-source-plan.md) §1, item by item, both repos: LICENSE/NOTICE/SPDX ✓(S1) · README sells + honest labels ✓(S2·T7) · SECURITY ✓ · CONTRIBUTING/templates/CoC ✓ · DCO ✓ · GOVERNANCE + control-room-as-RFC ✓ · trademark policy ✓ · **public CI green** (check both repos' Actions are green on master and visible to anonymous viewers) · conformance-suite-runs-publicly = honestly marked NOT-YET (SV-P4 authoring; do not fake it). Every unmet item → a GitHub issue on the owning repo, labeled `g1-gap`.
- [ ] **T5 — Fresh-eyes read-through (the skeptic, part 2).** 30 minutes as a stranger, top of README downward, both repos: does anything promise what doesn't exist, reference internal-only systems (olymp, bp-dsk, backstage URLs), or dead-link (grep `](http` + `](./` and spot-check)? Fix small things inline; list bigger ones.
- [ ] **T6 — Close SV-P2.** Walk phase-DONE in [`00-task-management.md`](./00-task-management.md); compile `sv-p2-review-input.md` for Bora (include the T3 license report + T2 sweep verdicts); control room session-index row; project memory updated.

**Verify block:**
```bash
# G1 gaps are tracked, not silent:
gh issue list -R Collite/tatrman --label g1-gap; gh issue list -R Collite/tatrman-server --label g1-gap
# secrets scan artifacts recorded in findings (tool + version + zero-findings line or ⚑)
# CI public + green:
gh run list -R Collite/tatrman -b master -L 1; gh run list -R Collite/tatrman-server -b master -L 1
```

## Findings / ⚑

*(trademark result · sweep verdicts · license report summary · g1-gap issue list)*
