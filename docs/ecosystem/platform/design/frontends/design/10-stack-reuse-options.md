# Platform Frontends — Stack, Reuse & Contracts Options (workstream F)

> **[superseded 2026-07-10 · STRAT-3]** license is Apache-2.0 across the open surface; MIT mentions below are historical.

> Divergence catalogue for **F — what is legally and practically reusable, and on which stack the surfaces stand** (LF-5). Session 2026-07-10 — F arrives *heavily pre-narrowed*: A pinned the analysis side to the Designer's extension surface, C-T5 pinned the entry grid to a custom model + thin shell, B's lean already stepped away from envelope re-own. F's job is mostly to ratify the perimeter and decide the one genuinely open fork.
> Companions: [Control Room](./00-control-room.md) · [`02`](./02-bi-surface-options.md) (B) · [`06`](./06-entry-grid-options.md) §5 (C-T5).
>
> **Grounded against the repos (2026-07-10):** tatrman `packages/designer` = **React 19** + cytoscape + LSP protocol client (MIT); kantheon `frontends/iris` = **Vue** + PrimeVue + Vega-Lite + OTel web instrumentation; Sysifos = Vue, Midas-typed throughout.

## 0. The walls (decided elsewhere — F ratifies, does not reopen)

Analysis surfaces = **Designer Extensions** on the MIT React 19 surface (A) ⇒ the analysis stack is React *by construction*. The entry app = standalone `cz.tatrman` product (A) with a **custom model layer + thin virtualized grid shell** and *no heavyweight grid dependency* (C-T5). `PL D-3`: kantheon owns nothing shared — the arrow is `tatrman → platform → kantheon`; reuse-in-place of kantheon-owned code is illegal. `PL D-6`: report-renderer stays kantheon. Sysifos: **design DNA only** (its write model, validation topology and grid UX shaped C; its code is Midas-typed Vue). `PL A-1`: MIT = compile/clients-of-published-contracts; operate = platform.

## 1. F-1 · The entry app's framework — the one real fork

- **α · React everywhere platform-side.** The entry app joins the Designer's React 19 world. *Buys:* one stack across all platform frontends; component/client sharing with the extension surface is trivial; one hiring/maintenance profile; the Designer's tooling (Vite, testing-library) carries over. *Costs:* the team's recent frontend muscle memory (Iris, Sysifos) is Vue.
- **β · Entry app = Vue (per-surface split).** Honors team experience; the entry app shares nothing with the Designer anyway at the *component* level. *Costs:* two frontend worlds platform-side forever; every shared concern (door client, auth, formatting) either duplicates or must stay framework-free anyway; the split that Kantheon had is re-imported into the platform.
- **γ · Framework-agnostic core as a stance.** Not really a third framework choice but a discipline: **C-T5 already made the expensive part framework-free** — the solver/model/provenance layer is pure TypeScript with zero DOM/framework dependency (the prototype's model layer literally runs in Node for its tests). If the shared kit (F-2) stays pure-TS, the framework choice governs only the rendering shell, and a later β would cost a shell rewrite, not a product rewrite.

**Lean: α + γ's discipline** — React for the shell, pure-TS for everything below it. **FQ-1 (Bora's grounding input needed):** does Collite's frontend capacity/preference (Vue from Iris/Sysifos) outweigh single-stack economics? This is a people question, not an architecture question — the lean assumes it doesn't, and γ keeps the exit cheap either way.

## 2. F-2 · The shared platform frontend kit — and the license line

