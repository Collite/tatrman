# Stage 2C — Domain / attribute / measure validators

Goal: per-kind validation for domains, the shared `attribute` body (per-schema rule), and measure
additivity consistency. These are the "static" validators that don't need the map lattice.

Prereq: Stage 2B merged & green. TDD: 2C1 before 2C2–2C4.

References (verified):
- Validator placement: per-kind validation lives in `packages/semantics/src` (see how existing
  kinds validate; `default-schema.ts` holds schema/kind metadata). Add an MD validator module
  (e.g. `md-validators.ts`) invoked from the same place existing kind validators run.
- Codes: [`../../contracts.md`](../../contracts.md) §7. Rules: contracts §3.1 (domain), §3.3
  (attribute), §3.6 (measure) and §6.5 (additivity).

---

- [x] **2C1 — Tests first (red).** One positive + one negative fixture per rule:
  - domain: `kind: bound` with no source defers to 3A (record as pending, not yet error here);
    `kind:` on a scalar domain → `md/kind-on-scalar`; a malformed `restrict` value →
    `md/bad-restrict-value`; an unknown clause name → `md/unknown-restrict-clause` (warning);
    `members` on a non-discrete type → error.
  - attribute: md attribute without `domain:` → `md/attr-needs-domain`; md attribute with `type:` →
    `md/attr-type-in-md`; er attribute with `domain:` → `er/attr-domain-in-er`.
  - measure: `semiAdditive` with a `latestValid` override but no `validBy` →
    `md/semiadditive-no-validby`; a recompute formula on `nonAdditive` →
    `md/nonadditive-recompute-unsupported`; default `class` is `additive` with single `sum`.
  - Confirm red.

- [x] **2C2 — Domain validator.** Enforce `kind` only on member-set domains; validate `restrict`
  clause names + per-clause value shapes (`range` → `RangeLiteral`, `members` → labelled set,
  `pattern`/`length` shapes). Defer the `kind: bound` → source check to Phase 3 (3A) but leave a
  hook so 3A can complete it.

- [x] **2C3 — Attribute per-schema validator.** Using the schema of the enclosing file/dimension:
  md attributes **require** `domain:` and **forbid** `type:`; er attributes the reverse. This is the
  design's "one permissive body, per-schema validation" — the only place schema branches.

- [x] **2C4 — Measure additivity validator.** Default `class: additive`. `additive` → single agg.
  `semiAdditive` → object agg + `validBy` required when a latest-valid override is present.
  `nonAdditive` → mark-only; reject any recompute formula (v1.1 feature).

- [x] **2C5 — Verify.**
  - 2C1 tests pass. `pnpm --filter @modeler/semantics test`
  - `pnpm -r typecheck && pnpm -r lint && pnpm -r build`

- [x] **2C6 — Commit.** `Section MD-2C: domain/attribute/measure validators`.
