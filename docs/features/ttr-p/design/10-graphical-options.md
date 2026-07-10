# C1 — Graphical Surface: Option Catalogue (DIVERGENCE)

> **Mode: divergence.** Enumerate alternatives + trade-offs. Decisions go to the control-room decision log (batched at session close).
> Control surface: [`00-control-room.md`](./00-control-room.md). Surface architecture (C0): [`04-surfaces-options.md`](./04-surfaces-options.md). Canonical DSL (C3): [`05-canonical-dsl-options.md`](./05-canonical-dsl-options.md). Tooling (G): [`03-tooling-delivery-options.md`](./03-tooling-delivery-options.md).
> Opened 2026-07-03. Agenda from [`next-steps-260703e.md`](./next-steps-260703e.md) + C0/C3/G leftovers.

**The question C1 must answer.** The graphical surface is a *view of the whole program* (C0-γ) issuing structured edits against canonical text (G-e as amended by C3-h): what exactly does it render, how does it edit, and what lives in the family-wide `.ttrl` sidecar (H-2c)?

**What's already constrained (not on the table):**
- Text canonical; the Designer is a thin TS frontend over the repo-attached JVM **Designer server** (WS-LSP, G-b).
- Layout/view-state = per-document `.ttrl` **sidecar**, one grammar + extension family-wide (C3-h, H-2c). TTR-M migrates onto it — **C1-c designs the content schema for both languages.**
- Containers are the mixing unit; fragments are container content (C0-γ); movement is synthesized on cross-container wires (C3-d-iv); closed containers, program-level wiring (C3-d-iii).
- Bare-fragment programs: source text never rewritten; the graphical view shows the **derived** container (C0).

## Threads

| # | Thread | Status |
|---|---|---|
| C1-a | Viewing model | 🟢 **β decided** |
| C1-b | Fragment + derived-container rendering | 🟢 **skins; sub-forks i–iv decided** |
| C1-c | `.ttrl` content schema (family-wide) | 🟢 **c-i = ζ · c-ii = shared truth · c-iii = α + inventory** |
| C1-d | Edit vocabulary + `ttrp/*` methods | 🟢 **i–iv decided (β · formatter-owned · versioning · δ internal)** |
| C1-e | Display transport (Q11 leftover) | 🟢 **γ — run via `ttrp/run`, Arrow files, watch `out/`** |
| C1-f | One Designer server for both languages? | 🟢 **γ — converge with the TTR-M `.ttrl` migration arc** |

**🟢 C1 IS CONVERGED (2026-07-03).** Decisions batched to the control-room log. Leftovers → §Leftovers below.

---

## C1-a · Viewing model — RESOLVED → β (2026-07-03)

What is "the whole program" on canvas?

- **α · One flat op-graph.** Containers drawn as boxes around their nodes, everything visible at once. *Buys:* single canvas, no navigation. *Costs:* hero-scale programs already crowd it; container boundary (the load-bearing unit) is just a decoration; doesn't scale.
- **β · Two-level: orchestration view + drill-in. ← DECIDED.** Top level renders the container-collapsed graph — exactly the derived execution-layer graph (T9/B-T6) plus program-level leaves (movement, `store`, `display`) — with synthesized transfers visible as edges. Double-click a container → its inner op-graph. *Buys:* the canvas mirrors the model's own two layers (data vs execution); drill-in = container-as-function; edit locality matches C3 (program wiring top, island edits inside); nesting recurses naturally (C3-d-v). *Costs:* two view kinds to manage; `.ttrl` must carry per-view state (→ C1-c).
- **γ · Semantic zoom.** Continuous zoom morphs containers between collapsed/expanded. *Not foreclosed by β* — a later rendering enhancement over the same two-level model, not a competing model. v2+ polish.
- **δ (weird) · Text-primary + graph minimap.** The canvas is a companion pane, not the surface. Rejected: contradicts C0-γ's "graphical = view of the whole" and the co-primary ambition; the IDE ext already covers text-first users.

