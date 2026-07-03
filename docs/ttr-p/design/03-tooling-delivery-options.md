# G — Tooling & Delivery: Option Catalogue (DIVERGENCE)

> **Mode: divergence.** Enumerate alternatives + trade-offs. **No decisions here** — those go to the control-room decision log.
> Control surface: [`00-control-room.md`](./00-control-room.md). Branch context: [`01-design-space-map.md`](./01-design-space-map.md) §G. Internal model: [`02-internal-model-options.md`](./02-internal-model-options.md).
> Opened 2026-07-01, after **B-T3 = γ** (own model + own optimizer; delegate relational islands to Calcite) and Bora's direction: **the PL *compiler* moves to the JVM**, and a **metadata component** may live in modeler with Ariadne as a wrapper.

**The question G must answer.** Where and how does the PL toolchain physically live and ship? Specifically: (1) reuse the modeler polyglot monorepo or a new repo; (2) the physical line between the **JVM compiler** and the **TS designer**, and how the browser gets compiler smarts; (3) where the shared **metadata component** sits and what consumes it; (4) grammar targets + conformance; (5) does **"text is canonical"** carry to PL's graphical surface; (6) LSP custom methods + hosts; (7) publishing/versioning + what kantheon consumes.

### Assets grounding (read 2026-07-01)
The modeler repo is **already polyglot**: pnpm workspace `packages/{grammar, parser, semantics, lsp, edit, format, lint, md-catalog, migrate, sql, python, designer, vscode-ext}` + tests/{integration, conformance}; **Gradle** side `packages/kotlin/{ttr-parser, ttr-semantics, ttr-writer}` (published to Maven, consumed by kantheon per its §7.3); an `intellij-plugin/` dir; root carries both `package.json` and `build.gradle.kts`/`settings.gradle.kts` + a `justfile`. One `TTR.g4` generates **TS (antlr-ng), Kotlin (ANTLR Gradle plugin), Python (ANTLR jar)**, kept aligned by the **conformance harness**. So: a JVM compiler sharing a repo with a TS designer, one grammar → many targets, and a Maven-published Kotlin core consumed by kantheon are **all already-proven patterns here**, not novelties.

| Thread | Topic | Status |
|---|---|---|
| **G-a** | Repo / package placement | 🔵 lean reuse |
| **G-b** | The JVM ↔ TS boundary — **the crux** | 🟢 **decided → WS-LSP + Designer server; Kotlin-only, no KMP** (decision log) |
| **G-c** | Metadata component placement + shape (Ariadne-as-wrapper) | 🟢 shape settled — **JVM (not KMP)** `ttr-metadata` on `ttr-semantics`; consumed by Ariadne + PL compiler + Designer server |
| **G-d** | Grammar targets + conformance | 🟢 **Kotlin-only; no KMP/JS, no Python, no conformance harness** (follows G-b) |
| **G-e** | "Text is canonical" for PL's graphical surface | 🔵 lean α (carry over) |
| **G-f** | LSP custom methods + hosts | 🟢 one Kotlin LSP; **stdio (IDEs) + WS (Designer server)** |
| **G-g** | Publishing / versioning; what kantheon consumes | 🔵 lean α (kantheon consumes compiled plans) |

---

## G-a · Repo / package placement
- **G-a-α · Same monorepo, new packages** mirroring the TTR shape: PL grammar + a Kotlin compiler trio (parser/semantics/…) beside `packages/kotlin/ttr-*`, sharing root Gradle/pnpm/justfile, conformance, CI.
  - *Buys:* zero new infra; shares `TTR.g4` tooling, the ANTLR-Gradle setup, the designer, the LSP client wiring; the brief already frames PL as "the same project (editor tooling)."
  - *Costs:* the monorepo grows a second language family; must keep TTR vs PL packages clearly separated.
- **G-a-β · New repo** for PL, depending on modeler's published TTR artifacts.
  - *Buys:* clean separation; independent release cadence.
  - *Costs:* duplicate build/CI/conformance infra; cross-repo friction for a thing the brief calls one project; loses cheap sharing of the designer + LSP scaffolding.
