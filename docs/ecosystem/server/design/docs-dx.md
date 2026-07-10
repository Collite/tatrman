# Tatrman — Public Docs & DX Architecture (RO-27)

> **Status:** pinned 2026-07-10 (design-gaps session, item 2 of `../../next-steps-260710-design-gaps.md`; decision = **RO-27**, control room §7). This is *structure, not prose*: the information architecture, the tooling decisions, and the quickstart skeleton the SV-P4 docs deliverable builds from. **Docs are inside the RO-3 bar** ("from public artifacts and public docs alone") — this document is what makes that clause plannable.
> Watch item (plan §Risks): docs are the team's least-practiced deliverable — start the quickstart in SV-P4's *first* week.

---

## 1. Decisions (RO-27)

| # | Decision | Choice |
|---|---|---|
| ① | Top-level IA | **Four goal-shaped tracks** — *Get running · Model · Connect · Operate* — one per visitor job; genre discipline (tutorial / how-to / reference / explanation kept unmixed) applied **within** each track as a writing rule, not as the menu. Rejected: genre-first menu (visitor must learn the filing system before finding their path); product-first menu (visitor must learn the ecosystem taxonomy before their first governed answer). |
| ② | Generator | **MkDocs Material** (+ `mike` for versioning). The boring, proven infra-docs default: search, Mermaid, low authoring friction — the right risk profile for a bar-weighted, least-practiced deliverable. All-markdown ⇒ migration later is cheap. Rejected: Astro Starlight (component embedding + i18n attractive — **the named revisit** if a live `.ttrl` viewer in docs becomes a real need); Docusaurus (more framework than a docs-first team needs). |
| ③ | Site-source home | **The `tatrman` repo** (docs-as-code per RO-17; one site, one build, served at `tatrman.org`). Server/Kantheon-specific pages are co-located here by convention — the site documents the *ecosystem*; repo boundaries are an implementation detail the reader never sees. Rejected: separate site repo (a third repo to steward before a community needs it); per-repo docs + CI aggregation (correct long-term ownership — parked as the revisit condition when external contributors own product docs). |
| ④ | Versioning policy | **Latest-only until 1.0.0; from 1.0.0, versioned snapshots per minor** (mike), labeled with the product version (= the umbrella chart's `appVersion`, RO-24). Pre-debut churn stays cheap; the debut's docs freeze with the debut. |
| ⑤ | Data Academy | **A separate future property** (STRAT-8 / Dolphin) that consumes and links the *Model* track. Public docs stay product docs; the bar-gated site is not coupled to an unscoped education effort. |

## 2. The four tracks — scope and feeder sources

### Get running — "I have a database; show me the promise"
The quickstart (§3) plus its supporting how-tos (local/kind install, connecting each supported engine, troubleshooting first contact). **This track is the acceptance run's script** — the outsider executing RO-3 reads exactly these pages.
Feeds on: the umbrella chart (SV-P4), `ttr import-schema` (RO-26), the pilot's onboarding folklore.

### Model — "I own the semantics"
TTR-M language documentation: the layered models (db / er / cnc / md), packages and areas, bindings, aliases and search hints, named/pattern queries, the `security` block, worlds and composition. Generalizes the DFP wiki pattern (the pilot's analysts learned from a wiki — that structure is the field-tested seed). Reference genre = the language reference (generated where possible, from grammar/schema); tutorial genre = "model your first three tables"; explanation genre = "why the model is the deployment artifact".
Feeds on: `docs/manual/` (the existing user manual — its content migrates/adapts into this track), `docs/features/` design records (explanation-genre source material), the pilot wiki pattern.

### Connect — "I build agents"
The MCP surface as consumer documentation: the doors and tools, the OBO identity contract (what your agent MUST forward, what comes back), the result envelope and provenance, the compat rule, the conformance suite as *your* test harness. This track is [`mcp-surface.md`](./mcp-surface.md) (RO-25) rewritten for the outside reader — the contract doc is the reference-genre backbone; the track adds the tutorial ("your first agent against the door") and framework-specific how-tos (LangGraph / Koog / Claude-style MCP clients).
Feeds on: mcp-surface.md, the conformance suite fixtures (each fixture is a worked example), the reference Golems.

### Operate — "I run this"
Deploy and run: the chart's values contract, OIDC/Keycloak integration, identity verification duty (mcp-surface §3.4), policy-in-git (RO-7 open validator store), observability (the one-question-one-trace story), upgrade and versioning (RO-24 from the operator's seat), engine workers.
Feeds on: the chart (SV-P4), olymp-lineage deployment knowledge, `architecture.md`.

**Cross-track rule:** one concept, one home — tracks link, never restate (the wiki-rot preventive). The genre rule within tracks: no page mixes lesson, recipe, and reference.

## 3. The quickstart skeleton (the bar's path, verbatim)

> Target: a stranger with a database reaches a governed answer **in under an hour**, touching nothing but public artifacts and these pages. Each step names its verification ("you should now see…").

1. **Prereqs** — a k8s cluster (kind works) + a reachable MSSQL/PostgreSQL with any schema; an OIDC-capable IdP (bundled dev Keycloak acceptable for the quickstart).
2. **Install** — `helm install` the umbrella chart from GHCR; verify: all pods ready, `/ready` green.
3. **Import your schema** — `ttr import-schema` (RO-26): point at the DB, get the `db` mirror + `er` first cut + the review checklist; walk the checklist in VS Code (extension link); commit the model to a git repo.
4. **Serve the model** — point Veles at the model repo; verify: the catalog answers (`get_model` via `ttr-meta-mcp` or the Designer viewer).
5. **Connect an agent** — register the MCP doors in any MCP client (worked example: a generic MCP-capable assistant); forward your user's bearer token per the identity contract.
6. **Ask** — a natural question against your own data; see the answer *plus* the provenance attachment (`pipelineWarnings`: the RLS/mask/caps trail) — the governed-path proof, visible.
7. **Where next** — the three other tracks, by job.

Step 6's "see the provenance" is the money shot — the quickstart ends by *showing* the thesis (deterministic, governed, auditable), not asserting it.

## 4. Mechanics

- **Build:** MkDocs Material in `tatrman` (`docs-site/` source dir; nav = the four tracks), CI-built, published to `tatrman.org` (RO-17: docs-as-code; hosting rides the domain transfer, RO-15).
- **Versioning:** `mike` — `latest` alias pre-1.0.0; `1.0`, `1.1`… snapshots after, labeled by product version (RO-24).
- **Language:** English only for 1.0.0; Czech = a post-debut i18n question (revisit alongside the Starlight condition — Material's i18n is plugin-grade, Starlight's native).
- **Relationship to in-repo docs:** `docs/ecosystem/` (design corpus) and `docs/features/` are *not* site content — they are the engineering record; explanation-genre pages may distill them, never mirror them. `docs/manual/` content migrates into the Model track (the manual's future = the site).

## 5. SV-P4 deliverable shape (what "docs" now means in the plan)

1. Site scaffold (Material config, four-track nav, CI publish) — first week, with
2. **the quickstart** (§3) drafted against the dry-run cluster as soon as the chart stands — the acceptance run rehearses it;
3. Model track: manual migration + the language reference skeleton;
4. Connect track: mcp-surface-derived reference + the first-agent tutorial;
5. Operate track: values contract + identity + policy-in-git pages;
6. pre-debut docs freeze = the `1.0` snapshot (④).
