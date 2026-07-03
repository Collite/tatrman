# Stage 2 — Item 2: tests first (TS + Kotlin)

**Goal:** write failing tests that pin the kind → default-schema behavior, in
both languages, **before** any implementation (Stage 3). All tests here are
expected to FAIL until Stage 3 lands. Do not write production code in this
stage.

**The rule under test:** when a file has **no `schema` directive**, each
definition's qname uses the default schema for its kind (table in
[`INDEX.md`](INDEX.md)); namespace continues to fall back to the kind. When a
`schema` directive **is** present, it wins for the whole file (regression must
stay green).

**Reference behavior to mirror:** the namespace fallback at
`packages/semantics/src/reference-index.ts:12` (`namespace || def.kind`). The
schema default must behave analogously but per the kind→schema map, not a
constant.

---

- [x] **2.1 (TS) — Decide fixtures.** Create a fixture string per default-schema
  group: a schema-less file with one `def entity e { … }` (⇒ expect qname
  schema `er`), one with `def table t { … }` (⇒ `db`), `def role r { … }`
  (⇒ `cnc`), `def query q { … }` (⇒ `query`), and `def er2db_entity m { … }`
  (⇒ `map`). Keep each minimal but valid (entities need ≥1 attribute; tables
  need ≥1 column — see `validator.ts:44–104`).

- [x] **2.2 (TS) — Symbol-table test: qname schema component.** In
  `packages/semantics/src/__tests__/`, build the project symbol table from each
  2.1 fixture (no `schema` directive) and assert the resulting qname's
  schema-code component equals the expected value from the map. E.g. the
  `entity` fixture produces a qname beginning `er.` (namespace falls back to
  `entity`, so expect `er.entity.e` for package-less root). Cover all five
  groups.

- [x] **2.3 (TS) — Resolver test: schema-less reference resolves.** A schema-less
  `er` file where one entity/attribute references another by short name; assert
  `Resolver.resolveReference` resolves it (today it would mis-default to `db`
  and fail). Mirror the resolution context construction in
  `validator.ts:156–167`.

- [x] **2.4 (TS) — Regression: explicit directive still wins.** A file with an
  explicit `schema db namespace dbo` containing `def table t` resolves to
  `db.dbo.t` exactly as today. Assert no behavior change. Also assert an explicit
  directive that *disagrees* with a kind's default (e.g. `schema db` over a
  `def entity`) keeps using `db` (directive overrides kind).

- [x] **2.5 (TS) — Unit test for the helper (will exist in Stage 3).** Add a
  test file asserting `defaultSchemaForKind('entity') === 'er'`,
  `'table' === 'db'`, `'role' === 'cnc'`, `'query' === 'query'`,
  `'drillMap' === 'query'`, `'er2dbEntity' === 'map'`, covering every kind in
  `Kinds.kt`. (Import will be unresolved until Stage 3 — that is the expected
  red state.)

- [x] **2.6 (Kotlin) — mirror 2.2/2.4/2.5** in
  `packages/kotlin/ttr-semantics/src/test/kotlin/org/tatrman/ttr/semantics/`
  (`QnameSpec.kt` for qname-component assertions, `ResolverSpec.kt` for
  resolution, plus a `defaultSchemaForKind` unit case — likely alongside
  `Kinds.kt` coverage). Use the same five fixtures and the same expected schema
  codes so TS and Kotlin assertions are identical.

- [x] **2.7 — Confirm red, then tick.** Run `pnpm --filter @modeler/semantics test`
  and `./gradlew :packages:kotlin:ttr-semantics:test`. Confirm the new tests
  **fail** for the right reason (schema defaults to `db`; helper missing) and
  that nothing else regressed. Tick boxes.

### Stage 2 DoD
- [x] New tests exist in TS and Kotlin, all currently failing on the schema
      default (not on unrelated errors), and the explicit-directive regression
      tests are written and passing.

> **2.6 note:** the Kotlin `defaultSchemaForKind` unit case (TS 2.5) lands in
> Stage 3 alongside the function, not here. Kotlin is compiled — referencing a
> not-yet-existing symbol would break the whole `ttr-semantics` test module's
> compilation, so "confirm red, nothing else regressed" (2.7) couldn't run.
> Stage 2 Kotlin therefore ships the qname 2.2 (RED) + 2.4 (GREEN) cases via the
> symbol table, which compile and give the proper red/green.
