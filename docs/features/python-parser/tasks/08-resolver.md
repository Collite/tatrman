# Phase P4 / Stage 4.3 — Resolver (six-step chain)

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1.5 days.
The heart of the semantics layer. The binding instruction: **mirror
`resolver.ts` exactly** — the step order and the stock-qname shape are
load-bearing and pinned by the §5.1 dump (stage 5.1).

**Pre-flight:**
- Stage 4.2 merged (`SymbolTable` available and green).
- Read [`../contracts.md`](../contracts.md) §3.3 and
  [`../architecture.md`](../architecture.md) §5a (the six-step chain).
- Open `packages/semantics/src/resolver.ts` (the authority) and Kotlin
  `Resolver.kt` side by side. Note the TS `ResolutionStep` / `ResolutionAttempt`
  / `ResolutionResult` shapes already quoted in `contracts.md` §3.3.

**Tasks** (check each immediately after completion):

- [x] **4.3.1 — Result types** (`semantics/resolver.py`): `ResolutionContext`
      (schema_code, namespace, imports, package_name, enclosing_qname),
      `ResolutionStep` literal, `ResolutionAttempt`, and the `ResolutionResult`
      union (`Resolved(symbol, via_step)` | `Unresolved(reason, tried, candidates)`).
      Contracts §3.3.

- [x] **4.3.2 — `Resolver.__init__(symbols)`** and the public
      `resolve_reference(ref, context)` / `resolve_bare_id(name, scope)` entry
      points (contracts §3.3). `LexicalScope` mirrors the TS shape (schema/
      namespace + optional enclosing def).

- [x] **4.3.3 — Steps 1–2: lexical → same-package.** Lexical scope first (bare-id
      as a child of `enclosing_qname` when present), then same-package siblings via
      `symbols.get_by_package(context.package_name)`. Mirror the exact precedence
      from `resolver.ts`.

- [x] **4.3.4 — Steps 3–4: named-import → wildcard-import.** Named imports match
      the full suffix; a named import **shadows** a wildcard. Wildcard imports are
      **non-recursive**: a candidate matches only if it is exactly **one segment**
      below the imported package (a deeper match is a miss with reason
      `wildcard-non-recursive`). Cross-check against `resolver-v1.1.test.ts`.

- [x] **4.3.5 — Step 5: `cnc.*` auto-import.** Resolve stock roles to the
      **doubled** `cnc.cnc.role.<name>` qname (the `is_stock_cnc` shape the symbol
      table stores). This step is why stock is upserted under `stock://` (stage
      4.4 / 4.2.5).

- [x] **4.3.6 — Step 6: fully-qualified.** Exact-qname lookup last; resolve
      ambiguity (multiple suffix candidates) → `Unresolved(reason="ambiguous",
      candidates=…)`. Record each failed step in `tried` for diagnostics.

- [x] **4.3.7 — Green `test_resolver.py`.** Every step test + the ambiguous +
      not-found cases pass. The `via_step` reported on success must match the TS
      step names.

- [x] **4.3.8 — `mypy --strict` + `ruff`** clean. No `Any`; the union is exhaustive
      under `match`.

**Verification commands:**
```bash
cd packages/python/ttr-parser && . .venv/bin/activate
pytest -q tests/test_resolver.py
mypy --strict src/ttr_parser/semantics/resolver.py
```

**Stage DoD:**
- All eight tasks checked; `test_resolver.py` green.
- The six steps execute in the exact `resolver.ts` order; wildcard is
  non-recursive; stock resolves to the doubled `cnc.cnc.role.*` qname.
- Behavioural parity with TS is asserted by the suite now and **pinned** by the
  §5.1 conformance dump in stage 5.1.
