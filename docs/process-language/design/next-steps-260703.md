# PL Design — Next Steps (pick-up point, written 2026-07-03 end-of-day)

> Where to resume the TTR Processing Language design effort. Read the [Control Room](./00-control-room.md) decision log first, then this.
> Option catalogues: [`01-design-space-map.md`](./01-design-space-map.md) · B → [`02-internal-model-options.md`](./02-internal-model-options.md) · G → [`03-tooling-delivery-options.md`](./03-tooling-delivery-options.md) · C0 → [`04-surfaces-options.md`](./04-surfaces-options.md) · C3 → [`05-canonical-dsl-options.md`](./05-canonical-dsl-options.md).
> Supersedes [`next-steps-260702b.md`](./next-steps-260702b.md).

## Where we are

**A 🟢 · B 🟢 · G 🟢 · C0 🟢 · C3 🟢 — the canonical flow-DSL grammar is decided at the fork level.** The hero scenario exists in real syntax (converged rendering in `05` §RESOLVED). Remaining C: graphical (C1), fragment dialects (C2), NL (C4) — per the C0-f prototype order. Then D, E, F-lite, H. Z parked (v2).

## Decisions banked 2026-07-03 (see decision log for full text)

- **C3-a = γ hybrid**: chains (`->`) + assignment both first-class; a variable names any edge; formatter carries the style rule.
- **C3-b = `->`**: one token for chains, program-level wiring, and explicit edges.
- **C3-a-iii**: named args canonical; config **block for wide ops** (Aggregate, Pivot, Switch).
- **C3-a-iv baseline**: precedence `=` < `->` < call; chains legal in source position; **expression scope = input columns only**; port-name qualification (`left.x`).
- **C3-c**: **named-only** multi-in (`join(left: …, right: …)`).
- **C3-d-iii**: **closed containers** — bodies see only their own ports; wiring at program level.
- **C3-d-iv**: **movement synthesized** — cross-engine edge lowers to Store+Transfer+Load; staging location from project defaults (→ D).
- **C3-e**: keyword control — `b after a` (FS) · `a with b` (SS) · `a finishes with b` (FF); optional `control {}`.
- **C3-f**: two error ports — `err` (signal) + `rejects` (rows); unconnected ⇒ fail-fast.
- **C3-g**: fragments embed as **TTR tagged blocks** `"""sql … """` — the tag IS the dialect marker (reuses `TAGGED_BLOCK_LITERAL` + tag-peeling from TTR.g4); bare-fragment marker = **extension + first-line comment override** (`-- pl: dialect=sql`).
- **C3-h**: **layout/view-state SIDECAR** (same name, different suffix → H) — **amends G-e** ("into PL text" now = the sibling file). Forced partly by bare fragments (source never rewritten). Pair-integrity tooling = G work item.
- **World ref**: project default + optional `uses world "…"` pin.
- **Q11 Display — semantics resolved**: sink-only leaf; dynamic schema (T7 exception); named/multiple; bare-program default sink. **Transport still open → G/E.**

Grammar-prototype leftovers (not forks) are listed in `05` §Open leftovers (`=` vs `==`, δ-writeable, reserved port names, Union n-ary form, `program` header, materialize-not-surface confirmation).

## New/updated D dependencies (D is now clearly next)

D accumulated hard requirements this session:
1. **Default staging area** (movement synthesis needs it).
2. **Project defaults file**: world ref, default display sink, bare-fragment container target + shell.
3. **Schema references**: `load(files.sales_2026, schema: sales_csv)` — where schemas are declared (world doc vs inline vs TTR model), `ttr-metadata` vs `md-catalog`.
4. World doc grammar (`def world`) + storage/engine instance naming (`files.…`, `erp_pg`).
5. The MD syntactic-sugar session (parked since framing).

## Immediate next — two viable orders

- **Option 1 (per the standing plan): D next** — model binding + project defaults. Everything C3 deferred lands there; the converged hero rendering has `files.sales_2026`, `schema: sales_csv`, `erp.accounts`, `erp_pg`, `uses world` all pointing at D.
- **Option 2: C1 graphical next** (per C0-f prototype order) — but the Designer-server work is G implementation, and the *design* questions there (fragment rendering, view-state sidecar content) are small. Lean: **D first**, C1 after.

**After D:** E (emit; Calcite-translator "preserved-shape" principle; Q9 equivalence procedure) → F-lite (bash target, invocation bindings, FF/Q10, fail-fast) → H (names: language name, sidecar suffix, dialect extensions, Byx/Kyx renames).

## Key mental model to reload (one paragraph)

One graph of operation nodes with typed ports; containers are **closed functions** bearing engine targets; text is canonical, and the canonical language is now concrete: **γ hybrid** statements — `->` chains for linear runs, `=` names (SSA) at fan-out/reassignment — named-only multi-in, keyword control deps (`after`/`with`/`finishes with`), two error ports (`err`/`rejects`), wide ops may take config blocks. SQL/pandas islands embed per-container as **`"""sql` tagged blocks** (TTR's own embedding token; tag = dialect); bare fragment files are valid programs (extension + comment-override marker). Cross-engine edges get Store+Transfer+Load **synthesized** from project defaults; `Display` is the explicit frontend sink. **Layout lives in a per-document view-state sidecar** (G-e amended). World = project default + in-document pin. P2 "no miracles" everywhere: explicit, or deterministic from project defaults, or error. The hero scenario in final syntax: `05-canonical-dsl-options.md` §RESOLVED.
