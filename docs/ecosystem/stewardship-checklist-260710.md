# Stewardship & trademark — decisions + checklist (2026-07-10)

> OQ-5 + OQ-9 + OQ-2/OQ-12 + OQ-3 **resolved**, OQ-6 **shaped**, OQ-4 approach ratified, OQ-11 proto half closed (walk-through session 2026-07-10; decision-log entries **RO-15..RO-20** in the platform control room §7). This is the execution checklist.

**Decided:**
- **OQ-5 ✅ — Collite = the steward entity.** 100% Bora-owned, established 2009, no other active business, registry clean, software development within its registered scope. Collite wears all five hats: trademark owner · public-infrastructure owner (GitHub org, domains) · contribution counterparty (CLA/DCO, OQ-12) · commercial licensor (`cz.tatrman`, Tatrman Platform License) · the entity diligence reads.
- **OQ-6 shaped — word mark "TATRMAN", EUTM direct, applicant Collite, classes 9 + 42** (41 = education/training under consideration for the Data Academy line). Clearance check (Bora, ~1 h) still to run before filing.

## Checklist — Collite (steward setup)

- [x] Registry hygiene verified — filings current, software within registered activity (Bora, 2026-07-10)
- [ ] Transfer `tatrman.org` / `.com` / `.cz` from Bora personally → Collite — **before the publish gates** (Maven Central verifies the `org.tatrman` namespace via tatrman.org DNS)
- [ ] Create the public GitHub org under Collite ownership (rides OQ-9)
- [ ] Name Collite in the legal texts as they get drafted: CLA/DCO counterparty (OQ-12) · Tatrman Platform License licensor (`cz.tatrman`)
- [ ] `docs/ecosystem/ecosystem.md` §10: name Collite as the steward (doc touch-up, next commit)

## Checklist — Trademark (OQ-6)

- [ ] **Clearance check** (Bora, ~1 h): TMview search for "tatrman" + `TATR-*` marks, focused on classes 9/42 — the TATRA-adjacency scan — **before filing and before Aricoma diligence reads the repo**
- [ ] Decide class **41** (education/training — Data Academy, partner enablement) in or out
- [ ] File **EUTM word mark "TATRMAN"**, applicant **Collite**, classes **9 + 42** (+41 if in) — ~€900–1050; no dependency on any other OQ, file as soon as clearance passes
- [ ] Record the application number; calendar the **opposition window** (3 months from publication)
- [ ] Once public docs go live: consistent "Tatrman™" first-use marking (optional, cheap signal)

## Checklist — GitHub & publishing (OQ-9 → RO-17)

- [ ] **Recover the `tatrman` GitHub account** (it's Bora's, ~2020): check which `@username` the reset email names; log into the Google-SSO-created account and free the gmail (rename/delete/change email), then forgot-password with the **username** `tatrman`; if still stuck → GitHub Support, "Account access" (Google-SSO email collision)
- [ ] Transform the recovered `tatrman` user → **organization**; migrate repos from the Collite org at leisure (transfers keep redirects) — interim home = Collite org
- [ ] Maven Central: register + **verify the `org.tatrman` namespace** (DNS TXT on tatrman.org — requires the domain under Collite first)
- [ ] Generate the **publisher PGP key** under Collite; secure custody + backup (it is the H-6 trust root; signs everything on Central)
- [ ] Reserve the **npm org** `tatrman` + VS Code Marketplace / Open VSX publisher names
- [ ] Pick the docs-site generator (MkDocs Material vs Astro Starlight) at SV-P4 start; docs-as-code, CI-built, served on `tatrman.org`

## Publish-gate blocker (from the OQ-11 check, RO-20)

- [ ] Rename `org.tatrman.proteus.v1` → **`org.tatrman.translate.v1`** in `ttr-plan-proto` (tatrman); delete kantheon's byte-identical `shared/proto/.../proteus/v1/translator.proto` and consume the artifact — **before gate 2 freezes the wire**
- [x] Diff `worker.proto` kantheon ↔ ai-platform — done 2026-07-10 (RO-21): kantheon = additive superset; repoint adopts it
- [ ] Relocate `kantheon.common.v1` shared messages → **`org.tatrman.common.v1`** (all 8 spine protos import kantheon-internal packages today — must break before the server move publishes wire contracts)
- [ ] Adopt **TableHint** into plan.v1 (un-reserve field 3, ai-platform numbering) — before gate-2 freeze (Bora, 2026-07-10)
- [ ] llm gateway → `llm.v1`: take kantheon's superset (EmbedText), functional service name

**Timing anchors:** trademark application on file → before Aricoma diligence · domain transfer → before publish gates (SV-P1) · everything else rides its named OQ.
