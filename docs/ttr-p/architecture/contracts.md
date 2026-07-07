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
| `ttrp/getGraph` | `{uri, version}` → `{graph, provenance, derived}` | full graph incl. derived containers for bare fragments; nodes carry source ranges + er provenance (E-d) |
| `ttrp/applyGraphEdit` | `{uri, version, edits[]}` → `WorkspaceEdit` | β vocabulary (C1-d); formatter-owned placement; stale version ⇒ error, client replays |
| `ttrp/getLayout` / `ttrp/setLayout` | `{uri}` → sidecar content · `{uri, layout}` → ok | sidecar rewritten wholesale; ζ keys; pair-integrity diagnostics |
| `ttrp/getWorld` | `{uri}` → `{world, engines[], executors[], storages[], staging}` | resolved world for the document |
| `ttrp/transpile` | `{uri, version}` → `{bundlePath, manifest}` | = `ttrp build` |
| `ttrp/run` | `{uri, version}` → `{runId, exitCode, out[]}` | shells out to the bundle (C1-e); Designer watches `out/` |
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

Named ids, stable, documented: `TTRP-<AREA>-<NNN>` (areas: EQ, SQL, PD (pandas), B, CTL, CAP, MOV, SCH, WLD, RLS…). Every rejected form carries a suggested alternative (`TOP 10` → "use LIMIT 10"; `agg` → "use aggregate"; `==` → "use ="). The reject tables per dialect are versioned fixtures (test + assist repair vocabulary).

## 9. Conformance — `ttrp-conform` (S3, Q9)

Invoker contract: reads `manifest.json` → provisions `TTR_CONN_*` → runs `run.sh` per engine placement variant → collects `out/` + staged Arrow → compares under the seven-point procedure (fingerprint schemas; multiset rows, canonical-sort under terminal Sort; NULLS LAST; decimal exact / float64 declared tolerance; UTC-µs; binary collation). Doubles as emit regression suite and standalone-vs-Kantheon drift guard. Invoked as `ttrp conform`.

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

- **v1 · 2026-07-04** — initial consolidation from the design log (A…H, C0–C4, F-lite, S1–S25).
- **v1.1 · 2026-07-07** — authoringContext v1 schema finalized (Stage 4.2); §7 is now normative via `authoring-context.schema.json` (+ two example instances). The C4 leftover ("concrete schema = plan work item") is closed. Diagnostics area `EMT` (SQL/emit, `TTRP-EMT-001..006`) recorded in Phase 3.