- **Lean: α (same monorepo).** The polyglot monorepo already does exactly this; β pays real infra cost for separation we don't need.

## G-b · The JVM ↔ TS boundary — THE CRUX
γ + the JVM move put the compiler in Kotlin; the designer is unavoidably TS/browser. A JVM LSP **cannot** run in a Web Worker, so: how does the browser designer get parse/semantics/checking?
- **G-b-i · KMP front-half, JVM-only back-half.** Compile the **front half** — parser, semantics/resolver, world-binding, author-time capability *checking* (T5-d, T8 preview), the graph model — as **Kotlin Multiplatform** (JVM **and** JS). The **back half** — relational-island → PlanNode, Calcite, SQL emit, the optimizer — stays **JVM-only**. Designer imports the JS artifact and runs the real front-end in-browser.
  - *Buys:* one Kotlin source for the front-end, two targets; the designer gets *authoritative* checking offline/in-browser (no backend needed); maps **exactly** onto T3-b (author-time vs compile-time) and onto γ (Calcite is the JVM back-half); no TS reimplementation of language logic.
  - *Costs:* KMP discipline (no JVM-only deps in the front-half — Calcite must stay strictly back-half); KMP/JS build + interop surface; ANTLR-on-KMP path to validate (antlr-kotlin targets KMP; needs confirming vs the current ANTLR-Gradle JVM setup).
- **G-b-ii · JVM LSP is the single brain; TS is a thin client.** One Kotlin LSP served over **stdio** (VS Code, IntelliJ) and **WebSocket** (designer). No KMP; TS side is a dumb client.
  - *Buys:* simplest build (one JVM artifact); no KMP; single source of truth at runtime.
  - *Costs:* the designer needs a **live backend** — no offline/in-browser checking, latency per keystroke, a WS server to host + secure; worst fit for a "static React designer" (A-scope calls the designer a *static* app).
