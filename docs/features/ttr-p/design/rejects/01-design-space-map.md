# Rejects Design — Design-Space Map

> The catalogue of **branches to explore**. Deliberately divergent: options are added, never removed
> without recording why. Control surface: [`00-control-room.md`](./00-control-room.md).
> Read each workstream as **Question → Branches → Cross-links → Open**.

---

## R-A. What is a reject? (trigger + per-node-kind matrix)

**Question.** Which events turn an input row into a reject row — and which node kinds can produce
rejects at all?

**Branches:**
- **R-A1. The trigger set.** (α) row-level *expression-evaluation failure* only (cast parse failure,
  division by zero, arithmetic overflow, invalid date) — rejects = the rows on which the node's row
  function is partial; (β) α **plus declared expectations** — author-stated predicates
  (`expect`/quality clauses) whose violators divert (dbt tests / Deequ / Delta Live Tables
  `ON VIOLATION DROP ROW` prior art); (γ) α **plus structural misses** — unmatched rows on a join
  (Alteryx/SSIS lookup-error style), duplicate keys, etc.
- **R-A2. The node-kind matrix.** Which of load / calc / filter / join / aggregate / branch /
  store can reject, and what a reject *means* for each (see `02` §2 for the full matrix walk;
  aggregates and multi-in nodes are the hard rows).
- **R-A3. Failure identity across engines.** Is "this row fails" defined by (α) the *executing
  engine's* runtime behavior, or (β) the *canonical catalogue* (per-function validity spec engines
  must implement/enforce)? — the A4 crux; cross-links R-D2, candidate principle R-P2.

**Cross-links:** R-A1 → R-B (schema depends on what rejects), R-A3 → R-C/R-D (producer = validity
implementation), R-A2 → R-F (what fragments could tap).
**Open:** R-Q3 (overflow statable?), R-Q5 (aggregates).

---

## R-B. The rejects-stream contract

**Question.** What exactly flows on a `rejects` edge — schema, invariants, ordering, multi-error
behavior — such that T2-a static typing and Q9 conform both hold?

**Branches:**
- **R-B1. Schema.** (α) input-row schema verbatim; (β) input row + canonical error-metadata columns;
  (γ) fixed error-envelope schema with the row serialized into a payload column; (δ) author-declared
  projection.
- **R-B2. Partition invariant.** (α) exact disjoint partition — `out ⊎ rejects = in` as multisets;
  (β) weaker "rejects ⊆ in, out ⊆ op(in∖rejects)" leaving room for engine slack.
- **R-B3. Multi-in nodes.** Whose row does a join reject — left, right, the pair? (α) pair-schema
  (prefixed columns), (β) reject the *evaluation input* (decompose so failures happen in single-in
  nodes first), (γ) per-side reject ports.
- **R-B4. Multi-error rows** (R-Q7): dedupe to one reject row vs one row per error.

**Cross-links:** R-B1 → R-Q6 (canonical codes or A4 breaks); R-B3 → RH-2; R-B2 → conform test shape.

---

## R-C. SQL producer mechanism (PG, MSSQL)

**Question.** A SQL statement is all-or-nothing — a row-level evaluation error (PG `22P02` etc.)
aborts the statement; SELECT has no native per-row error channel. How does a rejects-connected node
compile to SQL?

**Branches (full walk in `03`):**
- **R-C1-α · Guard-and-branch (validity-predicate rewrite).** Derive a total validity predicate
  V per reject-capable expression; `rejects = σ(¬V)`, `out = op(σ(V))`. Pure relational.
