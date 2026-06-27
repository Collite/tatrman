# 04 — Slot-filling qname + resolver (modeler / TS) — **the core**

Goal: uniform canonical key + `classifyReference`/`resolveReference` with scoped
unique-match. See [`../architecture.md`](../architecture.md) §3–§5,
[`../contracts.md`](../contracts.md) §3–§4.

**Pre-flight:** Phases 01–03 merged; baseline green. Read
`packages/semantics/src/{qname.ts,default-schema.ts,reference-index.ts,resolver.ts}`.
Note `reference-index.ts:20` `namespace || defaultNamespaceForSchema(schema) ||
def.kind` — the line being removed.

## Tasks

- [ ] **4.1 Tests first (qname).** `qname.test.ts`: `qnameToKey` package-first, uniform
  (`shop.sales.db.dbo.table.Orders`; `shop.core.er.entity.customer` with no schema).
  `classifyReference` vocabulary cases incl. `er.entity.customer` (entity→**kind**, not
  schema); a query resolves under model **db** with schema `dbo` (D14, kind `query`);
  `cnc.role.X` resolves model **cnc**, kind `role`, **no schema** (D15).
- [ ] **4.2 Tests first (resolver).** `resolver.test.ts`: full elision
  (`shop.sales.Orders`), context kind (`target: { table: X }`), er/md no-schema,
  explicit `db.dbo.Orders`, ambiguity → `AmbiguousReference`, mixed-model file (db def
  uses header `schema`; er def ignores it — D12).
- [ ] **4.3 Qname shape.** Replace `Qname` with `{package, model, schema?, kind, parts}`
  (contracts §3); `qnameToKey` drops absent slots.
- [ ] **4.4 `modelForKind`.** Extract the single-valued kind→model map out of
  `defaultSchemaForKind`; both model-derivation and default-schema read it. Remap
  `query`/`drillMap`→**db** (D14) and confirm `role`/`er2cncRole`→**cnc** (D15);
  drop `query` from the `modelCode` set (keep the `QUERY` token for `def query`).
- [ ] **4.5 `classifyReference`.** Pure/total; classify leading segments via `Vocab`
  (model code → package longest-match → schema → kind keyword → name).
- [ ] **4.6 `resolveReference`.** Fill order of architecture §5; scoped unique-match
  (current package/schema first, then global) + `require-qualified-refs` hook.
- [ ] **4.7 Rewire symbol keys.** `reference-index.ts`: drop the line-20 fallback;
  build keys from the uniform slots; header `schema` applies to db/binding defs only.

## Done when

- [ ] semantics suites green; `shop.sales.Orders` resolves; er refs resolve with no
  schema slot; mixed-model file behaves per D12.
- [ ] `pnpm --filter @modeler/semantics test` + `pnpm -r typecheck` green.

**Verify:** `pnpm --filter @modeler/semantics test`
