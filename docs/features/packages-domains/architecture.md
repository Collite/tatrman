# Packages & Domains — Architecture

**Status:** v1, 2026-06-19. Architecture artifact for the Packages & Domains increment. Design rationale: [`docs/v1-1/design/v1.1-packages-and-graphs.md` §14](../../v1-1/design/v1.1-packages-and-graphs.md#14-addendum-2026-06-19--nested-packages-finalised-root-prefix-no-cascade-domains). Canonical shapes: [`docs/v1-1/design/v1-1-contracts.md` §13](../../v1-1/design/v1-1-contracts.md#13-packages--domains-increment-2026-06-19). Cross-repo coordination: [`grammar-v1-1-changes.md` §9](../../v1-1/design/grammar-v1-1-changes.md#9-addendum-2026-06-19--nested-packages-declaration-authority-and-the-ttrd-domain-file).

## 1. What this increment delivers

A finalisation of the v1.1 package model so it works for the live `ai-models` consumer, plus a new **domain** concept. Five capabilities:

1. **Nested packages, finalised** — multi-segment dotted packages are a confirmed requirement (B14); tooling stops assuming a single path segment anywhere.
2. **Declaration-authoritative packages** — the in-file `package` declaration is the source of truth; the directory is a checked convention whose mismatch severity is configurable (B15, B16).
3. **Configurable root prefix** — optional `[packages].root`, Go module-style, elidable in references (B17), with a no-cascade derivation rule (B18).
4. **Domains** — a new TTR-native `.ttrd` file kind: named, recursive groupings of packages (+ entities) used to scope downstream consumers (B19, B20).
5. **Cross-repo wiring** — a deterministic **resolved-packages artifact** Modeler emits (B22), consumed by `ai-models` CI; agent schema widened to accept nested packages and `domains`; domain→package expansion happens at Golem **runtime** (B21).

Out of scope (tracked separately): the `cnc.cnc.*` de-duplication (B23, design §14.7 Q4); multi-classpath-root (design §13.3).

## 2. Component map

```
                    modeler.toml [packages]{root, layout}
                              │
              ┌───────────────┴────────────────┐
              ▼                                 ▼
   @modeler/semantics                     @modeler/semantics
   derivation + effectivePackage()        Domain symbol table
   (PD1)                                  + recursive membership
              │                           + .ttrd validation (PD3)
              ▼                                 ▲
   package-prefixed qnames,                     │
   mismatch / prefix-divergence dx        @modeler/parser
              │                           DomainBlock AST +
              │                           .ttrd file-kind dispatch (PD2)
              ▼                                 │
   ┌─────────────────────────────────────────────────────┐
   │  modeler resolve-packages  (CLI, PD4)                │
   │  → .modeler/resolved-packages.json (deterministic)   │
   └─────────────────────────────────────────────────────┘
              │
              ▼  committed into model-ttr/ (snapshot; ai-models CI reads it)
   ai-models repo (PD5)
   ├── agents/agent.schema.json   (packages regex widened; +domains)
   ├── tools/validate_agents.py   (validate vs artifact, not dirs)
   └── model-ttr/*.ttrd           (domain definitions, model-owned)
              │
              ▼  GOLEM_PACKAGES / GOLEM_DOMAINS
   Golem runtime expands domains → packages/entities on metadata refresh
```

## 3. Where each piece lives

| Concern | Package / repo | Notes |
|---|---|---|
| `[packages].root` / `layout` config | `@modeler/lsp` project-info loader; mirrored in `@modeler/migrate` | Contract: contracts §13.1 |
| Derivation + `effectivePackage()` + no-cascade | `@modeler/semantics` | The only place the root prefix and cascade rule are implemented; symbol table calls it |
| Mismatch / prefix-divergence diagnostics | `@modeler/semantics` validator | Severity from `layout` knob |
| `.ttrd` tokens + `DomainBlock` AST + dispatch | `@modeler/grammar` (`TTR.g4`), `@modeler/parser` | Shares engine with `.ttr`/`.ttrg` |
| Domain symbol table + recursive closure + domain diagnostics | `@modeler/semantics` | New `DomainTable`; recursion confined here |
| `resolve-packages` CLI + artifact | `@modeler/migrate` (or new `@modeler/cli`) | Deterministic JSON, contracts §13.4 |
| Agent schema + validator + `.ttrd` authoring | `ai-models` repo | PD5; consumes the artifact |
| Runtime domain expansion | `ai-platform` Golem bootstrap | Out of Modeler's code; documented contract only |

## 4. Key data flows

**Authoring a package (PD1).** File declares `package X` → semantics computes `derivedPackage()` from `root + path` → if declaration present it wins; mismatch raises a config-severity diagnostic; prefix-divergence raises the louder one. Symbol qnames are always canonical (root-prefixed); references may elide `root`.

**Authoring a domain (PD2–PD3).** `.ttrd` parsed → `DomainBlock` AST → `DomainTable` resolves each `packages:` member to its recursive closure and each `entities:` member via the standard resolver → unresolved members raise `ttr/domain-member-not-found`; duplicate names raise `ttr/duplicate-domain`.

**Emitting the artifact (PD4).** CLI walks the project, builds the project symbol table + domain table, serialises `ResolvedPackagesArtifact` deterministically → `.modeler/resolved-packages.json`.

**Consuming downstream (PD5).** `ai-models` CI reads the artifact; validates every `shem.packages`/`shem.domains`/`shem.entities` name exists; **does not** expand domains (runtime does). Golem, on metadata refresh, expands `domains` → concrete packages/entities in-process.

## 5. Invariants this increment must preserve

- **Text is canonical** (CLAUDE.md). The artifact is a derived projection, never a source of truth; it is regenerable and gitignored by default.
- **One LSP across hosts.** All new language logic (derivation, domains) lives in `parser`/`semantics`; no per-host logic.
- **Parser stays mechanical.** `.ttrd` parsing mirrors `.ttrg`: grammar accepts, semantics enforce file-kind. No resolution logic in `@modeler/parser`.
- **`root` elision is total.** Anywhere a reference is accepted (TTR text, `.ttrd` members, agent YAML), omitting the configured `root` must resolve identically to including it. One regression fixture per surface (PD5 Q5).
- **Backward compatibility.** A project with no `[packages].root`, no `.ttrd`, and `layout = "flexible"` behaves exactly as the already-shipped v1.1 model. Every existing sample still parses and resolves.

## 6. Risks

| Risk | Mitigation |
|---|---|
| No-cascade rule confuses authors who expect subtree rename | `ttr/package-prefix-divergence` Warning + docs guidance; fixtures cover leaf-only vs prefix override |
| Nested-package entity refs ambiguous in agent YAML | Push entity-granularity into `.ttrd` `entities:` (resolver disambiguates); raw `shem.entities` validated against artifact, never `split` |
| Artifact drift between repos | Determinism contract + committed snapshot in `model-ttr/`; `model-ttr` CI runs `resolve-packages --check` to block drift (Q1 resolved) |
| `cnc.cnc.*` flip tempts scope creep | Explicitly deferred (B23); not in any PD phase |
| Two `X.*` semantics (import vs load) conflated | Recursion confined to `DomainTable`; named in design §14.3 and tested |
