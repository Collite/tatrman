# MD feature — brainstorming & planning transcripts

Captured 2026-06-26 from this machine's Cowork sessions. These are conversational
transcripts (assistant tool calls collapsed to `(called X)`; all user/assistant prose
preserved). They trace the MD feature from the design doc through the full Phase 0 and
Phase 1–4 task lists, including the two locked decisions (calc param surface, catalog floor).

The **foundational design brainstorm** (origin session `49a16e4b`, where the firm naming
and modeling decisions were first hammered out) wasn't separately re-exportable from this
machine, but its complete outcome is `docs/features/md/design.md` — read that alongside
these. The `md-model-design` memory (in `../memory/`) also distills the firm decisions.

---

## Session 1 — "Multidimensional models language design"

This session opens just as the design brainstorm concludes (with "write the design doc")
and then produces the Phase 0 legacy-rename task set, stage by stage, with the user's
decisions woven in (`def area` over a file kind; removing the obsolete sync scripts; adding
the Python parser to Stage D; adding the `ai-models` migration as Stage E; and adding Stage
AA for the inline `mapping:` → `binding:` rename).

```
[user] YEs, agreed, please, write the design doc
[assistant] Let me check the existing design-doc conventions so the new doc matches, then write it.
[assistant] (called mcp__workspace__bash)
[assistant] All three confirmed. Writing the design doc in the repo's house style, at `docs/features/md/design.md`.
[assistant] (called Write)
[assistant] (called mcp__cowork__present_files)
[assistant] Done — `docs/features/md/design.md` captures the whole brainstorm.

It's organized as: the two-layer framing (model now, ops DSL deferred), the naming/migration decisions (`domain` reclaimed, old one → `area`/`.ttra`; `mapping` → `binding`; `attribute` shared across schemas), the seven logical objects with syntax sketches and the rules we settled (maps-as-functions, emergent leaves vs single `key`, calc/bound domains, hierarchy inference + `via:`, additivity classes), the binding layer (wide/long, journaling, map-mediated attributes, multi-source as multiple defs, thin `md→er`), the built-in catalog as a cross-repo contract, how it sits against your existing invariants, and a deferred/open list plus suggested next steps.

Two things I deliberately left as open in the doc rather than inventing answers: the **catalog's home package + sync story**, and the exact **seed list of Time built-ins**. Those are the natural starting point if you want to move toward a contracts/grammar pass next.

[user] OK, first of all, please, prepare the Phase 0 - legacy rename tasks, so we can fix the current state.  Just prepare the task list to rename the current "domain" and "mapping" keywords (anything else?) and "ttrd" files.
While at it, please, let's rename the "ttr" extension to "ttrm" for "Tatrman Model" file.

Question: the "area" is actually a part of the model; could we just use the "def area ... " construct and not a completely new file?
[assistant] Before writing the task list I need to scope exactly what these renames touch. Let me map the current state.
[assistant] (called mcp__workspace__bash) ×4
[assistant] Workspace shell is hung; switching to file tools.
[assistant] (called Read)
[assistant] Planning skill gives me the format the user expects: extremely specific tasks, broken into mini-lists of 6–8 with checkboxes, an overall management doc, TDD ordering. I have the scope mapped. First, my answer to your question, since it changes Stage B.

**Yes — make it `def area`, drop the file kind.** "area" is model content (a grouping of packages/entities), so a normal `def area { packages: […], entities: […] }` belongs alongside other defs, can be imported/namespaced/referenced like them, and lets you delete the entire `.ttrd`/`.ttra` file-kind concept (its grammar block, the semantic "file ⇔ exactly one block" rule, and the special-casing in `resolve-packages`/`migrate`). The one consequence to coordinate: ai-platform's agent registry currently discovers areas by `.ttrd` *file*; it must switch to discovering `area` *defs*. That's a cleaner contract and we're already coordinating a breaking grammar bump. I've built Stage B on this; say the word if you'd rather keep a file kind.

Writing the Phase 0 task set now.
[assistant] (called Write) ×5, (called Edit), (called mcp__cowork__present_files)
[assistant] Phase 0 task set is ready under `docs/features/md/plan/phase-0-legacy-renames/`, in your planning format (mini-lists of 6–8 checkboxed, TDD-ordered tasks with verification commands and verified file pointers).

Structure: an **INDEX** management doc (rationale table, sequencing A→B→C→D, gates, DONE criteria), then four stages — **A** `schema map`→`schema binding` (er2db defs untouched), **B** `domain`/`.ttrd`→`def area` (file kind deleted), **C** `.ttr`→`.ttrm` (the wide, mechanical one; `.ttrg` left alone), **D** the grammar **3.0** bump + ai-platform/Kotlin-conformance sync + a `migrate phase0` helper + dead `.ttrl` cleanup.

Two things worth your attention:
- "Anything else?" — the only other collision-class rename I found is the schema code itself; `attribute` stays in both schemas, and `model`/`relation`/`graph` don't clash. I also surfaced the dead **`.ttrl`** layout references (removed in v1.1 per CLAUDE.md D4 but still in the designer) and folded their cleanup into Stage D.
- I couldn't run the scoping greps at the end — the workspace shell hung — so the file-counts are left as live `rg` commands in the tasks rather than hard numbers. The file/line pointers themselves come from greps that did succeed, but the implementer should re-run each `rg` since line numbers drift.

One open call for Stage C2: keep the internal VS Code language **id** as `ttr` (less churn) vs. renaming it to `ttrm` — I recommended keeping it. Flag if you'd rather rename the id too.

[user] I am reading the Stage D plan - but we have moved the parser in here, ai-platform should just consume the package, I think?
If so, please, remove the sync scripts so that they do not mislead us in the future; and write a specific file "new grammar version process.md" to prepare a template check-list for future updates.
Also, we have now the Python parser - updating it should belong to the Stage D as well, I think.
[assistant] Let me verify the current state — the sync scripts, what references them, and where the Python parser lives.
[assistant] (called Glob/Grep ×6, Read ×3, bash, ToolSearch, allow_cowork_file_delete, Edit ×N, Write)
[assistant] You were right on all three. Here's what I did:

**Removed the obsolete sync machinery** (the grammar is target-neutral and the parser now lives here, so ai-platform consumes the published artifact, not a vendored copy):
- Deleted `packages/grammar/scripts/sync-to-ai-platform.sh` and `check-sync.sh`, and the `.github/workflows/grammar-sync.yml` workflow.
- Removed the `sync-ai-platform` script from root `package.json`.
- Rewrote the CLAUDE.md "Grammar regeneration" paragraph: `TTR.g4` is the single canonical source for all three generated parsers (TS/Kotlin/Python), not vendored anywhere; consumers bump the published Maven/PyPI version; drift is caught by `conformance.yml`.

**Wrote `docs/grammar-master/new-grammar-version-process.md`** — a reusable, tick-box template for future grammar bumps: pre-flight (additive vs breaking; target-neutrality guard), grammar edit, regenerate+wire all three targets, the conformance lock-step gate, docs, repo gates, tag-driven publish, and downstream dependency bump.

**Folded the Python parser into Stage D.** D3 now re-ports and conforms *all three* targets (TS, Kotlin, **Python** via `generate-python-parser.sh` + pytest/ruff/mypy); D4 is now "publish (`kotlin/v…`, `python/v…`) + ai-platform bumps the dependency version (no sync)."

(Notes: couldn't `git rm` from the sandbox, so the three files are deleted from the working tree but need `git add -A` to stage; `docs/ai-platform-upgrade.md` still has sync references but reads as a v2.0 migration record, left as-is.)

[user] OK. PLease, add the Stage E - `ai-models` repo migration.
In the `ai-models` repository (~/Dev/ai-models) there is the model-ttr folder with the actual content of the model used in the ai-platform. We need to migrate the files there and check that the ai-platform's metadata service loads the new files.
[assistant] (called Glob; requested directory access; bash ×N to inspect model content; Write Stage E; Edit INDEX; Write ai-models memory; present_files)
[assistant] Stage E added and the INDEX updated (sequencing now A→B→C→D→E, with a fifth DONE criterion).

What inspecting `~/Dev/ai-models/model-ttr` told me:
- **41 `.ttr` files** to rename, **2 `.ttrd` domains** (`accounting`, `obchod`) to convert to `def area`.
- **No `schema map` anywhere** — the model uses only `db`/`er`/`cnc` (per `modeler.toml`), so the binding rename doesn't touch this repo at all.
- The ai-models Python tooling only scans `agents/**/*.yaml`; the `.ttr`/`.ttrd` discovery lives inside the **Modeler CLI's `resolve-packages`** (driven by the repo `justfile`). So the real work is: rename files, convert domains, **regenerate `resolved-packages.json` with the 3.0 CLI**, and run the existing `just check-*` gates.

The acceptance task (E7) is the metadata-service check you asked for: point ai-platform's loader (the Stage-D branch that understands `.ttrm` + `area`) at the migrated `model-ttr/`, assert zero load errors and that all `er.entity.*` / `db.dbo.*` / `db.query.*` / `cnc.cnc.role.*` references resolve against a pre-migration baseline, and confirm `METADATA_GIT_KIND=ttr` discovery now globs `.ttrm`. E runs last and lands its PR in lockstep with the modeler 3.0 release and the ai-platform loader PR.

[user] We have forgotten one change: the inline "mapping" keyword in E-R model - this should change to "binding" as well to keep it consistent.
Please, create a Stage AA in which we will do this chage, as the Stage A is already being developed. UPdate the overall Phase 0 with that.
Anything else that seems overlooked to you?
[assistant] (called bash, Grep; Write Stage AA; Edit INDEX + Stage E + Stage D migration helper; present_files)
[assistant] Stage AA is in, sequenced **A → AA → B → C → D → E**, and the `mapping:`→`binding:` rename now also flows into the Stage D migration helper (D6) and the ai-models content (Stage E, new task E4a — confirmed needed since `vyteznost/er.ttr` and most `er.ttr` files use it).

**Anything else overlooked — what I found while scoping AA:**
Already folded in: the ai-models inline-`mapping:` usage (E4a + D6), and the diagnostic-code rename `ttr/duplicate-mapping` → `ttr/duplicate-binding` (AA3) — flagged because it's a *breaking diagnostic-code change* worth a CHANGELOG line.

Three that still want a decision:
1. **`er2db_*` def keywords** — kept (they name the source→target specifically). My lean: keep them.
2. **`modeler.toml` schema declarations** — any `declared = [… "map" …]` / `namespaces = { map = … }` must become `"binding"`; should be explicit in Stage A (ai-models is already clean — db/er/cnc only).
3. **Cosmetic internals** — `mapping-references.ts` / `mapping-synthesizer.ts`, `collectMappingReferences` / `MappingReference` keep "mapping" unless renamed (AA4 leaves this your call).
One I couldn't fully check: whether the **Designer** surfaces "mapping" in any user-facing label — noted as a check during AA.
```

