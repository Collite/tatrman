# PD5 — `ai-models` integration (agent schema, validator, `.ttrd` samples)

**Goal:** make the `ai-models` repo consume nested packages and domains: widen the agent schema, add `shem.domains`, switch the CI validator from "package = top-level dir" to validating against the resolved-packages artifact, and author the first `.ttrd` domain files. Runtime domain expansion is `ai-platform`/Golem's job — this phase only documents that contract.

**Repo:** `~/Dev/ai-models` (NOT the modeler repo). **Reads:** [contracts §13.4–§13.5](../../design/v1-1-contracts.md#135-cross-repo-ai-models-agent-schema-diff-pd5), [design §14.5](../../design/v1.1-packages-and-graphs.md#145-impact-on-ai-models-b14-b21-b22), and in `ai-models`: `agents/agent.schema.json`, `tools/validate_agents.py`, `agents/_tests/run_cases.py`, `docs/agent-registry/02-contracts.md`.
**Blocked by:** PD4 (artifact exists + Q1 decided).
**Blocks:** nothing (increment tail).
**Estimated time:** 2–3 days.

## Tests-first

`ai-models` already has a fixture-driven validator suite (`agents/_tests/run_cases.py`) and `tools/gen_frontend_configmap/test_gen.py`. Add cases there first.

- [ ] **Schema fixtures** (`agents/_tests/`): add cases —
  - nested package: `shem.packages: [prodeje.regional]` → **valid** against the widened schema.
  - `shem.domains: [accounting]` with no `shem.packages` → **valid** (anyOf packages|domains).
  - neither `packages` nor `domains` → **invalid** (anyOf violated).
  - legacy flat: every existing `agents/*.yaml` (`ucetnictvi`, `testovani`) → still **valid**.
- [ ] **Validator fixtures** (against a checked-in test artifact `agents/_tests/resolved-packages.json`):
  - `shem.packages: [does_not_exist]` → fails with "not found in resolved-packages".
  - `shem.domains: [no_such_domain]` → fails.
  - `shem.entities: [artikl.er.entity.artikl]` present in the artifact → passes; absent → fails.
  - nested `shem.packages: [prodeje.regional]` present in artifact → passes (proves no `split(".",1)` mis-parse).
- [ ] **`gen_frontend_configmap` test** — if the generator emits package/domain env vars, extend `test_gen.py` to assert `GOLEM_DOMAINS` is emitted (comma-joined) and `GOLEM_PACKAGES` still emitted; domains are **not** expanded at generation time (runtime does that).

## Library reference

`jsonschema>=4.21`, `pyyaml>=6` (already used). Confirm current APIs via Context7 if unsure:

```
mcp__context7__resolve-library-id { libraryName: "jsonschema", query: "Draft202012Validator anyOf, iter_errors" }
```

The validator structure to extend is `tools/validate_agents.py` (`validate_registry`, rules 2 & 3). The artifact shape is contracts §13.4.

## Implementation tasks

- [ ] **PD5.1 — Widen `shem.packages`.** In `agents/agent.schema.json`, change the `packages.items.pattern` from `^[a-z0-9_]+$` to `^[a-z0-9_]+(\.[a-z0-9_]+)*$`. Update the field description.
- [ ] **PD5.2 — Add `shem.domains`.** Add the optional `domains` array (pattern `^[a-z0-9_]+$`, `uniqueItems`) per contracts §13.5. Replace `shem.required: ["packages"]` with `anyOf: [{required:[packages]},{required:[domains]}]`. Update `docs/agent-registry/02-contracts.md` §1.2/§1.3 (`GOLEM_DOMAINS` derived value) and `agents/README.md`.
- [ ] **PD5.3 — Validator consumes the artifact.** In `tools/validate_agents.py`: add `--resolved-packages <file>` (default `agents/_tests/resolved-packages.json` for tests, the committed `model-ttr` snapshot for CI — per PD4 Q1). Replace `discover_packages` (dir-listing) with loading the artifact. Rewrite rule 2 to check `shem.packages` ⊆ artifact package names (canonical or declared). Rewrite rule 3 to check `shem.entities` ⊆ artifact `entities[].qname` — **delete the `split(".",1)` logic**. Add rule 8: `shem.domains` ⊆ artifact `domains[].name`.
- [ ] **PD5.4 — Author first `.ttrd` files.** In `model-ttr/`, create domain definitions (one file per domain per PD2 Q2 default) covering the domains agents currently approximate via package lists — at minimum `accounting` (`ucetnictvi` + `obchodni_doklady`) and one nested example once a nested package exists. Validate they parse with the modeler tooling.
- [ ] **PD5.5 — Migrate an agent to `domains`.** Convert one existing agent (e.g. `ucetnictvi.yaml`) to reference `domains: [accounting]` instead of/alongside `packages:`, proving the end-to-end path. Keep `testovani.yaml` on raw packages as the escape-hatch regression.
- [ ] **PD5.6 — CI wiring + artifact freshness (two workflows; keep the agent gate Node-free).** The committed artifact is `model-ttr/resolved-packages.json` (committed, non-ignored). Because `model-ttr/` is inside the `ai-models` repo, split CI so the hot path stays Node-free:
  - **`validate-agents.yml` (existing, Node-free):** pass `--resolved-packages model-ttr/resolved-packages.json`; it only reads the committed JSON + jsonschema/pyyaml. No Node/Modeler. This is the gate every agent PR hits.
  - **`validate-model-ttr.yml` (NEW, may use Node):** path-filtered to `model-ttr/**`; runs `modeler resolve-packages --check --out model-ttr/resolved-packages.json` to fail if the committed snapshot is stale. Only runs when the model changes, so BAs editing `agents/**` never pay the Node cost.
  Mirror the `validate-agents` change in `justfile`; document the regen command (`modeler resolve-packages model-ttr --out model-ttr/resolved-packages.json`) in `agents/README.md` and `model-ttr`'s README.
- [ ] **PD5.7 — Document runtime expansion.** In `docs/agent-registry/02-contracts.md`, add the derived-value rows for `GOLEM_DOMAINS` and state explicitly that Golem expands domains → packages/entities **at runtime** on metadata refresh (not at CI/generation). This is the `ai-platform` contract; no code here.

## Verify by running

```bash
cd ~/Dev/ai-models
just validate-agents      # uv run tools/validate_agents.py
just test-agents          # fixture suite
just test-configmap
just check-agents         # all of the above
```

All exit 0. New nested-package and domains fixtures pass; the bad-domain / bad-package fixtures fail with clear messages; existing agents still validate.

## DONE when

- [ ] Every checkbox ticked.
- [ ] Agent schema accepts nested dotted packages and `shem.domains`; `anyOf` requires at least one of packages/domains.
- [ ] `validate_agents.py` validates names against the resolved-packages artifact; no directory-listing, no `split(".",1)`.
- [ ] At least two `.ttrd` domains authored in `model-ttr/`; one agent migrated to `domains:`; one agent kept on raw packages.
- [ ] Agent gate (`validate-agents.yml`) stays Node-free and reads the committed `model-ttr/resolved-packages.json`; a separate `validate-model-ttr.yml` guards freshness with `resolve-packages --check`.
- [ ] `GOLEM_DOMAINS` + runtime-expansion contract documented; existing `ai-models` CI (`just check-agents`) green.
