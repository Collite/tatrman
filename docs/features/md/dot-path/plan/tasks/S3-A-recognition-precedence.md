# S3-A — recognition, precedence, asof plumbing (ttrp-frontend)

Goal: MD paths become live in TTR-P expression position: column-first precedence with the
shadowing warning (R23), resolver invocation, `asof` as a compile-time parameter (D17), and the
`Explanation` exposed on the frontend API.

Prereq: S2-C. TDD: S3-A1–A2 (red) before S3-A3–A6.

## Tasks

- [ ] **S3-A1 — red integration specs.** In ttrp-frontend's existing checker-spec pattern, over a
  `.ttrp` program that `use`s the sales-model fixture (testFixtures dep on ttr-semantics +
  ttr-md-resolver): (a) `calc { k = kaufland.sales.2025.net * 1.1 }` on some input resolves —
  assert the node's expression IR carries a resolved-path marker with the canonical text;
  (b) same program with an **input column named `kaufland`** → column wins **and** MD-012
  warning at the chain's range (R23); (c) forcing MD under shadow via `customer.Kaufland…`
  resolves MD with no warning; (d) chain that is neither column nor MD → the per-token MD
  diagnostics surface (not EXP_001).
- [ ] **S3-A2 — red AsofParamSpec.** `asof` follows TTR-P's compile-time parameter mechanism
  (declared/defaulted/substituted): unset → compile wall-clock (assert injectable clock, not
  `Instant.now()` scattered); set via the parameter surface → threads to the resolver (golden:
  `lastMonth` under pinned asof); recorded in the bundle manifest field `mdAsof` (nullable until
  S6 wires fingerprints).
- [ ] **S3-A3 — implement expression wiring.** The expression builder maps `mdPath` parse nodes
  to a pre-resolution IR node; the checker's scope pass: try input columns (existing C3-a-iv
  path) → on miss or for shadow detection, call `MdPathResolver.resolve` with model + snapshot
  (snapshot source = null until S6-B; a frontend-level `MemberSnapshot?` injection point exists
  from this task) + asof + no context (reads). Cache resolutions per compile pass.
- [ ] **S3-A4 — diagnostics wiring.** Add area `MD` ids to the Stage-1.1 diagnostic enum
  (`MD_001("TTRP-MD-001", …)` …014) with contracts §6 texts; map `MdDiagnostic` → frontend
  diagnostics at the path's source range (SourceLocation rules per CLAUDE.md).
- [ ] **S3-A5 — asof plumbing** per A2; injectable `Clock` on the compile-pass context.
- [ ] **S3-A6 — Explanation exposure.** Frontend API: resolved-path IR nodes expose
  `explanation: Explanation` + `shape` (serialized DTOs) for ttrp-lsp's future hover — **no LSP
  work**, just the accessor + one spec asserting it's populated.
- [ ] **S3-A7 — regression + gates.** Full existing ttrp-frontend suite green — specifically
  C3-a-iv scope cases, `EXP_001`, `FN_001/002` unchanged for non-path chains. Kotlin gate green.
  Commit `md-sugar S3A: mdPath recognition + precedence + asof`.

## Coder notes

_(empty)_

## References

- Contracts §6 (R23, MD-012) · decisions D17 · TTR-P C3-a-iv (expression scope), Stage-1.1 enum
  pattern (`docs/ttr-p/implementation/v1/tasks-p1-s1.2-expressions.md`).
- Register the S1-B4 `CatalogEntry` adapter into `CompositeCatalog` here (replaces the T5-c-β
  KDoc placeholder) — calc tokens inside paths do NOT go through the expression function
  namespace; the catalog seat is for the calc-map entries only.
