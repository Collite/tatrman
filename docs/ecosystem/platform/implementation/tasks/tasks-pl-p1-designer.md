# PL-P1 (②) — Reader Designer + OpenMetadata export (stages S8–S9)

> **[superseded 2026-07-10 · STRAT-3]** license is Apache-2.0 across the open surface; MIT mentions below are historical.

> Pre-flight: S6–S7 deployed locally (a live Veles to point at). DoD: [`../plan.md`](../plan.md) §PL-P1. Check each box the moment its task is done. S8 is `tatrman` (MIT Designer + Extensions surface — Q-4-a/P1: nothing platform-only may leak into the MIT shell). S9 spans both repos (adapter panel work MIT; export organ + connector platform).

## S8 · Veles backend adapter + Designer Extensions {#s8}

Verify: `pnpm --filter @tatrman/designer test` green; `pnpm --filter @tatrman/designer dev` with `?server=<veles-ws-url>` renders catalog + model graph from the fixture world; worker and loopback modes verifiably load **zero** extensions.

- [ ] **T1 (tests first).** Vitest `VelesDataSource.test.ts` against a mocked WS: `"implements ModelDataSource: getModelIndex/getModelGraph/getObject/search resolve"` · `"sends Authorization: Bearer <token> on the WS handshake"` · `"capabilities.edit === false"` · `"onModelChanged fires on ttrm/modelChanged notification"` · `"JSON-RPC error -32000 surfaces as a typed NotLoaded state, not a throw"`.
- [ ] **T2 (tests first).** `extensions.test.ts`: `"loader fetches /v1/designer/extensions and dynamic-imports each moduleUrl"` (mock ESM module with an `activate` spy) · `"activate receives {dataSource, backend, auth}"` · `"worker/loopback backends load nothing"` · `"a failing extension is isolated (logged, shell unaffected)"`.
- [ ] **T3.** Implement `VelesDataSource` in `@tatrman/designer` (extends the MD6 adapter family beside `WsDesignerServerDataSource`): same protocol, plus bearer auth (token from explicit config/URL param; static token is v1 — leave `// PL-P1: IdP flow post-v1 ⚑` marker) and `backend.kind = 'veles'`. Selection stays explicit (`?server=`), never sniffed.
- [ ] **T4.** Implement the **Designer Extensions surface** (contracts §10) as `@tatrman/designer/extensions` entry point: the three interfaces verbatim + the loader from T2. This file set is the MIT contract — changes require a contracts.md changelog entry (tell the reviewer).
- [ ] **T5.** Wire catalog + model-graph panels over `VelesDataSource` (they already speak `ModelDataSource`; fix row-less-box enrichment gaps via `getObject` as in WS mode). Manual check against local Veles; screenshot in the PR.
- [ ] **T6.** i18n + theme-token sweep of anything touched (house frontend rules: no bare English, no hex literals, scoped styles).
- [ ] **T7.** Run Verify, check tracker boxes, commit `PL-P1.S8: VelesDataSource + Designer Extensions surface`.

## S9 · TTR-P program graphs + OpenMetadata export {#s9}

Verify: `pnpm --filter @tatrman/designer test` + `./gradlew :services:veles:test :connectors:ttr-connect-openmetadata:test` green; the conformance-lane job exports the hero catalog + lineage into a dockerized OpenMetadata (pinned version) and re-runs **byte-identically** (I-3 determinism).

- [ ] **T1 (tests first).** Vitest `program-graph-adapter.test.ts`: hero `manifest-v2-hero.json` fixture (from PL-P0) → render model: `"islands become nodes labeled engine/executor, transfers become edges via staging"` · `"waves render as columns/levels"` · `"onFailureOf renders as a distinct error edge"` · `"lineage section is NOT rendered here"` (separate panel, PL-P2 feeds it).
- [ ] **T2.** Read-only **TTR-P program-graph panel** in `@tatrman/designer` (MIT — it renders a documented MIT contract, §6): manifest fetched via Veles (`getObject`-adjacent route or the stored-manifest fixture endpoint from S7.T4).
- [ ] **T3 (tests first).** Kotest `OmMappingTest.kt` in `connectors/ttr-connect-openmetadata` (platform repo): TTR fixture table/columns → `CreateTableRequest` JSON **golden** (name mangling per contracts §12 rule 1 reused; FQN = `<service>.<db>.<schema>.<table>`) · E-5 `lineage.columns[]` → `PUT /v1/lineage` payload golden (`fromColumns`/`toColumn` FQNs, one edge per output column, `pipeline` ref = the envelope identity — stub value until PL-P2) · `"same projection ⇒ same bytes"` (determinism replay).
- [ ] **T4.** **Export organ** in Veles: `Projection` builder (catalog entities from `MetadataQuery`; column lineage from stored manifest sections) + connector scheduling seam (manual trigger endpoint only — `// PL-P7: refresh organ drives this`).
- [ ] **T5.** Implement `ttr-connect-openmetadata` **export half** on the Connector SPI (contracts §18): OM REST client (JWT bot token via `authRef`→SecretRef — K8s secret in deploy, static env in tests; see tracker Library card for the verified endpoints `PUT /api/v1/tables`, `PUT /api/v1/lineage`). Capabilities: `{EXPORT_CATALOG, EXPORT_LINEAGE}`.
- [ ] **T6.** Write the **IQ-4 lossiness list** as `connectors/ttr-connect-openmetadata/MAPPING.md`: minimum per contracts §18 — FS/SS → job dependencies (flattened), world/manifest structure → custom properties, TTR types → OM approximations (table them), `security` blocks → not exported. Normative for DataHub later.
- [ ] **T7.** Conformance-lane CI job: docker OM (pinned tag), seed fixture world, run export, assert entities + lineage via OM's read API; re-run export, assert no diff (idempotent create-or-update).
- [ ] **T8.** Run Verify, check ALL tracker boxes for PL-P1, run the plan-level DoD checklist ([`../plan.md`](../plan.md) §PL-P1) end-to-end on the local deploy, commit `PL-P1.S9: program graphs + OpenMetadata export`, and request the PL-P1 `/review`.
