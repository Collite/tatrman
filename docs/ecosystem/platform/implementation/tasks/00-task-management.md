# PL — Task Management (overall tracker)

> Structure: **Plan → Phase → Stage**; each stage is a mini task list of 6–8 checkboxed tasks in its phase file. Source of truth: [`../plan.md`](../plan.md) (phases, pre-flight, DoD) · [`../../design/contracts.md`](../../design/contracts.md) (every schema/API cited as §N) · [`../../design/architecture.md`](../../design/architecture.md). Task lists exist for **every phase, PL-P0 → PL-P7**. ⚠ Everything after PL-P1 was pre-generated 2026-07-09 at Bora's request, ahead of the phase reviews the plan originally gated them on — **before starting any phase, re-validate its list against ALL preceding `/review` outcomes**; anything a review changed wins over a pre-generated list, and later-phase lists (P4+) should be expected to need amendments, not just re-reading.

## Rules for the coder (read before every session)

1. **Check each checkbox the moment its task is done** — in the stage file AND the stage row here. Never batch checkbox updates to the end of a session.
2. **TDD is mandatory.** Test tasks come *first* in every stage — write them, watch them fail, then implement. Do not reorder.
3. Run the stage's **Verify** command line before marking the stage's last task done.
4. Test tiers: *unit* (per class) and *component* (inter-class, in-package, fixture-backed) only. **No full E2E in these stages** — integration testing has a separate flow; the Q-6 acceptance run is plan-level (PL-P7).
5. Repo conventions apply per repo: `tatrman` — CLAUDE.md (ESM, `.js` imports, grammar regeneration procedure, no `any`); `tatrman-platform` — kantheon-inherited patterns via its own `shared/` (see Library reference card). Commit style: **`PL-P<n>.S<m>: <description>`**. One repo per commit; never mix repos in one logical change without cross-referencing commit messages.
6. Missing dependency from a later stage/phase? Stub it behind the named seam and leave a `// PL-P<n>: <what arrives>` marker comment — never a silent workaround.
7. Golden hashes and golden bytes (archive determinism, mode-drift, OM payloads): recompute by hand/fixture first, then update the test — never the other way round.
8. **P2 check before every push in `tatrman-platform`:** no dependency on kantheon artifacts, `org.tatrman:*` only as published versions (the S5 dependency-rule test enforces it — keep it green, never disable it).

## Pre-flight gate (verify before PL-P0)

- [ ] Strangler ① green: `org.tatrman:ttr-metadata`(+`-git`) and `org.tatrman:ttr-plan-proto`/`ttr-translator` published and consumed (M-plan + A-plan DONE).
- [ ] TTR-P toolchain emits F-lite bundles passing `ttrp-conform` (manifest v1 works — PL-P0 graduates a *working* contract).
- [ ] `docs/ecosystem/platform/design/contracts.md` ⚑ flags reviewed by Bora (PL-P0.S2 ratifies).
- [ ] Kantheon donor commit pinned for the ② transplants (`pinakes`, shared libs).

## Phase & stage tracker

