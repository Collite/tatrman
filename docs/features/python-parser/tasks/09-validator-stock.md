# Phase P4 / Stage 4.4 — Validator subset + StockLoader + load_project

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1–1.5 days.
Completes the semantics layer and ties parse + resolve into the consumer entry
point.

**Pre-flight:**
- Stage 4.3 merged (resolver green).
- Read [`../contracts.md`](../contracts.md) §3.0 (entry point), §3.6 (validator),
  §3.7 (stock loader).
- Open `packages/semantics/src/{validator,stock-loader}.ts` + `stock/cnc-roles.ttr`
  and Kotlin `Validator.kt`/`StockLoader.kt` (`cnc-stock-roles.ttr`).

**Tasks** (check each immediately after completion):

- [ ] **4.4.1 — Bundle the stock vocab.** Add the build step that **copies**
      `packages/semantics/src/stock/cnc-roles.ttr` → `src/ttr_parser/semantics/stock/cnc-roles.ttr`
      at build time (extend the Hatchling hook from stage 1.1; gitignore the copied
      file — single source of truth, no committed duplicate, D4-style). Ensure it is
      `force-include`d into the wheel (like `_generated/`).

- [ ] **4.4.2 — `semantics/stock_loader.py`** (← `stock-loader.ts`, contracts
      §3.7): `StockLoader.load()` reads the bundled `cnc-roles.ttr` via
      `importlib.resources.files("ttr_parser.semantics") / "stock/cnc-roles.ttr"`
      and `parse_string`s it; `stock_qnames()` returns the **doubled**
      `cnc.cnc.role.<name>` frozenset. Make `test_stock_loader.py` green.

- [ ] **4.4.3 — `semantics/validator.py`** (← `validator.ts`, contracts §3.6):
      the **portable subset only** — `validate_document` + `validate_references` +
      `validate_project` + `validate_imports` (cardinality strings, target shapes,
      type aliases, search-block sub-properties, drill_map args, unresolved/
      duplicate). **Exclude** the TS-only validators (file-ordering, `.ttrg` graph,
      package-declaration, duplicate-search-property). Emit `ValidationDiagnostic`
      (code/severity/source/message). Make `test_validator.py` green.

- [ ] **4.4.4 — `semantics/project.py` — `Project`** (contracts §3.0): holds the
      `SymbolTable` + the `tuple[ParseResult, ...]`; `resolve(ref, context)` →
      `ResolutionResult`; `validate()` → validator diagnostics; `diagnostics()`
      aggregates parse errors + resolution failures + validation.

- [ ] **4.4.5 — `load_project(root, with_stock=True)`** (contracts §3.0):
      `parse_directory(root)` → upsert every document, then (if `with_stock`)
      `StockLoader.load()` upserted under a `stock://` URI → return `Project`. Make
      `test_project.py` green (incl. the cross-file + decoy fixture).

- [ ] **4.4.6 — Public surface.** Re-export the semantics entry points from
      `ttr_parser.semantics` and (the common ones) from `ttr_parser` per the
      `__all__` plan in stage 4.2.1.

- [ ] **4.4.7 — Green the whole semantics suite + a real sample.** All stage-4.1
      suites pass. Run `load_project` over a real `samples/` project and confirm
      references resolve with no spurious diagnostics.

- [ ] **4.4.8 — `mypy --strict` + `ruff`** clean across `semantics/`.

**Verification commands:**
```bash
cd packages/python/ttr-parser && . .venv/bin/activate
./scripts/generate-python-parser.sh   # also copies stock/cnc-roles.ttr now
pip install -e .
pytest -q                              # all parser + semantics suites green
python -c "from ttr_parser.semantics import load_project; p=load_project('../../../samples'); print(len(p.diagnostics()))"
```

**Stage DoD:**
- All eight tasks checked; every stage-4.1 suite green.
- Stock vocab is copied from canon at build (no committed duplicate) and bundled
  in the wheel; `cnc.*` auto-imports resolve via the loaded stock.
- `load_project(...).resolve(...)` works end-to-end on a real `samples/` project;
  `mypy --strict` + `ruff` clean.
