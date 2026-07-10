# SV-P0 · S3 — The move (kantheon → tatrman-server, history-preserving)

> One migration, rename-on-arrival (RO-1). Mechanism: `git filter-repo` on a THROWAWAY kantheon clone, then merged into tatrman-server with `--allow-unrelated-histories`. Kantheon itself is NOT modified in this stage (deletion is S5) — until S5 lands, the modules exist in both repos; only tatrman-server's copies are touched from here on.
> `pip install git-filter-repo` if missing. Never run filter-repo on your working kantheon clone.

**The move set (dir renames happen IN the filter step — arrival names only ever exist in tatrman-server):**

| kantheon path | tatrman-server path |
|---|---|
| `services/ariadne` | `services/veles` |
| `services/theseus` | `services/ttr-query` |
| `services/proteus` | `services/ttr-translate` |
| `services/argos` | `services/ttr-validate` |
| `services/kyklop` | `services/ttr-dispatch` |
| `services/echo` | `services/ttr-fuzzy` |
| `services/kadmos` | `services/ttr-nlp` |
| `services/prometheus` | `services/ttr-llm-gateway` |
| `workers/arges` | `workers/ttr-worker-postgres` |
| `workers/brontes` | `workers/ttr-worker-mssql` |
| `workers/steropes` | `workers/ttr-worker-polars` |
| `tools/ariadne-mcp` | `tools/ttr-meta-mcp` |
| `tools/theseus-mcp` | `tools/ttr-query-mcp` |
| `tools/echo-mcp` | `tools/ttr-fuzzy-mcp` |
| `tools/kadmos-mcp` | `tools/ttr-nlp-mcp` |
| `infra/whois` | `infra/ttr-identity` |
| `infra/health` | `infra/health` (RO-22) |
| `infra/backstage` | `infra/backstage` (RO-22) |
| `shared/proto` | `shared/proto` (spine protos only — kantheon-only proto dirs pruned in S4 T2) |
| `shared/libs/kotlin/{otel-config, logging-config, ktor-configurator, db-common, data-formatter, fuzzy-common, whois-common, keycloak-auth}` | same paths | 
| `shared/libs/kotlin/ariadne-client` | `shared/libs/kotlin/ttr-meta-client` |
| `shared/libs/kotlin/llm-gateway-client` | `shared/libs/kotlin/ttr-llm-client` |
| `shared/libs/kotlin/query-translator` | `shared/libs/kotlin/query-translator` — **TEMPORARY, extracts to tatrman at SV-P1 gate 2** (contracts §7) |

**Stays kantheon:** charon (+charon-mcp), metis (+mcp), pinakes, kallimachos (+mcp), report-renderer, capabilities-mcp, all agents, frontends, kantheon-native libs (bff-base, capabilities-client, component-testkit, envelope-render, integration-harness, pattern-params, ariadne-client's kantheon consumers switch to the artifact in S5).

- [ ] **T1 — Freeze the donor.** Pick the kantheon commit to move from (clean master, all suites green); record the hash in findings (transplant-pin discipline). `git clone ~/Dev/collite-gh/kantheon /tmp/kantheon-donor && cd /tmp/kantheon-donor && git checkout <pin>`.
- [ ] **T2 — Filter.** On the throwaway clone, one `git filter-repo` invocation with a `--path` flag per source path in the table and a `--path-rename old:new` per renamed row (write the full command into findings before running; dry-run first with `--dry-run`). Result: a repo containing only the move set, renamed, with full history.
- [ ] **T3 — Graft.** In `tatrman-server`: `git remote add donor /tmp/kantheon-donor && git fetch donor && git merge --allow-unrelated-histories donor/master` (message: `SV-P0 S3: transplant spine from kantheon@<pin>, renamed on arrival`). `git log --follow services/veles/…` must show pre-move history.
- [ ] **T4 — Module wiring.** Extend `settings.gradle.kts` with every arrived module (mirror kantheon's `include(...)` lines, paths updated). Copy any missing version-catalog entries the moved builds reference. Do NOT fix code yet — expected state after T4: `./gradlew projects` lists all modules; `./gradlew build` FAILS on stale package/module references (S4's job).
- [ ] **T5 — Rename module dirs' internal identity.** Per arrived module: `build.gradle.kts` artifact/module names (`ariadne` → `veles`, etc. — grep each build file for its old name), Docker image names in build files if present. Keep proto/package renames OUT (S4).
- [ ] **T6 — health + backstage arrive (decided, RO-22: "nothing to paywall there").** Both are in the move set (rows above, names unchanged). Verify after the graft: kantheon-side consumers (agents' health-check endpoints, backstage catalog registrations) reference *deployed services*, not repo modules — list any kantheon build-time dependency on these two in findings (expected: none).
- [ ] **T7 — Python worker venv hygiene.** `workers/ttr-worker-polars` and `services/ttr-nlp` carry `.venv` dirs in kantheon's tree — confirm `.gitignore` excludes them in tatrman-server and none were carried by the filter (they shouldn't be — verify `git ls-files | grep -c '\.venv'` = 0).
- [ ] **T8 — Record + check the S3 row** in `00-task-management.md`; findings list: donor pin, filter-repo command used, module count arrived.

**Verify block:**
```bash
cd ~/Dev/collite-gh/tatrman-server
git log --follow --oneline services/veles | tail -5     # history reaches back before 2026-07-10
./gradlew projects | grep -c 'ttr-\|veles'              # all arrived modules listed
git ls-files | grep -c '\.venv'                         # 0
```

## Findings / ⚑
_(donor pin: … · filter-repo command: … · T6 kantheon build-time deps on health/backstage: expected none)_
