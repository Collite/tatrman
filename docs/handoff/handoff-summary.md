# Handoff summary — where we left off (2026-06-26)

Read this first. It's the bridge context for continuing the modeler work on another
machine. Pointers below are into the repo proper; the `db-er-binding-summary.md` and
`brainstorm-transcript.md` siblings expand the MD-specific parts.

## The project in one paragraph

Tatrman Modeler is **editor-side tooling** for the TTR modeling language: a VS Code
plugin, a static React graphical designer, and (later) an IntelliJ plugin, all sharing
one TypeScript LSP server. TTR itself is consumed at runtime by the separate `ai-platform`
repo; this repo never talks to that service. The canonical grammar is
`packages/grammar/src/TTR.g4`, the single source for three generated parsers (TS via
antlr-ng, Kotlin via Gradle→Maven, Python via the ANTLR jar→PyPI). It is **not vendored**
anywhere; consumers bump the published artifact version, and cross-target drift is caught
by the conformance harness (`conformance.yml`).

## Active workstream: the multidimensional (MD) feature

This is the main thing in flight. We are adding a **multidimensional (ROLAP-first,
MOLAP-later) model** to TTR — a logical, binding-free vocabulary plus a binding layer down
to the physical (`db`) and entity-relation (`er`) models, and (deferred) an operations DSL.

Authoritative design + plans (in the repo, not just here):

- `docs/features/md/design.md` — the conceptual + syntactic design (outcome of the brainstorm).
- `docs/features/md/contracts.md` — full v1 contracts: AST shapes, property tables, symbol
  namespaces, leaf/grain + hierarchy-inference + additivity algorithms, `md/*` diagnostics,
  and the `@modeler/md-catalog` package contract.
- `docs/features/md/grammar-md-changes.md` — the additive **3.1** grammar sketch.
- `docs/features/md/map-catalog.md` — the built-in calc-map catalog (the cross-repo contract).
- `docs/features/md/plan/` — INDEX + TDD-ordered task lists for Phase 0 and Phases 1–4.
- `docs/features/md/RAE/` — legacy reference examples (Czech retail model) that ground the design.

### Sequencing

**Phase 0 — legacy renames (grammar major bump to 3.0), do before any MD work.**
Task lists at `docs/features/md/plan/phase-0-legacy-renames/`. Stages, in order
**A → AA → B → C → D → E**:

- **A** — `schema map` → `schema binding` (the schema code; `er2db_*` defs unchanged in meaning).
- **AA** — inline `mapping:` property on ER entities/attributes/relations → `binding:`
  (added after A was already in development; includes the `ttr/duplicate-mapping` →
  `ttr/duplicate-binding` diagnostic-code rename — a breaking code change, CHANGELOG it).
- **B** — drop the 2.3 `domain` block + `.ttrd` file kind; replace with a normal **`def area`**
  def (decided: area is model content, no file kind). ai-platform's agent registry must switch
  to discovering `area` *defs* instead of `.ttrd` *files*.
- **C** — `.ttr` → `.ttrm` ("Tatrman Model"); `.ttrg` unchanged. VS Code language **id** stays
  `ttr` (recommended, less churn).
- **D** — grammar **3.0** version bump; regenerate + conform all three targets (TS, Kotlin,
  **Python**); a `migrate phase0` helper; dead `.ttrl` layout-reference cleanup. The obsolete
  grammar sync scripts/workflow were **deleted** — ai-platform now bumps the published dependency,
  guided by `docs/grammar-master/new-grammar-version-process.md`.
- **E** — migrate the real model content in the **`ai-models`** repo (`~/Dev/ai-models/model-ttr`,
  41 `.ttr` files, 2 `.ttrd` domains, no `schema map`), regenerate `resolved-packages.json` with
  the 3.0 CLI, and verify ai-platform's metadata service loads the migrated files (E7 acceptance,
  depends on the Stage-D loader support for `.ttrm`/`area`).

**Phases 1–4 — the MD feature itself (additive grammar 3.1).** Task lists under
`phase-1-foundation/`, `phase-2-logical-semantics/`, `phase-3-binding/`,
`phase-4-conformance/`:

- **P1 foundation** — `@modeler/md-catalog` package · grammar 3.1 + regen + parser fixtures ·
  AST/walker for the logical objects and the binding objects.
- **P2 logical semantics** — symbols + catalog preload · resolver · domain/attr/measure
  validators · map + calc-catalog · leaf/grain + hierarchy inference · cubelet + LSP + integration.
- **P3 binding** — bound-domain & table-map bindings · cubelet shapes/columns/journaling ·
  multi-source/completeness/md2er.
- **P4 conformance & ship** — cross-target conformance · catalog vendoring + 3.1 publish ·
  RAE end-to-end fixtures · manual + CHANGELOG.

### Two decisions that were locked (don't relitigate)

1. **Calc param surface → a dedicated `calcRef` grammar rule with named args**
   (`calcRef : id ( LPAREN ( calcArg (COMMA calcArg)* )? RPAREN )? ; calcArg : id propSep? value ;`).
   Reusing `functionCall` was rejected — it's positional-only, which breaks on optional-with-default
   params. So `calc: truncToDay` and `calc: fiscalYearOfDate(fiscalYearStartMonth: 4)`.

2. **Calc-map catalog v1 floor → ship exactly 11**, grounded in real `ai-models` usage:
   `truncToDay/Month/Quarter/Year/Week`, `monthOfDate`, `quarterOfDate`, `yearOfDate`,
   `quarterOfMonth`, `dayOfMonth`, `weekOfYear`. **No fiscal family** — the real `účetní období`
   ("RRRR.MM", QUCTOBD) is a table-keyed entity, i.e. a `kind: bound` domain + `md2db_domain`, not
   a calc map. Week default is **ISO-8601**; recorded lowering caveat for ai-platform: SQL Server's
   bare `DATEPART(week,…)` isn't ISO, so the lowering must pin `ISO_WEEK`/`DATEFIRST 1`
   (captured in `map-catalog.md` §4bis and Stage 4B2).

### Still open (decide as you go)

- The catalog's **home package** and its versioning/sync story (mirrors `@modeler/grammar` /
  `TTR.g4`); a **declaration-only escape hatch** for extra abstract functions is targeted for v1.1.
- Cosmetic internal renames in semantics (`mapping-references.ts` / `mapping-synthesizer.ts`,
  `collectMappingReferences` / `MappingReference`) — left as your call to limit churn.
- Whether the Designer surfaces any user-facing "mapping" labels (check during AA).
- The **operations DSL (Layer B)** paradigm (fluent / algebraic / SQL-like) and the shared
  **scalar-expression tier** — both deferred to v1.1+.

## Companion repos

- `~/Dev/ai-platform` — consumes the published TTR parser artifacts at runtime; gets the
  Stage-D loader change for `.ttrm`/`area` and the calc-map lowerings.
- `~/Dev/ai-models` — the real TTR model content (`model-ttr/`), migrated in Phase 0 Stage E.

(See the memory files in `../memory/` for the durable facts about these.)