- **G-b-iii · Thin TS front + authoritative JVM LSP (mirror today's TTR split).** Keep a lightweight TS parser for editor responsiveness (highlight, quick parse) + the JVM LSP as authority, kept conformant — exactly how TTR runs a TS parser (editor) + Kotlin parser (artifacts) today.
  - *Buys:* proven pattern in this very repo; responsive editor without a backend round-trip for basics.
  - *Costs:* **two parsers to keep conformant** (the cost we might shed for PL — see G-d); TS front duplicates *some* logic.
- **G-b-iv (the weird one) · Whole compiler to JS** via Kotlin/JS, replacing Calcite in-browser with a JS relational optimizer.
  - *Buys:* fully client-side.
  - *Costs:* reimplement/replace Calcite — rejected on sight (γ chose Calcite precisely to *not* build the relational optimizer).
- **Lean: i (KMP front-half).** It uniquely gives the static designer real, offline compiler checking while keeping Calcite/emit JVM-only, and it *is* the T3-b split made physical. **Key risk to retire first: ANTLR-on-KMP** — if antlr-kotlin can't cleanly target JS, fall back to iii (thin TS front + JVM authority) rather than ii.

## G-c · Metadata component placement + shape
The Q6 idea: modeler ships a metadata **component** (pure library); Ariadne is the **service** wrapping it; the designer + PL compiler also consume it.
- **Shape.** A Kotlin (**ideally KMP**, so the designer gets it too) library: *TTR model → model-graph + queries*. Pure — **no** bearer/RLS/session/gRPC/capabilities-mcp (those stay in Ariadne). Preserves modeler's "editor-tooling, never talks to the platform at runtime" invariant: **a library is not a service.**
- **Placement options:** (α) extend the existing `packages/kotlin/ttr-semantics` with graph-query surface; (β) a **new** `packages/kotlin/ttr-metadata` (or `model-graph`) depending on `ttr-semantics`; (γ) fold into a broader `ttr-model` core. *Lean: β* — a named metadata/model-graph artifact, layered on `ttr-semantics`, is the cleanest thing for Ariadne + designer + PL-compiler to each depend on.
- **Consumers:** Ariadne (JVM, wraps it → service); PL compiler (JVM, populates the compile-time **world** from TTR models — T4); designer (via **KMP-JS** if G-b-i, else via the LSP). One model representation, no drift — the payoff.
- **Coupling note:** *not a new kind* of coupling — it's another artifact in the already-permanent `Collite/modeler` Maven group kantheon deliberately keeps (§7.3). Ariadne already consumes `org.tatrman:ttr-*`.
- *Open:* does the metadata component subsume or sit beside `md-catalog` (today TS, data-only, vendored)? If KMP, `md-catalog` likely wants a Kotlin/KMP home too.

## G-d · Grammar targets + conformance — a simplification opportunity
TTR needs **three** generated parsers (TS/Kotlin/Python) because it has three consumers, held together by the conformance harness. **PL's consumer set is smaller:**
- If **G-b-i (KMP)**: PL needs **Kotlin only** — the JS target comes from the *same* Kotlin via KMP, not a separately-generated antlr-ng TS parser. **No TS parser to conform, no Python target** ⇒ the multi-target conformance harness may be **unnecessary for PL**.
- If **G-b-iii (thin TS front)**: PL needs Kotlin + a TS parser ⇒ conformance harness returns (2 targets).
- **Python target for PL?** TTR ships Python because ai-platform's Python services consume it. Does any PL consumer need a Python parser? Likely **no** in v1 (Steropes/Kadmos consume *compiled output*, not PL source). ⇒ **drop Python for PL.**
- *Lean:* PL is **Kotlin-primary**; JS via KMP; **no Python target; no cross-target conformance harness** — a real saving, contingent on G-b-i. PL still gets its own canonical `.g4` beside `TTR.g4`.

## G-e · "Text is canonical" for PL's graphical surface
Modeler invariant (fork #5): text is the source of truth; the Designer issues **structured edits** → LSP synthesizes a `WorkspaceEdit` → host applies → re-parse. T3-c already set PL's canonical form = **text**.
- **G-e-α · Carry it over verbatim.** PL graphical edits round-trip through PL text.
  - *Buys:* consistency with TTR + the `@modeler/edit` synthesizer pattern; text stays diffable/reviewable/versionable; matches T3-c.
  - *Costs:* the PL execution graph is *richer* than a TTR model (layout, ports, control/error edges) — the round-trip must preserve all of it in text (↔ the `.ttrg` `layout` block precedent in modeler v1.1).
- **G-e-β · Graph primary for PL** (graph is canonical; text is a projection).
  - *Buys:* the designer owns a natural graph; no synth-edit dance.
  - *Costs:* breaks the modeler invariant; loses text diffs/review; two sources of truth risk. Rejected unless a strong reason emerges.
- *Lean: α.* But flag: PL's graphical surface carries **layout + control/error ports** that must serialize into PL text — reuse the modeler v1.1 `layout`-block-in-file approach rather than a sidecar.

## G-f · LSP custom methods + hosts
- TTR has `modeler/getModelGraph`, `getLayout`/`setLayout`, `getProjectInfo`, `applyGraphEdit`. **PL analogues:** `pl/getExecutionGraph`, `pl/getWorld` (the compile-time world/environments — T4), `pl/transpile` / `pl/getTargets` (per-engine emit + placement preview), `pl/applyGraphEdit`, `pl/getLayout`.
- **Hosts:** VS Code (`vscode-ext`), **IntelliJ** (`intellij-plugin/` exists), the **designer**. With G-b-i, the designer may bypass an LSP entirely for the front-half (calls the KMP-JS lib directly) and use LSP only for back-half services (`transpile`) — *open*.
- *Open:* is the PL LSP the same server as TTR's (one LSP, more methods) or a sibling? (TTR's "one LSP across hosts" invariant suggests one server; PL is a different language family, suggesting a sibling. Reconcile.)

## G-g · Publishing / versioning — what does kantheon consume?
Sharpened by "PL is a compiler, Kantheon is a runtime" (T4):
- **The metadata component** *is* published + consumed (Ariadne wraps it) — `org.tatrman:ttr-metadata`, `Collite/modeler` group.
- **The PL compiler** — does kantheon consume it as a Maven artifact (runtime/on-demand compilation), or does PL compile **ahead-of-time** and kantheon merely receives compiled **plans** (PlanNode/orchestrator graph)?
  - **G-g-α · Kantheon consumes compiled output only** (plans), never the PL compiler. Cleanest with T4's compile-vs-runtime split; keeps modeler "no runtime coupling."
  - **G-g-β · Kantheon embeds the PL compiler** (Maven artifact) for runtime/dynamic compilation (e.g. an agent authors PL and needs it compiled server-side).
  - *Lean: α for v1* (AOT compile; kantheon runs plans), revisit β if a runtime-authoring path (Pythia/Golem emitting PL) demands server-side compilation. (↔ Q1: is the agent a first-class surface user?)
- **PL grammar versioning:** its own canonical `.g4` + version, mirroring TTR's grammar-master process; conformance harness only if G-d keeps >1 target.

---

## DECIDED (2026-07-01) — G-b and its cascade
**G-b = WS-LSP + Designer server (option ii), Kotlin-only, no KMP.** See decision log. The Designer is a **thin frontend**; a repo-attached **Designer server** (JVM) is its backend — it attaches to the **model repository** directly (no upload), embeds the compiler front-half + the shared metadata component, and serves the Designer over WS-LSP + `pl/*`. *"Use the IDE, or run a server for the Designer."*

**The component roster this creates / confirms:**
1. **PL grammar** — canonical `.g4` beside `TTR.g4` (Kotlin-only generation).
2. **PL compiler** (Kotlin) — parser → semantics/resolver → normalizer/rewriter (T8) → relational-island→PlanNode + Calcite (γ) → codegen; optimizer later.
3. **`ttr-metadata`** (JVM, `packages/kotlin/`, on `ttr-semantics`) — pure model-graph + queries; consumed by **Ariadne** (wraps → service), the **PL compiler** (populates the compile-time world, T4), and the **Designer server**.
4. **PL LSP** (Kotlin) — one server, transports **stdio** (VS Code, IntelliJ) + **WS** (Designer server). Restores "one LSP across hosts."
5. **Designer server** (JVM) — repo-attached backend for the Designer frontend; hosts the WS-LSP; embeds compiler front-half + `ttr-metadata`. **Editor infrastructure, not platform runtime** → modeler's no-runtime-coupling invariant holds.
6. **Designer frontend** (TS/React) — thin client over WS.
7. **`vscode-ext`** + **`intellij-plugin`** — thin LSP clients (stdio).

**Simplifications banked:** no KMP/JS, no standalone TS parser, no Python target for PL, **no cross-target conformance harness** (single Kotlin parser).

## RATIFIED (2026-07-02) — G is fully converged
- **G-a = same monorepo** (α). **DECIDED.**
- **G-e = text canonical (α)**, layout/ports serialized into PL text (v1.1 layout-block precedent). **DECIDED.**
- **G-g = kantheon consumes compiled plans (α) for v1**; `ttr-metadata` is the one published+consumed artifact. **DECIDED** (revisit β only if runtime agent-authoring lands — Q1).

## Open
- ~~Does **TTR's own designer** converge onto the same JVM Designer server?~~ — **RESOLVED 2026-07-03 (C1-f = γ):** converge, but **with the TTR-M `.ttrl` migration — one arc, after TTR-P v1** (view-state code written once, Kotlin-side). See `10-graphical-options.md`.
- **Designer server multi-user/auth** — local single-user in v1, or shared/hosted (→ auth/RLS)? (Likely local v1.)

## Cross-links
G → B-T3 (γ: Kotlin+Calcite back-half), G → T4 (`pl/getWorld`; metadata component populates the compile-time world), G → T5-d/T8 (author-time checking = KMP front-half), G → T6 (world capability manifest surfaced by the metadata/world component), G → E (back-half = per-engine emit), G → C (designer consumes the front-half; text-canonical round-trip), G → Q1 (agent-as-user drives G-g-β), G → kantheon §7.3 (Maven group + Ariadne consumption).
