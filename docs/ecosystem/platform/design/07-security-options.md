# Tatrman Platform — Security & Governance Options (workstream H)

> **[superseded 2026-07-10 · STRAT-3]** license is Apache-2.0 across the open surface; MIT mentions below are historical.

> Divergence catalogue for **H — identity, policy, enforcement, secrets, and trust** across the Platform (and what, if anything, security means standalone). Session opened 2026-07-09 (after the review-260708 incorporation; H carries the effort's largest rider inbox).
> Companions: [Control Room](./00-control-room.md) · [Design-Space Map](./01-design-space-map.md) · [`03-service-architecture-options.md`](./03-service-architecture-options.md) (C-5 placements) · [`05-scheduler-options.md`](./05-scheduler-options.md) (envelope/principal) · [`06-orchestration-options.md`](./06-orchestration-options.md) (secrets refs, plugin trust).
>
> **Scope guard:** H designs the *security model* — who is a principal, what a policy is, where enforcement sits, how secrets resolve, what is trusted. The boxes were placed by C (C-5-i/ii/iii); H fills them. Policy-authoring/admin UX = G; megaprovider IAM federation = I; quota *mechanics* = F (H owes only the governance flavor, F-5-δ, parked).

## Inherited constraints & the inbox

- **Q-2 verification (kantheon repo, 2026-07-08):** "the OPA thing" = **whois** (user/role directory + OPA bundle server: Keycloak/ERP sync → own PG; serves `UserRecord` lookups + policy bundles `roles.tar.gz`). **Argos** = the PlanNode *validator* (RLS predicate injection, column DENY/MASK, TopN, strict coercion, admin bypass) with an **in-process HOCON policy store** — OPA dropped at Stage 3.2. Identity resolves at the **theseus-mcp edge** (Keycloak JWT → `PipelineContext.auth_roles`); whois = optional **fail-closed** role enrichment.
- **Kantheon fork-§6 invariants (prior art, carried over):** bearer-only identity · enrichment-never-authority · fail-closed.
- **C-5:** Argos moves with the hall, LLM Guard = validator SPI (C-5-i=c, decided); **C-5-ii** whois-descendant = FI-4's "security server bound to metadata" (H shapes it); **C-5-iii** the Platform needs its own ingress identity — it cannot inherit theseus-mcp's.
- **B's IOU:** compile-time policy = **advisory-only** — B-3-α forces it (compile output must never depend on identity; B-4: the seam admits data + diagnostics, never identity). H confirms or the B stack re-opens.
- **F:** the envelope reserves a **principal field** (FQ-5: a scheduled run executes as whom?); F-5-δ identity-priced quotas parked on H.
- **E:** registration entries carry connection **refs**, never secrets (E-2-γ); resolution platform-side; plugin signing/trust (EQ-2, now incl. the determinism kit); **E-3-β governance line owed** (native-DAG bypasses the hall — H bounds it).
- **Review §3.6a:** the secret store behind connection refs is on the run path of *everything* (envelope bindings, Charon, harvest connectors) and has never been catalogued, even as a box.
- **Q-1 / LF-8:** does any security concept exist standalone? TTR-P Q8 prior art: artifacts run as a **trusted principal**, tripwire-verified.
- **P1/P2/P3 · A-α** (checks = MIT-side "compile"; enforcement = platform "operate") · **D-3** (toolchain-touched contracts ⇒ tatrman-owned MIT).
- **Hero, life 2:** "OPA authorizes who may run/see what"; life 3: delegated runs must not become a policy hole.

---

## H-1 · Policy representation & source of truth

**Question:** what *is* a policy — in what language, living where, versioned how?

- **H-1-α · Platform config.** Policies are rows/admin-UI state in the security server's DB; no reviewable artifact.
  - *Buys:* fastest UX; no format to design.
  - *Costs:* invisible authority (P3 alarm — who-may-do-what is exactly the content that wants review); no diff, no audit-by-git; unexportable, so Q-1's answer is forced to "standalone has nothing."
- **H-1-β · Policy as TTR-family documents.** A `policy`-kind document: authored, repo-native, versioned like worlds; the platform enforces it; standalone can lint against it (T6's declare/verify split applied to authority).
  - *Buys:* text-is-canonical consistency; dogfooding; review flow for authority changes; MIT linter is A-α-legal.
  - *Costs:* a policy language is a *big* language-design surface (conditions, attributes, RLS predicates) — the toolchain isn't there; D-3-γ (contracts-as-TTR) was parked for the same reason; policy churn cadence ≠ model churn cadence.
- **H-1-γ · Adopt-external: Rego/OPA bundles bound to metadata objects.** Policies are Rego (or Rego + a thin data schema) keyed by metadata qnames (models, worlds, programs, engine instances); the security server serves **content-hashed, versioned bundles**; whois's bundle-server muscle is the transplant seed. TTR never models policy.
  - *Buys:* mature engine + ecosystem (FI-4 says "the OPA machinery" verbatim); policy-as-code review flow for free if bundles live in a git repo; content-hash pinning gives canon discipline (BQ-3's temperament vocabulary applies: policy is *canon*, not observation); RLS predicate content can stay structured data beside Rego (Argos's HOCON content migrates, → H-7).
  - *Costs:* two authoring worlds (TTR for models, Rego for authority); Rego expertise is real ops surface; binding-by-qname needs a discipline so policy doesn't dangle when models rename (lineage of qnames — a metadata-server job).
- **H-1-δ · Weird: policy as world content.** Authority entries live in the platform-managed world (E-2-γ echo — "the world describes the surroundings, including who may touch them").
  - *Buys:* one registry concept stretched further; compiler could see policy through the seam (advisory lint natively).
  - *Costs:* worlds describe *facts about engines and data*, not authority; access churn would churn a canon document kind (the B-2-α disease, worse); the seam explicitly bans identity — smuggling authority *data* in invites the confusion B-4 drew the line against. Catalogued to reject loudly.

**Lean: γ, with δ's one good idea kept as a pointer** (policy *binds to* world/metadata qnames; it never lives in the world) and β parked as the v2 dogfooding arc beside D-3-γ. Policy = canon: bundles content-hashed, pinned, reviewable.

**RESOLVED 2026-07-09 → H-1 = γ + the SECURITY-BLOCK SUGAR (Bora's addition): a `security` block in TTR documents — precedented by the E-R→DB shortcut mapping and the `semantics` block — is an MIT toolchain front-end that GENERATES standard policy fragments** ("entity `accounts`: accounting reads it" · "entity `salaries`: HR reads it" · "this column is PII: mask"). Common cases get sugar; everything else stays hand-written Rego. Pins ratified with it: **(1)** generation is one-way and deterministic (generated fragments never hand-edited; B-3-α discipline extends to the policy compiler); composition with hand Rego = **deny-overrides** (sugar grants, hand Rego can always take away); **(2)** the block never alters emitted plans (enforcement stays at the hall — principal-dependent; compile stays advisory) and is **T6-fingerprint-neutral** (access changes must not churn world verification); **(3)** the workflow line: security block = the *data owner's* declaration, hand Rego = the *org's* policy; **(4)** v1 sugar vocabulary = read grants + column masks/classifications + ownership; **row-level predicates stay Rego-side** (sugar-for-rows = a later, conscious extension). Grant-reference discipline (role names verbatim vs classification indirection) = **HQ-1, settles with H-2**. Grammar surface = a TTR-M amendment → the queued amendment sweep; generator + block grammar = toolchain-touched ⇒ tatrman-owned MIT (D-3); bundle *build* (composition + signing) = platform (H-4). β thereby partially un-parks as sugar-over-γ instead of policy-as-TTR-truth. Rejected: α admin-UI config (invisible authority); δ policy-in-world (authority in a facts document; B-4 confusion); β-as-truth (policy language surface too big — its usable half survives as this sugar).

## H-2 · Identity: ingress, principals, and the scheduled-run question

**Question:** who mints a principal, from what, at which edge — for humans, machines, and scheduled runs?

**H-2-i · Ingress (C-5-iii).**
- *α · IdP-verified JWT at every door, via one shared ingress module.* Keycloak (or any OIDC IdP) is the only identity authority; each service (doors, metadata server, Designer backend) verifies bearer tokens with the same library; **bearer-only, enrichment-never-authority, fail-closed** carried over verbatim.
- *β · A gateway service in front.* One ingress process terminates auth, forwards asserted identity. *Costs:* asserted-identity headers are exactly what bearer-only forbids; the gateway becomes the thing to steal; another spine service in v1.
- *γ · Platform-native account store.* The platform mints its own users. *Costs:* a second identity truth beside the org's IdP — rejected-shaped on arrival (whois already learned this: it *syncs from* Keycloak/ERP, never owns).
- **Lean: α** — module, not gateway; the IdP stays the only authority.

**H-2-ii · Machine callers** (registered orchestrators calling the door, harvest connectors, kantheon's query-door adoption): OIDC client-credentials service principals from the same IdP; no API-key side channel. *Lean-shaped, little to fork.*

**H-2-iii · Scheduled-run principal (FQ-5).**
- *α · The deploying user.* Simple; **offboarding hazard** (person leaves, nightly dies or — worse — keeps running as a ghost) and privilege drift.
- *β · An envelope-named service principal, explicit and required.* P3-clean: the envelope states the run-as identity; deploy-time policy checks the deployer may *use* that principal (a grant, not an ambient right).
- *γ · Team/owner principal.* β with a default: programs deploy under a team's service principal unless overridden.
- *δ · Weird: the program IS the principal.* Each deployed program gets its own identity (SPIFFE-flavored); grants attach to programs. *Buys:* least-privilege by construction, beautiful audit. *Costs:* principal explosion; every grant flow doubled (who may deploy the program × what the program may touch).
- **Lean: β with γ's default UX** — explicit service principal in the envelope, defaulted to the owning team's; δ recorded as the least-privilege growth direction.

**RESOLVED 2026-07-09 → H-2 ratified: (i) = α** (IdP-verified JWT at every door via one shared ingress module; bearer-only / enrichment-never-authority / fail-closed carried verbatim; no gateway, no platform accounts); **(ii)** machine callers = client-credentials service principals from the same IdP, no API-key side channel; **(iii) = β for v1 (Bora):** a scheduled run executes as an **explicit, envelope-named service principal — required, no ambient default** (P3); deploy-time policy checks the deployer holds a *grant to use* that principal; γ's team-default = a later UX convenience, δ program-as-principal = the recorded least-privilege growth direction. **Discharges FQ-5** (the envelope's reserved field is now specified). **HQ-1 settled: (b)+(a)** — classifications (PII, domains) are the security block's native vocabulary (model facts, owner-declared; an org-level mapping binds classifications→roles); verbatim role names allowed for grants, **validated fail-closed at bundle build**, advisory lint standalone. · Rejected: (i)-β gateway (asserted-identity headers); (i)-γ platform accounts (second identity truth); (iii)-α deploying user (offboarding ghosts).

## H-3 · Enforcement-point map — what is a PEP, and which ship in v1

**Question:** the candidate enforcement points: **catalog reads** (who sees which models/worlds/lineage) · **deploy** (who may create/update envelopes) · **run** (who may trigger; the door checks) · **data plane** (the hall: Argos RLS/DENY/MASK — the only point that touches rows) · **Designer writes** · **compile-time** (advisory).

- **H-3-α · Minimal-spine v1.** Deploy/run authz at the doors + the transplanted Argos data plane + coarse catalog visibility (per-project/all-org read); Designer writes ride the git permission model (writes are commits — G-γ pending); compile-time = advisory diagnostics only.
  - *Buys:* every *effectful* path is guarded (doors + hall = the only ways to touch data or spend compute); smallest honest v1; matches the C-1 transplant discipline (Argos arrives working).
  - *Costs:* catalog is org-visible (fine single-org, v1 anchor); fine-grained metadata ACLs deferred.
- **H-3-β · Full metadata-object ACLs day one.** Per-object read/write grants across the catalog.
  - *Costs:* the heaviest possible start; single-org v1 doesn't need it; multi-tenant is parked. Catalogued as the growth shape, not the start.
- **H-3-γ · Weird: data-plane only.** Only the hall enforces; doors/catalog open inside the org ("the database is the moat").
  - *Costs:* "who may run programs" (compute spend, schedule sabotage) is a real authority question the hall can't see; deploy becomes unaudited. Catalogued to mark the floor.

**Lean: α**, and with it **B's IOU formally dischargeable**: compile-time policy = advisory-only, because B-3-α makes identity-dependent compile output illegal by construction — H confirms the line at convergence, not just by lean.

**RESOLVED 2026-07-09 → H-3 = α (minimal-spine PEP map for v1):** deploy/run authz at the doors · transplanted Argos data plane · coarse catalog visibility (per-project/org read) · Designer writes ride the git permission model (HQ-3 stays reserved pending G) · **compile-time policy = ADVISORY-ONLY — B's last IOU formally discharged** (B-3-α forces it: compile output may never depend on identity; the B stack's confirmation ledger is now clear). β = the recorded growth shape (fine-grained metadata ACLs arrive with multi-tenant/H-3 revisit); γ catalogued as the floor. · Rejected: β day one (heaviest start, unneeded single-org); γ (deploy/compute authority unguarded).

## H-4 · The security server (whois-descendant, C-5-ii)

**Question:** what shape is FI-4's "security server bound to the metadata"?

- **H-4-α · Transplant whois as-is, rekey.** Directory (IdP/ERP sync) + OPA bundle server; policy bundles gain metadata-qname keying (H-1-γ's binding).
  - *Buys:* exists, works, fail-closed discipline built in; the C-1 α-leaf pattern.
  - *Costs:* whois's contract is kantheon-shaped (role enrichment for Argos); the platform needs decision-serving for doors/catalog too — transplant alone under-serves.
- **H-4-β · Fold into the metadata server.** Policy/directory as another organ of C-2-γ.
  - *Costs:* C-2-γ was *just* scoped around four consumer classes; "authority" is none of them; availability coupling (catalog down ⇒ authz down) — the F-6-α disease. Rejected-shaped.
- **H-4-γ · PDP service: whois-descendant grows into the platform's policy decision point.** Directory + bundle serving + a decision surface; **PEPs (doors, hall, metadata server, Designer backend) pull signed, content-hashed bundles and evaluate locally** (OPA-native distribution) — decisions are local, the PDP is not on the hot path; bundle expiry windows give fail-closed semantics without availability coupling.
  - *Buys:* α's transplant is the starting organ (C-1 discipline); local evaluation = security server down ≠ hall down (within the expiry window); bundle hashes make policy state auditable ("which policy version was in force at run R" → run store records the bundle hash).
  - *Costs:* bundle lifecycle machinery (build, sign, distribute, expire); per-PEP embedded evaluation (OPA sidecar/lib per service).
- **H-4-δ · Weird: no server — policy through the seam.** Bundles distributed as canon via the ordinary snapshot/lock machinery; the directory outsourced entirely to the IdP.
  - *Buys:* radical reuse; policy pinned in `ttr.lock` like plugins.
  - *Costs:* the *seam* is a compile-time object — runtime PEPs (hall, doors) don't read locks; access revocation latency becomes "when did they last fetch" (unacceptable for authority); the directory can't be outsourced entirely (ERP attributes, service-principal grants). Catalogued for its one insight: *policy versions deserve pinning* — which H-4-γ's bundle hashes deliver anyway.

**Lean: γ starting from α's transplant.** Run records cite the policy-bundle hash in force (audit joins provenance — the F-7 pattern applied to authority).

**RESOLVED 2026-07-09 → H-4 = γ, starting from α's transplant** (move-adopt-migrate, the D-5 discipline inside H): the whois-descendant = directory (IdP/ERP sync) + **PDP** — builds, signs, and serves content-hashed policy bundles; **PEPs (doors, hall, metadata server, Designer backend) pull bundles and evaluate locally**; fail-closed at bundle expiry; **run records cite the bundle hash in force** (F-6-β schema gains the field). The security-block pipeline homes here: TTR sugar → generated fragments → **PDP bundle build** (compose with hand Rego under deny-overrides, sign, hash) → PEPs. Fills **C-5-ii**. · Rejected: α-alone (kantheon-shaped contract under-serves doors/catalog); β metadata-server organ (fifth consumer class; availability coupling — the F-6-α disease); δ policy-through-the-seam (runtime PEPs don't read locks; revocation latency unacceptable — its pinning insight survives as γ's hashes).

## H-5 · Secrets & connection-ref resolution (review §3.6a — the unshaped box)

**Question:** connection refs (`E-2-γ`, envelope bindings, Charon named connections, harvest connector auth) resolve to secret material — where does the material live, and who touches it?

- **H-5-α · Platform-native secret store.** An encrypted store inside the platform (security or metadata server organ).
  - *Buys:* self-contained; no external dependency for small deployments.
  - *Costs:* we own key management, rotation, audit — the classic build-a-vault trap; enterprise customers already have Vault/KMS and will demand integration anyway.
- **H-5-β · Secret-store SPI over external stores.** Refs are URIs (`secret://<store>/<path>`); bindings for Vault, K8s Secrets, cloud secret managers; **K8s Secrets = the default binding** (the platform ships on K8s per D-4).
  - *Buys:* the platform never owns key management; deployment-appropriate (small = K8s Secrets, enterprise = Vault); the SPI is the same socket-and-plug pattern as the validator SPI (C-5-i) and connector SPI (E-4).
  - *Costs:* an SPI to version; per-store auth bootstrapping (the "secret zero" problem is the deployment's, not ours — say so).
- **H-5-γ · No store: dispatch-time env injection only.** Refs resolve at dispatch; material is injected into worker/Charon process env (`TTR_CONN_*` generalized); the platform holds nothing at rest.
  - *Buys:* the standalone idiom platform-ized; smallest surface.
  - *Costs:* *something* must still hold the material to inject it — γ is a delivery mechanism wearing a storage costume.
- **H-5-δ · Weird: sealed per-worker config.** Workers hold their own connection material (config/CSI-mounted); the platform only names connections, never resolves them.
  - *Buys:* platform never touches secrets at all. *Costs:* per-worker config drift; connection governance (E-g's central named connections, Charon's pairs) dies; adding a connection = touching worker deployments.

**Lean: β for storage + γ as delivery, explicitly composed:** refs resolve at dispatch through the secret-store SPI; material is injected into the executing process env and **never enters the envelope, the run store, logs, or any artifact** (a stated invariant, tripwire-tested). Standalone parity: `TTR_CONN_*` unchanged (the ref *convention* is shared; only the resolver differs — the B-1 pattern, applied to secrets).

### H-5 dive · The full picture (2026-07-09)

**The problem, precisely.** A compiled program never contains credentials (F-lite settled it: the bundle names *connections*; bash-land users supply material via `TTR_CONN_<NAME>`, fail-fast if unset — the artifact stays secret-free and reviewable). The platform inherits the shape twice: registration entries carry connection **refs** only (E-2-γ, decided) and the envelope binds program connections to registered ones (F-2-β) — still refs. So at dispatch, *somebody* must turn `warehouse_pg` into a URI-with-password and hand it to the worker/Charon process. The fork is really **four questions**: (1) **storage** — where does material live at rest? (2) **resolution** — who turns ref → material, when? (3) **delivery** — how does material reach the executing process? (4) **invariants** — what may *never* contain material? The options differ mainly on (1); β and γ compose because they answer different questions.

**Why α fails:** owning storage = owning key management (master-key custody, rotation, re-encryption, audit, HSM questions — the build-a-vault trap), *plus* **secret duplication**: the org's PG password already lives in their Vault; a copy in us drifts on rotation (Vault rotates Tuesday, our copy is stale, the nightly dies at 3am). Enterprise security teams demand integration with *their* store anyway — α builds the SPI later regardless, after building a vault first.

**Why β for storage:** one small interface (`resolve(ref) → material`); refs are URIs (`secret://k8s/arges-prod`, `secret://vault/tatrman/warehouse_pg`); bindings for **K8s Secrets (default — D-4 ships on K8s, so a store is present in every deployment)**, Vault, cloud managers. The platform never owns key management; dispatch-time resolution picks up rotation automatically (no stale copies); third instance of the socket-and-plug idiom (validator SPI C-5-i, connector SPI E-4). Cost stated honestly: **"secret zero"** (how the platform authenticates to the store) is the *deployment's* configuration — K8s service account / workload identity / AppRole — documented, not solved by us.

**Why γ for delivery (and only delivery):** γ is not a storage answer — material must come from somewhere; alone it's a storage costume over nothing. As delivery it is *literally F-lite's contract*: island payloads already speak env-var connections, so platform-executed and bash-executed islands receive credentials identically — parity, P1.

**Why δ fails:** N-workers × M-connections ops matrix; adding a connection = an infra rollout, not an admin action; Charon needs source×target pairs configured everywhere; and central connection *governance* — a policy question H-3 owns — is unenforceable over credentials whose existence the platform can't see.

**The composed shape, rendered (hero life 2):**

1. Registration (E-2-γ): world entry `arges_prod`, `ref: secret://k8s/arges-prod` — refs only.
2. Deploy (F-2-β): the envelope binds the program's `warehouse_pg` → `arges_prod` — still refs.
3. Nightly fires → door authorizes under the envelope's service principal (H-2/H-3) → executor walks the wave; per island: **resolve via the SPI at dispatch time**, inject `TTR_CONN_*` into that worker invocation, worker connects, island completes, material evaporates with the process. **Only the connections that island's manifest declares are injected** — least exposure by construction.
4. Charon transfer edges: same resolution, per transfer, for the source×target pair.
5. **The never-at-rest invariant:** material never appears in the envelope, run store, logs, lineage events, or any artifact; run records carry ref *names* (+ the policy-bundle hash, H-4). **Tripwire-tested mechanically:** CI plants canary credentials and asserts their bytes appear nowhere downstream — a check, not a promise.
6. **Failure semantics:** store unreachable at dispatch ⇒ the island fails **pre-flight**, exactly like an unset `TTR_CONN_*` in bash-land — the same failure shape in both modes (P3); retryable per manifest.
7. **No-secret-API consequence (design content):** the platform exposes **no endpoint that returns secret material** — resolution is a side effect of dispatch only; there is nothing for a curious client to call.
8. **Life 1 untouched:** the standalone user sets `TTR_CONN_*` themselves — shared convention, different resolver (the B-1 one-contract-two-bindings pattern applied to secrets). **Life 3:** a door-calling Airflow DAG never sees credentials (it holds only its principal token); a standalone-world native DAG uses the user's own engine credentials — credential-bounded per H-8's line.

**Implementation caveat, on record:** env vars are readable via `/proc` by same-user processes and can leak through crash dumps/child processes; file-mount or in-memory delivery are hardening refinements *within* the γ-delivery choice (planning-level), not a different fork.

**RESOLVED 2026-07-09 → H-5 = β storage + γ delivery, composed, with the never-at-rest invariant and the no-secret-API rule.** Secret-store SPI (K8s Secrets default binding; Vault/cloud managers as bindings); refs resolve at dispatch; injection per island/transfer, manifest-scoped; canary tripwire in CI; secret-zero = deployment configuration, documented. · Rejected: α platform-native store (build-a-vault trap; rotation drift via duplication); γ-alone (delivery wearing a storage costume); δ sealed per-worker (governance dies; connections become infra rollouts).

## H-6 · Trust: plugins, bundles, envelopes (EQ-2's H-side)

- **H-6-α · Checksums + lock pinning only** (exists: sha256s in bundles, identities in `ttr.lock`). Integrity against accident, not adversaries.
- **H-6-β · α + signature verification + the determinism kit.** Emit plugins verified against publisher signatures (Maven-PGP-grade; verify-if-signed in v1, require-signed as policy knob); the **double-compile byte-compare kit** (review §3.3) ships in `ttrp-conform` and is a certification requirement for third-party plugins; policy bundles signed by the PDP (H-4-γ needs this anyway).
- **H-6-γ · Full supply-chain attestation** (SLSA-ish provenance for every artifact). Demand-driven; enterprise-sales-shaped; not v1.

**Lean: β.** The trust roots are two: the IdP (identity) and publisher keys (artifacts) — name both in the design.

**RESOLVED 2026-07-09 → H-6 = β:** checksums + lock pinning (existing) **+ publisher-signature verification for emit plugins** (verify-if-signed v1; require-signed = a deployment policy knob) **+ the determinism kit in `ttrp-conform`** (double-compile byte-compare; certification requirement for third-party plugins — discharges the EQ-2 widening) **+ PDP-signed policy bundles** (H-4-γ requires them anyway). **Two trust roots named: the IdP (identity) · publisher keys (artifacts).** γ SLSA-grade attestation = demand-driven, post-v1. · Rejected: α-only (integrity against accident, not adversaries — insufficient once commercial plugins exist); γ-now (enterprise-sales-shaped, not v1).

## H-7 · Data-plane policy content (RLS scope, Argos's migration)

**Question:** Argos enforces RLS predicate injection + column DENY/MASK from an in-process HOCON store. Platform-side, where does that *content* live and is RLS in v1?

- **H-7-α · Transplant as-is.** HOCON in-process, moved with Argos; H-1-γ arrives later.
  - *Buys:* zero-risk transplant (C-1's α-leaf verbatim); RLS works day one. *Costs:* policy content invisible to the security server; two policy stores (HOCON + bundles) the moment H-1-γ lands.
- **H-7-β · Rekey into H-1-γ bundles at transplant time.** Argos's policy store reads from the PDP's bundles (RLS predicates + DENY/MASK as structured data beside Rego), keyed by metadata qnames.
  - *Buys:* one policy discipline from the start; "which policy was in force" answerable for data-plane decisions too. *Costs:* the transplant stops being verbatim (a migration inside a move — mini-arc discipline strained).
- **H-7-γ · RLS out of platform v1.** Doors + catalog only; data-plane policy stays kantheon-side until later.
  - *Costs:* the hall would run *without* its validator's policy content — hero life 2 names "OPA authorizes who may run/see what"; and Argos moves with the hall anyway (C-5-i decided) — shipping it lobotomized is more work, not less. Rejected-shaped.

**Lean: α-then-β as strangler steps** (transplant working, then rekey the store in a follow-up arc — move, adopt, *then* migrate; the D-5 discipline inside H). RLS **is** v1 (it arrives with Argos).

**RESOLVED 2026-07-09 → H-7 = α-then-β strangler:** Argos transplants with its in-process HOCON store **verbatim** (RLS/DENY/MASK working day one — RLS is v1 by construction); a follow-up arc rekeys the store to read from the PDP's bundles (H-1-γ content: RLS predicates + masks as structured data beside Rego, keyed by metadata qnames) — after which data-plane decisions also answer "which policy version was in force" via the bundle hash. **HQ-5** (HOCON→bundle migration: mechanical translation vs re-authoring) = the β-step's work item. Security-block sugar (H-1) can generate mask/classification content into the same bundles once β lands. · Rejected: β-at-transplant (migration inside a move — strains the mini-arc discipline); γ RLS-out-of-v1 (Argos moves anyway per C-5-i; lobotomizing it is more work; hero life 2 dies).

## H-8 · Standalone security meaning (Q-1 / LF-8) + the bypass line

- **H-8-α · None.** Security is 100% platform; the word doesn't appear in OSS docs.
- **H-8-β · Advisory policy lint.** The MIT toolchain lints a program against an exported/pinned policy bundle ("this program reads `hr.salaries`; principal class X would be denied") — a *check* under A-α, diagnostics through the ordinary channel (B-4-legal: data + diagnostics).
- **H-8-γ · Trusted-principal stance.** TTR-P Q8 verbatim: standalone artifacts run as the invoking principal with whatever credentials that principal holds (`TTR_CONN_*`); integrity = checksums/fingerprints (tripwire); *enforcement* is inherently "operate" and therefore platform.
- **Lean: γ now, β cheap-once-H-1-γ exists** (a bundle is a file; the linter is a check) — Q-1's answer-shape: *standalone security = integrity + (optionally) advisory lint; enforcement is the Platform's*.

**The E-3-β bypass line (owed to E), tabled for ratification:** *platform-governed engine credentials resolve platform-side only (H-5); a standalone native-DAG run can only touch engines with credentials the user already holds — the bypass is credential-bounded, not policy-bounded.* Going around the hall is possible exactly when it was already possible without us; the platform adds enforcement to *its* credentials, not a new hole.

**RESOLVED 2026-07-09 → H-8 = γ now + β cheap-once-H-1 exists.** **Q-1 answered / LF-8 resolved:** *standalone security = integrity (checksums/fingerprints, trusted-principal stance per TTR-P Q8) + optional advisory policy lint against an exported/pinned bundle; ENFORCEMENT is inherently "operate" and therefore the Platform's.* The H-1 security block makes β materially richer (generator and linter share the same MIT machinery). **The E-3-β bypass line ratified as tabled** — credential-bounded, not policy-bounded; E's owed governance line discharged. · Rejected: α zero-concept (needlessly weak — β is nearly free under H-1); β-as-enforcement (a lint is not a guard; A-α draws the line).

---

## Hero rendering ("one program, three lives")

- **Life 1 (standalone):** H-8-γ — runs as you, with your `TTR_CONN_*`; checksums verify integrity; optionally lint against the org's exported policy bundle. Nothing MIT-side gates on the Platform (P1).
- **Life 2 (platform):** deploy requires the `deploy` grant; the envelope names the team service principal (H-2-iii-β/γ); the nightly trigger fires the door, which authorizes `run` against the PDP's current bundle (hash recorded in the run record); per island, Argos injects RLS/masks from the same bundle line (H-7-β eventually); Charon and workers receive connection material by dispatch-time injection (H-5) — secrets never at rest in envelope/logs; the Designer shows the run to those with catalog visibility (H-3-α).
- **Life 3 (federated):** Airflow's door-calling DAG authenticates as a client-credentials service principal (H-2-ii); the hall enforces exactly as in life 2 (delegation is a frontend, not a bypass); the harvest connector authenticates platform-side; a standalone-world Kestra native DAG is credential-bounded per the E-3-β line.

## Cross-links out

- **→ B:** the IOU discharges at convergence — compile-time policy = advisory-only (H-3 lean; forced by B-3-α/B-4).
- **→ F:** FQ-5 answered by H-2-iii; run records cite the policy-bundle hash (F-6-β schema gains a field); F-5-δ stays parked until multi-tenant.
- **→ E:** EQ-2 lands in H-6-β (signatures + determinism kit); the E-3-β bypass line above; harvest-connector auth = H-2-ii principals.
- **→ C:** C-5-ii/iii filled (H-4, H-2-i); the metadata server is a PEP for catalog reads (H-3) and records qname lineage that H-1-γ's bindings depend on.
- **→ G:** Designer-write enforcement rides the git model under G-γ (HQ-3 if G lands elsewhere); admin/policy-authoring UX.
- **→ I:** IdP is the only identity authority — megaprovider IAM *federates into* it (I's LF-7 sibling, not H's problem).
- **→ K:** the platform world's edit rights (who may register engines) = an H-3 policy object; K's home-repo choice decides whether that's git perms or a PEP.
- **→ J:** names — the PDP/security server, the policy-bundle convention, principal naming.

## Open questions (H-local)

- **HQ-1 · Role/attribute model:** mirror kantheon's Keycloak-role vocabulary, or metadata-object-scoped grants (RBAC now, ReBAC-lite later)? Shapes H-1-γ's data schema.
- **HQ-2 · Audit log:** where does the authority audit trail live (run store? PDP? metadata server?) and at what grain? Sibling of the parked observability item — decide together.
- **HQ-3 · Designer writes:** git permissions (G-γ) vs a platform PEP — blocked on G's write-model fork; H reserves the PEP option.
- **HQ-4 · Query-door/agent identity:** kantheon agents adopt the door (D-5 ⑥) — do their Keycloak roles pass through verbatim, or map into platform grants? (theseus-mcp edge inheritance, made explicit.)
- **HQ-5 · HOCON→bundle migration path** for Argos's existing RLS/mask content (H-7's α→β arc): mechanical translation or re-authoring?

## Convergence status

**🟢 H IS CONVERGED (2026-07-09)** — **H-1** γ Rego/OPA bundles bound to metadata qnames **+ the TTR `security`-block sugar** (MIT generator; owner-declaration vs org-policy line; deny-overrides; fingerprint-neutral) · **H-2** α ingress module (bearer-only/fail-closed) + client-credentials machine principals + **explicit envelope-named service principal, required (FQ-5 discharged)**; HQ-1 = classifications native, verbatim roles fail-closed-validated · **H-3** α minimal-spine PEPs; **compile-time = advisory-only (B's ledger clear)** · **H-4** γ PDP from the whois transplant; PEPs evaluate locally; run records cite bundle hash (**C-5-ii filled**) · **H-5** β storage SPI + γ dispatch-injection; never-at-rest invariant; no-secret-API rule (see dive) · **H-6** β signatures + determinism kit (**EQ-2 widening discharged**); two trust roots named · **H-7** α-then-β; **RLS is v1** · **H-8** γ+β; **Q-1 answered, LF-8 resolved**; E-3-β bypass line ratified. **Riders out:** HQ-2 (audit-log home — decide with the parked observability item) · HQ-3 (Designer-write PEP — waits on G's write fork) · HQ-4 (agent-role mapping at query-door adoption — D-5 ⑥ work item) · HQ-5 (HOCON→bundle migration — the H-7 β-step) · security-block grammar → TTR-P/TTR-M amendment sweep · F-6-β run-record schema gains the bundle-hash field (F planning). F-5-δ stays parked (multi-tenant).
