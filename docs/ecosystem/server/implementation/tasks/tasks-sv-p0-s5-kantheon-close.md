# SV-P0 · S5 — Kantheon closure (delete moved modules, consume artifacts)

> The "delete" leg: kantheon drops the moved modules and consumes the spine as `org.tatrman:*` artifacts (`0.0.1-LOCAL` from mavenLocal — the exact mechanism already used for ttr-metadata; flips to published coordinates at SV-P1 gate 3). Kantheon = persona space; agents and their protos are untouched except imports of moved types.

- [ ] **T1 — Remove moved modules.** Delete the S3 move-set directories from kantheon (`git rm -r`); drop their `include(...)` lines from `settings.gradle.kts`. Keep: charon, metis, pinakes, kallimachos, report-renderer, capabilities-mcp, all `agents/`, `frontends/`, kantheon-native libs. (health + backstage moved with the spine — RO-22.)
- [ ] **T2 — Consume spine artifacts.** Add `org.tatrman:*:0.0.1-LOCAL` dependencies (from S4 T8) wherever a kantheon module depended on a moved project: `project(":shared:libs:kotlin:ariadne-client")` → artifact `org.tatrman:ttr-meta-client`, `llm-gateway-client` → `ttr-llm-client`, proto/stub deps → the server's proto artifact, query-translator consumers → the server's vendored module artifact ⚑ (temp until gate 2). Version catalog entries, not inline strings.
- [ ] **T3 — Kantheon proto tree slim.** In kantheon `shared/proto`: delete the moved spine proto dirs (they now come from the artifact) + the `proteus/v1/translator.proto` duplicate (RO-20); KEEP `org/tatrman/kantheon/*`, `metis`, `pinakes`, `kallimachos`, and **charon — renamed here: `charon.v1` → `transfer.v1`** (`git mv charon transfer`, package `org.tatrman.transfer.v1`, sweep charon service + charon-mcp imports; the Charon service keeps its persona *name*, only the wire changes — ledger §3).
- [ ] **T4 — Agent import sweep.** Agents/tools referencing moved types: update imports to the artifact packages (`org.tatrman.meta.v1`, `query.v1`, `llm.v1`, `common.v1.ResponseMessage` where an agent consumed a spine message). `kantheon.common.v1` stays for agent-only protos (contracts §5) — do NOT relocate agent protos.
- [ ] **T5 — Build + suites green.** `./gradlew build` at kantheon root; fix stragglers found by compile errors only (no opportunistic refactors). Python services' (`agents/`, none moved) configs untouched.
- [ ] **T6 — Housekeeping.** Delete `_to_delete/` folders in kantheon AND tatrman (handover §8 — stale git lock files; verify contents are only `*.lock`/tmp_obj before deleting). Also delete kantheon root-level stale planning droppings ONLY if Bora confirms (⚑ list them in findings: `midas next steps.txt`, `v1 next steps 260627.md`, review files — likely wanted, do not touch without a yes).
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
_(root-file cleanup list for Bora: … · any agent behavior surprises: …)_
