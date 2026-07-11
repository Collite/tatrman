# SV-P0 — Phase review input (for Bora)

> Compiled at S6 close (2026-07-11). Source: the six stage lists' findings sections + the
> phase-DONE checklist in [`00-task-management.md`](./00-task-management.md). SV-P0 is a
> **repo + naming fork**, not a publish: the read spine moved from kantheon into the new
> Apache-2.0 `tatrman-server`, personas came off the wire (ledger J-v2), protos reconciled,
> kantheon closed to consume the moved code as `0.0.1-LOCAL` artifacts, and the pilot was
> **pinned pre-move** (no registry publishing in SV-P0 — that is SV-P1).

## Outcome: all six stages DONE

| Stage | Result | Branch |
|---|---|---|
| S1 · server bootstrap | ✅ pushed `master`, CI wired (build + dependency-rules + report-only grep-gate) | tatrman-server `master` |
| S2 · tatrman proto amendments | ✅ `TableHint` into plan.v1; published `0.0.1-LOCAL` | tatrman `sv-p0-server-fork` |
| S3 · the move | ✅ history-preserving `git filter-repo` graft | tatrman-server `sv-p0-move` |
| S4 · rename & proto sweep | ✅ build green, deps-rule OK, **wire** gate clean | tatrman-server `sv-p0-move` |
| S5 · kantheon closure | ✅ build green off the moved modules; artifacts consumed; **wrinkle solved** | kantheon `sv-p0-kantheon-close` |
| S6 · deployment + gates | ✅ via **PIN**; dormant `release-image.yml`; wire gate clean both repos | tatrman-server `sv-p0-move` |

**Phase-DONE checklist:** 4/5 met; the 5th is *this* review (⚑ disposition by Bora).

## What needs Bora's decision (⚑)

1. **S6 image-org path** — moved-service images wired as flat `ghcr.io/collite/<functional-name>`
   (kantheon precedent). Alternative: repo-scoped `ghcr.io/collite/tatrman-server/<name>`. Flat is
   safe (functional `ttr-*` names; kantheon no longer builds `health`/`veles`). **Decide before SV-P1 gate 3.**
2. **S6 / S4 blind spot (build-breaking, SV-P1 scope)** — the S4 gate never scanned
   `Dockerfile`/`.tpl`/`logback.xml`/`uv.lock`, so **deployment-internal** persona residue survived.
   Wire surfaces are clean; this is chart/image plumbing that renames **with** the SV-P1 repoint. Two
   items are genuinely broken and must be fixed before the SV-P1 Python image build:
   - `services/ttr-nlp/Dockerfile` COPYs non-existent `services/kadmos/…`; `uv.lock` names `kadmos-service`.
   - `workers/ttr-worker-polars/Dockerfile` COPYs non-existent `workers/steropes/…`.
   - Also: `_env.tpl` env-names + `logback.xml` dead loggers (inert; rename with charts).
   - **Harden the T5 gate**: add `Dockerfile*`, `*.tpl`, `logback.xml` to the include list.
3. **S1 admin (needs Bora's GH admin token)** — set `Collite/tatrman-server` default branch to
   `master` + delete the empty `main` (blocked on a 404: my token lacks repo-admin).
4. **S5 housekeeping (needs a yes)** — kantheon root-level stale planning files (`midas next steps.txt`,
   `v1 next steps 260627.md`, `review-00[1-6].md`, `reviews.md`, `tasks-review-*.md`) left untouched.
5. **Clean-machine build note** — a fresh kantheon build needs `./gradlew publishToMavenLocal` in
   tatrman-server first (interim `0.0.1-LOCAL` artifacts aren't on any registry until SV-P1 gate 3).

## Non-gating follow-ups (deferred, tracked)

- **S5 T7 / S6 T2·T3** — kantheon `README/CLAUDE.md/docs` prose + olymp chart/backstage rename: all
  ride the SV-P1 repoint. Prose is not gate-relevant; the chart rename can't precede image publish.
- **The pin** (S6 T4) — pilot bp-dsk stays on pre-move persona images (`ghcr.io/boraperusic/*:testing`)
  at recorded digests; olymp `master@12796ac`. Full digest table in the S6 findings.

## Verify-block evidence

- **Wire gate** (`grep -iE '<retired personas>'` over `.proto/.kt/.kts/.py/.conf/.yaml/.toml`, refined
  exclusions for Prometheus-monitoring + `\barges\b`): **CLEAN on tatrman-server and kantheon** — the only
  matches are the legitimate monitoring subsystem (`PrometheusHealthChecker`, `/metrics` scrape, in-process
  Prometheus-text metrics), never the retired `prometheus` LLM-gateway persona (now `ttr-llm-gateway`/`llm.v1`).
- **Pilot** — all 17 moved Argo apps `Synced` (pin path; no repoint performed by design).