What both surfaces (extensions + entry app) need: **query-door client** (Theseus, typed results) · **auth/principal** handling · **Veles metadata client** (md model, hierarchies — the same source that drives drills and spreads) · **ttrl utilities** (form parsing, view-state shapes — thread 3) · **entry-plane client** (journal, reservations, check-in, entry records — D/C-T14 contracts) · the **grid model layer** (C's solver/provenance) · formatting (cs-CZ numbers, units) · design tokens.

- **α · One kit family, split along the `PL A-1` license line.** MIT `org.tatrman` packages for **clients of published contracts** (door client, metadata client, ttrl utils, formatting) — the MIT extension surface needs them and PL D-2 says platform panels ride an MIT-defined surface; `cz.tatrman` package(s) for **the entry plane** (journal/reservation/check-in client, the grid solver — product logic of the "operate" side).
- **β · Everything `cz.tatrman`.** Simpler ownership; but the MIT Designer/extension surface then re-implements door/md clients it plainly needs — drift by construction.
- **γ · No kit.** Each app owns its clients. Catalogued as the floor: three copies of the door client within a year.

**Lean: α.** All kit code framework-free (F-1-γ); React bindings live with the apps.

## 3. F-3 · envelope/v1, charts, and report rendering — ratify the dissolution

The old LF-5 headline question ("re-own `envelope/v1`?") **dissolved under this effort's decisions**: the query door returns typed results (no envelope needed on the read path); the entry app's render model is the grid model (C); B's thin viewer leaned to **Vega-Lite-as-a-library** rather than re-owning Iris's envelope pipeline. Options stand as the map recorded them (F-α re-own · F-β fork · F-γ don't reuse) — **lean: F-γ don't-reuse**, with the *stack half* of B's chart lean ratifiable here (charts = Vega-Lite as a library, spec knowledge carries from Iris, code does not) while B's product shape stays B's. Report-renderer: `PL D-6` standing; if report packs become platform scope, that's a future re-place proposal, not reuse.

## 4. F-4 · Iris & Sysifos — formalize DNA-only

Zero code reuse (Vue↔React, Midas-typed, `PL D-3` direction). What *does* carry: Sysifos's write-model DNA (already embedded in C's decisions), Iris's Vega-Lite spec experience (F-3), and Iris's **OTel web-instrumentation pattern** (pattern, not package — the entry app should be born instrumented). Lean: ratify as stated.

## 5. LF-5, the closing statement

With F-1..4 as leaned: **nothing is reused in place from kantheon; nothing needs re-owning** — the platform surfaces stand on tatrman-side code (Designer stack + new kit + new entry app) and published platform contracts. `PL D-3`'s one-way arrow is honored trivially, because the flow of *code* is all within tatrman/platform; only *design DNA and spec knowledge* crossed the line, and those carry no license. LF-5 resolves by dissolution rather than by a re-owning project — the cheapest possible answer.

## 6. Open questions

- **FQ-1 ·** Team capacity/preference (the map's original open): does Vue muscle memory outweigh single-stack economics for the entry app's shell? (Gates F-1; Bora.)
- **FQ-2 ·** Shared design tokens / visual language between Designer chrome, extensions, and the entry app — one platform look? (Sweep-adjacent; touches naming/branding, `PL J` register.)
- **FQ-3 ·** Package naming for the kit + the grid solver home (`cz.tatrman:*`, Slavic register) — parked to the naming item in the consolidation sweep.

## Convergence status

**🟢 F CONVERGED (2026-07-10).** **FQ-1 answered: "React is fine"** ⇒ **F-1 = α + γ's discipline** (React shells everywhere platform-side; everything below the shell pure TS). **F-2 = α** (one kit family split along the `PL A-1` MIT line, framework-free core). **F-3 = F-γ** (envelope don't-reuse; charts = Vega-Lite-as-library). **F-4 ratified** (Iris/Sysifos DNA-only + the OTel pattern). **LF-5 RESOLVED by dissolution** — nothing re-owned, nothing reused in place.

**The Iris + Sysifos → React migration (Bora: "I would really want one package").** Recorded honestly, in two parts:

1. **Scope boundary:** Iris and Sysifos are **kantheon products** (FI-3: agent-coupled / Midas-coupled, both stay kantheon; FI-4: this effort doesn't reopen PL decisions). Their internal stack is kantheon's call, so the migration is a **kantheon-side initiative this effort can propose, not decide** — parked here with a named handoff (joins the PL-P6.S2.T6 pattern: an offered item for the kantheon post-split arcs).
2. **The good news — "one package" arrives in two stages, and stage one is free.** Because the F-2 kit core is framework-free pure TS, **Vue apps can consume it today**: `PL D-3`'s arrow (`tatrman → platform → kantheon`) makes kantheon a *legal downstream consumer* of both the MIT `org.tatrman` packages and the platform client packages. Iris and Sysifos get the door client, formatting, tokens-as-data — one package family at the **logic level** — without touching a line of Vue. Stage two, **component-level** unity (shared React panels, the grid shell), arrives when kantheon runs the migration. The migration itself is real work (Iris: dockview workspace, PrimeVue, the envelope render pipeline; Sysifos: Midas-typed throughout) and nothing in the platform v1 depends on it — which is exactly why it can be an incremental kantheon decision rather than a prerequisite.