**Consequences pinned with β:**
1. Orchestration view = collapsed containers **+ program-level leaves**; the author sees what F-lite emits as waves.
2. `.ttrl` keys view-state per level: one orchestration canvas + one canvas per container (→ C1-c).
3. Edit locality: program-level wiring edits in the orchestration view; island edits in drill-in (→ C1-d).
4. Drill-in of a nested container shows its children collapsed — same rule at every level.

## C1-b · Fragment + derived-container rendering — RESOLVED → γ generalized to SKINS (2026-07-03)

How does drill-in render a container whose body is a `"""sql`/`"""pandas` fragment — and the derived container of a bare-fragment program?

Options walked: **α** opaque code container (cheap; kills the Designer for bare-fragment programs) · **β** parsed read-only sub-graph (compiler derives it anyway; E-d provenance drives node↔text hover/jump) · **γ** both, code default + "view as graph" toggle · **δ** graphically editable fragments with unparse-back (rejected on sight — rewrites authored fragment text, violates C0's "source text stays as written").

**Decision (Bora): γ, generalized — rendering is a pluggable SKIN layer over the one graph.** Named skins define node presentation and edge-orientation conventions; the fragment code-pane vs icon-node vs text-node question dissolves into skin choice. Initial skin sketches:
- **"Alteryx/KNIME"** — icons per node type; data edges only (control hidden or minimal).
- **"Enso"** — text-forward nodes: node description, or the code fragment when description is missing.
- **Edge-orientation convention per skin** — e.g. data flows left→right, control flows top→down.

Skins redraw the same graph; the selected skin is recorded in `.ttrl`. Derived sub-graphs (fragment drill-in) render read-only in every skin.

**Layout mode (decided with it): BINARY per canvas — deterministic AUTO-layout until the user first rearranges, then fully MANUAL. No hybrid in v1.** First drag snapshots the auto positions into `.ttrl` and flips the canvas to manual; a "reset to auto" affordance discards. Auto mode persists nothing (P2: deterministic re-derivation beats stored state).

**Sub-forks — all RESOLVED (2026-07-03):**
- **C1-b-i = fixed built-in skin set in v1.** Skin authoring format (possibly a TTR-family doc kind) deferred.
- **C1-b-ii = positions survive skin switch; the author owns the result** (v1). No per-skin position sets, no forced reset.
- **C1-b-iii = skin is PER-CANVAS** (orchestration view and each container view choose independently). **TTR-M gets skins too** — the `.ttrl` skin field is generic, family-wide.
- **C1-b-iv = AUTO-ONLY for derived canvases** (fragment sub-graphs): manual mode unavailable where node identity is unstable; keeps the binary layout rule clean.

## C1-c · `.ttrl` content schema — family-wide (H-2c)

*(pending; gates the TTR-M migration recorded in `docs/v1-1/design/v1.1-packages-and-graphs.md` §15)*

### C1-c-i · Node identity / view-state keys — RESOLVED → ζ (2026-07-03)

**Scope observation that shrank the fork:** durable identity is needed ONLY by `.ttrl` entries on **manual-mode** canvases. Structured edits address nodes by **source range** (per document version, LSP-style); derived canvases are auto-only (C1-b-iv). Blast radius of any key breakage = manually placed positions, nothing semantic.

Catalogue walked:
- **α · SSA-qualified name keys** (`crunch/sales#2`; anonymous chain nodes `crunch/sums~1`). *Buys:* readable/diffable sidecar; coherent with Q7-γ ("names survive as labels") and E-b (SSA names → CTE names) — one naming story across text, emit, canvas, view-state; survives reordering. *Costs:* renames break keys; inserting a reassignment shifts SSA ordinals → silent **mis-attachment** (the nastiest failure).
- **β · Statement-index paths** (`crunch/3/1`). Line-number keying; every insertion shifts everything. Rejected.
- **γ · Content hashes.** Survives rename+reorder but the key changes when the node itself is edited — position lost exactly where you're working; opaque sidecar. Rejected (hash-identity fits artifacts (F-f), not live editing).
- **δ · Explicit ids in text** (`@id(n17)`; the Alteryx ToolID / Enso hidden-UUID solution). Perfectly stable; pollutes canonical text, two regimes (hand authors won't write ids), known merge pain (Enso). **Named fallback** if ζ proves too lossy.
- **ε (weird) · Reconciliation matching** (React-style: name → op-kind+neighborhood, unmatched → auto-layout). Graceful but heuristic — position "jumps" are P2-flavored miracles even if only presentational.
- **ζ · α + maintained pair + deterministic orphaning. ← DECIDED (revisitable in practice).**
  1. LSP rename / structured edits rewrite the sidecar **atomically** (pair-integrity toolset — the G work item).
  2. **Orphaning rule** closes mis-attachment: if the SSA chain length of name X (or the chain length behind an anonymous key `X~n`) changed since the sidecar was written, ALL `X#n`/`X~n` entries orphan → auto-layout for those nodes. Deterministic, conservative, never guesses (P2-clean).
  3. Orphans are **visible**: Designer badge + LSP diagnostic ("layout entries unmatched — reset or re-place"), not silent decay.
  - *Costs accepted:* non-LSP text edits degrade layout visibly; editing a reassignment chain drops that chain's manual positions.

### C1-c-ii · Committed vs personal — RESOLVED (2026-07-03)

**`.ttrl` is SHARED TRUTH (versioned/committed) in v1** — the TTR-M layout-block precedent carries to the sidecar. Per-user overlay = not in v1. (Which fields are worth committing — e.g. viewport — settles in C1-c-iii.)

### C1-c-iii · Schema inventory + grammar — RESOLVED → α (2026-07-03)

**Grammar = α: the TTR-M grammar hosts `.ttrl`** — a TTR-family document parsed by `ttr-parser`; the v1.1 `layout`-block grammar promoted to document body and extended. (Rejected: β fresh `.g4` — third grammar for a config-shaped format; γ JSON/TOML — `.ttrl` is user-visible versioned text, F-f-i's reasoning applies doubly.) TTR-P's sidecar dependency on `ttr-parser` costs nothing new (the compiler already embeds the metadata component).

**Inventory ratified:** document header (`ttrl <version>`; target implicit by name pairing) + per-canvas blocks: canvas key (TTR-P: `program` / container path · TTR-M: graph name; qname keys) · `skin` (per-canvas, built-in set) · `mode auto|manual` (auto ⇒ no nodes block) · `nodes { <ζ-key>: x, y }` (manual only) · `collapsed: […]`. Derived canvases never appear (C1-b-iv).

- **Viewport (zoom/pan) DROPPED from `.ttrl`** — ephemeral, Designer-server local; committed-viewport churn is the exact thing C3-h fought. Check whether skin subsumes TTR-M's old `displayMode` during migration.
- **Edge routing (bendPoints): NOT v1** — schema reserves the slot; Designer doesn't write it.

**Consequence:** the TTR-M migration (v1.1 docs §15) has its concrete target schema; the migration tool maps `.ttrg` layout blocks → sidecar canvases 1:1 (qname keys unchanged, viewport dropped, bendPoints dropped/held).

## C1-d · Edit vocabulary + methods namespace

*(pending; includes the δ-writeable question from C3 leftovers)*

All four RESOLVED (2026-07-03, leans taken as-is):

- **C1-d-i = β · MINIMAL AUTHORING VOCABULARY** — the bar is "the hero scenario can be built on canvas": add/remove op (palette) · wire/unwire data edges (cross-container = just a wire; synthesis C3-d-iv) · create/delete container + assign target/dialect · bind container ports · rename variable · node args/expressions via a **property panel** (textual by design — snippets lift through the one expression grammar, T5-e) · control edges drawable. Everything else → text. (Rejected: α full parity — delays everything; γ read-only v1 — dilutes C0-g's "2 full surfaces" and A4's authored-in-≥2.)
- **C1-d-ii = FORMATTER-OWNED PLACEMENT** — the edit synthesizer emits minimal canonical statements; the formatter (which already carries C3-a's style rule) owns insertion position + style. Same edit ⇒ same text, byte-for-byte (P2). New node → after its upstream's defining statement in the target container; new container → after the last; wiring → the wiring section.
- **C1-d-iii = α · LSP DOCUMENT VERSIONING** — structured edits carry the document version; stale ⇒ rejected, Designer re-pulls and replays. The standard `WorkspaceEdit` discipline TTR-M already does. (Rejected: last-write-wins — silent loss; single-writer lock — session friction.)
- **C1-d-iv = δ INTERNAL-ONLY** — with d-ii formatter-owned, structured edits emit γ-hybrid canonical text; no consumer remains for a writeable `node/edge` surface form. **Closes the C3 "δ-writeable?" leftover.**

**Methods namespace:** `ttrp/*` — `ttrp/getGraph`, `ttrp/applyGraphEdit`, `ttrp/getLayout` / `ttrp/setLayout` (sidecar rewritten wholesale, C3-h), `ttrp/getWorld`, `ttrp/transpile`. Whether TTR-M's `modeler/*` renames to `ttrm/*` = cosmetic H leftover, folded into C1-f's outcome.

## C1-e · Display transport — RESOLVED → γ (2026-07-03; Q11 fully closed)

Hidden fork surfaced first: does the Designer *run* programs? Shelling out to F-lite's bash executor is IDE-run-button territory, not platform coupling (G-b invariant holds).

- **α · No interactive path** — F-c file drop only; Designer never shows a result. Rejected: weak analyst story.
- **β · `ttrp/run` + WS Arrow streaming** — full experience; streaming/progress/cancel = v2-shaped machinery. Rejected for v1.
- **γ · Run via method, transport via the filesystem. ← DECIDED.** `ttrp/run` shells out to the bundle exactly as a terminal would; display sinks land as **Arrow IPC files** in `out/` (the F-c drop with Arrow as the Designer-facing format = Q9's fingerprint format); the Designer watches `out/` and renders at completion. One execution path — run-from-Designer and run-from-terminal indistinguishable (P2-friendly); β later = "add streaming," not "redo transport."

## C1-f · One Designer server for both languages? — RESOLVED → γ (2026-07-03; closes the G open item)

- **α · Converge now** — TTR-M rework lands on the TTR-P critical path. Rejected.
- **β · Two backends permanently** — sidecar/ζ-orphaning/skin/pair-integrity machinery written twice (TS + Kotlin), drifting forever. The worst steady state. Rejected.
- **γ · Converge at migration time. ← DECIDED.** v1 Designer server is TTR-P-only; TTR-M's `.ttrl` migration (v1.1 docs §15) and its Designer convergence onto the JVM server become **one arc** — the view-state code is written once, Kotlin-side; TTR-M adopts server + sidecar together. Nothing TS-side gets built only to be discarded. (`modeler/*` → `ttrm/*` method rename folds into that arc.)

## Leftovers (design points, not forks — fold into consolidation)

- Diagnostics on canvas: where T5-b split warnings / rls-egress tripwires / T6 capability errors render (node badges, which view level).
- `err`/`rejects` port affordance (always-visible stubs vs on-demand; unconnected = fail-fast is textually invisible — show it?).
- E-d provenance display: physical vs er names on nodes — ties to the `[pl]` display-default knob (D-e).
- Display data preview UI (in-canvas vs side panel) — enabled by C1-e γ.
- Skin authoring format (post-v1; possibly a TTR-family doc kind — C1-b-i deferral).
- Built-in skin roster + visual design (implementation work, not design forks).
- Designer-server watch mode (repo files changing under an open session) — G arc detail.

## Cross-links

C1 → T9/B-T6 (orchestration view = derived execution graph) · C1 → C3-h/H-2c (`.ttrl` sidecar; pair integrity) · C1 → G-b/G-f (Designer server, WS-LSP, method namespace) · C1 → Q11 (Display transport) · C1 → E-d (provenance rendering: er-origin names in node details) · C1 → TTR-M v1.1 §15 amendment (migration gated on C1-c).