| Phase | Stage | Mini task list | Done |
|---|---|---|---|
| **PL-P0 · Contracts + S1 batch** | S1 Record the amendment batch | [`tasks-pl-p0.md#s1`](./tasks-pl-p0.md#s1) | [ ] |
| | S2 Ratify contracts + repo lines | [`tasks-pl-p0.md#s2`](./tasks-pl-p0.md#s2) | [ ] |
| **PL-P1 · ② seam client** | S1 Snapshot archive + cache | [`tasks-pl-p1-seam.md#s1`](./tasks-pl-p1-seam.md#s1) | [ ] |
| | S2 `ttr.lock` + fetch + server source | [`tasks-pl-p1-seam.md#s2`](./tasks-pl-p1-seam.md#s2) | [ ] |
| | S3 Stats + compile record + manifest v2 + mode-drift | [`tasks-pl-p1-seam.md#s3`](./tasks-pl-p1-seam.md#s3) | [ ] |
| | S4 K `extends` grammar + composition + import-schema | [`tasks-pl-p1-seam.md#s4`](./tasks-pl-p1-seam.md#s4) | [ ] |
| **PL-P1 · ② platform** | S5 `tatrman-platform` + `tatry` bootstrap | [`tasks-pl-p1-platform.md#s5`](./tasks-pl-p1-platform.md#s5) | [ ] |
| | S6 Veles core (snapshots, stats, ingress) | [`tasks-pl-p1-platform.md#s6`](./tasks-pl-p1-platform.md#s6) | [ ] |
| | S7 Veles reads + ingest skeleton + deploy | [`tasks-pl-p1-platform.md#s7`](./tasks-pl-p1-platform.md#s7) | [ ] |
| **PL-P1 · ② Designer** | S8 Veles backend + Designer Extensions | [`tasks-pl-p1-designer.md#s8`](./tasks-pl-p1-designer.md#s8) | [ ] |
| | S9 Program graphs + OpenMetadata export | [`tasks-pl-p1-designer.md#s9`](./tasks-pl-p1-designer.md#s9) | [ ] |
| **PL-P2 · ③ toolchain** | S1 Params + on-failure (grammar → manifest) | [`tasks-pl-p2-toolchain.md#s1`](./tasks-pl-p2-toolchain.md#s1) | [ ] |
| **PL-P2 · ③ Radegast** | S2 Run store + event outbox | [`tasks-pl-p2-radegast.md#s2`](./tasks-pl-p2-radegast.md#s2) | [ ] |
| | S3 Envelope store + deploy validation | [`tasks-pl-p2-radegast.md#s3`](./tasks-pl-p2-radegast.md#s3) | [ ] |
| | S4 Executor wave walker (F-4) | [`tasks-pl-p2-radegast.md#s4`](./tasks-pl-p2-radegast.md#s4) | [ ] |
| **PL-P2 · ③ hall** | S5 Kyklop + workers transplant + CQ-4 + F-5 | [`tasks-pl-p2-hall.md#s5`](./tasks-pl-p2-hall.md#s5) | [ ] |
| | S6 Secret SPI + dispatch injection + canary | [`tasks-pl-p2-hall.md#s6`](./tasks-pl-p2-hall.md#s6) | [ ] |
| **PL-P2 · ③ door** | S7 Door API + `ttr deploy` | [`tasks-pl-p2-door.md#s7`](./tasks-pl-p2-door.md#s7) | [ ] |
| | S8 Designer run/lineage panels + ③ roster + DoD | [`tasks-pl-p2-door.md#s8`](./tasks-pl-p2-door.md#s8) | [ ] |
| **PL-P3 · ④ Charon** | S1 Charon transplant | [`tasks-pl-p3.md#s1`](./tasks-pl-p3.md#s1) | [ ] |
| | S2 Transfers via Charon + FQ-6 lifecycle + DoD | [`tasks-pl-p3.md#s2`](./tasks-pl-p3.md#s2) | [ ] |
| **PL-P4 · ⑤ hall** | S1 Argos transplant + hall wiring | [`tasks-pl-p4-hall.md#s1`](./tasks-pl-p4-hall.md#s1) | [ ] |
| | S2 Validator SPI + LLM-Guard plugin | [`tasks-pl-p4-hall.md#s2`](./tasks-pl-p4-hall.md#s2) | [ ] |
| **PL-P4 · ⑤ Perun** | S3 Security block grammar + generator | [`tasks-pl-p4-perun.md#s3`](./tasks-pl-p4-perun.md#s3) | [ ] |
| | S4 Perun — directory + PDP | [`tasks-pl-p4-perun.md#s4`](./tasks-pl-p4-perun.md#s4) | [ ] |
| | S5 PEP wiring + fail-closed + DoD | [`tasks-pl-p4-perun.md#s5`](./tasks-pl-p4-perun.md#s5) | [ ] |
| **PL-P5 · E emit** | S1 Emit SPI + bash extraction | [`tasks-pl-p5-emit.md#s1`](./tasks-pl-p5-emit.md#s1) | [ ] |
| | S2 Determinism kit + plugin trust | [`tasks-pl-p5-emit.md#s2`](./tasks-pl-p5-emit.md#s2) | [ ] |
| | S3 ttr-emit-kestra | [`tasks-pl-p5-emit.md#s3`](./tasks-pl-p5-emit.md#s3) | [ ] |
| **PL-P5 · E Airflow** | S4 ttr-emit-airflow3 (both bindings) | [`tasks-pl-p5-airflow.md#s4`](./tasks-pl-p5-airflow.md#s4) | [ ] |
| | S5 ttr-connect-airflow3 harvest | [`tasks-pl-p5-airflow.md#s5`](./tasks-pl-p5-airflow.md#s5) | [ ] |
| **PL-P6 · ⑥ adoption** | S1 Theseus transplant + slim | [`tasks-pl-p6.md#s1`](./tasks-pl-p6.md#s1) | [ ] |
| | S2 Kantheon adoption + HQ-4 + delete legs | [`tasks-pl-p6.md#s2`](./tasks-pl-p6.md#s2) | [ ] |
| **PL-P7 · ⑦ triggers** | S1 Zorya trigger layer | [`tasks-pl-p7-triggers.md#s1`](./tasks-pl-p7-triggers.md#s1) | [ ] |
| | S2 Harvest scheduling + imports + stats | [`tasks-pl-p7-triggers.md#s2`](./tasks-pl-p7-triggers.md#s2) | [ ] |
| **PL-P7 · ⑦ close-out** | S3 Deploy views + graduation gate | [`tasks-pl-p7-acceptance.md#s3`](./tasks-pl-p7-acceptance.md#s3) | [ ] |
| | S4 Fresh-instance rehearsal (tatry umbrella) | [`tasks-pl-p7-acceptance.md#s4`](./tasks-pl-p7-acceptance.md#s4) | [ ] |
| | S5 Q-6 acceptance run | [`tasks-pl-p7-acceptance.md#s5`](./tasks-pl-p7-acceptance.md#s5) | [ ] |

Stage order: S1–S4 (tatrman) and S5–S7 (platform repos) may interleave after S1 (S6.T5 consumes S1's `ttr-snapshot` artifact via Maven Local); S8–S9 need S6–S7 running locally. The PL-P1 DoD ([`../plan.md`](../plan.md) §PL-P1) is checked only when all nine stages are done.

## Phase exit reviews

House cadence: `/review` after each phase; `[x]` marks are intent, not truth — the reviewer verifies against runtime (review artifacts: `docs/ecosystem/platform/implementation/review-NNN.md`, serial numbering shared with the repo's other efforts).

- [ ] PL-P0 review · [ ] PL-P1 review · [ ] PL-P2 review · [ ] PL-P3 review · [ ] PL-P4 review · [ ] PL-P5 review · [ ] PL-P6 review · [ ] PL-P7 review (final)

## Library reference card

- **`org.tatrman:ttr-metadata`** — `ModelSource`/`ModelStorage` SPI (`listFiles(extensions, prefixes)`, `read`, `fetchVersion`), `MetadataLoader.load(): LoadResult`, `MetadataRegistry` (atomic snapshot + listeners), `MetadataRefresher.tryRefresh()`, `MetadataQuery` (listObjects/getObject/search/graph/erToDb), `WorldResolver.resolve(qname): WorldResolution` → `ResolvedWorld{engines, executors, storages, staging, fingerprint}`. Fixtures via `testFixtures(project(":packages:kotlin:ttr-metadata"))`. (Verified from ttr-metadata contracts v1.5, 2026-07-09.)
- **Ktor service patterns** — copy shapes from kantheon `EXAMPLES.md`: §1a/1b `installKtorServerBase` + ≤45-line `Application.kt`, §2a `buildJsonObject` (never `mapOf` in `respond`), §2b sealed-interface DTOs, §8 OTel bootstrap. These libs get transplanted into `tatrman-platform/shared/` in S5.T4 — after that, reference the transplanted copies, not kantheon.
- **Deterministic tar+zstd** — `org.apache.commons:commons-compress:1.27.x`: `TarArchiveOutputStream` with `setLongFileMode(LONGFILE_POSIX)`, entries via `TarArchiveEntry(path)` + `setModTime(0)` + `setIds(0,0)` + `setMode(0644)`, added in bytewise path order; wrap in `com.github.luben:zstd-jni` `ZstdOutputStream(out, 19)` with `setCloseFrameOnFlush(false)` and checksums off. Golden-hash test on two OS runners is the determinism proof (contracts §2).
- **TOML (`ttr.lock`)** — parse with `org.tomlj:tomlj`; WRITE with the hand-rolled deterministic writer (sorted keys, fixed formatting) — tomlj does not round-trip formatting and lock bytes are hashed (contracts §5).
- **OpenMetadata REST** (verified 2026-07-09 via Context7, OM 1.x/2.x line) — auth: JWT bot token, `Authorization: Bearer`. Entities: `PUT /api/v1/tables` with `CreateTableRequest{name, databaseSchema (FQN), columns[{name,dataType,dataLength?}]}` (create-or-update semantics). Column lineage: `PUT /api/v1/lineage` body `{edge:{fromEntity:{id,type}, toEntity:{id,type}, lineageDetails:{sqlQuery?, columnsLineage:[{fromColumns:[FQN…], toColumn:FQN}], pipeline?:{id,type}}}}`. Map E-5 `lineage.columns[]` → one `columnsLineage` entry each; FQN = `<service>.<db>.<schema>.<table>.<column>`.
- **jgit** — only through `org.tatrman:ttr-metadata-git`'s `GitArchiveStorage`; never raw jgit in Veles routes.
- **Airflow 3 REST** (verified 2026-07-09 via Context7): token — `POST /auth/token` with `{"grant_type":"client_credentials","client_id":…,"client_secret":…}` (Keycloak auth manager — matches H-2-ii) → `{access_token}`; trigger — `POST /api/v2/dags/{dagId}/dagRuns` body `{"logical_date":…, "conf":{…}}` with `Authorization: Bearer`; poll — `GET /api/v2/dags/{dagId}/dagRuns/{runId}` (or the streaming `/wait` variant, `Accept: application/x-ndjson`).
- **Kestra** (verified 2026-07-09 via Context7): flow YAML = `{id, namespace, tasks:[{id, type, …}], triggers:[{id, type: io.kestra.plugin.core.trigger.Schedule, cron}]}`; shell islands via `io.kestra.plugin.scripts.shell.Script` (`script: |`), python via `io.kestra.plugin.scripts.python.Script`; execute — `POST /api/v1/executions/{namespace}/{flowId}`.
- **OPA bundles** (PL-P4): standard bundle = `.tar.gz{.manifest, *.rego, data.json}`, JWT signing via `.signatures.json`; PEP = OPA sidecar with a `bundles:` config pointing at Perun; query `POST localhost:8181/v1/data/<package>`. Signing keys via BouncyCastle (`bcpg-jdk18on`), same lib for PL-P5 PGP artifact verification.
- **cron parsing** (PL-P7): `com.cronutils:cron-utils` — `CronParser(CronDefinitionBuilder.instanceDefinitionFor(UNIX))`, `ExecutionTime.forCron(...).nextExecution(zonedNow)`; timezone from the envelope.
- **Locally cloned, graphify'd references** (`~/Dev/view-only/`): `calcite/`, `koog/`, `kotlin-mcp-sdk/` — none needed in PL-P1; Calcite matters again in PL-P5 (emit) and PL-P6 (query-door translation remnants).
