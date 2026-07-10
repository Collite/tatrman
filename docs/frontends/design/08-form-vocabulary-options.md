# Platform Frontends — Entry-Form Vocabulary Options (workstream C, thread 3)

> Divergence catalogue for **C thread 3 — the form/view declaration**: what a modeler writes, where it lives, and what it must say. This is Q-4 and Q-7's home, and the vocabulary got heavy after threads 1–2: the form's **load slice** is now simultaneously the working view's extent (GI-9), the **reservation scope** (CQ-15), the **ripple boundary** (`07` §10a), and the **draft's home** (C-T10). Session 2026-07-10.
> Companions: [Control Room](./00-control-room.md) · [`06`](./06-entry-grid-options.md) (grid core 🟢) · [`07`](./07-commit-preview-options.md) (commit/reservations 🟢).
>
> **Scope guard:** thread 3 owns the *declaration* — its ontology, field set, authoring, and lifecycle. It does **not** design TTR-M syntax (that is the TTR-M effort's court; this doc produces the **demand spec**). Round governance (who may edit forms mid-round) = Q-3.
>
> **Prototype v0.8:** the grid now shows **"The form behind this view"** — the hero's declaration rendered as annotated pseudo-md, every field traced to the decision that demands it.

## 0. What the form must be able to say (the accumulated bill)

Threads 1–2 and D/E have already *ordered* most of the vocabulary — this thread's job is mostly to give the orders one shape:

| Field group | What it declares | Demanded by |
|---|---|---|
| **binding** | which cube; how the version dimension is handled | Q-5 (cubelet = cube × version slice at storage grain); D-v (protection from version metadata) |
| **grain** | per remaining dimension: leaf, or roll to a level, or aggregate away, or pin to a member (filter) — defines the **load grain** | E two-pass (working view = aggregate of cubelet); the three grains (`06` §1); **= reservation scope** (CQ-15) |
| **hierarchies** | which hierarchy per dimension (≤1) | GI-4 (single rollup path ⇒ unambiguous subtotals) |
| **layout** | initial rows/columns/filters, shown/hidden, collapse | GI-4 ("initial layout"); pivot state itself = per-user prefs, never canon (Q-7 lean) |
| **entry** | enterable measures; legal **seed sources** (named slices, e.g. ACT2027); spread-default overrides | E-i (defaults in md, overridable); E zero-base/seed; GI-5 |
| **guards** | max loaded cells | CQ-7 (the in-memory budget; also bounds the reservation's size) |
| **narrowing** | which dimension(s) the check-out dialog may subtree-narrow | CQ-15 ratified shape ("optional subtree narrowing that equally narrows the load") |

## 1. C-T15 · What *is* a form, ontologically?

- **α · A new md canon document kind.** `form` joins cube/dimension/hierarchy in TTR-M's vocabulary; authored IDE/Designer-side, reviewed, versioned — canon like everything md. *Buys:* everything the form says is model knowledge (grain, hierarchies, entry semantics), and its md siblings — E-i spread defaults, D-v version protection — already live there; one declaration family, one review path; A's "planners never author canon" line lands exactly right (modelers author forms, planners use them). *Costs:* TTR-M grows a document kind (a real vocabulary amendment — but one we already owe them for E-i/D-v; this rides the same conversation).
- **β · A TTR-P artifact.** The form as an annotated query/projection program. *Buys:* reuses an existing artifact kind. *Costs:* a form is not a transformation — it binds and configures; programs compile to plans, forms configure a surface; wrong tool wearing the right uniform.
- **γ · Platform-side configuration (not canon).** Forms as app config in the entry product. *Buys:* no language work. *Costs:* contradicts the whole md-knowledge line (E-i put spread defaults in md *because* they're model knowledge — the form is more so); forms escape review; the reservation scope would be defined by unreviewed config. Catalogued as the floor.
- **δ · Weird: form = a saved query-door plan + entry flags.** The read side is literally a plan; entry-ness is a flag set. Marks how thin a form *could* be — and why it shouldn't: plans are compiled artifacts, not authored declarations.

**DECIDED 2026-07-10 → α, merged into the `ttrl` layout-document family** (Bora: *"yes, alpha, but I would think about merging into the ttrl layout documents; because this is exactly it"*). The form is not a new invented kind — it is a **`ttrl`-family document**: the family's "how this thing is presented and interacted with" artifact, bound to a cube and extended with entry semantics (grain, entry block, guards, narrowing). Consequences:

- **The demand spec re-splits cleanly.** The *layout/entry* content (grain, hierarchies choice, layout, entry block, guards, narrowing) targets the **`ttrl` schema** — riding the existing family-wide coordination (`PL` GQ-1: shared schema, gated on TTR-P C1's content schema, never forked). The *model-metadata* content stays TTR-M-side as already owed: E-i spread defaults, D-v version protection, Q-5 cubelet sharpening, `default_hierarchy` (CQ-19).
- **Two lifecycles, one schema — state it explicitly.** Today's `.ttrl` content is *generated view state* (Designer layout, panel state). The form becomes the family's first **authored canon** citizen: written by a modeler, reviewed, versioned. Same schema family, two lifecycles — the canon/prefs line (Q-7) survives *inside* ttrl: form documents = repo canon; per-user pivot state = the same schema shapes stored in platform prefs. **CQ-4 gets its shape for free**: personal view state is ttrl content in the prefs store, never in the repo.
- **New coordination item CQ-20:** reconcile §0's field inventory with the existing ttrl content schema (what already exists — layout/rows/columns/collapse likely do; what's new — grain, entry, guards, narrowing) with the schema's owners (TTR-P C1 / GQ-1 conversation).

## 2. C-T16 · The field set — and the minimality fork

The §0 table is the candidate inventory. The fork is **how much is mandatory**:

- **α · Minimal core + defaults.** Mandatory: binding + grain. Everything else defaults: hierarchy → the dimension's md-default hierarchy; layout → all non-pinned dims on rows, months on columns (or a platform default); entry → all measures enterable (minus version protection); guards → platform cap; narrowing → none. *Buys:* a five-line form is a working form; forms stay readable. *Costs:* defaults must be pinned somewhere (md dimension metadata grows a `default_hierarchy`).
- **β · Everything explicit.** No defaults; forms are long but self-contained. P3-flavored, but punishes the common case.
- **γ · Profiles/templates.** Forms inherit from a base form. Powerful, premature — parking-lot shape.

Sub-fork — **the version parameter (important):** is the version *baked into* the form (`version: BUD2028` — a new form per round) or is the form **parameterized** (`version: parameter of role budget-version` — the planner picks an open version at check-out, protection via D-v metadata)? *Lean: parameterized* — forms survive rounds; "form instance = form × chosen version"; the reservation pins the *chosen* version member (CQ-15's "version always pinned" is about the instance, not the declaration).

**DECIDED 2026-07-10 → α + parameterized version** (Bora: "parametrized alpha"). Minimal mandatory core (binding + grain), md-defaulted rest (`default_hierarchy` on dimensions); the version is a declared *parameter role*, the concrete version picked at check-out; form instance = form × chosen version.

## 3. C-T17 · Authoring, validation, and the demand spec

Authoring surface: IDE (md text) and the Designer's edit mode (writes-through-git, `PL Q-4-a`) — both already exist for md; forms add no new authoring machinery. **Validation is where the form earns canon status** — form-author-time checks the compiler/validator must run: grain ≥ storage grain (you cannot load finer than stored); declared hierarchy exists on the dimension and is a single rollup path; enterable measures exist and are additive (v1 — non-additive parked); seed sources resolve to real slices; guard ≤ platform cap; narrowing dims ⊂ grain dims. Every one of these turns a runtime surprise into a review-time error.

**The cross-effort demand spec** (this thread's concrete deliverable): re-split per the T15 decision — the **ttrl schema** gains the form document kind (fields per §0, layout/entry content; via the GQ-1 coordination), and **TTR-M** gets the model-metadata additions (`default_hierarchy` if T16-α, packaged with the already-owed E-i spread/driver defaults, D-v version protection, and Q-5 cubelet sharpening) as a single modeling-vocabulary amendment. The validation rules above span both (cross-document validation — the LSP/compiler's home turf).

## 4. C-T18 · Lifecycle

Forms are canon ⇒ versioned in git, reviewed, released like the rest of md. Mid-round form edits = the same **discipline** question as mid-round dimension edits (Bora 2026-07-10) → Q-3's round governance owns who/when; technically a form change mid-round does *not* disturb held reservations (reservations snapshot member sets at acquisition — CQ-15), it only affects the *next* check-out. Form deletion/rename with open reservations: reservation survives (it holds members, not the form), but re-open fails gracefully. Pivot state and per-user saved personal views: **not canon**, platform prefs (Q-7 lean; storage home = CQ-4, still open).

## 5. The hero form, rendered (the demand spec made concrete)

```
form opex_budget_entry {                 # ttrl-family layout document, authored canon (T15 decided)
  cube: opex                             # binding — Q-5: cubelet = cube × version slice
  version: parameter (role: plan)        # T16 sub-fork: parameterized; picked at check-out;
                                         #   protection via version metadata (D-v)
  grain {                                # THE LOAD GRAIN — working view extent (GI-9),
    cost_center: level department        #   reservation scope (CQ-15), ripple boundary,
    account:     leaf                    #   draft home (C-T10). Storage grain below it
    month:       leaf                    #   stays invisible (2 CCs per department here).
  }
  hierarchies {                          # ≤1 per dimension (GI-4)
    cost_center: org
    account:     coa
    month:       calendar
  }
  layout {                               # INITIAL only — pivot state is per-user prefs,
    rows:      [cost_center, account]    #   never canon (Q-7)
    columns:   [month]
    collapsed: [month.Q3, month.Q4]
  }
  entry {
    measures:     [amount]               # enterable; additive-only v1 (E)
    seed_sources: [ACT2027]              # legal explicit-copy sources (E zero-base rule)
    defaults:     from model             # E-i md defaults; overridable at entry,
  }                                      #   recorded in the entry record
  guards { max_loaded_cells: 100000 }    # CQ-7 — bounds memory AND reservation size
  narrowing: [cost_center]               # check-out dialog may subtree-narrow (CQ-15)
}
```

## 6. Leans → decisions (thread 3 ratified 2026-07-10)

1. ~~Lean~~ **DECIDED: T15 α merged into `ttrl`** — the form = a ttrl-family layout document with entry semantics; demand spec re-split (layout/entry → ttrl schema via GQ-1; model metadata → TTR-M).
2. ~~Lean~~ **DECIDED: T16 α + parameterized version.**
3. ~~Lean~~ **DECIDED (approved): T17** — validation rules as listed = part of the demand spec; authoring rides existing IDE/Designer-edit-mode paths, nothing new.
4. ~~Lean~~ **DECIDED (approved): T18** — mid-round form edits under Q-3's discipline; reservations unaffected by form changes (member-set snapshots); pivot state stays prefs (as ttrl content in the prefs store).

## 7. Open questions (thread 3)

- **CQ-16 ·** Multi-measure forms: mixed enterable/read-only measure columns in one grid — v1 or parked? (Hero is single-measure; the field set supports it either way.)
- **CQ-17 ·** Curve/profile library (seasonality patterns): md reference data (canon), cube slices, or user-local? E said "reference data, canon or cube slice" — pin it here or at TTR-M.
- **CQ-18 ·** Form-level enterable *regions* beyond measures (e.g. "future months only editable"): v1 or does D-v version/time protection cover the real cases?
- **CQ-19 ·** The `default_hierarchy` dimension-metadata addition — confirm with the TTR-M effort that it doesn't collide with their hierarchy plans.
- **CQ-20 ·** Reconcile the §0 field inventory with the existing `ttrl` content schema (what exists vs what's new: grain, entry, guards, narrowing) — the GQ-1 coordination conversation; forms = the family's first authored-canon citizens, so the schema's two lifecycles (generated view state vs authored form) need naming there.

## Convergence status

**🟢 THREAD 3 CONVERGED (2026-07-10).** **T15 = α merged into the `ttrl` layout-document family** ("this is exactly it") — layout/entry vocabulary → ttrl schema (GQ-1 path); model metadata (E-i, D-v, Q-5, `default_hierarchy`) → TTR-M; two lifecycles, one schema (canon form docs vs prefs-stored view state — CQ-4 shaped for free). **T16 = α + parameterized version.** T17/T18 approved as decided. **Q-4, Q-5, and Q-7 formally RESOLVED.** Remaining here: CQ-16..18 (minor, planning-stage-able), CQ-19/20 (cross-effort coordination items for the amendment conversations). **C's only remaining thread: Q-3 (workflow/round governance)** — then C goes 🟢 and the effort heads for its consolidation sweep.
