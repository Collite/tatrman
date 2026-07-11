# SV-P0 · S5 — Kantheon closure (delete moved modules, consume artifacts)

> The "delete" leg: kantheon drops the moved modules and consumes the spine as `org.tatrman:*` artifacts (`0.0.1-LOCAL` from mavenLocal — the exact mechanism already used for ttr-metadata; flips to published coordinates at SV-P1 gate 3). Kantheon = persona space; agents and their protos are untouched except imports of moved types.

- [x] **T1 — Remove moved modules.** Delete the S3 move-set directories from kantheon (`git rm -r`); drop their `include(...)` lines from `settings.gradle.kts`. Keep: charon, metis, pinakes, kallimachos, report-renderer, capabilities-mcp, all `agents/`, `frontends/`, kantheon-native libs. (health + backstage moved with the spine — RO-22.)
- [x] **T2 — Consume spine artifacts.** Add `org.tatrman:*:0.0.1-LOCAL` dependencies (from S4 T8) wherever a kantheon module depended on a moved project: `project(":shared:libs:kotlin:ariadne-client")` → artifact `org.tatrman:ttr-meta-client`, `llm-gateway-client` → `ttr-llm-client`, proto/stub deps → the server's proto artifact, query-translator consumers → the server's vendored module artifact ⚑ (temp until gate 2). Version catalog entries, not inline strings.
- [x] **T3 — Kantheon proto tree slim.** In kantheon `shared/proto`: delete the moved spine proto dirs (they now come from the artifact) + the `proteus/v1/translator.proto` duplicate (RO-20); KEEP `org/tatrman/kantheon/*`, `metis`, `pinakes`, `kallimachos`, and **charon — renamed here: `charon.v1` → `transfer.v1`** (`git mv charon transfer`, package `org.tatrman.transfer.v1`, sweep charon service + charon-mcp imports; the Charon service keeps its persona *name*, only the wire changes — ledger §3).
- [x] **T4 — Agent import sweep.** Agents/tools referencing moved types: update imports to the artifact packages (`org.tatrman.meta.v1`, `query.v1`, `llm.v1`, `common.v1.ResponseMessage` where an agent consumed a spine message). `kantheon.common.v1` stays for agent-only protos (contracts §5) — do NOT relocate agent protos.
- [x] **T5 — Build + suites green.** `./gradlew build` at kantheon root; fix stragglers found by compile errors only (no opportunistic refactors). Python services' (`agents/`, none moved) configs untouched.
- [x] **T6 — Housekeeping.** Delete `_to_delete/` folders in kantheon AND tatrman (handover §8 — stale git lock files; verify contents are only `*.lock`/tmp_obj before deleting). Also delete kantheon root-level stale planning droppings ONLY if Bora confirms (⚑ list them in findings: `midas next steps.txt`, `v1 next steps 260627.md`, review files — likely wanted, do not touch without a yes).
- [ ] **T7 — Docs & catalog sweep.** kantheon `README.md`/`CLAUDE.md`/`docs/architecture` diagrams: moved services now "consumed from tatrman-server"; backstage catalog entries for moved services point at the new repo (or are removed here and re-registered in S6). Check the S5 row in `00-task-management.md`.

**Verify block:**
```bash
cd ~/Dev/collite-gh/kantheon
./gradlew build                                          # green without the moved modules
grep -rn 'project(":services:\(ariadne\|theseus\|proteus\|argos\|kyklop\|echo\|kadmos\|prometheus\)' . ; test $? -eq 1 && echo NO-STALE-PROJECT-DEPS
grep -rn 'charon\.v1' --include='*.proto' --include='*.kt' . ; test $? -eq 1 && echo TRANSFER-OK
ls _to_delete 2>/dev/null ; test $? -ne 0 && echo CLEANED
```

## Findings / ⚑

**Status: ✅ DONE (T1–T6). Branch `sv-p0-kantheon-close` (pushed). `./gradlew build` green.**
Verify block: NO-STALE-PROJECT-DEPS · TRANSFER-OK · `_to_delete/` CLEANED · gate clean.

