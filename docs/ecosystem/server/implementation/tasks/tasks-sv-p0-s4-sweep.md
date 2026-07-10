# SV-P0 · S4 — Rename & proto sweep (tatrman-server side)

> The wire-surface half of rename-on-arrival: proto packages per contracts §2, the `common.v1` relocation (§5), the `llm.v1` convergence (§6), and Kotlin package/import updates until the whole repo builds green. Same change window as S3.

- [ ] **T1 — Proto package sweep.** In `shared/proto/src/main/proto/org/tatrman/`: `git mv` each spine proto dir to its final name and update `package` + `option java_package` per the contracts §2 map (ariadne→meta, theseus→query, proteus→translate, argos→validate, kyklop→dispatch, echo→fuzzy, kadmos→nlp, prometheus→llm; worker + security unchanged). Update every cross-proto `import` path. Delete `proteus/v1/translator.proto` (the enum duplicate — the service proto now imports `org.tatrman.translate.v1` enums from the `ttr-plan-proto` artifact; add that artifact to `shared/proto`'s proto dependencies the same way kantheon consumes ttr-metadata).
- [ ] **T2 — Prune kantheon-only protos.** Remove from tatrman-server's `shared/proto`: the whole `org/tatrman/kantheon/` tree, `charon`, `metis`, `pinakes`, `kallimachos` (they stay kantheon-owned). `buf.yaml` module config updated if it enumerates paths.
- [ ] **T3 — `common.v1` relocation (contracts §5).** New `org/tatrman/common/v1/response_message.proto`, content verbatim from kantheon's `kantheon/common/v1/response_message.proto`, package `org.tatrman.common.v1`. Sweep all eight spine protos: `import "org/tatrman/kantheon/common/v1/response_message.proto"` → the new path; type refs `kantheon.common.v1.ResponseMessage` → `common.v1.ResponseMessage`. The S1 dependency-rule script's proto check now has real teeth — run it.
- [ ] **T4 — `llm.v1` convergence (contracts §6).** `prometheus_chat.proto` → `llm/v1/llm_gateway.proto`; package `org.tatrman.llm.v1`; service `PrometheusService` → `LlmGatewayService`; drop the `java_outer_classname` option; keep kantheon's superset content (Chat + Embed) intact.
- [ ] **T5 — Kotlin sweep.** Repo-wide: update packages/imports for every renamed proto package and module (`grep -rn -E 'tatrman\.(ariadne|theseus|proteus|argos|kyklop|echo|kadmos|prometheus)\.' --include='*.kt'` must land at 0); service main classes, Ktor route registrations, gRPC stub references, `application.conf`/env prefixes that carry old names. Mechanical, large — commit in per-service slices to keep review sane.
- [ ] **T5b — MCP capability manifests (RO-25).** In each MCP door's `resources/manifests/tools/*.yaml`: `capability_id` + `category` go functional per the mcp-surface contract §2.1 (`theseus.query:v1`→`query.run:v1`, `theseus.compile:v1`→`query.compile:v1`, `ariadne.*`→`meta.*`, `echo.match:v1`→`fuzzy.match:v1`); `service_endpoint` hosts follow the N1 service renames. Tool short names unchanged. Update the CapabilitiesRegistration specs' expectations with the sweep.
- [ ] **T6 — Config/env vocabulary.** Per J-v2 guardrails: no persona in env-var prefixes. Sweep `*.conf`, `*.yaml`, Dockerfiles in the moved modules (`ARIADNE_*` → `VELES_*` is legal — Veles is a surviving persona — but `THESEUS_*` → `TTR_QUERY_*` etc.). Keep the `roleSource: bearer|whois` config vocabulary verbatim (ledger §3 note).
- [ ] **T7 — Tests green.** Per-service test suites, then `./gradlew build` at root. Component tests referencing old proto type names get updated with the sweep, never deleted; any test that can't be made green without behavior change → ⚑ findings, stop.
- [ ] **T8 — Publish interim artifacts.** `./gradlew publishToMavenLocal` (all modules, `0.0.1-LOCAL` — interim-local only; **the 0.9.x line per RO-24 enters at SV-P1's publish gates**, nothing local carries it early) — S5's kantheon rebuild consumes these. Record artifact list in findings. Check the S4 row.

**Verify block:**
```bash
cd ~/Dev/collite-gh/tatrman-server
./gradlew build
bash scripts/check-dependency-rules.sh && echo RULES-OK
grep -rn -iE 'ariadne|theseus|proteus|argos|kyklop|arges|brontes|steropes|echo\b|kadmos|prometheus' \
  --include='*.proto' --include='*.kt' --include='*.kts' --include='*.conf' --include='*.yaml' . \
  | grep -viE 'CHANGELOG|history|docs/|lore|Forked 2026' | head    # expect: empty
ls ~/.m2/repository/org/tatrman/ | grep ttr-                        # interim artifacts present
```

## Findings / ⚑

**Status: JVM BUILD GREEN (T1–T5 core done); persona-string hygiene (T5b/T6 + file/comment
renames) IN PROGRESS.** Branch `sv-p0-move` (pushed). Commits: `57d828e` proto+package sweep
· `2881a36` build-green (artifact pins, fingerprints graft, shadowJar/ktlint fixes) ·
`4591c26` config package refs.

- **T1–T4 (proto) DONE.** 8 spine proto packages renamed functional; `proteus/v1/translator.proto`
  deleted (enums from the `ttr-plan-proto` artifact under `translate.v1`); `common.v1` +
  `capabilities.v1` relocated out of the kantheon namespace; all kantheon-only protos pruned
  (charon/metis/pinakes/kallimachos + `kantheon/*`); gRPC services + `java_outer_classname`s
  renamed; llm gateway dropped its outer classname. `shared/proto` builds green.
- **T5 (Kotlin) core DONE.** Repo-wide package/import + gRPC-stub-class substitution (477 .kt/
  .kts, then config .conf/.yaml too); source dirs `git mv`'d to match. **`./gradlew build` GREEN**,
  `check-dependency-rules.sh` → RULES-OK, all module tests pass.
- **Interim artifact pins (SV-P0 window):** `ttr-plan-proto` + `ttr-translator` pinned to
  `0.0.1-LOCAL` (mavenLocal) in the catalog — the 0.8.5 published artifacts still carry
  `proteus.v1`, which mismatches the renamed service protos. Reverts to the 0.9.x line at
  SV-P1 gate 2. **⚑ Requires `./gradlew :packages:kotlin:{ttr-plan-proto,ttr-translator}:publishToMavenLocal`
  in the tatrman repo before a clean-machine build** (S2 branch `sv-p0-server-fork`).
- **⚑ Two more move-set gaps found + grafted (history-preserving) beyond the S3 table:**
  `shared/testdata/fingerprints/` (workers' cross-engine oracle) and `capabilities-client`
  published under `groupId=cz.tatrman` (fixed → `org.tatrman`; would have violated the
  dependency rule). `ttr-query-mcp` fat jar needed `isZip64=true` (>65535 entries).
- **⚑⚑ REMAINING — persona-string hygiene for the grep gate (NOT done):** ~14 config files
  (env prefixes `THESEUS_*`→`TTR_QUERY_*`, gRPC hosts, HOCON section keys — the keys are
  coupled to `config.getConfig("proteus")` call sites, so .conf + .kt must move together),
  39 k8s manifests (image/service/label/env names — coordinates with S6/olymp), 26 persona-named
  `.kt` files to `git mv` (e.g. `TheseusServiceImpl.kt`→`QueryServiceImpl.kt`), proto/kotlin
  comments, `infra/backstage` catalog, and T5b MCP capability manifests.
- **⚑⚑ GATE FINDING — the persona regex over-matches on `echo` and `prometheus`:**
  - `echo\b` matches the English verb (e.g. `WriteOutcome.kt` "columns **echo** the list") —
    not the Echo/fuzzy service.
  - `prometheus` matches Micrometer's **`PrometheusMeterRegistry` / `PrometheusConfig`**
    (the real monitoring system — used in veles, ttr-query-mcp, infra/health, everywhere) —
    not the retired LLM-gateway persona.
  A blind replacement of these two words would break the build (real class names) and corrupt
  prose. **Decision needed (affects ledger §5 / S6 T5 gate definition):** refine the gate regex
  to exclude `PrometheusMeterRegistry|PrometheusConfig|prometheus:9090|/metrics|actuator/prometheus`
  and the echo-verb, **or** accept a documented allow-list of legitimate `echo`/`Prometheus`
  hits. The retired-persona instances (Echo service; "Prometheus LLM gateway" comments/image
  names) still get renamed regardless.

---
### (original plan, retained for reference)

**Pre-work done + scope fully mapped on branch `sv-p0-move`.** The kantheon-only-lib decision (Bora: move all 3) is executed — commit
`97a60d7` grafts `capabilities-client` + `component-testkit` + `integration-harness`
history-preserving from kantheon. Branch pushed to `origin/sv-p0-move`.

- ⚑ **CASCADE from moving `capabilities-client` — `capabilities.v1` must relocate.**
  `capabilities-client` does `api(project(":shared:proto"))` and uses generated classes from
  `org/tatrman/kantheon/capabilities/v1/capabilities.proto`. But S4 T2 prunes the whole
  `org/tatrman/kantheon/` tree, and the dependency rules forbid tatrman-server protos in the
  kantheon namespace. **Resolution (mirrors the §5 `common.v1` pattern): relocate
  `org/tatrman/kantheon/capabilities/v1/capabilities.proto` →
  `org/tatrman/capabilities/v1/capabilities.proto`, package `org.tatrman.capabilities.v1`;
  both repos keep a copy (kantheon agents keep theirs).** "capabilities" is already a
  functional name (no persona) — only the `kantheon` namespace segment moves. Add this as a
  T3-sibling step in the actual sweep.
- **Proto inventory in the grafted `shared/proto` (survey done):**
  - **Rename (T1)** per contracts §2: `ariadne→meta`, `theseus→query`, `proteus→translate`
    (service proto `proteus.proto`→`translate.proto`), `argos→validate`, `kyklop→dispatch`,
    `echo→fuzzy` (`echo_service.proto`), `kadmos→nlp`; `security`+`worker` unchanged.
  - **Delete (T1):** `proteus/v1/translator.proto` (enum dup) — the translate service proto
    imports `org.tatrman.translate.v1` enums from the `ttr-plan-proto` artifact, which is
    ALREADY `api(libs.tatrman.ttr.plan.proto)` in `shared/proto/build.gradle.kts`. Good.
  - **llm.v1 (T4):** `prometheus/v1/prometheus_chat.proto` →
    `llm/v1/llm_gateway.proto`, package `org.tatrman.llm.v1`, service
    `PrometheusService`→`LlmGatewayService`, drop `java_outer_classname`, keep Chat+Embed.
  - **common.v1 (T3):** `kantheon/common/v1/response_message.proto` →
    `org/tatrman/common/v1/response_message.proto`. **Every spine proto imports
    `org/tatrman/kantheon/common/v1/response_message.proto`** (ariadne/theseus/proteus/argos/
    kyklop/kadmos confirmed; echo/prometheus don't) — all switch to the new path + type ref.
  - **Prune (T2):** `charon`, `metis`, `pinakes`, `kallimachos`, and the rest of
    `org/tatrman/kantheon/*` (_smoke, common/handoff, envelope, golem, hebe, iris, kleio,
    midas, pythia, report, sysifos, themis) — kantheon-owned.
  - Cross-proto imports to rewrite: `theseus`→(ariadne, proteus, translator); `kyklop`→ariadne.
- **Also fold in (from S3 T5, deferred):** old-name `project(...)` cross-refs in build files —
  `ttr-fuzzy-mcp`→`:services:echo`; `ttr-meta-mcp`→`:services:ariadne`+`ariadne-client`;
  `ttr-query-mcp`→`:services:theseus` — retarget to arrival names.
- **The big remaining piece = T5 Kotlin sweep:** internal source packages of the moved code
  are `org.tatrman.kantheon.<persona>.*` / `org.tatrman.whois.*` / `org.tatrman.kantheon.
  <worker>.*` plus every generated-proto import. Repo-wide, ~26 modules — the bulk of S4.
  Recommend per-service commit slices; build goes green only at T7.
