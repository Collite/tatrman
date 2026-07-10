# Platform Frontends — Product Shape & Placement Options (workstream A)

> Compact catalogue for **A — surface topology + license placement** (LF-1, LF-6). Discussed and converged in-session 2026-07-09, *after* B/D/E — deliberately, because their outcomes pre-shaped this fork (D made every frontend a thin client of the journal/commit contracts; B decomposed into B-read and B-author, both extension-shaped).
> Companions: [Control Room](./00-control-room.md) · [Map](./01-design-space-map.md) §A.

## The branches (as discussed)

- **A-α · Two standalone products** — BI app + entry app. *Costs:* the BI app duplicates what the Designer already is (catalog, runs, lineage live there).
- **A-β · One analyst workbench** — view + author + enter as modes. *Costs:* the planner and the analyst are different people with different rhythms; one UI serving both serves neither.
- **A-γ · Everything as Designer Extensions** — no new app. *Costs:* a planning round (journal sessions, seed ops, submit rhythm, non-technical planners) inside a metadata tool's chrome hurts both products.
- **A-δ · Excel add-in** — the weird one, taken seriously: TM1/Anaplan ship Excel as a *primary* planning surface; planners live there. D's thin contracts make it a legal alternative client of the same entry path.

## Resolution

**🟢 A IS CONVERGED (2026-07-09, Bora — "exactly my thinking, including the Excel add-in").** The **split verdict**:

1. **Analysis side = Designer Extensions.** The exploration viewer (B-read's thin native viewer) and the TTR-P authoring panel (B-author) ship as platform extensions on the `PL Q-4-a` surface, joining catalog/runs/lineage. The Designer becomes the platform's single pane of glass for everyone who *reads and builds*. Same React stack, same Veles/Theseus plumbing, no new app shell.
2. **Entry side = its own standalone platform product.** The planner persona, the round rhythm, journal sessions, seed ops, and grid-first simplicity justify a dedicated app. Slavic name → parked per `PL J` register (with B's surface naming).
3. **A-δ Excel add-in = recorded future alternative client** of D's entry contracts (journal/ratify/commit + the working-view model) — not v1; parked with revisit condition *post-v1 planner-adoption demand*. The contracts stay client-agnostic so this remains cheap.
4. **License (LF-6, A-ii):** both new surfaces are **platform-side (`cz.tatrman`)** — the analysis extensions ride the MIT-defined extension surface per the existing `PL D-2` pattern; the entry app is "operate" through and through (`PL A-1`). **No new MIT frontend.**
5. **Config-vs-use note:** declarations that are *canon* (entry-form/working-view definitions, spread defaults, version protection — the Q-4/Q-7 family) are authored wherever canon is authored (IDE / Designer edit mode when it graduates), by modelers — **planners never author canon; the entry app is a use surface, not a config surface.** Details of the declaration vocabulary stay with C (Q-7).

**Rejected:** β one-workbench (persona collision) · pure γ (planning inside metadata chrome) · pure α-two-apps (BI shell duplicates the Designer). Resolves **LF-1** and **LF-6**.
