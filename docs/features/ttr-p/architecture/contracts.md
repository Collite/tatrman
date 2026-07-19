# TTR-P — Contracts (v1)

> **Status:** consolidated 2026-07-04. Source of truth for every TTR-P cross-component boundary: file formats, the project manifest, LSP methods, the bundle, emit payloads, diagnostics. Companion: [`architecture.md`](./architecture.md); decision IDs reference [`../design/00-control-room.md`](../design/00-control-room.md).
>
> **Changelog** at the end. Changes to anything here require a changelog entry.

---

## 1. File kinds & markers

| Kind | Extension | Dialect marker | Notes |
|---|---|---|---|
| Canonical program | `.ttrp` | extension | no `program` header (S12); identity = filename |
| Bare TTR-SQL program | `.ttr.sql` | double extension; override `-- ttr: dialect=sql` | source text never rewritten (C0) |
| Bare TTR-pandas program | `.ttr.py` | double extension; override `# ttr: dialect=pandas` | |
| Bare TTR-B program | `.ttrb` | extension; override `# ttr: dialect=b` (S19) | |
| View-state sidecar | `.ttrl` | — | family-wide; pairs by filename (`x.ttrp` + `x.ttrl`) |
| World / models | `.ttrm` (TTR-M) | — | world = `schema world` doc in the model repo (S22) |
| Bundle | `<program>.bundle/` (S1) | — | see §5 |

Embedded fragments in `.ttrp`: TTR tagged block literals — `"""sql`, `"""pandas`, `"""ttrb` (C3-g, C4-f). Tag = dialect. Fragment interiors are byte-preserved (C2-f); comment lexis per dialect: `--` (TTR-SQL), `#` (TTR-pandas, TTR-B) (S19).

## 2. The project manifest — `[ttrp]` table (S5)

Lives in the project manifest file (`modeler.toml`-equivalent; resolution = walk up, same as TTR-M). All keys optional unless marked; P2: no user dotfiles in the chain; precedence for the world: `uses world` pin > `[ttrp] world` > error.

```toml
[ttrp]
world            = "acme.worlds.dev"      # REQUIRED unless every program pins
bare-target      = "erp_pg"               # bare-fragment container target
bare-shell       = "bash"                 # bare-fragment executor
split-policy     = "warn"                 # T5-b escalation: warn | error
display-default  = "arrow"                # bare-program final-result sink format
staging          = ""                     # only if world doesn't declare staging: true
rls-egress       = "warn"                 # warn | error (Q8)
assist-provenance = "none"                # none | comment (C4-d-iii)
default-imports  = ["erp.*"]              # bare-fragment implicit prelude ONLY (S18)
```

## 3. Language-surface contracts (summary — grammars are canonical)

- **Statement forms (C3-a γ):** chains (`a -> filter(…) -> sort(…)`) + SSA assignment, freely mixed; precedence `=` < `->` < call. `=` is the one equality, context-separated; `==` rejected with diagnostic `TTRP-EQ-001` except inside TTR-pandas (S9).
- **Multi-in:** named-only (`join(left: …, right: …)`); **Union: list form only**, `union(a, b, c)`, internal ports `in1..inN` (S11). Inside TTR-SQL, SQL's own positional syntax carries port meaning (C2-b-ii).
- **Reserved port names:** `in, out, err, rejects, true, false, else` — lowercase; port names share column lexical rules (S10).
- **Control:** `b after a` (FS), `a with b` (SS); optional `control {}` block; `finishes with` reserved, use = capability error `TTRP-CTL-001`.
- **Containers:** closed; `container <name>(in …, out …, err …) target <engine> { … }` or `container <name> target <engine> """<tag> … """`; cross-container wiring at program level only.
- **Fragments (C2/C4):** single default-out; `err` only (no rejects producers); document scope flows in (ports > imports > qnames; same-level ambiguity = error); TTR-SQL = one query expression (`WITH` + final `SELECT`; clause table in `11-fragments-options.md`; `SELECT *` expands statically; LIMIT/OFFSET require ordered input else `TTRP-SQL-014`, S15); TTR-pandas methods = `select calc filter join aggregate sort union limit load store display` — full words only (S17); TTR-B roster + verbose expression synonym table in `12-nl-options.md`.
- **Movement:** cross-engine data edge ⇒ synthesized Store+Transfer+Load via world staging (D-f); explicit `load`/`store` for sources/sinks and control (S14); `via <storage>` override.
- **Schemas:** declared only — world doc or program (`schema:` inline / named def); inline > program > world; same-level conflict = error (D-c). Types = TTR db-schema attribute types verbatim (S23).

