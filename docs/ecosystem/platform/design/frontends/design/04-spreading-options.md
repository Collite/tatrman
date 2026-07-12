# Platform Frontends — Spreading & Allocation Options (workstream E)

> **[superseded 2026-07-10 · STRAT-3]** license is Apache-2.0 across the open surface; MIT mentions below are historical.

> Divergence catalogue for **E — where spread/split semantics live, and what they are** (LF-4). Session 2026-07-09, designed jointly with [`03-write-path-options.md`](./03-write-path-options.md) (D).
> Companions: [Control Room](./00-control-room.md) · [Map](./01-design-space-map.md) §E.
>
> **Scope guard:** E owns the *semantics and placement* of spreading. Grid interaction (how a planner invokes a spread) = C; where results land = D.

## 0. The problem statement

The planner states intent at a coarse cell (`Sales × FY2028 = 120M`); the system derives the leaf cells (department × account × month) such that **the leaves always sum back to the stated total** — honoring locks, using a declared method, deterministically. This is GI-3: the *point* of the entry surface. Requirements that fall out of the principles:

- **P3 (no miracles):** method, driver, locks, and rounding rule are explicit or declared defaults — never tool magic.
- **P4 (explainable):** every derived cell can show `entered / method / driver / locks honored / rounding residual`.
- **One authority:** preview and commit must be *the same computation* (Sysifos's three-layer validation lesson: echoes are caches, one place is truth).
- **Determinism:** same inputs ⇒ same leaves, byte-stable — spreads are re-derivable forever (audit, replay, `PL B-3` temperament).

## 1. The vocabulary (method set — placement-independent)

The v1 candidate set, each a named, parameterized method:

| Method | Semantics | Notes |
|---|---|---|
| **proportional** | leaf share = driver-slice share | the workhorse; hero uses ACT2027 as driver |
| **even** | equal split over enterable leaves | degenerate proportional (driver = 1) |
| **profile** | multiply a declared curve (seasonality) along one dim, proportional over the rest | curves = reference data, canon or cube slice |
| **driver-formula** | rate × volume style: leaf = f(driver cells) | the general form; needs expression syntax |
| **manual + re-spread remainder** | user fixes leaves (locks), remainder re-spreads over the unlocked set | the interactive loop's core |
| **rounding reconciliation** | residual assignment so Σleaves == total exactly | **must be pinned**: largest-remainder with stable tie-break (dimension key order) is the lean; the rule is part of the method signature |

Invariants: sum-preservation (validated, not assumed) · locks are honored or the spread *errors* (over-constrained = explicit failure, P3) · every method is a pure function of `(target, driver slices, locks, hierarchy structure)`.

**Sub-fork E-i · where defaults are declared:** per-cubelet/measure **in the TTR-M `md` model** (canon: "opex spreads proportional-to(ACT, prior-year) by default") — overridable at entry, everything recorded in the entry record (D-iii) · vs entry-form config (platform-side) · vs ad-hoc only. Lean: TTR-M-declared defaults (a modeling-vocabulary amendment; Q-4 sharpened) — spreading behavior is *model knowledge*, not UI preference.

---

## 2. Placement branches

### E-α · Client-side (the grid computes)

The frontend spreads; the server stores results. *Buys:* zero-latency feel. *Costs:* the authority problem is fatal — either the server re-derives (then α is just an echo of someone else's semantics) or the client is authoritative (P3/P4 die; determinism across browser versions is fiction). **Catalogued as an echo layer at most, never the authority.**

### E-β · A platform allocation service

Spread methods as server capability (an allocation endpoint the entry path calls; preview hits the same endpoint). *Buys:* one authority; simple mental model; language untouched. *Costs:* semantics locked in service code — not reviewable text, versioned only as service releases; a *second* computation engine beside the workers (the spread of a big cube wants to run *in the engine*, not in a service's RAM); P4 explanations are service logs, not inspectable artifacts.

### E-γ · TTR-P language capability

Spreading = a deterministic transform **expressible in TTR-P**: a `spread`/`allocate` construct (or stdlib layer over md models) that compiles like everything else — to SQL on Arges/Brontes, to Polars on Steropes. Relationally it's joins + ratio math + residual assignment over the hierarchy closure; TTR-P can express it *today*, painfully; the language work is the **ergonomic layer** (md-aware verbs that know hierarchies, enterable levels, drivers), the same move `MD_CALC_CATALOG` made for calc maps.

*Buys:* reviewable text (the spread IS a program — P4's explanation is the artifact itself); deterministic by the language's own discipline (`PL B-3` machinery applies); **executes in the engines at engine scale**; MIT under `PL A-1` (it's *compile* — the standalone story gets spreading for free, e.g. spread-then-emit in a bash-bundle world); pairs perfectly with D-β-ii (the entry-apply program *contains* the spread); preview = same program through the query door, dry.
*Costs:* a real language arc — grammar/semantics/stdlib in the TTR-P effort's court (cross-effort dependency with their own control-room discipline; this effort can *specify the demand*, not the syntax); interactive latency = compile+dispatch round trip (mitigated: query-door interactive priority, cubelet-scale plans are small); over-constrained/error semantics must be language-grade precise.

### E-δ · Hybrid: γ semantics + β ergonomics

The language owns the semantics (γ); the platform ships a **deterministic fragment generator** — the surface calls `spread(target, method, driver, locks)`-shaped APIs and receives canonical TTR-P (or directly `plan.v1`) it never string-builds. Optionally an **α-echo** in the grid for keystroke feel, explicitly labeled preview-approximation until the door round trip confirms (or omitted if latency proves fine).

*Buys:* γ's authority + a stable machine surface for C's grid; generation is deterministic (same request ⇒ same fragment bytes — the harvest-connector discipline, `PL I-3`).
*Costs:* the generator is a new MIT artifact to own; two entry points into one semantics (API + hand-written TTR-P) must provably agree — same-plan-bytes is the test.

### E-ζ · Weird/floor: no spreading in v1

Leaf-level entry only; planners spread in Excel and paste (C's bulk grid). *Marks the floor and names the honest MVP fallback* — but contradicts GI-3 (spreading is the differentiator), and the paste path still needs sum-validation, locks, and audit, i.e. half of E anyway.

---

## 3. Prior art (why this shape)

TM1/Anaplan/Jedox: cell write-back with spread menus (proportional/even/repeat/growth), **holds** (locks), and sandboxes — the interaction vocabulary is 30 years proven; none of them make the spread *inspectable text* (their audit is a log, not an artifact — that's our differentiator via γ). FINOS Perspective: editable grid + Arrow streaming = the client echo's natural host (α-echo in E-δ, C's grid). `MD_CALC_CATALOG`: the precedent for md-aware derived-computation vocabulary living beside the language.

## 4. Cross-links out

- **→ D:** γ/δ + D-β-ii is the load-bearing pairing (spread inside the canon entry-apply program); if D converges away from β-ii, E-β regains ground.
- **→ C:** the grid's spread invocation, lock gestures, and latency budget; the α-echo question is really C's feel question.
- **→ TTR-P effort:** the demand spec (md-aware spread construct: methods table above + hierarchy/enterable-level awareness + error semantics) — file as an incoming item to their control room; this effort does not design the syntax.
- **→ TTR-M:** E-i default declarations + Q-5 cubelet contract + D-v version metadata travel together as one modeling-vocabulary amendment conversation.
- **→ B:** the same md hierarchy metadata that drives spreads drives the semantic projection (R3) and the native viewer's drill — one source (Veles-served model), three consumers.

## 5. Leans (not decisions)

1. **E-γ with δ's ergonomics:** the language owns spread semantics (as a specified demand to the TTR-P effort); the platform ships the deterministic fragment generator; the grid gets an echo only if the door round trip proves too slow for typing feel.
2. **E-i:** spread defaults declared in TTR-M md (canon), overridable per entry, recorded in the entry record.
3. **Rounding pinned** as part of each method's signature (largest-remainder, stable tie-break) — no method without a declared residual rule.
4. **v1 method set:** proportional · even · manual+re-spread+locks · rounding rule. Profile and driver-formula fast-follow (they stress the expression surface; let the TTR-P demand spec cover them so the construct isn't designed too small).

## 6. Open questions (E-local)

- **EQ-1 ·** The TTR-P demand spec: is `spread` a language construct, a stdlib/catalog layer (MD_CALC_CATALOG-style), or a compiler-known macro? (Their call; our requirements doc.)
- **EQ-2 ·** Over-constrained semantics: locks + total contradiction — error always, or declared relaxation orders? (Lean: error always in v1, P3.)
- **EQ-3 ·** Does the fragment generator emit TTR-P text or `plan.v1` directly? (Text keeps humans in the loop and review possible; plan skips a parse. Lean: text.)
- **EQ-4 ·** Latency budget: what round-trip time makes the α-echo unnecessary? (Measure at C's prototype; number, not vibes.)
- **EQ-5 ·** Driver slices that are themselves being edited this round (spread BUD by BUD-so-far): snapshot semantics needed — driver = journal-overlay or committed-only?

## Convergence status

**🟢 E IS CONVERGED (2026-07-09, Bora — with the TWO-PASS AMENDMENT, superseding the single-pass lean).**

**The two-pass model:** the planner works in an **in-memory working view** (the cubelet aggregated over declared dimensions to displayable size); ALL rich manipulation mechanisms (§1's vocabulary and more) live there as **non-authoritative tooling** whose outputs the user ratifies — their design moves to **workstream C**. The **committed (invisible) pass** is a **single mechanism: proportional-over-unlocked-remainder against the current base**, rounding = largest-remainder with stable tie-break.

Pinned with it: **zero base = ERROR** (refuse to spread; the planner opens a finer view or **seeds explicitly** — seed = a leaf-level copy operation, a distinct C-level entry op, no spreading involved; rejected: auto-seed+even fallback, even-only fallback, dense creation) · **existing support only** (no silent densification; creating combinations = separate explicit act) · **additive measures only in v1** (non-additive → parking lot) · **rebase-on-commit** (base = current state; drift ⇒ re-preview) · defaults declared in **TTR-M md** (E-i), overridable, recorded in the entry record · **placement = γ+δ**: the one construct is a TTR-P language capability (MIT, engine-executable, reviewable — the cross-effort demand spec shrinks to this single construct + error semantics); the platform ships the deterministic fragment generator.

**Consequence:** "drivers" (ACT2027 etc.) are visible-pass tooling + seed sources; the committed pass has no driver concept — its base IS the current slice. Discharged: EQ-2 (over-constrained = error always), EQ-5 (rebase-on-commit); EQ-4 (echo latency budget) → C; EQ-1/EQ-3 stand as the demand-spec + generator-output questions for the TTR-P effort / planning stage. Full rationale: control room §7.

**Amendment 2026-07-10 (from C's grid-core session — control room §7 C-section is ground truth): THE COMMITTED PASS IS LOCK-FREE.** Locks (and ephemeral formulas) are frontend-only, never stored; since ratified assignments at load grain cover *disjoint* storage regions, the "unlocked remainder" clause was vacuous at commit — the construct simplifies to *per-assignment proportional-over-existing-base* (zero base = error, largest-remainder rounding unchanged). The entry record drops its locks field (D-iii amendment); the TTR-P demand spec shrinks accordingly. Re-ratified the same day: additive-only v1 stands (C's GI-7 tension resolved → non-additive stays parked).
