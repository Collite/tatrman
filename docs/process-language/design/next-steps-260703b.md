# PL Design — Next Steps (pick-up point, written 2026-07-03, after the D session)

> Where to resume the TTR Processing Language design effort. Read the [Control Room](./00-control-room.md) decision log first, then this.
> Option catalogues: [`01`](./01-design-space-map.md) · B → [`02`](./02-internal-model-options.md) · G → [`03-tooling-delivery-options.md`](./03-tooling-delivery-options.md) · C0 → [`04-surfaces-options.md`](./04-surfaces-options.md) · C3 → [`05-canonical-dsl-options.md`](./05-canonical-dsl-options.md) · D → [`06-model-binding-options.md`](./06-model-binding-options.md).
> Supersedes [`next-steps-260703.md`](./next-steps-260703.md).

## Where we are

**A 🟢 · B 🟢 · G 🟢 · C0 🟢 · C3 🟢 · D 🟢 (core)** — the canonical grammar exists in decided syntax, and the whole name→physical-object resolution chain is designed. Remaining: **E** (emit), **F-lite** (execution), **C1/C2/C4** (graphical, fragments, NL sessions), **H** (names — accumulating fast), the parked **MD-sugar session**, Z (v2).

## Decisions banked in the D session (2026-07-03; see decision log for full text)

- **D-a = γ**: all model tiers referenceable by design; **v1 = db + er**, md reserved (MD-sugar session). Ref kind **package-derived** (`erp.db.accounts` vs `erp.er.customer`; short forms via TTR `import`). er depth = **names + relation-joins** (`on: relation customer_sales`).
- **D-b**: world names ride the same **qname + import** mechanism; **position-typing** checks kinds per syntactic position.
- **D-c = δ**: ad-hoc schemas in **both homes** (world-declared + program inline/named), P2-ordered: inline > named-in-program > world; same-level conflict = error.
- **D-d**: world = **new TTR schema kind** (`schema world`: `def engine`/`def executor`/`def storage`, instance `extends` type manifest); **storage `hosts:` model packages** (attachment = environment truth). Storage grouping renamed off "TableContainer" (working: `namespace` → H).
- **D-e = α**: project defaults in the **project manifest** (`[pl]` table): world ref, bare-fragment target+shell, split policy, display default. Precedence: `uses world` pin > project defaults > nothing (P2).
- **D-f**: staging = **declared default** (`staging: true` storage) + **feasibility check** (both sides must reach it) + **`via <storage>` override**.
- **D-g**: compiler **embeds the metadata component** (Q6 D-side settled), reads model repo + world **directly, offline** via project-default paths; Kantheon metadata = world-population source only; Ariadne = runtime wrapper. Component boundary → G work item.
- `06` §RESOLVED has the **er-flavored hero variant** (logical names + relation-join in real syntax).

## D leftovers (recorded, not blocking)

World-extends-world · auth = named-connection indirection (Charon precedent, no secrets in world docs) — confirm in E/F · world-doc placement (model repo vs beside programs) · schema type vocabulary (reuse TTR db types?) · H names (world file extension, `namespace`, defaults keys). **MD-sugar session parked** — unchanged.

## Immediate next — E (transpilation & emit)

E's agenda, mostly pre-seeded:
1. **Import the Calcite-translator "preserved-shape" principle** (adjacent-session finding, review-260702).
2. **SQL islands**: PL relational subgraph → `plan.v1.PlanNode` → Proteus → Calcite → dialect SQL (T3-γ boundary). What exactly crosses (expression lowering to `plan.v1.Expression`, T5-a adapt).
3. **Dataframe islands**: direct Polars/pandas codegen — structure, NULL-semantics enforcement layer (T5-d canonical SQL NULL on engines that don't match).
4. **er emit**: attribute→column rewrite + relation-join expansion happen where (normalize vs emit)?
5. **Q9**: define A4's "identical results" equivalence procedure (ordering, float tolerance, collation, NULL ordering, datetime precision) — seeds the conformance harness.
6. **Invocation bindings** at the artifact boundary (with F-lite): what the compiled artifact looks like (bash script + per-island delivery: inline psql vs REST-to-Arges), id-normalization/`languageDetails` per engine.
7. Q8 (cross-engine RLS) — at minimum record the v1 stance ("trusted principal"?).

**After E:** F-lite (bash executor, FF/Q10, fail-fast — largely pre-answered) → C1 graphical (fragment rendering, view-state sidecar content) → H (one naming sweep: language name, file extensions, sidecar suffix, `namespace`, Byx/Kyx renames) → MD-sugar session.

## Key mental model to reload (one paragraph)

One graph, typed ports, closed containers bearing engine targets; canonical text = γ hybrid statements (`->` chains + SSA assignment), named-only multi-in, keyword control, `err`/`rejects`, `"""sql` tagged fragments, layout in the view-state sidecar. **Names now resolve end-to-end:** a program references model objects by TTR qname (db tables *and* er entities in v1 — package-derived kinds, `import` sugar, relation-joins) and world objects (engines/storages/schemas) through the same mechanism with position-typing; the **world** is a TTR `schema world` document whose storages **host** model packages and mark the **staging** default; **project manifest `[pl]`** supplies world ref + bare-fragment shell + policies; ad-hoc schemas are declared inline or in the world (P2-ordered), never inferred. The compiler embeds the metadata component and works offline. Cross-engine edges synthesize Store+Transfer+Load through declared staging; `Display` is the frontend sink. P2 everywhere: explicit, or deterministic from project defaults, or error.
