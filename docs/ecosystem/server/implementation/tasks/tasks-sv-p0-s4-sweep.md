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
_(published artifact list: … · any behavior-change flags: …)_
