# Tatrman Platform — Designer Evolution Options (workstream G)

> Divergence catalogue for **G — the browser Designer as a Platform product** (writes, multi-user, run/lineage views, registration UX) versus the standalone view-only story. Session opened 2026-07-09 (after H and K converged — G's inputs are all in place).
> Companions: [Control Room](./00-control-room.md) · [Design-Space Map](./01-design-space-map.md) §G · [`03-service-architecture-options.md`](./03-service-architecture-options.md) (C-2's Designer organ) · [`07-security-options.md`](./07-security-options.md) (H-3/HQ-3) · map §K (composition).
>
> **Scope guard:** G designs the Designer *product and its write model* — not the metadata server's contract (C, decided), not policy (H, decided), not the graph-rendering tech stack (implementation). Registration UX mechanics land here because E-2-γ/K assigned them.

## Inherited constraints (the pre-shaped cage — G has less freedom than any workstream so far)

- **FI-2/FI-4:** standalone Designer = **view-only** over `.ttrl`/model files inside IDEs; platform Designer = browser app, later writes, multi-user.
- **Q-4-a (decided):** ONE MIT React frontend in tatrman, **backend-selectable** (MD6 adapter: browser-worker LSP · loopback `ttr-designer-server` · platform metadata server); platform-only panels = **platform-shipped extensions on an MIT-defined extension surface**.
- **Text is canonical** (CLAUDE.md invariant): the Designer never owns model state; edits are structured ops → `WorkspaceEdit` → host applies → re-parse. The `@tatrman/edit` synthesizer + `modeler/applyGraphEdit` are the designed path; the CST/trivia layer exists so surgical edits preserve comments/layout.
- **C-2-γ:** the Designer's platform backend = the metadata server's Designer-facing organ. **F-6-β:** run/lineage views read the metadata server, never the executor. **C-2 amendment:** lineage views are **column-grain** in v1.
- **E-2-γ + K (decided):** registration = world content; admin actions produce **commits to the platform-world repo**; the UX must hide the plumbing. **H-3/HQ-3:** Designer writes ride git permissions; a platform PEP is reserved if G lands elsewhere.
- **Review inputs (map §G):** (1) E-2-γ/K *presume* writes-through-git — G must confirm or they re-render; (2) the standalone preview asymmetry must be stated as a decision.
- **S24/G-b:** `ttr-designer-server` = editor infrastructure (repo-attached, loopback, read-only v1); the *symmetry* with the platform server is library-level only (C-2).
- **`.ttrl`:** the family-wide per-document view-state sidecar; TTR-M's migration off the in-file layout block is pending, gated on the TTR-P C1 session's content schema.
- **Hero life 2:** the Designer shows runs + column lineage; previews ride the query door (C-3-γ), interactive priority (F-5-γ), under the caller's principal (H-2).

---

## G-1 · The write model (the crux — everything upstream leans on it)

**Question:** when the platform Designer writes, what *is* a write?

- **G-1-α · Live co-editing.** CRDT/OT sessions on the model (Figma-flavored); presence, multi-cursor; the server materializes text periodically.
  - *Buys:* the modern collaborative feel; no merge conflicts as UX.
  - *Costs:* CRDT over *generated-from-text graphs* is a research project (the graph is a projection; concurrent structured ops on a projection of text with trivia is unsolved territory); "text is canonical" becomes "text is a checkpoint" — the invariant dies in spirit; every upstream decision that assumed commits (E-2-γ, K, H-3's git-perms enforcement) re-renders; the IDE/git half of the userbase edits the same files *outside* the CRDT session — two concurrency worlds colliding.
- **G-1-β · Pessimistic locking.** Check-out/check-in per document; one editor at a time; others read.
  - *Buys:* dead simple; no merge UX ever.
  - *Costs:* locks in a git-backed world are advisory theater (the IDE user ignores them); lock lifecycle ops (stale locks, admin breaks) are perennial support tickets; punishes the common case (two people on *different* parts of one document).
- **G-1-γ · Writes-through-git.** Every edit session works on a **branch**; "save" = commit (via the `WorkspaceEdit` path); "publish" = merge/PR per repo policy; multi-user = git semantics + **advisory presence** ("Bora is editing `accounts.ttrm`"); conflicts = git conflicts, surfaced as a review flow, not an editor fight.
  - *Buys:* text-is-canonical *literally enforced*; every upstream assumption (E-2-γ registration commits, K's platform-world repo, H-3 git-perms, the D-5 arc discipline) is confirmed, not re-rendered; audit/review of model changes = code review (the org already knows how); the IDE user and the browser user are *the same kind of writer* — one concurrency world.
  - *Costs:* merge conflicts are real UX (mitigate: advisory presence + short-lived sessions + fine-grained documents); "save" latency = commit latency; the platform needs server-side workspaces (branch checkouts) per edit session (→ G-3); non-git-literate analysts need the plumbing *completely* hidden (branch/commit/PR vocabulary optional, not required).
- **G-1-δ · Weird: the browser Designer never writes.** It grows into a rich *reader* (catalog, lineage, runs, dashboards); all editing stays in IDEs forever (map's G-δ).
  - *Buys:* G's hardest problem deleted; the reader is most of the v1 value anyway.
  - *Costs:* FI-4 names "later writes, multi-user" as framing — δ contradicts it; registration UX (E-2-γ/K) *requires* writes (admin edits to the platform world) — δ would exile registration to hand-edited files, which is not a product. Catalogued to mark the floor.

**Sub-fork G-1-γ-i · publish flow:** direct-commit-to-main vs PR-per-session vs **repo/branch policy decides** (both supported; the org's branch protection is the arbiter — reusing git's own governance instead of inventing one).

**Lean: γ, with γ-i = policy-decides.** The presence channel is ephemeral state in the metadata server (advisory only, v1). α recorded as the *within-session* future (live co-editing inside one branch session, two users sharing a workspace — composable later without touching the write model).

**RESOLVED 2026-07-09 → G-1 = γ (writes-through-git; session = branch, save = commit, publish = repo-policy-decides), α recorded as the within-session future.** E-2-γ/K/H-3's presumptions confirmed — nothing re-renders (review input #1 discharged). **Timing (Bora): read-only ships first; edits follow** — the write model is *designed now, built second* (see G-4). Rejected: α-as-write-model (CRDT over a text projection; kills the invariant); β locking (advisory theater); δ never-writes (contradicts FI-4; registration needs writes).

## G-2 · How graphical edits become text (the LSP seam)

**Question:** the designed path is structured graph ops → `modeler/applyGraphEdit` → `WorkspaceEdit`. Is that the whole story?

- **G-2-α · Structured ops only.** Every edit the Designer offers is a graph operation the `@tatrman/edit` synthesizer understands.
  - *Buys:* trivia-preserving surgical patches by construction; the op vocabulary is reviewable design surface.
  - *Costs:* the op vocabulary will always lag the language (new grammar features need new ops); "edit this expression" as a graph op is a text editor wearing a costume.
- **G-2-β · Embedded text editing.** The Designer embeds a text panel (Monaco-style, LSP-connected); the graph is a *view*, text is the editor.
  - *Buys:* full language surface immediately; zero op-vocabulary maintenance.
  - *Costs:* abandons the graphical designer's point for structural edits (drag-an-edge beats typing a reference); the graphical roundtrip (S24's reason to exist) becomes decoration.
- **G-2-γ · Both, honestly split.** Graph ops for graph-shaped edits (add/remove/rename nodes, edges, containers, layout); an embedded text panel for content-shaped edits (expressions, `semantics`/`security` blocks); both routes produce `WorkspaceEdit`s into the same session branch.
  - *Buys:* each edit kind gets its natural tool; one write path underneath (G-1-γ's commit discipline unifies them).
  - *Costs:* two editing idioms to keep coherent (selection sync, dirty-state across panels).

**Lean: γ** — α alone can't express the language; β alone abandons the graphical designer. The security block (H-1) lands as *text* first (its grammar is young); graph affordances for it later.

**RESOLVED 2026-07-09 → G-2 = γ (both idioms, honestly split: graph ops for structure, embedded text for content; one `WorkspaceEdit` path into the session branch).** Rejected: α-only (op vocabulary lags the language); β-only (abandons the graphical designer's point).

## G-3 · Platform session architecture (what serves an edit session)

**Question:** reads are cheap; edits need a workspace. What runs where?

- **G-3-α · Stateless browser-only.** The browser-worker LSP (existing) compiles/validates against snapshot archives served by the metadata server; edits push `WorkspaceEdit`s back as commit requests.
  - *Buys:* no server-side sessions at all; the standalone browser path reused verbatim.
  - *Costs:* the TS LSP in a worker over a whole *served workspace* (multi-repo, big projects) strains memory/fetch; commit assembly (rebase, conflict detection) client-side is fragile; secrets/preview paths can't originate client-side anyway.
- **G-3-β · Server-side sessions.** Every Designer connection gets a server-side workspace (branch checkout + LSP/designer-server instance) over WS (the MD6 adapter path, platform-sized).
  - *Buys:* real git workspace per session (G-1-γ's natural home); server-side validation authority.
  - *Costs:* session-ful service (lifecycle, idle reaping, scale-out); heavy for read-only visitors — the *majority* (catalog browsers, run watchers) don't need a workspace.
- **G-3-γ · Hybrid: stateless reads, session-ful edits.** Read paths (catalog, graph views, lineage, runs) hit the metadata server's serving organ directly — no session, no LSP; **entering edit mode** materializes a server-side workspace (branch checkout + LSP) on demand; previews ride the query door with the session's principal.
  - *Buys:* the read majority stays cheap; the edit minority gets the full workspace; maps one-to-one onto G-1-γ (session = branch).
  - *Costs:* two serving paths to keep consistent (the graph you *read* vs the graph your *session* sees — mitigated: an edit session simply switches the data source to its workspace).

**Lean: γ.** Edit-session workspaces are the platform's concern (Gradle/Kotlin side, near `ttr-designer-server`'s machinery); the MIT frontend just switches backends per the MD6 adapter — Q-4-a undisturbed.

**RESOLVED 2026-07-09 → G-3 = γ (stateless reads; "edit" materializes a server-side branch workspace on demand; previews ride the query door under the session's principal).** Rejected: α browser-only (workspace assembly client-side is fragile); β sessions-for-everyone (the read majority doesn't need a workspace).

## G-4 · The v1 platform panel set (the extension surface's first cargo)

Candidates, each a platform-shipped extension on the Q-4-a surface: **catalog** (browse/search models, worlds, programs) · **graph/model views** (the existing Designer, platform-backed) · **runs** (F-6-β reads: history, live status) · **lineage** (column-grain, C-2 amendment) · **registration wizard** (E-2-γ/K: form-shaped UX → world-repo commit under the hood) · **presence** (advisory) · **deploy/envelope views** (F-2-β: what's deployed where, provenance) · **policy views** (H: which bundles in force — read-only).

- **G-4-α · Reader-first v1:** catalog + graph views + runs + lineage + registration wizard; presence/deploy/policy views v1.x.
- **G-4-β · Workbench v1:** all of it, plus edit mode. The full FI-4 vision in one release.
- **G-4-γ · Weird: registration-only v1** — the platform Designer ships as *just* the admin console; modeling views stay IDE-side. Catalogued to mark the floor (wastes the existing frontend).

**Lean: α + edit mode behind a flag** — the reader set is most of life-2's visible value; edit mode (G-1-γ/G-3-γ) hardens behind a feature flag and graduates when the workspace machinery is proven. Deploy views join when F's build lands (strangler ⑦).

**RESOLVED 2026-07-09 → G-4 = α reader-first (Bora: "first read-only, then edits"):** v1 = catalog + graph/model views + runs + column lineage + registration wizard; **+ GQ-2 decided in: read-only TTR-P program graphs ship in the reader set** (rendered from the E-5 documented manifest — near-free); edit mode = designed now (G-1/G-2/G-3 above), built second, graduating from behind a flag. GQ-3 ratified with the package: one wizard, kind-parameterized (E-2-γ made engines/orchestrators/connections the same world-entry concept). Rejected: β workbench-v1 (scope); γ registration-only (wastes the frontend).

## G-5 · The standalone/IDE story (the other mode, stated)

- **FI-2 confirmed as permanent:** the IDE view-only path (VS Code webview over `.ttrl` + model files) **stays when the platform Designer matures** — it is the standalone mode's Designer, not a transitional artifact. (Answers the map's open question.)
- **The preview asymmetry, stated as a decision (review input #2):** standalone Designer has **no data preview** — preview is execution, execution is "operate" (A-α), and the query door is the platform's. The standalone user runs bundles by hand (life 1). One sentence, on the record.
- **`.ttrl` coordination:** the browser Designer's view-state (layout, panel state) uses the same `.ttrl` sidecar schema as the IDEs — **GQ-1** tracks the dependency on TTR-P C1's content schema (don't fork the format).

---

## Hero rendering ("one program, three lives")

- **Life 1:** VS Code webview, view-only over the repo; no preview; `.ttrl` carries layout. Unchanged forever (G-5).
- **Life 2:** the browser Designer (MIT frontend, platform backend): catalog + graph of the deployed program, run history and **column lineage** from the metadata server; an analyst opens edit mode → a branch workspace materializes (G-3-γ) → drags a new edge (graph op) and edits an expression (text panel, G-2-γ) → "save" commits, "publish" follows repo policy (G-1-γ) → the org reviews a diff. An admin registers a new Postgres instance through the wizard — under the hood, a commit to the platform-world repo (K), fail-closed-validated (H-2).
- **Life 3:** delegated runs appear in the same run/lineage panels (E-4-β ingest) — the Designer doesn't know Airflow exists.

## Cross-links out

- **→ E-2-γ/K:** *confirmed* — registration UX = the wizard over world-repo commits; nothing re-renders (review input #1 discharged at convergence).
- **→ H:** HQ-3 resolves with G-1-γ (git perms are the write enforcement; the reserved platform PEP stays unused in v1); presence/session identity = H-2 principals.
- **→ C/F:** read panels consume C-2-γ serving + F-6-β reads only (no new contracts); preview = query door client (C-3-γ, F-5-γ interactive priority).
- **→ TTR-P:** GQ-1 (`.ttrl` schema, C1 session); the graphical surface for TTR-P *programs* (their 10-graphical work) is **not** G's scope — GQ-2 records the boundary.
- **→ J:** names — the extension surface, the wizard, edit-mode branding.

## Open questions (G-local)

- **GQ-1 · `.ttrl` content schema** — shared with TTR-P C1 (pending there); the browser Designer must not fork the format. Coordination item, not a fork.
- **GQ-2 · TTR-P program views** — does the platform Designer render TTR-P dataflow graphs in v1 (read-only, from the manifest graph — cheap?) or TTR-M models only? Lean: read-only program graphs are nearly free (E-5's documented manifest); authoring stays TTR-P's own workstream.
- **GQ-3 · Registration wizard scope v1** — engines only, or also orchestrators/connections? Lean: everything that is a world instance entry (one wizard, kind-parameterized — E-2-γ made them the same thing).
- **GQ-4 · Session limits/quotas** — edit workspaces are the first per-user server resource (H's F-5-δ sibling, single-org scale probably ignorable in v1; name it for ops).

## Convergence status

**🟢 G IS CONVERGED (2026-07-09, Bora)** — **G-1 γ** writes-through-git (session=branch, save=commit, publish=repo-policy; presence advisory; α = within-session future) · **G-2 γ** graph ops + embedded text, one `WorkspaceEdit` path · **G-3 γ** stateless reads / session-ful edits · **G-4 α** reader-first v1 (catalog, model views, runs, **column lineage**, registration wizard, **+ read-only TTR-P program graphs — GQ-2 in**), edit mode designed-now-built-second behind a flag · **G-5** ratified: IDE viewer permanent, preview asymmetry stated, `.ttrl` schema shared (GQ-1 coordination stands). **Discharged: HQ-3** (git perms are the write enforcement; H's reserved PEP unused in v1) **+ both review inputs** (E-2-γ/K presumption confirmed; asymmetry stated). GQ-4 (session quotas) = named ops item. Sequencing: reader panels ride strangler ② (metadata server); edit sessions + wizard-writes ride the platform-world repo (K) and land with edit-mode graduation.