## 4. LSP & Designer protocol — `ttrp/*`

One Kotlin LSP; transports stdio + WebSocket (same methods). Standard LSP (diagnostics, hover, definition, rename, formatting) plus:

| Method | Params → Result | Notes |
|---|---|---|
| `ttrp/getGraph` | `{uri, version}` → `{graph, provenance, derived, orchestration, autoLayout}` | authored graph (containers + authored node kinds + ports + edges); nodes carry ζ + source ranges + er provenance (E-d); `orchestration` = derived islands/transfers/waves; **`autoLayout`** = per-canvas ζ → abstract `{layer, index}` (deterministic Kotlin-side layout, C1-b — the client maps to pixels per skin orientation) |
| `ttrp/applyGraphEdit` | `{uri, version, edits[]}` → `WorkspaceEdit` | β vocabulary (C1-d); formatter-owned placement; stale version ⇒ error, client replays |
| `ttrp/getLayout` / `ttrp/setLayout` | `{uri}` → sidecar content · `{uri, layout}` → ok | sidecar rewritten wholesale; ζ keys; pair-integrity diagnostics |
| `ttrp/getWorld` | `{uri}` → `{world, engines[], executors[], storages[], staging}` | resolved world for the document |
| `ttrp/transpile` | `{uri, version}` → `{bundlePath, manifest}` | = `ttrp build` |
| `ttrp/run` | `{uri, version}` → `{runId, exitCode, out[]}` | shells out to the bundle (C1-e); Designer fetches outputs over the loopback `GET /out/{name}` route (browser can't read the FS; path-traversal-guarded, serves the current bundle `out/` only — S24) |
| `ttrp/explain` | `{uri, version, node?}` → normalized graph, placements, rewrites applied, island→payload map | S4 |
| `ttrp/authoringContext` | `{uri?, position?}` → context bundle (§7) | S8; serves assist hosts and agents |
| `ttrp/validate` | `{source or uri, dialect?}` → `{diagnostics[]}` | full front-half check of candidate text; the assist repair loop's gate |

## 5. Bundle contract (F-f)

```
<program>.bundle/
├── run.sh                  # wave-parallel bash; set -euo pipefail; wait -n abort
├── manifest.json           # see below
├── islands/<name>.{sql,py} # one file per island; SSA/CTE names preserved
├── transfers/<name>.py     # generated ADBC/connectorx movement scripts
├── schemas/*.json          # Arrow schema fingerprints per staging boundary (Q9-1)
└── plans/*.pb              # plan.v1 payloads — Kantheon-target worlds only (E-a)
# created at runtime, wiped on restart (F-e): logs/ staging/ out/
```

`manifest.json` (F-f-i; machine record — the narrative is run.sh + islands):

```json
{
  "ttrpVersion": 1,                     // spec version (S6)
  "toolchain": "org.tatrman:ttrp:<semver>",
  "program": "<filename>",
  "world": {"qname": "acme.worlds.dev", "fingerprint": "sha256:<semantic-hash>"},
  "islands": [{"name": "...", "engine": "...", "executor": "bash",
               "invocation": "psql|python3", "file": "islands/...", "sha256": "..."}],
  "transfers": [{"from": "...", "to": "...", "via": "<staging>", "file": "...", "sha256": "..."}],
  "waves": [["island-a","island-b"], ["island-c"]],
  "connections": ["TTR_CONN_ERP_PG", "TTR_CONN_FILES"],
  "displays": [{"name": "main_result", "file": "out/main_result.arrow"}],
  "files": {"<path>": "sha256:..."}
}
```

Exit contract: `0` ok · `1` island failure · `2` pre-flight failure (missing `TTR_CONN_*`, world-incompatibility). Credentials **only** via `TTR_CONN_<NAME>` env vars; the artifact is secret-free. Staging format = Arrow IPC everywhere (F-c-i). The world fingerprint is **recorded** by the artifact and **verified** by capable invokers (T6 split); bash pre-flight checks env/connections only.

## 6. Emit contracts (E)

- **SQL:** CTE-per-node; node → named CTE (SSA/CTE name); trivial islands flat SELECT (deterministic rule); every Sort emits explicit `NULLS LAST` (or authored placement); dialect v1 = Postgres.
- **Polars:** straight-line script; generated inline prelude containing only needed enforcement helpers (3VL NULL, decimal, UTC-µs datetimes — Q9 items 4–6); no runtime library dependency.
- **PlanNode:** when the container's target resolves to a Kantheon world engine, the invocation binding delivers `plan.v1.PlanNode` protobuf payloads under `plans/` — produced by `org.tatrman:ttr-translator`; `plan.v1` proto **vendored** in ttr-translator (S25 lean; final call in the extraction arc).
- **Invocation bindings v1 (F-c):** pg×bash `psql -v ON_ERROR_STOP=1 --no-psqlrc -f` · polars×bash `python3` (interpreter+packages from executor-type manifest) · display×bash file drop `out/<name>.<fmt>` + printed notice.

## 7. Authoring-context bundle (`ttrp/authoringContext`, C4-d/S8)

Deterministic, prompt-ready serialization — **normative schema: [`authoring-context.schema.json`](./authoring-context.schema.json)** (`$id: https://tatrman.org/schemas/ttrp/authoring-context/v1`; two example instances under [`examples/authoring-context/`](./examples/authoring-context/)). The bundle carries: `version` · `world` (resolved summary — qname, fingerprint, engines/executors/storages, staging, per-storage rls flag) · `capabilities` (per-engine node/function support tables — T6 manifests) · `modelObjects` (db + er, with schemas) · `scope` (imports, variables with resolved column schema, ports at cursor — present only when a cursor `position` was given) · `grammar` (spec version, statement summary, per-dialect rosters for ttrp/sql/pandas/ttrb) · `diagnostics` (the named-diagnostic catalogue = the repair vocabulary). Every object is closed (`additionalProperties: false`); determinism is the product (C4-d-ii). The **schema is final** (Stage 4.2); some content grows in later phases — the capability node/function rosters, model-object enumeration, and the TTR-SQL/TTR-B grammar rosters + TTR-B diagnostic rows land in P6/P7 with the shapes already pinned. Consumed by any LLM host; paired with `ttrp/validate` in a generate→validate→repair→review loop; generated text arrives as a proposed edit, never applied silently (C4-d-iii).

## 8. Diagnostics convention

Named ids, stable, documented: `TTRP-<AREA>-<NNN>` (areas: EQ, SQL, PD (pandas), B, CTL, CAP, MOV, SCH, WLD, RLS, **LAY** (`.ttrl` view-state pair integrity), **EDIT** (graphical edit), **FRG** (fragment/bare-program: unknown dialect tag, missing dialect marker, missing `[ttrp]` bare defaults), **RJ** (rejects / erroneous-rows producer)…). Every rejected form carries a suggested alternative (`TOP 10` → "use LIMIT 10"; `agg` → "use aggregate"; `==` → "use ="). The reject tables per dialect are versioned fixtures (test + assist repair vocabulary). **`LAY`** ids (Stage 5.2): `TTRP-LAY-001` layout entries no longer match the graph (orphaned → reset/re-place), `TTRP-LAY-002` sidecar parse error, `TTRP-LAY-003` sidecar references an unknown canvas.

### 8.1 Rejects diagnostics (`RJ`)

Two families (RJ-P6). **Authoring/rewrite diagnostics** — surfaced during compilation, each with a suggested alternative (`TtrpDiagnosticId`, `ttrp-frontend/.../diagnostics/Diagnostics.kt`):

| Id | When | Suggested alternative |
|---|---|---|
| `TTRP-RJ-101` | `rejects` wired on a node that can never reject (dead wire) | remove the `rejects` wire (the stream is always empty; R-A2-α) |
| `TTRP-RJ-102` | reject cluster moved off its engine (knob=escalate) | the rejects cluster was moved off this engine because it cannot produce rejects (knob=escalate) |
| `TTRP-RJ-103` | a user column uses the reserved `_ttrp_` prefix | the `_ttrp_` column prefix is reserved for synthesized rejects columns — rename this column (RS-5) |
| `TTRP-RJ-104` | a volatile (impure) function in a reject-capable position | a volatile (impure) function cannot appear in a reject-capable position (R-C2-b) |
| `TTRP-RJ-105` | (reserved) an ON expression spans both join inputs — superseded in v1 by RJ-108 (join-ON rejects are fail-closed, not a pair-schema fallback) | this ON expression spans both inputs — its rejects fall back to the pair schema (R-B3-β) |
| `TTRP-RJ-106` | the placed engine cannot produce rejects (knob=produce/error) | this engine cannot produce rejects; set `[ttrp] rejects-in-sql = escalate` (or move the site to a capable engine) |
| `TTRP-RJ-107` | `rejects` wired on a reject-capable cast whose type v1 does not emit faithfully (only `text->int64` + `op.div` are supported) | remove the `rejects` wire, or change the target type — bool/date/decimal/float/timestamp casts are fail-closed in v1 (RJ-P5 review, B1) |
| `TTRP-RJ-108` | a reject-capable expression appears in a join `on:` with a wired `rejects` port | move the `cast`/`op.div` into a `calc` before the join and wire `rejects` there — join-ON rejects are fail-closed in v1 (RJ-P5 review, B2) |

**Row reject-codes** — the per-row `_ttrp_reject_code` a reject producer emits into the erroneous-rows stream, one per catalogue-defined validity site (the `code:` field of each `ttrp/validity/*.yaml`):

| Code | Reject-capable site |
|---|---|
| `TTRP-RJ-001` | `cast text→int64` |
| `TTRP-RJ-002` | `cast text→decimal(18,4)` |
| `TTRP-RJ-003` | `cast text→float64` |
| `TTRP-RJ-004` | `cast text→date` |
| `TTRP-RJ-005` | `cast text→timestamp` |
| `TTRP-RJ-006` | `cast text→bool` |
| `TTRP-RJ-007` | `op.div numeric,numeric→numeric` (zero denominator) |
| `TTRP-RJ-008` | `fn.to_date` (unparseable) |
| `TTRP-RJ-009` | `fn.to_timestamp` (unparseable) |

Completeness is guarded by a test (`RejectDiagnosticsTableSpec`): every `TTRP-RJ-1xx` enum id and every validity-YAML `code:` appears here, and vice-versa.

## 9. Conformance — `ttrp-conform` (S3, Q9)

Invoker contract: reads `manifest.json` → provisions `TTR_CONN_*` → runs `run.sh` per engine placement variant → collects `out/` + staged Arrow (incl. the `rejects`/`bad` streams named in the manifest reject sites) → compares under the seven-point per-stream procedure (fingerprint schemas; multiset rows, canonical-sort under terminal Sort; NULLS LAST; decimal exact / float64 declared tolerance; UTC-µs; binary collation), **plus an eighth (partition) point** when the program produces rejects (RJ-P5): per reject site, `in == processed + rejects` per engine **and** the `(in, processed, rejects)` triple agrees across engines. `in`/`processed`/`rejects` are counted independently at run time (guard-input / guard-clean-output / reject terminal) into a `counts.json` beside the Arrow exports; the cross-engine triple-match turns red for a broken producer even when the accepted-row displays agree. Doubles as emit regression suite and standalone-vs-Kantheon drift guard. Invoked as `ttrp conform`.

## 10. Published artifacts

| Artifact | Content | Consumer |
|---|---|---|
| `org.tatrman:ttr-parser` / `:ttr-writer` / `:ttr-semantics` | TTR-M toolchain (existing; parses `.ttrl` too) | kantheon (Ariadne), TTR-P compiler |
| `org.tatrman:ttr-metadata` | model graph + queries + world resolution | Ariadne wrapper, TTR-P compiler, Designer server |
| `org.tatrman:ttr-translator` | Proteus core: island→RelNode→SQL/`plan.v1` | TTR-P compiler; kantheon Proteus (thin wrapper) |
| `org.tatrman:ttrp-*` (front-half, emit, lsp, cli, conform — module cut in plan Phase 0) | the TTR-P toolchain | editors, Designer server, agents (authoring seam, C4-e) |

Publishing: tag-driven per `PUBLISHING.md`; spec version via grammar-master process (S6). npm scope: `@tatrman/*` (S7, plan Phase 0).

---

## Changelog

- **v1.5 · 2026-07-19** — Rejects / erroneous-rows producer (design/rejects arc, RJ-P0…P6). Catalogue-defined canonical validity (`ttrp/validity/*.yaml`) + a graph-rewrite guard-and-branch elaboration stratum turn a wired `rejects` port into a real reject producer: SQL emits one more terminal SELECT (guard CTE + first-error CASE ladder), Polars a mask-and-split. §9 conformance gains an **eighth (partition) point** — per reject site, `in == processed + rejects` per engine + the triple agrees across engines (`counts.json` beside the Arrow exports; `processed` counted at the guard's clean output). New diagnostics area **`RJ`** (§8.1): authoring/rewrite `TTRP-RJ-101..106` + row reject-codes `TTRP-RJ-001..009` (from the validity YAMLs). `ttrp/getGraph` gains an `elaborated` flag (serves the normalized graph with the synth reject cluster, each node `synthesized` + `synthOf`-back-referenced); the authoring-context capability roster gains a per-engine `rejects` boolean (additive; schema stays v1). Fail-fast (unwired-rejects) programs stay byte-identical (R-P3). **Live-sealed PG↔Polars** (SQL Server target parked — [#44](https://github.com/Collite/tatrman/issues/44)).

- **v1.4 · 2026-07-08** — Fragment dialects (Phase 6). `"""sql` / `"""pandas` fragment interiors now decompose clause-wise into the standard node set (C2-a-β): the dialect grammars (`TTRSql.g4`, `TTRPandas.g4`, own grammars per C2-g α) lower each fragment to canonical TTR-P AST, so resolution + graph construction are shared and **bare ≡ embedded ≡ canonical produce byte-identical normalized graphs** (the KEY GATE; `NormalizedGraphJson` is the canonical serializer). S16 clarified: the shared keyword/operator table is Kotlin-hosted (`KeywordTable` + `CatalogId` + the one `Expression` IR) — the dialect grammars SKIN it and carry sibling drift specs, NOT an ANTLR grammar unit. Fragments still **emit verbatim** (`SqlIslandEmitter` unchanged) — decomposition is for graph structure/identity, not re-emission; interiors stay byte-verbatim (C2-f). New diagnostics area **`FRG`** (`TTRP-FRG-001..003`, §8): unknown dialect tag, missing dialect marker (bare programs), missing `[ttrp]` bare-target. Reject tables `ttr-sql.rejects.toml` (SQL-001..015) + `ttr-pandas.rejects.toml` (PD-001..010) are versioned fixtures (§8). Grammar files added to `packages/grammar/src/` (Kotlin-only generation, G-b). **Remaining P6 work** (bare-program wrapper synthesis from `[ttrp]` defaults, conform-three-ways, Designer fragment drill-in) noted in the Phase-6 progress doc.

- **v1 · 2026-07-04** — initial consolidation from the design log (A…H, C0–C4, F-lite, S1–S25).
- **v1.1 · 2026-07-07** — authoringContext v1 schema finalized (Stage 4.2); §7 is now normative via `authoring-context.schema.json` (+ two example instances). The C4 leftover ("concrete schema = plan work item") is closed. Diagnostics area `EMT` (SQL/emit, `TTRP-EMT-001..006`) recorded in Phase 3.
- **v1.3 · 2026-07-07** — Designer edit + run (Phase 5.4). `ttrp/applyGraphEdit` implemented: the closed β vocabulary (C1-d-i) → a formatter-owned whole-document `WorkspaceEdit`; stale version ⇒ `ContentModified` carrying **`TTRP-EDIT-001`** (client re-pulls + replays). New diagnostics area **`EDIT`** (`TTRP-EDIT-001..004`, §8): stale version, fragment/derived target rejected, unknown op, invalid target. New loopback **`GET /out/{name}`** route on `ttr-designer-server` (§4/§5, path-traversal-guarded, serves the current bundle `out/` only — the browser's transport for run outputs). v1 applyGraphEdit cut synthesizes the additive hero-build ops (createContainer/addNode/connect/assignTarget); mutating/rename ops return `TTRP-EDIT-003` pending their synthesis (rename already available via `textDocument/rename` + the 5.2 sidecar-atomic participant).
- **v1.2 · 2026-07-07** — Designer surface (Phase 5.1/5.2). §4: `ttrp/getGraph` result gains `orchestration` (derived islands/transfers/waves) + **`autoLayout`** (per-canvas abstract `{layer, index}` — the deterministic Kotlin-side layout contract, C1-b); `ttrp/getWorld` shape pinned (`{world, fingerprint, engines[], executors[], storages[], staging}`). New diagnostics area **`LAY`** (`TTRP-LAY-001..003`, §8) for `.ttrl` pair integrity. The `.ttrl` sidecar body is grammar-hosted in `TTR.g4` v4.3 (C1-c-iii; separate `ttrlDocument` entry rule, `chains` records SSA chain lengths for deterministic orphaning). The WS-LSP transport mounts at `/lsp` on `ttr-designer-server` (MD8).
