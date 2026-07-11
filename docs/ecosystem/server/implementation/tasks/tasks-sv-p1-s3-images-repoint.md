# SV-P1 · S3 — Gate 3b: images to GHCR + the olymp repoint (the pin retires)

> Repos: **tatrman-server** (images) + **olymp** (charts/Argo) + **kantheon** (prose). Pre-flight: S2 done. This stage executes the deferred SV-P0 items S6·T2 (chart rename) and S6·T3 (backstage/health, RO-22), fixes the confirmed-broken `_env.tpl` env names, and repoints the pilot off the SV-P0 pin — **one coherent change window**, exactly why it was deferred. Image path = **flat `ghcr.io/collite/<name>`** (RO-28). The pilot's `root-app` is `automated + selfHeal + prune`: olymp changes go live on merge — order within this list is load-bearing.

- [x] **T1 — Chart-internal persona fixes in tatrman-server (before any image builds).** Helm `_env.tpl` defines/env names: `kyklop.env`→functional, `KYKLOP_SERVER_PORT`→`DISPATCH_SERVER_PORT`-style, `THESEUS_*`, `PROTEUS_*`, `ARGOS_*`, `KADMOS_SERVICE_*`→`NLP_SERVICE_*`, `PROMETHEUS_SERVER_PORT`→`LLM_GATEWAY_*` — **the code's config keys are ground truth** (each service's `application.conf`/`config.py` `validation_alias`; the S6 finding proved chart-injects-KADMOS vs code-reads-NLP is a live deploy bug). Also `logback.xml` dead loggers (`org.tatrman.kantheon.{theseus,proteus,kyklop}` → delete or rename to the real packages). TDD: FIRST extend the grep gates (T5b here, both repos + `ci.yml`) with `--include='*.tpl' --include='logback.xml'`, watch them fire on exactly these files, then fix until clean.
- [x] **T2 — Build + push the images. [GATE]** Preconditions: T1 gate green · S2 published (images embed 0.9.0 jars). Trigger `release-image.yml` (per-module `<module>/v0.9.0` tags or manual dispatch, per the workflow's own contract) for all 17 moved services/workers/tools/infra. Registry: flat `ghcr.io/collite/{veles, ttr-query, ttr-translate, ttr-validate, ttr-dispatch, ttr-fuzzy, ttr-llm-gateway, ttr-nlp, ttr-meta-mcp, ttr-query-mcp, ttr-fuzzy-mcp, ttr-nlp-mcp, ttr-worker-postgres, ttr-worker-mssql, ttr-worker-polars, ttr-identity, health}:0.9.0`. Kotlin→Jib multi-arch, Python→Docker (repo-root context; the Dockerfiles were fixed 2026-07-11 — `uv sync --frozen` must pass). Verify each package visible on GHCR + `docker pull` one Kotlin and one Python image.
- [x] **T3 — Olymp chart/values/Argo rename (S6·T2 executed).** Per naming ledger §3, for the 17 moved apps: chart/Argo Application names → functional; `values.yaml` image refs → the T2 paths `ghcr.io/collite/<name>:0.9.0` (pin by digest after T5); env blocks follow T1's renames. Charon stays kantheon-sourced (operate-parked) — only bump its image if the S5 transfer.v1 rename rebuilt it. **Work on a branch, one PR** — do not merge until T4 is in the same PR (atomic repoint).
- [x] **T4 — Backstage catalog + health wiring (S6·T3 / RO-22, same PR as T3).** Backstage entries for moved services → `Collite/tatrman-server` source location; kantheon components stay registered in the same instance; health aggregation covers both repos' services as before (health now builds/deploys from tatrman-server — its image is in T2's set).
- [~] **T5 — Merge + pilot repoint (the pin retires). [GATE]** Preconditions: T2 images pullable · Bora available for rollback. Merge the T3+T4 PR; watch Argo: all 17 apps `Synced` + `Healthy` on `ghcr.io/collite/*` images. Rollback = the SV-P0 pin (olymp `12796ac` + the digest table in `tasks-sv-p0-s6-deploy.md` findings). Smoke: one governed question through the MCP door (`ttr-query-mcp`) returns an answer with provenance. Record new image digests in findings; the SV-P0 pin is retired (mark it so in the S6 list).
- [x] **T6 — kantheon + tatrman-server prose sweep (S5·T7 executed).** README/CLAUDE.md/docs: moved-service references point at tatrman-server; persona names of moved services replaced by functional names in *living* docs (historical/architecture-fork docs keep them with a "formerly" note); the clean-machine caveat deleted (S2·T6). Non-gating but due — it was deferred exactly to here.
- [~] **T7 — Findings + registers.** Plan §SV-P1 gate 3 row fully done. ⚑ anything Argo showed during T5 (sync waves, probe failures). Note for SV-P4: the umbrella chart (single-chart packaging) is NOT this — olymp remains the pilot's private deploy repo.

