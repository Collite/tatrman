# PL-P6 (⑥) — Query door + kantheon adoption (stages S1–S2)

> Pre-flight: PL-P4 review done (the hall is whole: Argos + Perun live). DoD: [`../plan.md`](../plan.md) §PL-P6. ⚠ Pre-generated 2026-07-09 — re-validate against preceding reviews; **HQ-4 is decided in S2, not pre-decided here.** S1 is `tatrman-platform`; S2 is a `kantheon` arc with platform support. This phase ends the accepted duplication: the mini-arc **delete** legs parked since PL-P2/P3/P4 execute in S2.

## S1 · Theseus transplant + slim (the query door) {#s1}

Verify: `./gradlew :services:theseus:test` green; a fixture plan submitted to the query door validates (Argos + LLM-Guard live), dispatches at **interactive** priority, and returns synchronously; CQ-6 validation proven both ways.

- [ ] **T1.** `git filter-repo` move from the re-pinned kantheon commit: `services/theseus` → `services/theseus` + proto slice `org/tatrman/theseus` → swept `cz.tatrman.theseus.*`. (`tools/theseus-mcp` STAYS kantheon — it is the agents' MCP skin, intelligence-side.) `TRANSPLANTS.md` updated.
- [ ] **T2 (tests first).** `QueryDoorTest.kt` — written BEFORE the slim (tracker rule 2; the slim is behavior-changing): `"a plan.v1 payload validates against Veles's CURRENTLY-SERVED resolved world (CQ-6) — stale world qname → structured refusal"` · `"deployed-bundle T6 fingerprints are NOT this door's business (assert the code path is absent)"` · `"dispatch tags interactive — under a saturated batch fixture, the query dispatches first (F-5 proof from the consumer side)"` · `"bearer-only ingress; client-credentials principal accepted"` · `"LLM-Guard plugin fires for door=QUERY (closes the PL-P4.S2.T5 marker)"`.
- [ ] **T3.** Slim per C-3: the door accepts **one validated plan, synchronous** — strip orchestration remnants the program door now owns; whatever runtime translation ad-hoc plans still need stays (the C-3 note), everything else routes through the shared hall client from PL-P4.S1.
- [ ] **T4.** Finish T2's assertions over the slimmed core: Veles `GET /v1/worlds/current` for CQ-6; OPA-sidecar PEP for query authz; AuditEvents through the spine.
- [ ] **T5.** Helm + roster + OTel; the ⑥ roster now = the full hall behind both doors.
- [ ] **T6.** Run Verify, check tracker boxes, commit `PL-P6.S1: Theseus slimmed as the query door`.

## S2 · Kantheon adoption + HQ-4 + the great delete {#s2}

Verify: kantheon's own suites green against the adopted door (its CI, not ours); the duplicated spine modules are GONE from kantheon's build; platform dependency-rule test still green (P2 holds at the new steady state).

- [ ] **T1.** *(decision task — do FIRST, with Bora)* **HQ-4**: agent Keycloak-role pass-through vs mapping into platform grants. Write `docs/decisions/hq4-agent-roles.md` (in tatrman-platform): the options, the call, and the enrichment-never-authority constraint either way. The rest of this stage implements the verdict.
- [ ] **T2 (tests first).** *(kantheon)* Adoption contract tests in Ariadne/agents: `"agent queries authenticate as client-credentials service principals (H-2-ii) against the platform query door"` · `"roles reach authz per the HQ-4 verdict"` · `"door refusal surfaces to the agent as the same structured error shape the mini-spine produced"` (agents must not notice the move).
- [ ] **T3.** *(kantheon)* Rewire Ariadne's execution path to the platform query door (base URL + principal from kantheon config); keep Ariadne itself — it stays the agents' facade, thinning on its own schedule (C-3).
- [ ] **T4.** *(kantheon)* **The delete legs**: land the parked DRAFT delete PRs (PL-P2.S5, PL-P3.S1, PL-P4.S1, PL-P4.S4 whois) plus theseus itself — the mini-spine (its argos/kyklop/worker/charon/theseus/whois copies and their deployment wiring) leaves kantheon's build and clusters. P2 check: kantheon now consumes `cz.tatrman` services over the network and `org.tatrman` artifacts by version — no source-level platform code remains.
- [ ] **T5.** *(kantheon)* Doc sweep touchpoint: kantheon's CLAUDE.md/AGENTS.md lose the spine sections; "what kantheon means" = the intelligence half + a client of the platform (the recorded post-split arc — this task does the mechanical part, the full sweep stays a kantheon-repo item).
- [ ] **T6.** Cross-repo smoke on the local deploy: a kantheon agent query → query door → hall → worker → synchronous result, with audit events citing bundle hashes; record the transcript.
- [ ] **T7.** Run Verify, execute the PL-P6 DoD checklist ([`../plan.md`](../plan.md) §PL-P6), check ALL tracker boxes, commit(s) per repo, request the PL-P6 `/review`.
