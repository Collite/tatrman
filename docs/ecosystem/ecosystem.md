# The Tatrman Ecosystem

> **A description of the planned state.** Written 2026-07-10, at the close of the ecosystem
> strategy review. This document describes the Tatrman ecosystem as it is intended to exist —
> what each part is, what it is for, and how the parts relate. It is a description, not a
> pitch and not a decision record: the *why* behind individual decisions lives in the design
> corpus (`docs/ecosystem/platform/design/`, `docs/ecosystem/platform/design/frontends/design/`, the TTR-P effort, and the
> kantheon fork records). Statements about the present are marked with their status:
> **live** (running at the pilot deployment), **extracted** (implemented in the open lineage),
> **planned** (designed, not built), **parked** (designed or sketched, deliberately deferred).

---

## 1. The problem

Enterprises hold their most valuable data in systems whose *meaning* is nowhere machine-readable.
A data warehouse encodes years of semantic decisions — what "sales" means, which of two time
dimensions a question refers to ("when it happened" vs. "when we learned of it"), how a customer
hierarchy rolls up, which rows a given user may see — but encodes them as folklore: in the heads
of analysts, in scattered documentation, in the shape of hand-written SQL.

Large language models are, simultaneously, extremely fluent in SQL and entirely blind to this
meaning. A model asked "how did Kaufland orders do last month?" can produce syntactically perfect
SQL and still join the wrong tables, pick the wrong time dimension, ignore row-level security,
and present the result with confidence. The industry's default answer — "text-to-SQL, but with
more context in the prompt" — improves the average case and leaves the failure mode intact:
the plumbing between intent and data remains probabilistic, unauditable, and ungoverned.

There is a second, older half of the problem. Data modeling as traditionally practiced does not
pay for itself: models are drawn, admired, and abandoned, because nothing downstream *consumes*
their meaning. (The pilot project behind this ecosystem began on a mainstream data platform with
conventional modeling; the modeling produced no benefit and that platform was abandoned — an
experience the industry repeats daily.) A model only earns its maintenance cost when machines
act on it.

## 2. The thesis

The Tatrman ecosystem is built on one architectural conviction:

> **Use the LLM for what it is good at — understanding intent — and make everything between
> intent and data deterministic.**

Concretely, the pattern (validated in production testing at the pilot, see §9) is:

1. **Models carry the semantics, machine-readably.** The physical schema, the entity–relationship
   model over it, conceptual roles, hierarchies, synonyms, search hints, and security-relevant
   classifications are authored as text, in a modeling language, versioned in git.
2. **The LLM structures intent, in a language it already speaks.** Entity recognition binds the
   user's words to modeled entities (one call); intent is then structured as SQL *over the modeled
   entities* — not over physical tables (a second call). SQL here is used as what it is best at:
   a precise, universally-spoken syntax for expressing a question. The LLM never navigates joins,
   time dimensions, dialects, or security.
3. **Everything after intent is deterministic.** A translator maps the entity-level query through
   the model's bindings to a physical plan; a validator injects row-level security and platform
   rules into the plan itself; a dispatcher routes it to an engine; results stream back typed.
   The same input produces the same plan, every time, and every answer is traceable to a
   validated plan — *auditability is a property of the architecture, not a logging feature.*

Security is structural: the validator sits between translation and execution as a separate
service, and there is no flag to bypass it. Governance is not applied to the answer; it is
applied to the plan.

This is what "**prepare your data for AI consumption**" means here: not embeddings, not a
vector index, but a machine-consumable semantic model with a deterministic, governed execution
path underneath it — so that *any* agent, from any vendor, can be given safe access to enterprise
data through a contract rather than through trust.

## 3. The ecosystem at a glance

Four names cover the whole ecosystem:

| Name | What it is | License (planned) | Status |
|---|---|---|---|
| **Tatrman** | The standard and toolchain: the TTR languages, formats, contracts, compiler/translator libraries, IDE support, Designer | Apache-2.0 | partially live; open lineage exists |
| **Tatrman Server** | The open runtime: metadata serving plus the governed query path (the "spine") | Apache-2.0 | live at pilot (client lineage); extracted (open lineage); publication planned |
| **Kantheon** | The intelligence suite: analytical agents that consume Tatrman Server | reference agents Apache-2.0; vertical/advanced agents commercial & services | Golem live at pilot |
| **Tatrman Platform** | The operate tier: scheduled program execution, data movement, enterprise policy and audit at fleet scale | commercial (Tatrman Platform License) | parked (fully designed) |

