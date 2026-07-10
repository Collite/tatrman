# Z-P6 — Pipeline integration, explain, bundle, conform

> Pre-flight: Z-P5 done; served `ttr-metadata` backing available (ladder tests need a stoppable server; everything else runs on the repo backing). DoD: [`../plan.md`](../plan.md) §Z-P6 — which is the **Z 1.0 global DONE**.

## S1 · Pipeline stage, snapshot ladder, bundle {#s1}

Verify: front-half + emit + `ttr-optimizer` suites; `ttrp build` on the hero with `optimize=on` produces the D-plan bundle.

- [ ] **T1 (tests first).** Pipeline-stage tests (front-half/emit component suite): `"optimize=off ⇒ compiler output byte-identical to pre-Z"` (golden corpus, again — the stage must be a true no-op when off) · `"optimize=on, fully-pinned program ⇒ byte-identical bundle"` (invariant 3 + global DONE clause) · `"optimize=on, free hero ⇒ derived containers reach movement synthesis; synthesized movement matches the D-plan cuts"`.
- [ ] **T2 (tests first).** Snapshot/ladder tests: `"snapshot taken once at pass start"` (spy `MetadataSource`, assert single `statsSnapshot()` call) · `"no MetadataSource ⇒ TTRP-OPT-001, exit-mapped to build error"` · `"source lost after pass start ⇒ plan completes + TTRP-OPT-002 warning"` (fake backing that dies after first call) · `"served vs repo backing produce identical plans given identical stats content"` (fingerprint equality ⇒ plan equality).
- [ ] **T3 (tests first).** Bundle tests: `manifest.json` gains `stats` + `optimizer` blocks exactly per contracts §7 (schema-validate the JSON against a checked-in JSON-schema fixture); `"optimize=off ⇒ neither block present"`; hint-deviation entry appears when a `prefer` is deviated (fixture: prefer pg on the polars-optimal container).
- [ ] **T4.** Implement the stage: `OptimizeStage(config, metadataSource, registry)` between placement-check and movement synthesis; entry = ceiling + snapshot + ladder; exit = `PlanApplier` output + collected diagnostics; profile resolution (`makespan` builtin).
- [ ] **T5.** Implement bundle-manifest additions (emit module) + `TTR_CONN`-style pre-flight untouched (Z adds nothing to runtime).
- [ ] **T6.** Wire `[ttrp]` keys end-to-end (real loader replaces the Z-P0 map form; delete the marker) + CLI flags `--optimize/--profile/--budget` with precedence test.
- [ ] **T7.** Green; commit `Z-P6.S1: optimize stage wired; snapshot ladder; bundle blocks`.

## S2 · Explain, conform mode, golden plans, docs {#s2}

Verify: full repo test sweep (`./gradlew build` + pnpm suites); `ttrp explain` snapshot goldens; `ttrp conform --against-unoptimized` on the hero (dev harness).

- [ ] **T1 (tests first).** Explain payload tests: hero D-plan `ttrp explain` JSON == checked-in golden (contracts §8 shape — placements with `why` provenance, islands with `derived:true`, cuts with bytes/ms, rewrites with `savedMs`, objective with critical path + gap, fingerprints, alternatives ≥ 1 (all-PG)); `"explain is deterministic"` (two runs, identical bytes).
- [ ] **T2 (tests first).** LSP `ttrp/explain` extension test (integration-harness pattern): request over the hero doc returns the same payload as CLI (shared serializer — assert via fixture equality, not duplicated logic).
- [ ] **T3 (tests first).** Conform-mode test (harness module, marked slow/dev-profile): `ttrp conform --against-unoptimized` builds both bundles (pinned-A vs optimized-D), runs both, compares under the Q9 seven points — hero passes. (This is the one runtime-touching check; it lives in the conform harness, not in unit/component suites — tracker rule 4.)
- [ ] **T4.** Implement `Explainer` (solution + provenance + evaluator breakdowns → payload; alternatives = the evaluated named baselines: all-on-`bare-target`, fully-pinned-if-pins-exist) shared by CLI and LSP; wire `ttrp explain`/`ttrp/explain`.
- [ ] **T5.** Implement `ttrp-conform --against-unoptimized` mode (reads one source, builds twice with `optimize=off/on`, runs the existing comparison pipeline).
- [ ] **T6.** Designer budget setting: `ttrp/*` initialization option `optimizeBudget` honored by the LSP-side build path (maps to `SolverBudget`); component test with the WS harness.
- [ ] **T7.** Golden-plan suite: hero + er-flavored hero variant, pinned stats fixtures, snapshot-tested explain outputs registered in CI; add the golden home per architecture §8 (`packages/kotlin/ttr-optimizer/src/test/resources/golden/`).
- [ ] **T8.** Docs sweep: TTR-P `architecture.md` §10 deferred-register prunes Z (now points here); repo `CLAUDE.md` gains the `docs/ttr-p/optimizer/` pointer; `PUBLISHING.md` gains the two artifacts; optimizer `contracts.md` changelog entry. **Then run the Z-P6 phase review = Z 1.0 global DONE review** (tracker §Phase exit reviews). Commit `Z-P6.S2: explain + conform mode + goldens; Z 1.0 complete`.
