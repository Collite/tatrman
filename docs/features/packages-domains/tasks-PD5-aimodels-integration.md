# PD5 — `ai-models` integration (agent schema, validator, `.ttrd` samples)

**Goal:** make the `ai-models` repo consume nested packages and domains: widen the agent schema, add `shem.domains`, switch the CI validator from "package = top-level dir" to validating against the resolved-packages artifact, and author the first `.ttrd` domain files. Runtime domain expansion is `ai-platform`/Golem's job — this phase only documents that contract.

**Repo:** `~/Dev/ai-models` (NOT the modeler repo). **Reads:** [contracts §13.4–§13.5](../../design/v1-1-contracts.md#135-cross-repo-ai-models-agent-schema-diff-pd5), [design §14.5](../../design/v1.1-packages-and-graphs.md#145-impact-on-ai-models-b14-b21-b22), and in `ai-models`: `agents/agent.schema.json`, `tools/validate_agents.py`, `agents/_tests/run_cases.py`, `docs/agent-registry/02-contracts.md`.
**Blocked by:** PD4 (artifact exists + Q1 decided).
**Blocks:** nothing (increment tail).
**Estimated time:** 2–3 days.

## Tests-first

`ai-models` already has a fixture-driven validator suite (`agents/_tests/run_cases.py`) and `tools/gen_frontend_configmap/test_gen.py`. Add cases there first.

- [x] **Schema/validator fixtures** (`agents/_tests/`, against checked-in `agents/_tests/resolved-packages.json`):
  - valid `nested-package.yaml` (`shem.packages: [prodeje.regional]`) and `domain-only.yaml` (`shem.domains: [accounting]`, no packages); existing `ucetnictvi`/`testovani` still valid.
  - invalid `bad-package` (`does_not_exist`), `bad-domain` (`no_such_domain`), `no-shem-members` (anyOf violated → "any of"); `bad-entity-package`/`missing-package` retokenised to "not found in resolved-packages"; `bad-entity` (no dot) still fails schema.
  - 12 fixtures pass (was 9).
- [x] **`gen_frontend_configmap` test** — N/A: the generator emits only the FE agent list (`id`/`label`/`host`), **not** package/domain env vars (`GOLEM_*` are set by the platform ApplicationSet, contracts §3). No `test_gen.py` change; its 5 tests still pass.

## Library reference

`jsonschema>=4.21`, `pyyaml>=6` (already used). Confirm current APIs via Context7 if unsure:

```
mcp__context7__resolve-library-id { libraryName: "jsonschema", query: "Draft202012Validator anyOf, iter_errors" }
```

The validator structure to extend is `tools/validate_agents.py` (`validate_registry`, rules 2 & 3). The artifact shape is contracts §13.4.

## Implementation tasks

- [x] **PD5.1 — Widen `shem.packages`.** Pattern → `^[a-z0-9_]+(\.[a-z0-9_]+)*$`; description updated.
- [x] **PD5.2 — Add `shem.domains`.** Optional `domains` array; `shem` now `anyOf:[{required:[packages]},{required:[domains]}]`. `shem.entities` pattern widened to a full dotted qname (`^[a-z0-9_]+(\.[a-z0-9_]+)+$`) since artifact qnames are multi-segment. Docs updated (02-contracts §1.2/§1.3, README).
- [x] **PD5.3 — Validator consumes the artifact.** `ResolvedPackages.load` replaces `discover_packages`; `--resolved-packages` flag (default the committed snapshot). Rule 2 ⊆ canonical/declared package names; rule 3 ⊆ `entities[].qname` (no `split`); new rule 8 ⊆ `domains[].name`.
- [x] **PD5.4 — `.ttrd` files.** `model-ttr/domains/accounting.ttrd` + `obchod.ttrd` (one-per-domain). Parse clean via the modeler tooling. (Nested-package example covered in the test artifact, since the real `model-ttr` is still flat.)
- [x] **PD5.5 — Migrate an agent.** `ucetnictvi.yaml` → `shem.domains:[accounting]`; `testovani.yaml` kept on raw packages.
- [x] **PD5.6 — CI wiring.** `validate-agents.yml` reads the committed snapshot (Node-free); NEW `validate-model-ttr.yml` (path-filtered to `model-ttr/**`) runs `resolve-packages --check` (checks out Collite/modeler@master for the CLI; needs `MODELER_REPO_TOKEN`). `justfile` mirrors it + adds `resolve-packages`/`check-model-ttr` recipes; regen documented in `agents/README.md` and a new `model-ttr/README.md`. Committed snapshot is non-ignored.
- [x] **PD5.7 — Runtime expansion.** `02-contracts.md` §1.3 `GOLEM_DOMAINS` row + new §4.1 stating Golem expands domains → packages/entities at runtime on metadata refresh (not at CI/generation).

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

- [x] Every checkbox ticked (PD5 implemented in `~/Dev/ai-models`, branch `feat/packages-domains`).
- [x] Agent schema accepts nested dotted packages and `shem.domains`; `anyOf` requires at least one of packages/domains.
- [x] `validate_agents.py` validates names against the resolved-packages artifact; no directory-listing, no `split(".",1)`.
- [x] Two `.ttrd` domains authored (`accounting`, `obchod`); `ucetnictvi` migrated to `domains:`; `testovani` kept on raw packages.
- [x] Agent gate stays Node-free and reads the committed snapshot; separate `validate-model-ttr.yml` guards freshness with `resolve-packages --check`.
- [x] `GOLEM_DOMAINS` + runtime-expansion documented; `just check-agents` green (validate-agents + 12 fixtures + 5 configmap).
