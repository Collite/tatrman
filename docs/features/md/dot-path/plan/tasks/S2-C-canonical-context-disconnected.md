# S2-C — canonical form, calc/asof, disconnected mode, context, explanation

Goal: finish the resolver core: canonical rendering + shape (R15), calc tokens with `asof` (R12),
disconnected rules (R13), `PathContext` overlay (R20 as a library function), explanations (R14).

Prereq: S2-B. TDD: S2-C1–C4 (red goldens/specs) before S2-C5.

## Tasks

- [ ] **S2-C1 — red CanonicalFormSpec + shape goldens.** Canonical text renders per contracts §3:
  cubelet, coordinates in the cubelet's **declared dimension order**, measure, `@ agg`; quoted
  members always quoted in output; `Star` renders `*`. Shape goldens: `sales.Kaufland.2025.net`
  → scalar; `sales.2025.month.*.net` → vector[time.month]; `sales.month.*.name.*.net` →
  sub-cubelet (2 free) (D10).
- [ ] **S2-C2 — red CalcTokenSpec.** Over sales-model (grain attr `date`, calc maps from the
  catalog): `sales.2025.month.june.net` via calc coordinate (`viaCalc` set); `lastMonth` with
  `asof = 2026-07-08` resolves to `time.month: 2026-06` — **and identically on a fixture model
  without any time-dim table** (D-"time is special only in its catalog"); changing `asof` changes
  the resolution (D17).
- [ ] **S2-C3 — red DisconnectedSpec.** `members: null` goldens: bare `Kaufland` → MD-007
  (D18); `customer.Kaufland` → resolves, coordinate `deferred: true` (R13); structural errors
  (unknown attribute in a pair) still fire offline; calc tokens and INT-year members? — decide:
  INT components are member-candidates and thus **also require pairing offline** (`year.2025`);
  encode that in a golden (this is the strict reading of R13 — note it in contracts changelog).
- [ ] **S2-C4 — red ContextOverlaySpec.** `PathContext` = a resolved `CanonicalPath`. Cases from
  the design note §4 table: context `plan[Kaufland, 2026].net`, input `sales.2025` → RHS
  `sales[Kaufland, 2025].net` (cubelet replaced, customer inherited, time overridden, measure
  inherited); share-of-total: input `sales.2024.customer.*` under same context → customer **free**
  (un-pin, D-"* escape"); context agg inheritance; explanation steps mark inherited slots
  (`via: "context"`).
- [ ] **S2-C5 — implement** all four: canonical renderer (+ stable `toString`), calc-coordinate
  resolution consulting `MdCalcCatalog` (S1-B4) with `asof` threading, disconnected enforcement
  in classifier+search (snapshot-null path), `PathContext` overlay as a **pre-search seeding** of
  coordinates (RHS tokens win by replacing seeds on the same dimension/slot). Explanation steps
  for every source (`token`, `default`, `context`).
- [ ] **S2-C6 — fixture sweep.** Contracts §10 checklist against the golden directory: every
  design-note example present, all 14 diagnostics exercised (MD-008–013 arrive in S3/S5/S6 —
  mark those goldens `pending: "S3"` etc. and have the harness skip-with-reason, so the sweep is
  visible), disconnected variants, context cases. Record the coverage table here.
- [ ] **S2-C7 — green + gates + publish.** Kotlin gate green;
  `./gradlew -Pversion=0.0.1-LOCAL :packages:kotlin:ttr-md-resolver:publishToMavenLocal` works.
  Commit `md-sugar S2C: canonical + calc/asof + disconnected + context`.

## Coder notes

_(empty — C3's INT-pairing decision note + C6 coverage table land here)_

## References

- Contracts §2 R12–R14, §3, §5 R20 · decisions 10, 13, 14, 17, 18 · design note §4–§5.
- `asof` is an `Instant` parameter here; its *plumbing* (CLI/manifest) is S3-A.