---

## Session 2 — "MD feature design planning"

This session produced all the MD feature task lists (Phases 1–4) and then nailed down the
two open decisions (calc param surface, catalog floor) against the real `ai-models` content.

```
[assistant] (cross-checked the docs against live TTR.g4; fixed two inaccuracies — the DOMAIN token was fully *deleted* in 3.0 so 3.1 must re-introduce it; one diagnostic code name had drifted)
[assistant] Done. Four planning docs written under `docs/features/md/`, all grounded in the current `TTR.g4` (3.0 post-Phase-0) and the design decisions in `design.md`.

**Time-converting (calc-map) functions** — `map-catalog.md` proposes a fixed v1 Time catalog in four families: Truncation (`truncToDay/Week/Month/Quarter/Year`, sub-day), Extraction (`monthOfDate`, `quarterOfDate`, `yearOfDate`, `dayOfWeek`, `weekOfYear`, `hourOfDay`, …), Roll-up (`quarterOfMonth`, `monthOfWeek`, `halfOfQuarter` — what `def hierarchy` infers between levels), and Fiscal (`fiscalYearOfDate`, `fiscalQuarterOfMonth`, … parameterised by `fiscalYearStartMonth`). Each entry is an abstract N:1 signature, no SQL — the cross-repo contract lands in a new **`@modeler/md-catalog`** package, vendored to ai-platform for lowering.

**The three planning docs:** `contracts.md` (full v1: AST shapes, property tables, symbol namespaces, leaf/grain + hierarchy-inference + additivity algorithms, `md/*` diagnostics, `@modeler/md-catalog` contract); `grammar-md-changes.md` (additive **3.1** sketch: `schema md`, the new def kinds, `md2db_*`/`md2er_cubelet` bindings, `DOTDOT` range literal); `plan/implementation-plan.md` (four phases with deliverables, TDD ordering, DoD).

[user] Create all task lists, please
[assistant] (matched Phase 0 format; verified package/file layout; created 4 phase folders, 4 INDEX docs, 17 TDD-ordered stage task-lists)
[assistant] All task lists created. Phase 1 foundation: md-catalog package · grammar 3.1 + regen + fixtures · AST/walker logical objects · AST/walker binding objects. Phase 2 logical semantics: symbols + catalog preload · resolver · domain/attr/measure validators · map + calc-catalog · leaf/grain + hierarchy inference · cubelet + LSP + integration. Phase 3 binding: bound-domain & table-map · cubelet shapes/columns/journaling · multi-source/completeness/md2er. Phase 4 conformance & ship: cross-target conformance · catalog vendoring + 3.1 publish · RAE end-to-end fixtures · manual + CHANGELOG.

Two things worth deciding before an implementer starts Phase 1: the calc **param surface** (`functionCall` vs a `calcArgs:` property) and the **catalog v1 floor** (~30 entries vs trimming to what `ai-models` uses).

[user] Let's discuss the points you have opened: [calc param surface; catalog v1 floor]
[assistant] ## 1. Calc param surface
First, a correction: `functionCall : id LPAREN ( value (COMMA value)* )? RPAREN` is **positional only** — no `key: value`. So the real choice is three-way:
**Option A — dedicated `calcRef` rule with named parens args** (my recommendation): `calcRef : id ( LPAREN ( calcArg (COMMA calcArg)* )? RPAREN )? ; calcArg : id propSep? value ;` → `calc: truncToDay` and `calc: fiscalYearOfDate(fiscalYearStartMonth: 4)`.
**Option B — `calc` + sibling `calcArgs: { … }`** (reuses `object_`, zero new rules).
**Option C — positional `functionCall`** → `calc: fiscalYearOfDate(4)`.
I'd pick **A** — params are all optional-with-defaults, which is exactly where positional args hurt (can't skip an earlier optional; adding a param later shifts positions, a silent breaking change). Named args are order-independent and forward-compatible, and `key: value` is TTR's idiom everywhere.

## 2. Catalog v1 floor
The deciding asymmetry: **adding** an entry is a cheap additive minor bump; **changing** a shipped signature is a breaking major bump. So ship a "certain core + verified usage" floor, not the full 30:
- **Certain core (~9):** `truncToDay/Month/Quarter/Year`, `monthOfDate`, `quarterOfDate`, `yearOfDate`, `quarterOfMonth`.
- **Add iff ai-models uses them:** `dayOfMonth`, `dayOfWeek`, `weekOfYear`, `truncToWeek`, and the `fiscal*` family.
- **Defer:** sub-day, `halfOfQuarter`, `monthOfWeek`/`quarterOfWeek`.
To finalize the middle bucket I'd grep ai-models; if you mount `~/Dev/ai-models` I'll give the exact list.
[assistant] (called AskUserQuestion — both locked: dedicated `calcRef` with named args; certain-core-plus-grep for the floor)
[assistant] (baked the grammar decision into the docs; mounted ai-models; grepped the real model content)
[assistant] Both decisions baked in, floor grounded in your real model content:
- Day/month/quarter/year all evidenced (heavy `datum_*`, `roční`, monthly trailing-window measures, literal `DATEPART(quarter,…)` / `DATEPART(week,…)` SQL). So **week is in** — `weekOfYear` + `truncToWeek` ship.
- The **fiscal family drops entirely** — the real `účetní období` / `fiskální období` ("RRRR.MM", QUCTOBD) is a *table-keyed entity*, i.e. a `kind: bound` domain + `md2db_domain`, not a calc map. (A nice validation of the bound-domain design.)
- v1 ships these **11**: `truncToDay/Month/Quarter/Year/Week`, `monthOfDate`, `quarterOfDate`, `yearOfDate`, `quarterOfMonth`, `dayOfMonth`, `weekOfYear`. Sub-day, `dayOfWeek`, `halfOfQuarter`, week-rollups, and all fiscal entries deferred.
- Week scheme default is **ISO-8601**, with a lowering caveat recorded: SQL Server's bare `DATEPART(week,…)` isn't ISO, so ai-platform's lowering must pin `ISO_WEEK`/`DATEFIRST 1`.
Stage 1A now seeds exactly that 11-entry set.

[user] [capture the SQL-Server week mismatch as an explicit ai-platform coordination note] YEs, please
[assistant] (Edit map-catalog.md §4bis "Lowering caveats (ai-platform contract)" + Stage 4B2 checkbox)
[assistant] Captured in both places ai-platform will look: `map-catalog.md` §4bis (lead row = the ISO-week mismatch, plus ISO week-year pairing and timezone-free truncation notes) and Stage `4B2` (explicit checkbox to carry §4bis into the lowering spec). The fix lives in ai-platform's lowerings; modeler only ships the abstract signature.
```

---

## Pointers (in the repo proper)

- `docs/features/md/design.md` — the authoritative design (outcome of the foundational brainstorm).
- `docs/features/md/contracts.md`, `grammar-md-changes.md`, `map-catalog.md` — the v1 spec.
- `docs/features/md/plan/` — Phase 0 (legacy renames) and Phases 1–4 (the MD feature) task lists.
- `docs/features/md/RAE/` — the legacy reference examples that ground the design.