- **R-C1-β · Engine-native try forms.** MSSQL `TRY_CAST`/`TRY_CONVERT`, PG16 `pg_input_is_valid`,
  sentinel-NULL disambiguation. (A manifest-supplied implementation of α's V, arguably.)
- **R-C1-γ · Two-pass / error-table.** Materialize rejects first, anti-join second (Oracle
  `LOG ERRORS INTO` ancestry). Note: the statement-per-OUT-port bundle model already gives the
  rejects port its own terminal SELECT — γ may collapse into α at emit.
- **R-C1-δ · Procedural row-at-a-time** (PL/pgSQL EXCEPTION loop, MSSQL cursor+TRY/CATCH) —
  semantically exact, performance-catastrophic; possible *fallback tier* for V-underivable exprs.
- **R-C1-ε · Refuse-and-escalate.** SQL engines declare no-rejects-production; a connected port =
  capability miss → T5-b node escalation to a rejects-capable engine.
- **R-C1-ζ · Load-boundary native capture** (PG17 `COPY … ON_ERROR`, MSSQL `BULK INSERT … ERRORFILE`)
  — ingest-only, version-gated; a special-case accelerator, not the mechanism.

**Cross-links:** R-C1 → R-E1 (α wants a graph rewrite; β wants emitter/manifest hooks); R-C1 → R-Q2/R-Q3/R-Q4.

---

## R-D. Polars producer + A4 conform

**Question.** How does Polars produce the *same* rejects stream — and how does conform check it?

**Branches (full walk in `04`):**
- **R-D1. Mechanism.** (α) mask-and-split (non-strict op + null-vs-null disambiguation mask — the
  same guard shape as R-C1-α); (β) row-loop try/except (`map_elements`) — exact, slow; (γ) native
  non-strict forms per catalogue function (`cast(strict=False)`, `str.strptime(strict=False)`).
- **R-D2. Failure identity** = R-A3 seen from the engine end: native try/non-strict forms accept
  *different lexical spaces* per engine (whitespace, `'+12'`, locale, overflow bounds). (α) trust
  engines + document divergence (A4 exception — ugly); (β) canonical validity spec + enforcing
  emit (regex/bounds checks) with native forms used only where provably equivalent.
- **R-D3. Conform coverage.** Rejects stream enters the seven-point compare as (α) a full result
  (multiset, schema-fingerprinted) vs (β) count-only smoke. Fail-fast parity: R-Q8.

**Cross-links:** R-D2 ↔ R-A3 ↔ R-P2; R-D1 → R-E1 (shared guard shape favors one canonical rewrite).

---

## R-E. Compiler integration locus + capabilities

**Question.** Where in the compiler does reject production happen — and how do capability manifests
describe it?

**Branches (full walk in `05`):**
- **R-E1. Locus.** (α) **graph rewrite** (a reject-elaboration stratum: rewrite rejects-connected
  nodes into guard+branch canonical subgraphs; emitters stay dumb); (β) **emitter-local** (each
  emitter handles rejects ports natively); (γ) **hybrid** — canonical rewrite with internal
  `try`-flavored validity functions; emitters may pattern-match and fuse to native forms where
  conformant.
- **R-E2. Manifest shape.** Validity/try support as parameterized per-(engine, function) capability
  entries (T10-consistent); PG-version parameterization (R-Q2).
- **R-E3. Identity + determinism.** The rewrite must be deterministic and sit where all three
  authoring surfaces hit it identically (identity gate); double-eval fencing for volatile exprs (R-Q4).
- **R-E4. Fail-fast path.** Unconnected rejects → today's plain emit, unchanged (R-P3).

**Cross-links:** R-E1 ↔ R-C1/R-D1; R-E2 → T6 manifests; R-E3 → Phase-7 identity gate.

---

## R-F. Fragment reject taps (C2-e-β) + surfaces

**Question.** Once producers exist, do fragments get to tap `rejects` — and what does the reject
path look like in each surface (TTR-SQL, TTR-pandas, TTR-B, Designer)?

**Branches (full walk in `05`):**
- **R-F1. Graduation boundary.** (α) keep C2-e as-is — rejects still require canonical containers
  (the unlock stays latent); (β) fragment-level tap: the fragment container header may declare a
  `rejects` port fed by the fragment's (single) reject site; (γ) in-dialect syntax extensions
  (`REJECTS INTO`-style clause in TTR-SQL, a `Divert failures to …` TTR-B verb, kwarg in TTR-pandas).
- **R-F2. Designer.** Rejects wire rendering, guard-subgraph collapse (show the authored node, not
  the elaborated guard+branch — provenance-driven, E-d-γ-consistent).

**Cross-links:** R-F1 → C2-e/C2-g (per-dialect grammars must reject unsupported syntax precisely);
R-F2 → `.ttrl` view state.

---

## Fork index (load-bearing, most first)

1. **R-A3/R-D2 — who defines failure** (canonical catalogue vs engine runtime). Everything conform
   hangs on this; it is also candidate principle R-P2.
2. **R-C1 — the SQL mechanism** (guard-and-branch vs native-try vs escalate vs tiers thereof).
3. **R-E1 — locus** (graph rewrite vs emitter-local vs hybrid) — decides where the semantics live
   as code and how many times they are implemented.
4. **R-B1 — rejects schema** (verbatim vs +metadata vs envelope).
5. **R-A1 — trigger set** (eval-failure only vs +expectations vs +structural).
6. **R-F1 — graduation boundary** (latent unlock vs fragment taps now).
