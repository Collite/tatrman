# PL Design — Next Steps (pick-up point, written 2026-07-02 end-of-day)

> Where to resume the TTR Processing Language design effort. Read the [Control Room](./00-control-room.md) decision log first, then this.
> Option catalogues: [`01-design-space-map.md`](./01-design-space-map.md) · B → [`02-internal-model-options.md`](./02-internal-model-options.md) · G → [`03-tooling-delivery-options.md`](./03-tooling-delivery-options.md) · C → [`04-surfaces-options.md`](./04-surfaces-options.md) · review → [`review-260702.md`](./review-260702.md).
> Supersedes [`next-steps-260702.md`](./next-steps-260702.md).

## Where we are

**A 🟢 · B 🟢 · G 🟢 · C0 🟢 — every framing and architecture fork is closed.** Remaining: per-surface design (C3 first), D, E, F-lite, H. Z parked (v2).

This session (2026-07-02) started with an independent **progress review** (`review-260702.md`) — read it for the pushback rationale — then converged everything it surfaced plus T6, T4, the B-sweep, and C0.

## Decisions banked 2026-07-02 (see decision log for full text)

**Ratifications (from the review):**
- **v1 placement = author-assigned containers**; compiler re-places only via T5-b escalation. No auto-placement until Z.
- **G-a/e/g ratified → G fully converged** (same monorepo; text canonical incl. layout/ports; kantheon consumes compiled plans).
- **Lineage = derived, never authored.**
- Logged: Q8 (cross-engine RLS), Q9 (A4 "identical results" equivalence procedure), Q10 (FF cross-engine implementability).

