# Progress — Phase 6b (deferred bare-program wrapper synthesis, T6.3.3/T6.3.4)

> **Status:** the Phase-6 Stage-6.3 deferred core — **bare-program wrapper synthesis + fragment
> scope resolution — is now done** (branch `feature/ttr-p-v1-p6b-bare-wrapper`, off master post-PR#21).
> A marked bare `.ttr.sql` / `.ttr.py` / `.ttrb` file is a valid TTR-P program (C0). 9 new specs
> green; no regressions across ttrp-frontend/-graph/-lsp/-emit/-cli/-conform. `[x]` = intent; the
> reviewer verifies against runtime.

## What landed

- **`WrapperSynthesizer`** (`dialect/bare`, T6.3.3) — a marked bare file desugars to a **canonical
  wrapper program** (derived text; the source is never rewritten, C0/C2-f):
  ```
  uses world "<world>"
  import <default-imports…>              # S18 bare-only implicit prelude
  _src_<p> = load(<p>)                   # one program-level load per derived in-port
  container <filename>(in <p…>[, out result]) target <bare-target> """<dialect>
  <interior verbatim>
  """
  _src_<p> -> <filename>.<p>
  <filename>.result -> display(main_result)   # unless the interior self-terminates (ttrb Show/Store)
  ```
  Container name = filename base (S12 analogue, identifier-sanitized). Derived in-ports = one per
  distinct external table reference the decomposer reports (the flagged `/review` rule). The wrapper
  is fed straight back through the **normal front-half**, so a bare program reuses parse → decompose
  → resolve → build **identically to the embedded case** — the key-gate design, at zero extra machinery.
- **Wired into `TtrpChecker.check`** — bare-file detection (`DialectMarker`) → synthesize → check the
  wrapper. Non-bare `.ttrp` files return early (no overhead, and **S18 respected**: default-imports
  never reach a canonical document). Missing `[ttrp] bare-target` ⇒ **`TTRP-FRG-003`** (no guessing, P2).
- **Fragment scope resolution (T6.3.4)** — the interior's bare table names resolve via the
  synthesized `import <default-imports>` prelude (C2-d: in-ports > imports > qnames), reusing the
  existing wildcard-import resolution (verified: `import erp.* ; load(accounts)` → `erp.accounts`).
- **Two supporting fixes:**
  - `DialectMarker` now recognizes the **`.ttrb`** extension (+ `ttrb` in the comment-override regex) —
    it was written in P6 before TTR-B existed.
  - `TtrPandasDecomposer.derived()` now **reports the bare receiver / join-`right` / union arms as
    derived in-ports** (was a hard-coded `emptyList()` — the "6.3 fills bare wrappers" TODO), so a bare
    pandas program gets its in-ports.

- **Specs (9, green):** `BareProgramSpec` (7, ttrp-frontend — sql/pandas/ttrb compile clean, container
  synthesized, in-ports derived, verbatim interior, FRG-003 negative, S18 boundary, filename→name) +
  `BareProgramGraphSpec` (2, ttrp-graph — bare `.ttr.sql`/`.ttr.py` build the full island graph:
  derived container + synthesized Load + Filter/Project + Display).

## Not done here (unchanged deferrals)

- **T6.3.5 full byte-identical bare ≡ embedded ≡ canonical gate** — the mechanism makes bare reuse the
  embedded path (so bare ≡ embedded holds structurally), but strict byte-identity to *canonical* still
  hits the two shared-infra deltas from the Phase-7 review (canonical `load("path")` → Literal that
  `refText` drops; fragment vs FlowBody out-mapping) **plus** a load-source-qname delta (bare
  `load(accounts)` keeps `source=accounts`, canonical `load(erp.accounts)` keeps `erp.accounts`).
- **T6.3.6 `ttrp conform` hero-three-ways** (needs dockerized PG) and **T6.3.7 Designer fragment
  drill-in** (needs the P5 designer stack) — unchanged.
- The **Phase-7 bare `.ttrb` hero** now compiles via this path in principle; its specific fixture uses
  a schema ref (`erp.sales_schema`) not present in the shared test world, so a live run still needs
  fixture/world alignment + PG (next-steps §1a).
