# Grammar-master tasks — master index

**Use this index to navigate.** Each linked file is a mini-task-list of 6-8
tasks intended to be executed in one coding session by one developer/agent.
Check each box right after the task is done.

**Read first (required context for every task list):**

- [`../plan.md`](../plan.md) — the overall plan and decisions D1–D7.
- [`../architecture.md`](../architecture.md) — module layout, build wiring,
  publishing flow.
- [`../contracts.md`](../contracts.md) — public API surfaces.
- [`../AST-NAMING.md`](../AST-NAMING.md) — TS↔Kotlin type mapping.

---

## Phase 1 — Parser + walker artifact

Goal: `org.tatrman:ttr-parser:0.1.0` and `org.tatrman:ttr-writer:0.1.0` ship to
GitHub Packages; ai-platform consumes them; vendored `.g4` and the Kotlin
walker/model files deleted from ai-platform.

| Stage | Mini-task-list | Status |
|---|---|---|
| 1.1 | [Scaffolding: Gradle + Kotlin module skeleton](phase-1/01-scaffolding.md) | ☑ |
| 1.2 | [Tests first: Kotest fixtures + harness](phase-1/02-tests-first.md) | ☑ |
| 1.3 | [Model + walker (with v2.0.0 fixes)](phase-1/03-model-and-walker.md) | ☑ |
| 1.4 | [Loader + dedent + error listeners](phase-1/04-loader-and-dedent.md) | ☑ |
| 1.5 | [ttr-writer module + round-trip tests](phase-1/05-ttr-writer.md) | ☑ |
| 1.6 | [Conformance harness (TS↔Kotlin AST diff)](phase-1/06-conformance.md) | ☑ |
| 1.7 | [Publishing: maven-publish + GH Actions + first 0.1.0](phase-1/07-publishing.md) | ☑ (one manual cleanup pending — see stage DoD) |
| 1.8 | [ai-platform consumer switch (separate ai-platform PR)](phase-1/08-aiplatform-switch.md) | ☐ |
| 1.9 | [Retire sync scripts + update modeler docs](phase-1/09-retire-sync.md) | ☐ |

**Phase 1 DoD** (verified by stage 1.9 final task):

- [ ] `cz.tatrman:ttr-parser:0.1.0` and `ttr-writer:0.1.0` resolvable from
      GitHub Packages.
- [ ] ai-platform builds green against the published artifacts; full test
      suite green.
- [ ] No `.g4` outside `modeler/packages/grammar/`.
- [ ] Conformance harness green in modeler CI.
- [ ] `sync-to-ai-platform.sh` / `check-sync.sh` deleted; references removed
      from `CLAUDE.md`, architecture doc, README.

---

## Phase 2 — Semantics artifact

Goal: `org.tatrman:ttr-semantics:<v>` ships; ai-platform's
`infra/metadata/resolve/*` and `BuiltinStockSource`-bundled stock content
collapse to consumers of the published artifact.

**Pre-flight:** Phase 1 must have shipped and survived at least one grammar
minor bump that ai-platform absorbed via version bump (proves the publishing
rhythm).

| Stage | Mini-task-list | Status |
|---|---|---|
| 2.1 | [Scaffolding: ttr-semantics Gradle module](phase-2/01-scaffolding.md) | ☐ |
| 2.2 | [Tests first: Kotest specs + fixtures](phase-2/02-tests-first.md) | ☐ |
| 2.3 | [Qname + SymbolTable + PackageInference + PackageGraph](phase-2/03-qname-symboltable.md) | ☐ |
| 2.4 | [Resolver (6-step chain)](phase-2/04-resolver.md) | ☐ |
| 2.5 | [Validator + StockLoader](phase-2/05-validator-and-stock.md) | ☐ |
| 2.6 | [Conformance harness extension (semantics)](phase-2/06-conformance.md) | ☐ |
| 2.7 | [Publishing: ttr-semantics 0.1.0](phase-2/07-publishing.md) | ☐ |
| 2.8 | [ai-platform consumer switch (separate ai-platform PR)](phase-2/08-aiplatform-switch.md) | ☐ |

**Phase 2 DoD:**

- [ ] `org.tatrman:ttr-semantics:0.1.0` resolvable from GitHub Packages.
- [ ] ai-platform's `infra/metadata/src/main/kotlin/infra/metadata/resolve/`
      directory deleted (callers updated to use `org.tatrman.ttr.semantics`).
- [ ] `BuiltinStockSource` reduced to an adapter that delegates to
      `StockLoader`.
- [ ] Conformance harness extended to compare resolved-qname sets +
      diagnostic-code sets; green.
- [ ] Next grammar minor bump requires zero hand-written semantics changes in
      ai-platform.

---

## Conventions

- **Check boxes the moment a task is done.** Do not batch.
- **Tests precede implementation** within each stage (TDD per the planning
  skill).
- **Reference docs** (`plan.md`, `contracts.md`, `architecture.md`,
  `AST-NAMING.md`) are normative — when a task and the docs disagree, the
  docs win and the task is wrong (open a discussion before changing course).
- **ai-platform vs modeler:** every task explicitly states which repo it
  touches. Phase 1.8 and 2.8 are the only stages that touch ai-platform code;
  all other stages are modeler-only.
- **No SNAPSHOTs.** Local iteration uses `publishToMavenLocal` per D6.