**T6 (capability manifests) — closed:**
- **β content model**: parameterized declarative entries (`Join{types}`, `Pivot{native}`; functions by catalogue id); rare predicate escape hatch.
- **The WORLD is a TTR-family document (`def world …`) and a COMPILE TARGET**; runtimes *verify compatibility* with it (x86 analogy); runtime advertisement = verification only.
- Manifests **separate** from rewrite rules (facts vs compiler knowledge; normalizer joins them).
- **Environment taxonomy: Storage / DATA engines / EXECUTION engines** (Bora's two-layer data-vs-execution stance, working, revisitable). **Execution engines are (just) engines** — the collapsed container graph is normalized to an execution engine via its manifest (the fractal, one level up). **Reframes F**: not "pick the orchestrator" but manifest + normalize + emit. **v1 execution engine = bash.** Manifests declare **invocation capabilities** ("can execute pg script", "can call REST via curl") → per-container **invocation binding** (inline psql vs REST call to Arges). Type manifests (ship with compiler) + instance overlays (in the world doc), instance `extends` type.

**T4 — closed:**
- **Q7 = γ**: variables are textual edge-sugar with **SSA reassignment** (`X = filter(X,…)` desugars to fresh instances); data-only (never containers); **one document = one program = one graph**; WorkspaceRef = runtime-only.

**B-leftover sweep — B CONVERGED 🟢:**
- `AggregateCall` distinct arm · no subquery expressions in v1 IR (surfaces desugar to semi/anti Join) · parameters compile-time only · dynamic schema = authored outputs only · schema sourcing via `ttr-metadata`/declared (→ D) · Distinct = sugar→Aggregate · Intersect/Except = engine-relative nodes · semi/anti = Join types · Explode/Unnest → v2.
- Compiler-phase work items (not forks): **T8 termination measure** (stratification: sugar ≺ function-lowering ≺ node-fission ≺ re-placement), **node-fission rewrite**, capability vocabularies (node-kind + execution-engine), diagnostics table (in `02` §T6-e).

**C0 — converged (`04-surfaces-options.md`):**
- **γ: tiered at the CONTAINER boundary.** Flow-DSL = the canonical language (file format, full coverage). **TTR-SQL and TTR-pandas are container-content dialects** (fragments; data-flow islands only). Container = the mixing unit. Graphical = view (G-e).
- **NL = BOTH**: Byx stays (strict controlled grammar, deterministic, LLM-free value) + LLM-assisted authoring layer additional.
- **Bare-fragment programs**: a pure TTR-SQL/TTR-pandas document is a valid program; compiler synthesizes container+shell from **project defaults**; dialect via explicit marker (never sniffed); source text never rewritten. Fragments are *generic* SQL — **pasting `NOLOCK` is an error**.
- **A1-bis resolved re-scoped**: v1 = 2 full surfaces (canonical, graphical) + 2 fragment dialects + Byx/LLM assist.
- **Prototype order: canonical flow-DSL → graphical → fragments → NL.**

**New principle — P2 · "No miracles."** Everything explicit or deterministically derived from project defaults; otherwise an error. No sniffing, no heuristics, anywhere.

## New open questions
- **Q11 · `Display` node** (preferred over "Output") — the explicit results-to-frontend sink, missing from T10. One discussion closes three threads: T7's output-only dynamic-schema exception, the bare-fragment sink convention, the frontend link (Arrow stream via Designer server?).
- C0 leftovers: fragment embedding syntax (heredoc/braces/mode-switch); does the graphical surface render fragments as sub-graphs or opaque code-containers; layout block per-document or per-container; names (H): canonical language name, Byx/Kyx renames, "fragment dialect" terminology.
- Still held: Q1 (agent as author — now maps to the LLM-assist path), Q8/Q9/Q10 (land in E/F), TTR-designer-on-Designer-server, `ttr-metadata` vs `md-catalog` (→ D).

## Immediate next — resume at C3
**C3 — the canonical flow-DSL grammar.** The session where the hero scenario (A5) finally gets written in real syntax. Agenda:
1. Write the hero scenario end-to-end in candidate syntax — the pressure test for: containers + targets, ports (named/default), control edges (FS/SS/FF), error ports, Load/Store/Transfer, variables (γ sugar), an embedded TTR-SQL fragment, the layout block.
2. Kyx lineage: what carries over (`+` chaining? `.True/.False` → named ports?) vs what the port model obsoletes.
3. Statement forms: chaining vs assignment (both, per Q7) — precedence/ambiguity rules.
4. Fragment embedding lexical form (C0 leftover) + the explicit dialect marker for bare fragments.
5. Q11 Display node will surface immediately (the hero scenario ends somewhere) — resolve it here or stub it.

**After C3:** D (model binding + **project defaults** — bare-fragments made it urgent; world doc is D-adjacent; MD sugar session) → E (emit; import the Calcite-translator "preserved-shape" principle; Q9) → F-lite (mostly pre-answered: bash target, invocation bindings, FF/Q10, v1 fail-fast) → H (names).

## Key mental model to reload (one paragraph)
One graph of operation **nodes** with typed **ports** (named + default, multicast, no implicit union); **containers** group nodes, act as functions, bear targets; variables are SSA edge-sugar in text. Nodes sit on a **physicality spectrum**; the T8 fixpoint rewrites (sugar ≺ function-lowering ≺ node-fission ≺ re-placement) until each engine can run its container — **primitive/macro is engine-relative**, and this now applies **one level up too**: the collapsed container graph is normalized to an **execution engine** (v1: bash) via its manifest, with **invocation bindings** choosing how each island is delivered (inline script vs REST). The compiler compiles against a **world** — a TTR-family document of Storage/Data/Execution environments with **β-parameterized manifests** (type + instance overlay); runtimes must be *compatible* with the world. Data model = (table envelope, table instance) — SSA for storage. Expressions: one PL grammar, canonical SQL NULL, catalogue functions; capability misses re-place whole nodes (after fission). Surfaces: **flow-DSL is canonical**; TTR-SQL/TTR-pandas are **container fragments** (generic dialects, never passthrough); bare fragments are valid programs via project defaults; Byx strict + LLM assist on top; graphical is a view; **text is canonical**; toolchain Kotlin/JVM, Designer server over WS. And **P2: no miracles** — explicit, or deterministic from project defaults, or error.