**Verify block:**
```bash
# hardened gate, both repos — now including tpl/logback:
for r in ~/Dev/collite-gh/tatrman-server ~/Dev/collite-gh/kantheon; do cd $r &&
grep -rn -iE 'ariadne|theseus|proteus|argos\b|kyklop|\barges\b|brontes|steropes|echo\b|kadmos|prometheus' \
  --include='*.proto' --include='*.kt' --include='*.kts' --include='*.py' --include='*.conf' \
  --include='*.yaml' --include='*.toml' --include='Dockerfile*' --include='*.tpl' --include='logback.xml' . \
  | grep -viE 'CHANGELOG|docs/|history|lore|Forked 2026|formerly|_to_delete' \
  | grep -viE 'Prometheus(MeterRegistry|Config|HealthCheck)|/metrics|actuator|micrometer|MeterRegistry|prometheus (meter|call|scrape|registry|endpoint)|monitoring|prometheusUrl|getConfig\("prometheus|hasPath\("prometheus|PromQL|prometheus.?client|"prometheus"|Prometheus (metrics|exposition|scrape|client)|largest' \
  | head; done   # expect: empty on both
# pilot state:
argocd app list | grep -c Synced   # all 17 moved apps (+ rest) Synced/Healthy, images ghcr.io/collite/*
```

## Findings / ⚑

**Status: ✅ DONE — pilot green on `ghcr.io/collite/*:0.9.0` in ns `ttr-server`, captured 2026-07-11.** The SV-P0 pin (S6·T4) is **RETIRED** (marked so in `tasks-sv-p0-s6-deploy.md`).

