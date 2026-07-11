# SV-P1 · S3 — Gate 3b: images to GHCR + the olymp repoint (the pin retires)

> Repos: **tatrman-server** (images) + **olymp** (charts/Argo) + **kantheon** (prose). Pre-flight: S2 done. This stage executes the deferred SV-P0 items S6·T2 (chart rename) and S6·T3 (backstage/health, RO-22), fixes the confirmed-broken `_env.tpl` env names, and repoints the pilot off the SV-P0 pin — **one coherent change window**, exactly why it was deferred. Image path = **flat `ghcr.io/collite/<name>`** (RO-28). The pilot's `root-app` is `automated + selfHeal + prune`: olymp changes go live on merge — order within this list is load-bearing.

- [ ] **T1 — Chart-internal persona fixes in tatrman-server (before any image builds).** Helm `_env.tpl` defines/env names: `kyklop.env`→functional, `KYKLOP_SERVER_PORT`→`DISPATCH_SERVER_PORT`-style, `THESEUS_*`, `PROTEUS_*`, `ARGOS_*`, `KADMOS_SERVICE_*`→`NLP_SERVICE_*`, `PROMETHEUS_SERVER_PORT`→`LLM_GATEWAY_*` — **the code's config keys are ground truth** (each service's `application.conf`/`config.py` `validation_alias`; the S6 finding proved chart-injects-KADMOS vs code-reads-NLP is a live deploy bug). Also `logback.xml` dead loggers (`org.tatrman.kantheon.{theseus,proteus,kyklop}` → delete or rename to the real packages). TDD: FIRST extend the grep gates (T5b here, both repos + `ci.yml`) with `--include='*.tpl' --include='logback.xml'`, watch them fire on exactly these files, then fix until clean.
- [ ] **T2 — Build + push the images. [GATE]** Preconditions: T1 gate green · S2 published (images embed 0.9.0 jars). Trigger `release-image.yml` (per-module `<module>/v0.9.0` tags or manual dispatch, per the workflow's own contract) for all 17 moved services/workers/tools/infra. Registry: flat `ghcr.io/collite/{veles, ttr-query, ttr-translate, ttr-validate, ttr-dispatch, ttr-fuzzy, ttr-llm-gateway, ttr-nlp, ttr-meta-mcp, ttr-query-mcp, ttr-fuzzy-mcp, ttr-nlp-mcp, ttr-worker-postgres, ttr-worker-mssql, ttr-worker-polars, ttr-identity, health}:0.9.0`. Kotlin→Jib multi-arch, Python→Docker (repo-root context; the Dockerfiles were fixed 2026-07-11 — `uv sync --frozen` must pass). Verify each package visible on GHCR + `docker pull` one Kotlin and one Python image.
- [ ] **T3 — Olymp chart/values/Argo rename (S6·T2 executed).** Per naming ledger §3, for the 17 moved apps: chart/Argo Application names → functional; `values.yaml` image refs → the T2 paths `ghcr.io/collite/<name>:0.9.0` (pin by digest after T5); env blocks follow T1's renames. Charon stays kantheon-sourced (operate-parked) — only bump its image if the S5 transfer.v1 rename rebuilt it. **Work on a branch, one PR** — do not merge until T4 is in the same PR (atomic repoint).
- [ ] **T4 — Backstage catalog + health wiring (S6·T3 / RO-22, same PR as T3).** Backstage entries for moved services → `Collite/tatrman-server` source location; kantheon components stay registered in the same instance; health aggregation covers both repos' services as before (health now builds/deploys from tatrman-server — its image is in T2's set).
- [ ] **T5 — Merge + pilot repoint (the pin retires). [GATE]** Preconditions: T2 images pullable · Bora available for rollback. Merge the T3+T4 PR; watch Argo: all 17 apps `Synced` + `Healthy` on `ghcr.io/collite/*` images. Rollback = the SV-P0 pin (olymp `12796ac` + the digest table in `tasks-sv-p0-s6-deploy.md` findings). Smoke: one governed question through the MCP door (`ttr-query-mcp`) returns an answer with provenance. Record new image digests in findings; the SV-P0 pin is retired (mark it so in the S6 list).
- [ ] **T6 — kantheon + tatrman-server prose sweep (S5·T7 executed).** README/CLAUDE.md/docs: moved-service references point at tatrman-server; persona names of moved services replaced by functional names in *living* docs (historical/architecture-fork docs keep them with a "formerly" note); the clean-machine caveat deleted (S2·T6). Non-gating but due — it was deferred exactly to here.
- [ ] **T7 — Findings + registers.** Plan §SV-P1 gate 3 row fully done. ⚑ anything Argo showed during T5 (sync waves, probe failures). Note for SV-P4: the umbrella chart (single-chart packaging) is NOT this — olymp remains the pilot's private deploy repo.

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

*(new digest table · smoke-test transcript pointer · anything Argo surfaced)*
