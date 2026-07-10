# Platform Frontends — Data-Write Path Options (workstream D)

> Divergence catalogue for **D — where entered numbers land** (LF-3). Session 2026-07-09.
> Companions: [Control Room](./00-control-room.md) · [Map](./01-design-space-map.md) §D · [`02-bi-surface-options.md`](./02-bi-surface-options.md) (the R fork couples here) · [`04-spreading-options.md`](./04-spreading-options.md) (E, designed jointly).
>
> **Scope guard:** D designs the *architecture of entry writes* — not the grid UX (C), not the spread math (E), not workflow/approvals (Q-3, parked to C).

## 0. What exactly is being written (the problem statement)

A committed entry = a set of **cell assignments** into a *writable version slice* of a cube that physically lives in an engine (hero: `opex[BUD2028]` on Arges). Properties the path must carry:

- **Human-originated, interactive.** Single-cell edits want sub-second feedback; a spread commit can be thousands of leaf cells (bulk).
- **Neither canon nor run.** Not git-shaped (it's data, not model text — the *first* platform write that doesn't fit "robots write through git"); not a scheduled program (no envelope, no trigger).
- **Governed.** RLS-on-write (may *this planner* write `Sales×BUD2028`?), version protection (ACT slices read-only), audit, provenance (P4: every derived cell explains itself).
- **Previewed.** P3 (propose-don't-write): derive → show → commit, never write-on-keystroke.
- **Concurrent.** A planning round = many planners on one cube, disjoint-ish slices + shared rollups (Q-2).

**The liberating observation:** the platform's data plane *already has* exactly one write mechanism — **program materialization** (TTR-P programs write their outputs into engines, validated, dispatched, audited, lineage-tracked). Every branch below is a different answer to "how does an entry ride, extend, or bypass that mechanism."

---

## 1. Branches

### D-α · The entry door (a third platform service)

A new service (Slavic name → J-parking) with the entry contract: `propose(entry set) → preview → commit`, drafts, SSE for bulk. Two sub-shapes:

- **α-i · door → hall.** The door synthesizes a *write plan*, Argos validates (write predicates — new), Kyklop dispatches to a worker which executes the write. Requires `plan.v1` to grow write-plan vocabulary — a heavy MIT-contract amendment.
- **α-ii · door writes directly.** The door owns engine connections and writes itself (precedent: Charon touches engines directly). Lighter; but a *second* data-plane-privileged service beside Charon, and the hall's validation/quota discipline is bypassed for writes.

*Buys:* right latency shape; a clean home for entry machinery (drafts, sessions, locks); symmetric "three doors, one hall" story (α-i).
*Costs:* new always-on service + new contracts (entry API, entry event kind, write-plan vocabulary for α-i); RLS-on-write lands as new Argos/Perun territory whichever way; F-5 quota story for writes must be answered separately.
*Hero:* planner commits; the door validates her principal against `Sales×BUD2028`, executes the leaf writes, emits entry events Veles ingests.

### D-β · Entry-as-program (ride the existing write mechanism)

An entry commit *is* a TTR-P program run: inputs = the entered assignments + driver slice refs; body = the spread transform (E); output = materialization into the version slice. Two sub-shapes:

- **β-i · ephemeral program per commit.** The surface synthesizes a small program each time; it runs once; the run record is the audit trail. *Costs:* run-history pollution (thousands of micro-programs); synthesized text as authority is uncomfortable (generated code nobody reviewed).
- **β-ii · a standing entry-apply program per cube.** Deployed **once, as canon** (reviewable text: read entry set → validate shape → spread (E) → merge into slice); each commit = a *run* of it with the entry set as **typed runtime input** (`PL F-4` params machinery, stretched from scalars to a table-valued input — an amendment). The program is code-reviewed like everything else; runs are cheap and uniform.

*Buys:* reuses the **whole spine** — Argos validation, Kyklop dispatch, quota, run store, audit events, and **lineage**: the budget slice's column lineage cites the entry-apply program; P4 explainability nearly free (the spread logic is program text; the inputs are recorded). No new service category.
*Costs:* interactive latency = door dispatch latency (needs the query door's interactive priority, or a Radegast fast lane — today Radegast is envelope/trigger-shaped, Theseus is read-shaped: **neither door takes "run this deployed program NOW with this input and write"** — the gap is real and named); param vocabulary amendment (table-valued input); preview needs a dry-run mode (run the same program without the final materialize — cheap to spec, must be pinned).

*Hero:* `opex-entry-apply@1` is canon in the project repo; the planner's commit fires a run with `{assignments: [...], driver: ACT2027, locks: [...]}`; the run record *is* the audit + provenance; lineage shows `BUD2028 ← opex-entry-apply ← planner entry 2026-11-03`.

### D-γ · Staged entries + apply (journal-first)

The surface writes **entry journal rows** synchronously to a cheap append-only store — `{cell coords, value, kind: entered|derived, method, driver ref, locks, principal, ts, session}` — and an **apply step** folds the journal into the cube slice. Preview = journal overlaid on the slice (what-if view without touching it).

- Journal home sub-fork: platform-owned store (new stateful component) vs a table beside the cube in the same engine (no new infra; per-engine DDL discipline).
- Apply cadence sub-fork: on-commit (synchronous — collapses toward β-ii if apply *is* the standing program) vs scheduled/deferred (BI sees stale slice until apply — honest but confusing mid-round).

*Buys:* interactive latency fully decoupled from governed apply; the journal **is** the draft/session substrate (C's Draft+SSE needs land here for free); append-only audit by construction (Sysifos/Midas discipline); replayable — the slice is a *materialization of the journal*, rebuildable; concurrency becomes journal ordering (Q-2 gets a substrate).
*Costs:* two representations of truth between commit and apply; the journal store is real infra with RLS of its own; overlay-reads for preview are a query-path feature someone must build.

### D-δ · Direct BFF/worker write (floor marker)

The surface's BFF writes engine tables under its own credentials. Cheapest possible; bypasses validation, audit, quota, lineage — everything H and F built. Catalogued to mark the floor.

---

## 2. Cross-cutting sub-forks (owned here, apply to any branch)

- **D-i · Write semantics:** **append-only journal + materialized current state** (cell history = event log; edits reverse-and-replace; Midas precedent, P4-friendly) vs **versioned overwrite** per (cell, version) (TM1/Anaplan idiom — simpler storage, thinner audit). Lean: journal — the platform's whole temperament is provenance.
- **D-ii · RLS-on-write:** Argos today injects *read* predicates at plan validation. Options: Argos gains write-predicate validation (symmetric, one policy brain — amendment to `PL H-7` scope) · door-PEP-only coarse check (slice-level, Perun bundle) · engine constraints as backstop. Lean: Argos-symmetric with door-PEP coarse check in front; engine constraints as belt-and-braces.
- **D-iii · Provenance — the entry record.** Sibling of the compile record: `{principal, ts, entered assignments, method+driver refs, locks, resulting leaf-set hash, program/run ref}`. Cited by run events; the substrate of P4. Lean: yes, in every branch; contract shape → planning-stage if this effort graduates.
- **D-iv · Concurrency (Q-2):** cell-level last-write-wins over the journal (history keeps losers visible) · slice claims (soft locks per planning region — advisory, G-1-γ's presence idiom at the data plane) · **weird: scenario = data branch** (Iceberg/Nessie-style branch per what-if, merge on approve — natural on Steropes/files, alien on PG/MSSQL; catalogued for the scenario-planning future, not v1).
- **D-v · Version/slice protection:** `ACT*` read-only, `BUD2028` open-then-locked (Q-3's workflow hook) — declared where? Lean: version-dimension metadata in the TTR-M md model (canon), enforced at D-ii's check.

---

## 3. Cross-links out

- **→ E:** β-ii *contains* E's spread as program text — the pairing that makes both simple; α needs E as a callable capability instead.
- **→ B:** R2 "published marts" = the read-side of the same slice; the entry path must leave slices in the shape the semantic projection (R3) expects.
- **→ C:** the journal (γ) is C's draft/session substrate; preview-overlay is C's what-if view.
- **→ `PL F`:** the "run a deployed program NOW with typed table input, interactively" gap — a door-contract amendment proposal either way (new verb on Radegast, or a write-capable sibling of Theseus).
- **→ `PL H`:** D-ii is an explicit amendment proposal (write predicates in Argos; entry-slice vocabulary in Perun bundles).
- **→ TTR-M:** D-v version metadata + Q-5 cubelet definition land as modeling-vocabulary questions.

## 4. Leans (not decisions)

1. **The composite: γ's front + β-ii's back.** Entry journal as the draft/session/audit substrate (γ, journal-beside-the-cube variant first — no new infra); **commit = a run of the standing, canon entry-apply program** with the journal batch as typed input (β-ii); slice = materialization; entry record (D-iii) in every commit. One authority, whole spine reused, interactive feel preserved.
2. **The door question shrinks.** Under the composite, "the entry door" is mostly *the missing verb* — run-deployed-program-now-with-input — plus draft/session endpoints. Whether that's a thin new service or a widened existing door is a J/naming + ops decision, not architecture; keep α alive only in that reduced form.
3. **D-i journal, D-ii Argos-symmetric, D-iii yes, D-v version metadata in md** — leans as stated above; D-iv cell-LWW + advisory slice claims for v1, branch-scenarios parked.

## 5. Open questions (D-local)

- **DQ-1 ·** The interactive-run verb: amend Radegast (`run(program, input) → sync result`) or a write-capable Theseus sibling? Latency budget for a spread-commit round trip?
- **DQ-2 ·** Table-valued typed runtime params — how far does `PL F-4`'s param machinery stretch before it's a new contract?
- **DQ-3 ·** Journal DDL ownership: who creates/migrates the journal table beside a cube (the deploy of the entry-apply program? Veles? the wizard)?
- **DQ-4 ·** Preview dry-run semantics: same program, materialize suppressed — is that a plan-level flag (MIT contract) or a door-level mode?
- **DQ-5 ·** Entry event kind on the F-6-β spine: new event type vs run-events-suffice (β-ii may make run events enough).

## Convergence status

**🟢 D IS CONVERGED (2026-07-09, Bora — jointly with E, under E's two-pass amendment).** **D = γ-front + β-ii-back composite:** append-only entry journal beside the cube (draft/session/audit substrate) · commit = a run of the standing **canon `<cube>-entry-apply` program** with the journal batch as table-valued typed input · slice = its materialization · **entry record** per commit (D-iii). Sub-forks: **D-i** journal + materialized state · **D-ii** Argos-symmetric write predicates + door-PEP coarse check (`PL H` amendment proposal) · **D-iv** cell-LWW + advisory slice claims + **rebase-on-commit** (leaf-set-hash drift ⇒ re-preview); scenario-as-data-branch → parking lot · **D-v** version protection = TTR-M md version-dimension metadata. Rejected: α full third door (reduces to the missing verb + session endpoints); β-i ephemeral programs; δ direct write. **DQ-1/DQ-2 stand as named `PL` amendment proposals** (the interactive run-with-input door verb; table-valued typed params); DQ-3/4/5 → planning stage if the effort graduates. Full rationale: control room §7.

**Amendment 2026-07-10 (from C's commit/preview session — control room §7 C-section is ground truth): D-iv CONCURRENCY REPLACED BY CHECK-OUT/CHECK-IN.** The optimistic composite (cell-LWW + advisory slice claims + rebase-on-commit) is superseded by a **reservation model**: exclusive slice check-out (overlap denied, holder visible; SVN's lock-modify-unlock discipline for non-mergeable content — "merging data cubes makes no sense"), check-in = the commit path unchanged but **requiring the held reservation**, commit ≠ release. cell-LWW is moot; advisory claims upgrade to mandatory reservations; the leaf-set-hash drift check survives only as a server-side internal invariant. The reservation registry (beside the journal, enforced at the journal/commit API) joins DQ-1/2 as a named PL contract amendment proposal. Same session refined the journal input shape: **two row kinds (seed = storage-grain slice copy, applied first | assignment = load-grain value)**. Open: stale-reservation policy (CQ-14), scope algebra (CQ-15) → `07-commit-preview-options.md` §7a.
