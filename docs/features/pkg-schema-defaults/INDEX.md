# Optional packages & type-derived schema/namespace defaults — task index

Two small, additive language tweaks. **No grammar change** — both `packageDecl`
and `schemaDirective` are already optional in `TTR.g4`. This is semantics-layer
work only.

Each linked file is a mini-task-list (≤8 tasks) for one coding session. **Tick
each box the moment its task is done — do not batch.** Tests precede
implementation within every stage (TDD).

## The two items

- **Item 1 — packages are optional, with a default.** *Already implemented.*
  `packageDecl?` (TTR.g4:38); consumers read `ast.packageDecl?.name ?? ''`
  (the empty-string **root package**); package is also inferred from the
  directory path (`package-inference.ts`); a non-root file with no `package`
  line emits the info diagnostic `MissingPackageDeclaration`
  (`validator.ts:455–481`). **Decision (confirmed): keep the empty root as the
  default — no named default package.** So Item 1 is *verification only*: lock
  the behavior in with tests in both TS and Kotlin. → [`01-packages-verify.md`](01-packages-verify.md)

- **Item 2 — schema & namespace are optional; defaults derived from object
  kind.** *Half done.* Namespace already falls back to the kind
  (`reference-index.ts:12`: `const nsOrKind = namespace || def.kind`). **Schema
  does not** — every site uses a hardcoded `?? 'db'`
  (`validator.ts:158, 302` plus the symbol-table population caller), so a
  schema-less `def entity` wrongly resolves under `db` instead of `er`. The
  work: derive the default **schema** from the definition's kind, mirroring the
  namespace fallback. → tests [`02-schema-defaults-tests.md`](02-schema-defaults-tests.md),
  impl [`03-schema-defaults-impl.md`](03-schema-defaults-impl.md),
  conformance [`04-schema-defaults-conformance.md`](04-schema-defaults-conformance.md)

## Scope (confirmed)

TS **and** the Kotlin twin **and** the conformance harness. Parity is normative
(grammar-master Phase 2 intent): TS and Kotlin must produce identical qname sets
and diagnostic-code sets for the same input.

## The kind → default-schema map (normative)

Applied **only when a file has no `schema` directive.** An explicit directive
always wins for the whole file (unchanged behavior).

| Default schema | Kinds (`def.kind` camelCase) |
|---|---|
| `db`    | `model`, `table`, `view`, `column`, `index`, `constraint`, `fk`, `procedure` |
| `er`    | `entity`, `attribute`, `relation` |
| `map`   | `er2dbEntity`, `er2dbAttribute`, `er2dbRelation` |
| `cnc`   | `role`, `er2cncRole` |
| `query` | `query`, `drillMap` |

Mirrors the sample headers (`samples/v1-metadata/*.ttr`: db→dbo, er→entity,
map→map, query→query) and the kind list in
`packages/kotlin/ttr-semantics/src/main/kotlin/org/tatrman/ttr/semantics/Kinds.kt`.

**One open call to confirm before implementing:** `drillMap` → `query` is a
proposal (drill maps describe query-to-query navigation; there is no `drill`
schema code). If you'd rather it default elsewhere, change the table above and
the single map entry — everything else flows from it.

## Stages

| # | Mini-task-list | Repo(s) | Status |
|---|---|---|---|
| 1 | [Item 1 — verify optional packages + root default](01-packages-verify.md) | TS + Kotlin | ☐ |
| 2 | [Item 2 — tests first (TS + Kotlin)](02-schema-defaults-tests.md) | TS + Kotlin | ☐ |
| 3 | [Item 2 — implement `defaultSchemaForKind` + wire it in](03-schema-defaults-impl.md) | TS + Kotlin | ☐ |
| 4 | [Item 2 — conformance fixtures + harness + DoD](04-schema-defaults-conformance.md) | modeler | ☐ |

## Definition of DONE (whole feature)

- [ ] Item 1 behavior covered by passing tests in TS and Kotlin (no code change).
- [ ] A schema-less file containing each kind resolves to the qname schema from
      the map above, in **both** TS and Kotlin.
- [ ] An explicit `schema` directive still overrides for the whole file.
- [ ] `pnpm -r test`, `pnpm -r typecheck`, `pnpm -r lint` green.
- [ ] `./gradlew :packages:kotlin:ttr-semantics:test` green.
- [ ] Conformance harness compares resolved-qname + diagnostic-code sets for the
      new schema-less fixtures; green.
- [ ] `CHANGELOG.md` notes the new default-schema-by-kind behavior (additive,
      no grammar-version bump — no syntax changed).

## Required reading before starting

- `docs/grammar-master/AST-NAMING.md` — TS↔Kotlin kind/type mapping.
- `docs/grammar-master/contracts.md` — semantics public surfaces.
- `packages/semantics/src/reference-index.ts` (`enclosingQnameOf`) — the
  existing namespace fallback this feature mirrors.
