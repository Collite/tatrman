# Packages & Domains — Implementation Plan & Task Management

**Status:** Plan v1, 2026-06-19. This is the **overall task-management document** for the Packages & Domains increment (phases PD1–PD5). It does not contain implementation tasks itself — those live in the numbered `tasks-PD*.md` files in this directory, each a mini-task-list of 6–8 tasks, TDD-first, with checkboxes.

This increment finalises the v1.1 package model for the live `ai-models` consumer and adds domains. It builds **on top of** the shipped v1.1 work (sub-phases A–I); it is not a rewrite.

## Artifacts (read before any task)

1. **[Design §14](v1.1-packages-and-graphs.md#14-addendum-2026-06-19--nested-packages-finalised-root-prefix-no-cascade-domains)** — rationale and decisions B14–B23.
2. **[Architecture](architecture.md)** — component map, data flows, invariants.
3. **[Contracts §13](v1-1-contracts.md#13-packages--domains-increment-2026-06-19)** — canonical shapes (config, `DomainBlock`, diagnostics, artifact, agent-schema diff). **This wins over any snippet in a task file.**
4. **[Grammar-changes §9](grammar-v1-1-changes.md#9-addendum-2026-06-19--nested-packages-declaration-authority-and-the-ttrd-domain-file)** — ai-platform coordination for nesting + `.ttrd`.
5. **[CLAUDE.md](../../../../CLAUDE.md)** invariants — text-is-canonical, one-LSP, parser-mechanical, `SourceLocation` ANTLR-style.
6. **`ai-models` repo** (`~/Dev/ai-models`) — `agents/agent.schema.json`, `tools/validate_agents.py`, `agents/README.md`, `docs/agent-registry/02-contracts.md`, `model-ttr/`.

## Phase index

| Phase | Mini-task-list | Subject | Repo | Blocked by |
|---|---|---|---|---|
| PD1 ✅ | [`tasks-PD1-config-derivation.md`](tasks-PD1-config-derivation.md) | `[packages].root`/`layout`; derivation; no-cascade; mismatch + prefix-divergence dx | modeler | v1.1 A–B shipped |
| PD2 ✅ | [`tasks-PD2-ttrd-grammar-ast.md`](tasks-PD2-ttrd-grammar-ast.md) | `.ttrd` tokens, `domainBlock` grammar, `DomainBlock` AST, file-kind dispatch | modeler | v1.1 A shipped |
| PD3 | [`tasks-PD3-domain-semantics.md`](tasks-PD3-domain-semantics.md) | `DomainTable`, recursive closure, domain diagnostics | modeler | PD1, PD2 |
| PD4 | [`tasks-PD4-resolved-artifact.md`](tasks-PD4-resolved-artifact.md) | `modeler resolve-packages` CLI + deterministic artifact | modeler | PD1, PD3 |
| PD5 | [`tasks-PD5-aimodels-integration.md`](tasks-PD5-aimodels-integration.md) | Agent schema widen, `domains` field, validator vs artifact, `.ttrd` samples | ai-models | PD4 |

```
PD2 ─┐
     ├─ PD3 ─┐
PD1 ─┘       ├─ PD4 ─ PD5
PD1 ─────────┘
```

**Critical path:** PD1 → PD3 → PD4 → PD5. **Parallel:** PD2 starts immediately (only needs v1.1.A grammar); PD1 and PD2 run concurrently.

## Pre-flight (do once before PD1)

- [ ] Confirm v1.1 sub-phases A and B (grammar + package-aware semantics) are merged to `main`. PD work assumes package-prefixed qnames already exist.
- [ ] Branch `feat/packages-domains` from latest `main`.
- [ ] Confirm `~/Dev/ai-models` is checked out and its `validate-agents` CI passes on `main` *before* changes (baseline green).
- [ ] Confirm `context7` MCP responds for `antlr4ng` (grammar work) and `jsonschema`/`pyyaml` (PD5 validator).
- [ ] Read the six artifacts above.
- [x] **Q1 decided (2026-06-19): committed snapshot.** Modeler emits `resolved-packages.json`, **committed at `model-ttr/resolved-packages.json`**. The agent gate reads it directly (stays Node-free); a separate `validate-model-ttr.yml` runs `resolve-packages --check` to prevent drift. No "generate-in-the-agent-gate" path.

## Definition of DONE (whole increment)

- [ ] Every box in every `tasks-PD*.md` is ticked; this index mirrors that state.
- [ ] `modeler.toml` `[packages].root`/`layout` honoured end-to-end; a project with `root` set resolves both prefixed and bare references identically (regression fixture green).
- [ ] No-cascade rule enforced: leaf-only override is clean; prefix override raises `ttr/package-prefix-divergence`. Fixtures for both.
- [ ] `.ttrd` files parse, validate, and surface go-to-def/find-refs on members (reuses LSP infra; smoke test).
- [ ] Domain recursive closure correct: `domain D { packages: [a] }` over a project with `a`, `a.b`, `a.b.c` yields all three; `import a.*` still yields only `a` (non-recursion preserved — explicit test asserting the contrast).
- [ ] `modeler resolve-packages` emits a byte-deterministic artifact matching contracts §13.4; re-running with no change is a no-op diff.
- [ ] `ai-models`: agent schema accepts nested packages + `domains`; `validate_agents.py` validates against the artifact; existing `agents/*.yaml` still pass; a nested-package fixture and a `domains`-referencing fixture pass; a bad-domain fixture fails with a clear message.
- [ ] All Modeler CI green: `pnpm -r build && pnpm -r test && pnpm -r typecheck && pnpm -r lint`.
- [ ] `ai-models` CI green: `just check-agents`.
- [ ] [Grammar-changes §9](../../design/grammar-v1-1-changes.md#9-addendum-2026-06-19--nested-packages-declaration-authority-and-the-ttrd-domain-file) reviewed by ai-platform's parser maintainer (async sign-off OK; nesting + `.ttrd`-is-not-loaded are the points that matter to them).

## Contract-amendment discipline

Same as the parent v1.1 plan: if a task reveals a contract that must change, edit [contracts §13](../../design/v1-1-contracts.md#13-packages--domains-increment-2026-06-19) **first**, the task file **second**, the code **third**. Bump the contracts changelog. Never patch around a contract divergence in a task file.

## Open questions (carry into the phases)

1. ~~**Artifact production in CI.**~~ **RESOLVED 2026-06-19 — committed snapshot.** The artifact is committed at `model-ttr/resolved-packages.json` (non-ignored). The agent gate (`validate-agents.yml`) reads it directly and stays **Node-free**; a separate, path-filtered `validate-model-ttr.yml` runs `modeler resolve-packages --check` to block snapshot drift (only when `model-ttr/**` changes). See PD4.6 / PD5.6.
2. **`.ttrd` placement** — one `domains.ttrd` vs one-file-per-domain vs `domains/` dir. Default: one-file-per-domain (mirrors `.ttrg`). Settle in PD2.
3. **`shem.entities` future** — keep as raw escape hatch or deprecate in favour of domain `entities:`. Decide after PD5.
4. **`cnc` de-dup (B23)** — explicitly NOT in this increment. Tracked in design §14.7 Q4.
