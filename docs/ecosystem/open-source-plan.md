# Tatrman — Open Source Operations Plan (v1, 2026-07-10)

> How the open core becomes an open-source *project* and then a *standard* — the operational
> half that the publish gates don't cover. Companion to `ecosystem.md` and
> `next-steps-260710.md` (same folder). Premise: **a public Apache-2.0 repo is not a project;
> a project is repo + releases + docs + contribution path + communication + a reason to care.**

## 0. The three maturity gates

| Gate | Meaning | Test |
|---|---|---|
| **G1 · Publishable** | clean history, license hygiene, honest README | a lawyer and a skeptic both leave calm |
| **G2 · Adoptable** | a stranger reaches a working governed answer alone | **time-to-first-governed-answer < 30 min** on a laptop we've never seen |
| **G3 · Contributable** | a stranger can change it without breaking it | first merged PR from someone we've never met |

**The trap between G1 and G2 kills most projects:** publish + announce + attention spike +
broken quickstart = spent credibility. Rule: **publish quietly, launch loudly, and only launch
after the quickstart survives strangers.** First strangers = Dolphin consultants (their
confusion is free QA and their success is channel enablement — one motion, two payoffs).

## 1. G1 · Publishable — the checklist

- `LICENSE` (Apache-2.0) + `NOTICE` + SPDX headers policy (header-check in CI, not by hand).
- **README that sells in 30 seconds:** what it is (the two-call thesis in three sentences),
  who it's for, quickstart link, honest status labels (live / extracted / planned — the
  `ecosystem.md` discipline, public).
- `SECURITY.md` — private disclosure channel + response SLA. A public repo makes CVEs our
  problem; decide the process before the first report, not after.
- `CONTRIBUTING.md` + issue/PR templates + `CODE_OF_CONDUCT.md` (Contributor Covenant, done).
- **DCO, not CLA.** Sign-off is frictionless; Apache-2.0 inbound=outbound suffices. A CLA only
  pays if we ever relicense — and the steward's control point is the **trademark**, not
  copyright aggregation. (Decision: OQ-12 resolved → DCO.)
- `GOVERNANCE.md`, one page, honest: steward-maintainer (BDFL) model; **the control-room
  method published as the RFC process** — language/contract changes go through a public
  design effort (diverge → options → append-only decision log). Our existing discipline IS
  mature-project governance; we just make it visible.
- **Trademark policy** published (the certification lever's public face): what may and may
  not be called "Tatrman"; conformance = the earning mechanism.
- Public CI, green; conformance suite runs publicly (third parties can self-verify — this is
  what makes it a *standard*, not just a codebase).

## 2. G2 · Adoptable — the product is the on-ramp

- **North-star metric: time-to-first-governed-answer.** Clone → one-command bring-up
  (compose / kind) → sample estate → question via MCP → answer with RLS applied and ⓘ
  provenance shown. Under 30 minutes, no human contact.
- **The Hartland estate is the public sample** — the demo doubles as the playground (data is
  TPC-DS-derived; models, Shems, and queries ship as the example repo).
- **Second path: your own database** — `ttr import-schema` → first-cut model → first governed
  question over the user's real data. This is the brownfield front door; it must be in the
  quickstart, not an appendix.
- Docs site on **tatrman.org** (static, generated from the repo): quickstart · concepts (the
  thesis, models, the plan hub, the security path) · language reference (the DFP wiki
  generalized and translated) · operations guide.
- **Release engineering:** semver; tagged releases with changelogs; **Maven Central** for
  `org.tatrman` (namespace verified via tatrman.org — GitHub Packages requires auth and kills
  adoption), PyPI, npm, GHCR images, Helm repo; artifacts signed. **Monthly minor cadence** —
  a visibly alive project is an enterprise due-diligence checkbox.
- Support surfaces: GitHub Issues (bugs) + GitHub Discussions (questions) + ONE chat
  (recommendation: Discord or Zulip — pick one, never three). Response-time norm stated
  honestly ("maintainer replies within N working days").

## 3. G3 · Contributable — grow the edges, guard the core

- **The SPIs are the contribution magnet:** workers, connectors, emit plugins. A stranger's
  first PR should be *their* worker or connector — additive, SPI-shaped, low blast-radius.
  Curate `good-first-issue` accordingly; write the "build a worker in a day" tutorial early.
- Architecture docs for contributors (the kantheon/tatrman doc discipline, public).
- Core changes (language, contracts, plan hub) go through the public RFC/control-room process;
  edges (plugins) go through review + conformance.
- **Every merged PR is a maintenance transfer.** Big surprise contributions get love AND
  scrutiny; "thank you, this lives better as a certified third-party plugin" is a valid merge
  decision.
- Contributor recognition from day one (release notes credits); additional maintainers only
  after sustained track record — and never as a courtesy.

## 4. Launch plan (assets mostly exist)

1. **Quiet publish** (repos public at G1) — no announcement; friendly-fire period with Dolphin
   consultants + DFP-adjacent users; harden the quickstart.
2. **Launch** (at G2, timed before the Aricoma negotiation concludes — the foundation-stone
   logic): launch post (deck slides 3–4 = the outline: the thesis + intent-SQL vs derived-SQL)
   · demo video (Hartland dress-rehearsal recording, cut down) · hosted read-only playground
   if the showcase cluster can carry it · Hacker News + r/dataengineering · **Czech community
   first** (JUG, data meetups — home crowd, forgives rough edges, gets the tatrman joke) ·
   LinkedIn for the channel audience.
3. **Talk circuit, ring order:** Prague → CEE → Europe. One reusable talk: "Governed
   text-to-plan: why the LLM never writes the joins."

## 5. Honest expectations (morale insurance, written down in advance)

- First 100 stars are friends. **Real signals, in order:** first stranger issue → first
  stranger PR → first production deployment we didn't know about.
- **For this product, GitHub is the credibility layer, not the funnel.** The channel
  (Dolphin/Aricoma engagements) is the adoption engine for years; OSS serves procurement,
  trust, and standard-ness. Never judge the strategy by stars.
- **Docs + support will eat 30–50% of maintainer time.** Sized into the retainer deliverables,
  not squeezed around them.
- The parked satellites will be requested weekly. The published parking lot with revisit
  conditions is how we say no without saying no.
- Metrics reviewed monthly: time-to-first-answer (measured, not estimated) · quickstart
  completion · stranger issues/PRs · known production deployments · (later) Marketplace
  installs. Anti-metric: stars.

## 6. First ten actions (in order)

1. Repo topology decision (handoff §3 / OQ-1) — everything below lands in its result.
2. LICENSE/NOTICE/SPDX + header CI check.
3. README v1 with honest status labels + the 3-sentence thesis.
4. SECURITY.md + disclosure inbox.
5. GOVERNANCE.md (steward + control-room-as-RFC) + trademark policy page.
6. CONTRIBUTING.md with DCO; templates; CoC.
7. Maven Central namespace verification for org.tatrman (tatrman.org DNS) — lead time, start early.
8. Quickstart v1: compose/kind bring-up + Hartland sample + first-question walkthrough.
9. Docs site skeleton on tatrman.org.
10. Friendly-fire round with Dolphin consultants; fix everything they trip on; only then plan the launch date.