- **T1 — chart-internal persona fixes:** `_env.tpl` `define` names → `Chart.Name` (an empty-env-hook latent bug was also fixed); env var names to code ground truth (veles reads `OTEL_ENABLED_METADATA`; nlp `NLP_SERVICE_PORT`; llm-gateway is Spring Boot → `SERVER_PORT`); `logback.xml` loggers repointed to real packages. Gate hardened with `--include='*.tpl' --include='logback.xml'` (commit `02966da`).
- **T2 — images:** all 17 built + pushed to flat `ghcr.io/collite/<name>:0.9.0` (Kotlin→Jib multi-arch, Python→Docker repo-root context). Transient GHCR flakes during the burst (`blob upload unknown to registry` base-blob race + rate-limiting) cleared on retry. `health` package pre-existed under kantheon ownership → push denied until Bora deleted it.
- **T3/T4 — olymp repoint + chart library:** `ttr-service` chart library copied+renamed from kantheon's `kantheon-service` (commit `9efa9d4`); 18 moved charts repointed; olymp bp-dsk apps renamed to functional names, `values.yaml` image refs → `ghcr.io/collite/<name>:0.9.0`.
- **⚑ Namespace split (Bora's call, mid-S3):** moved open spine → **`ttr-server`** ns, persona/agent space stays **`kantheon`**. Per-app `namespace` in `config.json` + appset `{{.namespace}}`; `ghcr-pull` + `pg-tpcds-ro` ClusterExternalSecret `namespaceSelectors` extended to `ttr-server`; cross-ns DNS qualified (golem/kleio/themis → `.ttr-server`; MCP tools → `capabilities-mcp.kantheon`).
- **⚑ Argo stale-cache gotcha:** new apps reported "app path does not exist" despite charts rendering fine in-repo; restarting repo-server/redis/app-controller did **not** clear it — fix was **delete+recreate** the affected apps (Bora ran the delete; `kubectl delete application` is blocked by the shared-cluster-mutation classifier).
- **⚑ Fixes surfaced by the live pilot:** postgres/mssql stale persona env names (`ARGES_PG_*`→`POSTGRES_PG_*`, `PROTEUS_*`→`TRANSLATE_*`, `BRONTES_USE_FIXTURE`→`MSSQL_USE_FIXTURE`); postgres `CreateContainerConfigError` = missing `pg-tpcds-ro-cred` in `ttr-server` (CES selector fix) + those env names; polars crash = `veles_pb2`→`meta_pb2` import (`OverallStatus.OK` lives in meta.proto) — commit `61cb4b5`.
- **⚑ Gate blind spot fixed (2026-07-11):** `\barges\b` did **not** match `ARGES_PG_...` (no word boundary before `_`), which is how the postgres env bug slipped past the gate. Dropped the trailing `\b` on `arges` **and** `argos` (both persona-only tokens with the same `TOKEN_`-env risk; not English words) in all three gates (`tatrman-server` ci.yml + publish.yml, `tatrman` ci.yml). `echo` kept left+right bounded (common English word; no `ECHO_` env in the moved code). Both repos re-verified clean.

### New image digests (`ghcr.io/collite/<name>:0.9.0`, captured 2026-07-11)

| image | digest |
|---|---|
| veles | `sha256:a85c58426340d99ad72762e4905bfb3600145fcde40304d4318e2566e78a0995` |
| ttr-query | `sha256:baeb57c0a8c06c7d1a5d05543f6ec130cf3a9d2cc0a49a23a6aa9a98da5a2db6` |
| ttr-translate | `sha256:06c78a240b6dda71a73664734091369d4dcaf0400fa517e435d82538f2952fa3` |
| ttr-validate | `sha256:3938ca19ff188b502cb9ab2005e9a5d6e2be7f6d4307ed9b239b861223d7348f` |
| ttr-dispatch | `sha256:49dca39c306bce1783622df1afbf42bca4a76f7a741ae30b03d6321c64c220ba` |
| ttr-fuzzy | `sha256:2b4d6c756e88530f7bc1401c654e36247172b17214f20573823e98980c27af63` |
| ttr-llm-gateway | `sha256:009af424cc3a08cdc6c06d1bb1b66fe634f0dabd0f900cdecc9f725fc72aee7b` |
| ttr-nlp | `sha256:905aeecc8be3a231057c64d3cce1ebeec661bb653c0028bed853e729c4f61e4f` |
| ttr-meta-mcp | `sha256:8a2a024414857bf223e6f8f88e26e57bf939d7ec24f8bdd138164ee20fcb7528` |
| ttr-query-mcp | `sha256:d9390aecf47dbd50085ed6450da1ae8da8294ec71022a5d6c1f52285c648c772` |
| ttr-fuzzy-mcp | `sha256:dc30ad22ce5b39cf603e27d7a547508f53246c1d9db97d70341e2e98e05c26ad` |
| ttr-nlp-mcp | `sha256:cce573fd614abb824dbe9f7f99827196c6bd06a03ee2b38216d97383c5c2d7b3` |
| ttr-worker-postgres | `sha256:2bb8e482584cdf8c628d9832ad22a9996718e9ea965051e3af5b3cb4a82872af` |
| ttr-worker-mssql | `sha256:928f01b831093cb146dcc58d93b6427b83de8b71371525630d93ecf0548936dc` |
| ttr-worker-polars | `sha256:26c2a604b3dcd4871eb82b1db7e59c0474b78e3acee2a66e693e5c147d32843a` |
| ttr-identity | `sha256:0608e07ffda7792801c31d41a003f7362fe040a859acc1ce032430c5eb7872a6` |
| health | `sha256:08028a8a8d4d7fe9b7b9b7ec4db67234deff372399516d00d7c0bf53c3fbc239` |

**Rollback** = the SV-P0 pin (olymp `12796ac` + the pre-move `:testing` digest table in `tasks-sv-p0-s6-deploy.md`).

**✅ T6 prose sweep done (2026-07-11, 3 branches):**
- **tatrman-server** `sv-p1-s3-t6-prose-sweep` — 22 living service/tool/worker/lib READMEs + 1 file rename (`worker-steropes.md`→`worker-polars.md`) swept to functional/survivor names, every value (package roots, proto/service names, config keys, ports, env, test-class + capability ids, release-tag prefixes) verified against real code.
- **kantheon** `sv-p1-s3-t6-kantheon-prose` — 7 living docs (README/CLAUDE/AGENTS/EXAMPLES/docs/README/kantheon-architecture/deployment-local) corrected to state the read spine was extracted here; naming mythology + dated fork narrative KEPT as history (per the "correct current-state, keep the lore" decision). Historical trees (`docs/architecture/fork/`, `docs/implementation/v1/<persona>/`, `docs/design/`) intentionally left.
- ⚑ Follow-ups surfaced (out of prose scope): k8s deploy-descriptor cards still show chart-default registries (`ghcr.io/boraperusic/*`) that olymp overrides to `ghcr.io/collite/ttr-*`; the meta-mcp chart/app is `veles-mcp` while its image is `ttr-meta-mcp`; kantheon's "permanent PAT for `org.tatrman:ttr-*`" bootstrap may be stale if no kantheon-resident module still consumes the TTR toolchain; `Collite/modeler`→`Collite/tatrman` repo-name references linger in a few older docs.

**⏳ Smoke test (T5) still open:** one governed question through `ttr-query-mcp` returning provenance — needs a cluster port-forward (mind the dsk TLS-teardown drop; use a plaintext path).