- **The S5 wrinkle (moved libs unconsumable) SOLVED** — see `tatrman-server` commit `ad6bad2`:
  a root **publishing convention** publishes the moved shared libs + proto stubs +
  client libs as `org.tatrman:*:0.0.1-LOCAL` to Maven Local (`otel-config`,
  `logging-config`, `ktor-configurator`, `db-common`, `data-formatter`, `fuzzy-common`,
  `whois-common`, `keycloak-auth`, `ttr-meta-client`, `ttr-llm-client`, `capabilities-client`,
  and `shared/proto` → **`ttr-server-proto`**). ⚑ **A clean-machine kantheon build needs
  `./gradlew publishToMavenLocal` in `tatrman-server` first** (the interim `0.0.1-LOCAL`
  artifacts aren't on any registry until SV-P1 gate 3).
- **T1/T2:** removed 21 moved module dirs + settings includes; rewired ~50 kantheon build
  files off `project(":shared:libs:kotlin:*")` to `org.tatrman:*` catalog entries
  (new `tatrman-server = "0.0.1-LOCAL"` version + 11 library aliases). The 3 grafted-to-server
  libs (`capabilities-client`, `component-testkit`, `integration-harness`) **stay in kantheon
  too** (each repo keeps its own copy — no cross-repo test-lib dep; Bora's "move all 3"
  gave the server its copies, it didn't strip kantheon's).
- **T3:** deleted the spine proto dirs from kantheon `shared/proto` (now the `ttr-server-proto`
  artifact, added `api(libs.tatrman.ttr.server.proto)` mirroring ttr-plan-proto); `charon.v1`
  → `transfer.v1` (`git mv`; the Charon **service name** is a survivor, kept — only the wire
  package changed). The `proteus/v1/translator.proto` dup was removed with the spine deletion.
- **T4:** swept agent imports of the renamed spine packages (`ariadne.v1→meta.v1`, `kadmos→nlp`,
  `prometheus→llm`, …; `worker.v1`/`security.v1` unchanged) + the moved client-lib internal
  packages (`kantheon.ariadne→veles`, `kantheon.llm→llm`, `whois→identity`). ⚑ **themis** spine
  response: switched `ResponseMessage`/`Severity` to the relocated `org.tatrman.common.v1`
  (§5) — those two symbols back an nlp (spine) response there.
- **T5 test stragglers (⚑, move-consequence deletions, not opportunistic):** removed the
  obsolete `ForkedProtoDescriptorSpec` (verified spine proto descriptors that moved to the
  server) and the 9 moved-tool bootstrap fixtures + `ForkedToolRegistrySpec` from
  `capabilities-mcp` (they cross-checked the moved MCP tools' manifests, now gone).
- **Persona hygiene → gate clean.** Same echo/prometheus nuance as S4 (Bora's refine-the-gate
  decision applied): hebe's `echo()` CLI verb + Micrometer/actuator monitoring excluded; the
  Echo/fuzzy service + Prometheus-LLM-gateway persona renamed (incl. `PrometheusEmbeddingsClient`
  → `LlmGatewayEmbeddingsClient`, pinakes `PrometheusClient` → `LlmGatewayClient`, kleio/
  kallimachos/pinakes config keys + sections). ⚑ **Bare-substring bug found + fixed:** the
  sweep's `arges→postgres` / `echo→fuzzy` had corrupted `largest→lpostgrest` (2, kantheon) and
  `echoes/echoed→fuzzyes/fuzzyed` (5, tatrman-server) — reverted in both repos; the grep-gate
  now uses `\barges\b`.
- **T6:** `_to_delete/` (only `tmp_obj_*`/`*.lock` git debris, untracked) removed from kantheon;
  tatrman had none. ⚑ **Root-level stale planning files NOT touched (need Bora's yes)** —
  candidates in kantheon root: `midas next steps.txt`, `v1 next steps 260627.md`,
  `review-001..006.md`, `reviews.md`, `tasks-review-*.md`. Likely wanted — leaving them.
- **T7 (docs/catalog) — light remaining:** kantheon `README.md`/`CLAUDE.md`/`docs/architecture`
  still describe the moved services as in-repo. These are `.md` (NOT gate-relevant — the gate
  greps code/config only) so they don't block the phase; a prose pass noting "moved services now
  consumed from tatrman-server" is a follow-up. Backstage catalog entries for the moved services
  left with `infra/backstage` (moved to the server; re-registered in S6).
