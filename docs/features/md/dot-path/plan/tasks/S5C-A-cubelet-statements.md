# S5C-A — cubelet statements: dispatch, virtual cubelets, merge/delete semantics

Goal: the statement family's compile-side semantics (contracts §11): R24 dispatch, R25 virtual
cubelets + session namespace, R28/R29 merge & delete checking. Materialization and lowering are
S5C-B.

Prereq: S5 complete; grammar already carries the operators (S0). TDD: S5C-A1–A3 (red) before
S5C-A4–A6.

## Tasks

- [ ] **S5C-A1 — red StatementDispatchSpec** (ttrp-frontend): over the sales-model fixture —
  (a) slice LHS + `=`/`+=` → §5 semantics (existing S5 specs untouched); (b) slice LHS + `:=` or
  `-=` → MD-020; (c) bare identifier resolving to model cubelet + all four ops → cubelet
  statement IR; (d) fresh name + `=`/`:=` → legal (virtual / materialize-new); fresh name +
  `+=`/`-=` → MD-021; (e) plain TTR-P variable assignment `x = filter(y, …)` (non-md RHS) parses
  and checks **exactly as before** — the dispatch must not touch it; (f) interleaving: cubelet
  statements between ordinary ops in one program.
- [ ] **S5C-A2 — red VirtualCubeletSpec**: `V = sales.2025.month.*` types as cubelet-shape
  (free: time.month; measure net); dot-path over the variable — `V.january.net` resolves
  (session namespace); SSA — `V = …; V = V.february` rebinding is legal and the second `V` reads
  the first (Q7-γ semantics); `V` shadowing a model cubelet name → MD-022 warning; virtual
  `V += X` / `V -= X` type as dataflow merge/anti-join (shape checks per R28/R29) with no
  persistence flags.
- [ ] **S5C-A3 — red MergeDeleteSpec**: bound targets — `plan += e` where e covers plan's grain →
  legal, collision arm carries the binding's journal mode; e with an unknown dimension → MD-023;
  e with extra free dims → collapse via default agg (R21-analog, assert in IR); `plan -= e` —
  keys-only region (values ignored), measure/agg token in e → MD-016 warning; `-=` on the
  diff-journaled fixture variant → MD-017.
- [ ] **S5C-A4 — implement dispatch (R24)** in the statement checker: LHS classification (slice
  path / model cubelet / session variable / fresh), operator legality table, cubelet-statement IR
  nodes (`VirtualDef`, `Materialize`, `Merge`, `Delete`) beside the S5 slice-assignment IR.
- [ ] **S5C-A5 — implement session namespace (R25)**: `MdPathResolver.resolve` overload with
  `sessionCubelets: Map<Name, CubeletShape>` (ttr-md-resolver — additive API, contracts
  changelog entry); frontend threads in-scope md-typed variables per statement position
  (SSA-correct: the binding visible at that point).
- [ ] **S5C-A6 — implement merge/delete checking (R28/R29)** — grain coverage, collapse marks,
  MD-016/017/021/023 wiring into the diagnostics enum (add MD-015…023 ids here, texts from
  contracts §6).
- [ ] **S5C-A7 — green + regression + gates.** All S5 specs still green; existing variable
  (Q7-γ) specs green; Kotlin gate. Commit `md-sugar S5CA: cubelet statements + virtual cubelets`.

## Coder notes

_(empty — record the resolver API addition in the contracts changelog)_

## References

- Contracts §11 (R24, R25, R28, R29), §6 (MD-015…023) · design note D20–D24.
- TTR-P Q7-γ (variables are edge names, SSA) — `docs/ttr-p/language-design.md` §3.6; the virtual
  cubelet must reuse that machinery, not add a second binding construct.
