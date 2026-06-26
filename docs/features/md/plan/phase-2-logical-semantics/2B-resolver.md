# Stage 2B — Resolver & reference diagnostics

Goal: resolve every MD cross-reference (opaque strings from the parser) against the 2A symbols, emit
`md/unknown-ref` (and the schema-placement check `md/unknown-schema-def`), and resolve the
"map attribute A to B" sugar to the underlying domain-map.

Prereq: Stage 2A merged & green. TDD: 2B1 before 2B2–2B4.

References (verified):
- Resolver: `packages/semantics/src/resolver.ts`; reference plumbing: `references.ts`,
  `mapping-references.ts`, `reference-index.ts`.
- Diagnostics emission: `packages/parser/src/diagnostics.ts` + how semantics publishes
  (search `resolver.ts` for existing `unknown` reference codes to mirror code/severity/range shape).
- Codes: [`../../contracts.md`](../../contracts.md) §7 (`md/unknown-ref`, `md/unknown-schema-def`).

---

- [x] **2B1 — Tests first (red).**
  - A domain/map/measure/hierarchy/cubelet ref that resolves → no diagnostic; a dangling ref →
    `md/unknown-ref` at the ref's range.
  - An `md` def under `schema er` (or a binding def under `schema md`) → `md/unknown-schema-def`.
  - "map attribute `Customer.code` to `Customer.id`" sugar resolves to the map between their
    domains (assert the resolved target is the domain-level map, surfaced to authors).
  - Confirm red.

- [x] **2B2 — Reference resolution.** In `resolver.ts`, resolve the MD ref fields against the 2A
  namespaces: domain refs (attribute/measure/map `from`/`to`, `md2db_domain`), dimension refs
  (hierarchy), attribute refs (hierarchy `levels`, cubelet `grain` dotted), map refs (hierarchy
  `via`, `md2db_map`), measure refs (cubelet), cubelet refs (bindings). Emit `md/unknown-ref` on
  failure with the precise source range.

- [x] **2B3 — Schema-placement check.** Emit `md/unknown-schema-def` when an MD logical def appears
  outside `schema md`, or an `md2*` binding def outside `schema binding` (mirrors the existing
  file-kind/schema policing for `graph`/`area`).

- [x] **2B4 — Attribute→domain map sugar.** Implement the resolution that turns an attribute-level
  map reference into the map over the attributes' underlying domains (design §5.3), so downstream
  stages (2E leaf/grain) operate on domain maps uniformly.

- [x] **2B5 — Verify.**
  - 2B1 tests pass. `pnpm --filter @modeler/semantics test`
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build`

- [x] **2B6 — Commit.** `Section MD-2B: MD reference resolution + schema-placement diagnostics`.