The license boundary follows one rule, fixed during the strategy review: **there must be a
meaningful open-source core** — everything an adopter needs to prove the ecosystem's promise
end-to-end is open; what an enterprise needs to *operate it at scale with confidence* is
commercial. Modeling, translation, validation, execution of interactive queries, the MCP
surface, and reference agents are open. Scheduling, orchestration, fleet operations, continuous
metadata harvest, and enterprise policy administration are the commercial tier.

A note on naming, for readers of older documents: service names are functional
(`ttr-<function>`; proto packages `org.tatrman.<function>.v1`). Three services keep persona
names for their semantic value — **Veles** (the metadata server: what is known), **Perun**
(the policy server: what is allowed), **Charon** (data movement: what travels between worlds) —
and agents in Kantheon carry persona names by design. Personas never appear in wire contracts
or published artifact coordinates.

## 4. Tatrman — the standard and toolchain

Tatrman is the part intended to become a *standard*: the language, formats, and contracts that
make a data model machine-consumable, plus the tools that let humans author and inspect it.

**The modeling language (TTR-M)** describes an environment in layered models: the **physical
model** (`db` — tables, views, columns, keys, indexes; treated as read-only observations of the
real system), the **entity–relationship model** (`er` — entities, attributes, relations, with
explicit *bindings* to the physical layer, including binding to declared queries where an entity
is a filtered or derived view), **conceptual roles** (`cnc` — fact, dimension, master,
transaction, bridge), and a **multidimensional model** (`md` — cubes, dimensions, hierarchies,
calculation catalogs). A **conceptual/ontology layer is planned** as the next model kind. Models
are organized into packages and areas, carry the semantics agents need (descriptions, aliases,
name attributes, fuzzy-searchable fields, named and pattern queries), and are plain reviewable
text under git — the model *is* the deployment artifact (status: **live**; the pilot's model is
authored and maintained in TTR-M by the client's own analysts).

**The plan hub.** All query languages parse to, and all engines execute from, one typed plan
representation (`plan.v1`, an encoding of Apache Calcite's relational algebra). Languages sit on
one side of the hub, models and engines on the other: adding a language or an engine is additive
(N + M), not combinatorial (N × M). This is the mechanism behind the ecosystem's orthogonality
claim — "any language over any model" — of which SQL-over-the-E-R-model is the first, live cell
(status: **live**).

**Formats and contracts** — model and world schemas, the plan protos, manifest formats, and
(from the platform design) snapshot archives and the lockfile — are owned by the open standard;
a standalone user can read every format their artifacts touch. The wire contracts services speak
(`org.tatrman.<function>.v1`) are part of the standard: a second implementation of any component
is possible by construction.

**The toolchain**: parser/writer libraries, the translator/compiler libraries, a modeler CLI,
IDE support (VS Code extension with live validation, navigation, hovers — **live**), a
conformance harness, and the **Tatrman Designer**, a browser/IDE frontend for viewing models,
graphs, and (in the platform context) runs and lineage. An **`import-schema` path** — bootstrap
a TTR-M physical model, and a first-cut E-R model, from an existing database — is **planned**
and is understood to be the standard's front door for brownfield adoption.

**Parked satellite:** the TTR-P processing-language family (fluent, dataframe-ish, SQL-ish, and
business-English surfaces over the same programs), the graphical write-enabled designer, and
graph optimization are a designed satellite (see §8) — deliberately sequenced after the core.

## 5. Tatrman Server — the open runtime

Tatrman Server is the deployable product an organization installs to make its modeled data
available for AI consumption: **one product name, a small constellation of services** (deployed
together via a single chart; Kubernetes-native, GitOps-friendly, OIDC/Keycloak-integrated).

| Service | Function | Status |
|---|---|---|
| **Veles** (`ttr-meta`, + `ttr-meta-mcp`) | Serves the model: catalog, model graph, search, snapshots; reads model source from git; the single source of what is known | live · extracted |
| `ttr-query` (+ `ttr-query-mcp`) | The query door: accepts a query in a supported language, drives translate → validate → dispatch, streams typed results (Arrow); renders to JSON/CSV/XLSX/Parquet | live · extracted |
| `ttr-translate` | Language ↔ plan translation over the plan hub; entity-level references mapped to physical via model bindings; joins derived from declared relations and foreign keys; dialect-aware unparse per engine | live · extracted |
| `ttr-validate` | Security and rules **in the plan**: row-level security predicates, column allow/deny/mask, result caps, strict coercion; structurally unavoidable | live · extracted |
| `ttr-dispatch` | Routes validated plans to capable engine workers | live · extracted |
| `ttr-worker-mssql` / `ttr-worker-postgres` / `ttr-worker-polars` | Per-engine executors (MS SQL, PostgreSQL, Polars dataframes); further engines arrive as workers implementing `worker.v1` | mssql, polars live · postgres extracted |
| `ttr-resolver` | Entity and value extraction from user text against the model's vocabulary (aliases, fuzzy fields); language-aware (Czech morphology proven) | live (client lineage) · open rewrite planned |
| `ttr-fuzzy`, `ttr-nlp` | Fuzzy candidate matching over model-declared searchable fields; NLP primitives (morphology, NER) | live · extracted |
| `chrono` / `geo` / `money` (+ `ttr-grounding-mcp`) | Deterministic grounding of universal spans — dates and fiscal calendars, places, amounts and currencies — so "last month" or "over 2M CZK" is resolved by code, not by the LLM | live (client lineage) · extraction planned |
| `ttr-llm-gateway` | The single egress to LLM providers (OpenAI-compatible), centralizing keys, quotas, observability | live · extracted |
| `ttr-identity` | Identity mapping between the IdP and source-system users; optional role enrichment | live · extracted |

**The MCP surface is the consumption contract.** Veles and the query door are exposed as Model
Context Protocol servers (`ttr-meta-mcp`, `ttr-query-mcp`, `ttr-fuzzy-mcp`, `ttr-grounding-mcp`);
any MCP-capable agent — from any vendor, in any framework — can discover the model and query
through the governed path with per-user identity. This is how the standard is consumed in
practice, and why the reference agents (§6) are demonstrations rather than requirements.

**Observability end-to-end**: every service emits OpenTelemetry traces/metrics/logs; one user
question is one trace across agent → query door → translator → validator → worker (**live**).

**What Tatrman Server is not.** It executes interactive, validated, single-plan reads (and, by
design, the entry write path when that satellite lands). It does not schedule, orchestrate
multi-step programs, or move data between engines — those belong to the Tatrman Platform tier
(§7). The line is deliberate: everything needed to *prove* governed AI access is in the open
Server; everything needed to *run a data estate* is the commercial tier.

## 6. Kantheon — the intelligence suite

Kantheon is where non-determinism is allowed to live: the agents. Its design principle is
**thin agent, fat platform** — agents own conversation and intent; the Server owns joins,
security, dialects, and formatting. An agent here is a *persona over a model scope*, not a body
of data logic.

**Golem** is the analytical Q&A agent (**live** at the pilot, in Czech, in production testing).
One Golem template serves many instances: each instance is declared in a YAML file — its model
packages, its scope, its environments — and provisioning is automatic. The declaration is called
a **shem**, which is also the architecture explained in one image: one Golem, many shems; the
scroll you insert determines what the creature knows and may do. Golem's turn pipeline resolves
entities, grounds dates/places/amounts deterministically, then chooses the cheapest viable
answer path — a vetted pattern query first, LLM intent classification second, LLM free-SQL
(dry-run validated through the translator and validator before use) last — and asks a
clarifying question rather than guessing when confidence is low.

**Two open reference implementations** of Golem are planned: Python + LangGraph (the
pedagogical reference, aggressively documented) and Kotlin + Koog (the product lineage). Two
frameworks, one MCP contract — the point is to prove that the surface, not the agent, is the
product.

**Pythia** (deep analysis — long-running investigative work over the same governed path) and
**Iris** (the agent-facing chat/workspace frontend) are the next personas (**extracted shape /
planned**). Vertical agents, advanced analytical agents, and client-specific agent engineering
are the commercial and services territory of the suite.

## 7. Tatrman Platform — the operate tier (parked)

The commercial tier is fully designed (the 2026-07 platform design corpus: architecture,
contracts, phased plan) and deliberately **parked** until the core is published and adopted.
It adds what turns an installation into an operated estate: **`ttr-run`** — deployment envelopes
around compiled program bundles, executed as dependency waves with retries, resume, and typed
runtime parameters; **`ttr-schedule`** — time/event/manual triggers, with external orchestrators
(Airflow and kin) participating as alternative frontends through the same door; **Charon** —
declarative data movement between engines; **Perun** — the policy decision point: directory
sync, signed policy bundles bound to model names, fleet-wide audit; **continuous metadata
connectors** — scheduled harvest in, catalog/lineage export out (OpenMetadata-anchored); and
**Designer Extensions** — deployment, operations, and lineage panels on the open Designer's
extension surface. Its standing principles carry over: one path to data (everything passes the
validator), artifacts reviewable, "robots write through git."

## 8. The other satellites (parked by sequence, not by doubt)

- **The processing-language family (TTR-P)** — programs over the same models, in multiple
  surfaces from fluent code to business-readable text, compiled through the same plan hub to
  multi-engine bundles; with it, the graphical write-enabled designer and the optimizer work.
  Substantially designed (language effort + platform design); parked behind the core.
- **The entry/planning product** — structured data *entry* (budgeting-style: enter at summary
  level, spread down deterministically, preview, commit through the governed write path) over
  md-model cubes. Design effort **complete** (2026-07-10, `docs/ecosystem/platform/design/frontends/`); tier-split the same
  day: the **analysis surface** (semantic layer service, md→Cube/OSI projection, Designer
  analysis viewer) belongs to **Tatrman Server** (open, post-v1 arcs), while the **entry product
  `tatrman-entry`** is a commercial **Tatrman Platform** product — parked on its trigger (first
  planning workload).
- Both satellites consume the same standard and Server; neither changes the core's contracts —
  that is what makes parking them safe.

## 9. Provenance — the pilot

The ecosystem is not a paper design. Its read spine runs at a production pilot: a mid-size
Czech manufacturer/distributor, whose ERP (MS SQL) is modeled in TTR-M **by the client's own
business analysts** (in Czech, with a wiki and IDE tooling), served by the metadata service,
and queried through Golem instances that the client declares themselves. The deterministic
translation and structural security path is live; the two-LLM-call pattern with deterministic
grounding is live; the Czech language stack (morphology, entity fuzzing) is live. The pilot
began as a conventional data-platform project on Microsoft Fabric; conventional modeling
delivered no consumable benefit there, and the project pivoted to the architecture described
here — the clearest available evidence for §1's second half.

What the pilot does *not* yet exercise, stated plainly: the operate tier (scheduled programs,
movement, fleet policy) and the satellites. Claims about those rest on design, not production.

## 10. Licensing, trademark, and stewardship

- **The core is open source.** Planned license: **Apache-2.0** (the patent grant matters for
  language and infrastructure work) for Tatrman, Tatrman Server, and the reference agents.
  Published coordinates live under `org.tatrman:*`; the commercial tier under `cz.tatrman:*` —
  the group id makes the license boundary physical.
- **The operate tier is commercial** under the Tatrman Platform License (text to be drafted at
  repo bootstrap).
- **The trademark is the long-term moat**, as is normal for open standards: the code is free,
  the name is defended. Clearance and registration (CZ/EUTM, classes 9/42) are pending; the
  `tatrman.org` / `.com` / `.cz` domains are held.
- **Stewardship**: a single steward entity — **Collite** (est. 2009; designated 2026-07-10) —
  owns the trademark and the public infrastructure, publishes the artifacts, and maintains the
  standard and the open lineage;
  contributions arrive as reviewed proposals ("robots write through git" applies to humans
  too). Delivery partners build services businesses on the standard without owning it;
  no exclusivity is granted.

## 11. Current markers (as of 2026-07-10)

Target versions: **Tatrman, Tatrman Server, and Tatrman Platform each target 1.0.0**; the frontends surfaces are the **1.1.0** features (analysis → Server 1.1.0, entry → Platform 1.1.0, RO-23).

The near-term sequence, in order: the naming sweep (personas → functional names, before
anything publishes); the publish gates (the metadata and translator libraries plus the plan
protos to public coordinates); the grounding and resolver components joining the open lineage;
the pilot platform refactored to consume published open artifacts (engagement milestone:
November 2026); publication of Tatrman Server and the reference Golem as the ecosystem's public
debut. The satellites re-open on their own evidence: the processing family when program
workloads demand it, the entry product when a planning workload pulls it, the Platform tier
when the first operated estate does.
