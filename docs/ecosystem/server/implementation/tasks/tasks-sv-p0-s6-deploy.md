# SV-P0 · S6 — Deployment rename + final gates

> Repo: **olymp** (cluster defs) + the pilot deployment. Same change window as the ledger's N1 (chart names, Argo apps follow the module renames). The pilot may alternatively be PINNED pre-move — that is an allowed DONE state if repointing now is inconvenient (record the pin).

- [x] **T1 — Image builds from tatrman-server.** Wire `release-image.yml` (cloned shape from kantheon) in tatrman-server for the moved services; images named `ttr-query`, `ttr-validate`, … and `veles` (persona legal in *release/image* names? NO for published chart names — J-v2 guardrail allows personas in Helm *release* names and k8s labels only; image repository names are publish-adjacent → use functional names: `veles` is a surviving persona and stays `veles`). Record the registry paths in findings.
- [ ] **T2 — Olymp chart/values rename.** ⏸ **DEFERRED to SV-P1** (pin path taken, T4). Chart names, `values.yaml` keys, image refs, Argo Application names for the moved services per ledger §3 (same change window discipline). Charon's chart stays kantheon-sourced (operate-parked) — only its proto changed (S5 T3): bump its image if the transfer.v1 rename rebuilt it.
- [ ] **T3 — backstage catalog + health wiring (RO-22 executed).** ⏸ **DEFERRED to SV-P1** (pin path taken, T4). health + backstage now build/deploy from tatrman-server; backstage catalog entries updated — moved services point at `Collite/tatrman-server`, kantheon components stay registered in the same instance; health aggregation covers both repos' services as before.
- [x] **T4 — Pilot repoint or pin.** ✅ **PINNED pre-move** (repoint is impossible in SV-P0 — rename-before-publish invariant means the renamed images don't exist yet; they publish at SV-P1 gate 3). Pin recorded below + in `00-task-management.md`.
- [x] **T5 — The grep gate, both repos (phase DONE gate).** Include list hardened
  (S6): `Dockerfile*` added — the S4 sweep's list missed it, which let stale
  `services/kadmos`/`workers/steropes` COPY paths through. `\barges\b` (not bare
  `arges`, which matched "l**arges**t") + `formerly` (historical provenance notes)
  refine the exclusions. **`*.tpl` + `logback.xml` join at SV-P1** — they still carry
  chart-internal persona env/logger names that rename with the image publish + olymp
  repoint (deferred, see the S4-blind-spot finding). The enforcing copy lives in
  tatrman-server `.github/workflows/ci.yml` (flipped off `continue-on-error` at S6).
  ```bash
  for r in ~/Dev/collite-gh/tatrman-server ~/Dev/collite-gh/kantheon; do cd $r &&
  grep -rn -iE 'ariadne|theseus|proteus|argos\b|kyklop|\barges\b|brontes|steropes|echo\b|kadmos|prometheus' \
    --include='*.proto' --include='*.kt' --include='*.kts' --include='*.py' --include='*.conf' \
    --include='*.yaml' --include='*.toml' --include='Dockerfile*' . \
    | grep -viE 'CHANGELOG|docs/|history|lore|Forked 2026|formerly|_to_delete' \
    | grep -viE 'Prometheus(MeterRegistry|Config|HealthCheck)|/metrics|actuator|micrometer|MeterRegistry|prometheus (meter|call|scrape|registry|endpoint)|monitoring|prometheusUrl|getConfig\("prometheus|hasPath\("prometheus|PromQL|prometheus.?client|"prometheus"|Prometheus (metrics|exposition|scrape|client)|largest' \
    | head; done   # expect: empty on both (only legitimate Prometheus monitoring excluded)
  ```
  (kantheon's agents may reference *surviving* personas — Golem/Pythia/Iris/Veles/Perun/Charon are not in the regex; anything the gate catches is real.)
- [x] **T6 — Close the phase.** All six stage rows checked in `00-task-management.md`; phase-DONE checkboxes walked; findings compiled into [`sv-p0-review-input.md`](./sv-p0-review-input.md) for Bora's phase review; control room gets the session-index row at the review.

**Verify block:** T5 is the verify block; plus `argocd app list` (Argo UI) showing synced apps — pin path: all 17 moved apps `Synced` (evidence below).

## Findings / ⚑

**Status: ✅ DONE via the PIN path (T1, T4, T5, T6). T2/T3 deferred to SV-P1 by design.**
Wire-surface gate CLEAN on both repos (only legitimate Prometheus *monitoring* remains — `PrometheusHealthChecker`, `/metrics` scrape, in-process Prometheus-text metrics; the retired LLM-gateway `prometheus` persona is renamed to `ttr-llm-gateway`/`llm.v1`).

### T1 — release-image.yml (DORMANT) + registry paths
- `.github/workflows/release-image.yml` written in tatrman-server (cloned kantheon shape; branch `sv-p0-move`). **Dormant through SV-P0** — fires only on a `<module>/v*` tag or manual dispatch; no tag is pushed in SV-P0 (rename-before-publish). Spine images publish for real at **SV-P1 gate 3**.
- **Registry paths** (functional image-repo names per J-v2; `veles` = surviving persona, kept):
  `ghcr.io/collite/{veles, ttr-query, ttr-translate, ttr-validate, ttr-dispatch, ttr-fuzzy, ttr-llm-gateway, ttr-nlp, ttr-meta-mcp, ttr-query-mcp, ttr-fuzzy-mcp, ttr-nlp-mcp, ttr-worker-postgres, ttr-worker-mssql, ttr-worker-polars, ttr-identity, health}:<version>`.
  Lanes: Kotlin→Jib (`:path:jib`, multi-arch); Python→Docker (repo-root context) for `ttr-nlp` + `ttr-worker-polars`. ⚑ **Image-org decision for Bora:** used flat `ghcr.io/collite/<name>` (kantheon precedent); a repo-scoped `ghcr.io/collite/tatrman-server/<name>` is the alternative — flat is safe because names are functional (`ttr-*`) and kantheon no longer builds the shared basenames `health`/`veles` post-move.

### ⚑ S4-sweep blind spot — Dockerfiles FIXED (2026-07-11); charts deferred to SV-P1
The S4 gate's `--include` list (`.proto/.kt/.kts/.py/.conf/.yaml/.toml`) never scanned **Dockerfiles**, **Helm templates (`.tpl`)**, **`logback.xml`**, or **`uv.lock`**, so retired-persona *deployment-internal* residue survived. Wire surfaces are clean (Kotlin pkgs = `org.tatrman.query` etc.; pyproject = `nlp-service`); this is chart/image plumbing.

- **✅ Python Dockerfiles FIXED (Bora's instruction, 2026-07-11).** `services/ttr-nlp/Dockerfile` + `workers/ttr-worker-polars/Dockerfile`: renamed the stale COPY dir paths (`services/kadmos`→`services/ttr-nlp`, `workers/steropes`→`workers/ttr-worker-polars`), the uvicorn module (`kadmos_service.api.routes:app`→`nlp_service.api.routes:app`), the polars entrypoint (`workers_steropes.main`→`workers_polars.main`), and the ttr-nlp OTel env var (`KADMOS_SERVICE_OTEL_PROTOCOL`→`NLP_SERVICE_OTEL_PROTOCOL`, matching the code's `config.py` `validation_alias`). Regenerated both `uv.lock` (root name `kadmos-service`→`nlp-service`, `workers-steropes`→`workers-polars`); `uv lock --check` passes → the Dockerfiles' `uv sync --frozen` will succeed.
- **✅ S3 move-gap also fixed:** the Python shared lib **`shared/libs/python/otel-config` was never grafted** (the Kotlin sibling `shared/libs/kotlin/otel-config` was) — both Python builds COPY it, so they were broken at that layer regardless. Copied it into tatrman-server (kantheon keeps its copy for `metis`/agents — a both-repos shared lib, not a move). All Dockerfile COPY sources now resolve.
- **✅ Gate hardened + flipped to ENFORCING:** `--include='Dockerfile*'` added to the T5 gate + tatrman-server `ci.yml` grep-gate; `continue-on-error` dropped (S6 is the designed flip point). Gate CLEAN with Dockerfiles scanned.
- **⏸ Still deferred to SV-P1 (chart-rename scope):** Helm `_env.tpl` define/env names (`kyklop.env`/`KYKLOP_SERVER_PORT`, `theseus`/`THESEUS_*`, `proteus`, `argos`, `kadmos`, `PROMETHEUS_SERVER_PORT`) and `logback.xml` dead loggers (`org.tatrman.kantheon.{theseus,proteus,kyklop}`, now inert). ⚑ **Confirmed the `_env.tpl` env names are genuinely broken vs code** (chart injects `KADMOS_SERVICE_PORT`; code reads `NLP_SERVICE_PORT`) → the chart rename is a real deploy-correctness fix, not cosmetic. Add `--include='*.tpl' --include='logback.xml'` to the gate once the chart rename lands.

### T4 — Pilot PIN (bp-dsk, ns `kantheon`), captured 2026-07-11 — 🔓 **RETIRED at SV-P1 S3 (2026-07-11)**
> The pilot was repointed to `ghcr.io/collite/*:0.9.0` in ns **`ttr-server`** at SV-P1 gate 3b (see `tasks-sv-p1-s3-images-repoint.md` findings — new digest table there). This pin below is now the **rollback target**, not the live state.
- **Chart pin:** olymp `master` @ **`12796ac887357dad11527e0fa70813549d620e42`** — the revision `root-app` is `Synced` to (== local olymp HEAD). All 17 moved Argo apps `Synced`.
- **Image pin** (`ghcr.io/boraperusic/<persona>:testing` @ resolved digest — the `:testing` tag is mutable, so digests are the immutable pin):

  | app (persona) | digest |
  |---|---|
  | ariadne | `sha256:1a15a38d4af94349284949fe2655e7faf069afef6bd033ae9580aa76e8e2409c` |
  | ariadne-mcp | `sha256:2c5a934fe1edb97be76e893b80ccec57d13cb3f0554a54dd9d30fa566bbf5e74` |
  | argos | `sha256:786639f73afdcabb8fd4f449949142da327eef2ad65eb823cc5077afd5714cb4` |
  | arges | `sha256:fceda01f48f40a54cf908ca0c556f1fda205bfc22c5769f5ac663e2fe7ad5e9f` |
  | brontes | `sha256:64ded60201546bfe7b5ac69bb6707bdf227c8cd5a35a23b697d44ab75976a585` |
  | echo | `sha256:9f936aef7ef63a80c87739e87b8ed9c83915bb1f69c3d65af8d4fd84e3cebce7` |
  | echo-mcp | `sha256:e526d7669b6f24faa281378aa590bdb588f793eae897f5581be25f3a66ae2cb2` |
  | health | `sha256:f0b88bcf195f3527814b26f6e95f5315c4ab746d66ce5c401bfd65f62e73bd30` |
  | kadmos | `sha256:f6334928aa6f648e42ea76c7a1ab2835f9c7880648eab0dcb77cefeb8769b42b` |
  | kadmos-mcp | `sha256:07e39569852015227127e41f8972114b9f293a0017bf4671709e8c1a349f9f94` |
  | kyklop | `sha256:454081b151a02e4b604d511527b67781450e21cda2dcfe54fb020fe29107c936` |
  | prometheus | `sha256:13fedb9e6031f29a869389f075b7fcc92f283066127aafdbe6b7621c830b63a3` |
  | proteus | `sha256:07ff20605ae25d6782d6b775a0cafe092683c21c1d85af65a6ca39a71caf8505` |
  | steropes | `sha256:8f9ab2eda205aac7f082ff3f3e0d48854d7b737acb2f95aed6a3654d6612d223` |
  | theseus | `sha256:06637cd4fe3a4b04ed52a047c7c22be2e28ab23aae75337612656e3020a19bc6` |
  | theseus-mcp | `sha256:523620dc2355105241dd7f03bb173ec9a32c00b50fd227fadaad94e4a4b04f42` |
  | whois | `sha256:df574c3cb8b85a1ceb5c8d5e2e62958f984317e2f11daefc5f2909c1f1d5c392` |

  These are the **pre-move** persona-named images; the pilot keeps running them untouched until SV-P1 (image publish + olymp repoint). Repointing now was not "inconvenient" but *impossible* — no renamed images exist under the rename-before-publish invariant.

### T2/T3 deferral rationale (why not now)
The pilot's `root-app` runs with `automated + selfHeal + prune`. Renaming olymp charts/Argo apps to point at renamed images **that don't exist yet** would auto-sync and break the live pilot. Chart/backstage rename therefore rides **with** the image publish at SV-P1 gate 3 (single coherent repoint), not ahead of it. This is the plan's sanctioned "pinned pre-move" DONE state (plan §SV-P0).
