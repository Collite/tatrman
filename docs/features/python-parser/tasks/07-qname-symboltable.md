# Phase P4 / Stage 4.2 — Qname + SymbolTable + PackageInference + PackageGraph

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1–1.5 days.
Foundations of the semantics layer, under `ttr_parser/semantics/`. **Mirror the
TS modules exactly**; cross-check against the Kotlin `ttr-semantics`.

**Pre-flight:**
- Stage 4.1 merged (failing semantics suites exist).
- Read [`../contracts.md`](../contracts.md) §3.1–§3.5.
- Open `packages/semantics/src/{qname,symbol-table,project-symbols,
  package-inference,package-graph}.ts` and Kotlin
  `Qname.kt`/`SymbolTable.kt`/`PackageInference.kt`/`PackageGraph.kt`.

**Tasks** (check each immediately after completion):

- [x] **4.2.1 — `semantics/__init__.py`** + package scaffolding. Plan re-exports
      (`Qname`, `SymbolTable`, `SymbolEntry`, `Resolver`, `ResolutionContext`,
      `ResolutionResult`, `Resolved`, `Unresolved`, `Validator`,
      `ValidationDiagnostic`, `StockLoader`, `Project`, `load_project`,
      `infer_package`, `PackageGraph`).

- [x] **4.2.2 — `semantics/qname.py`** (← `qname.ts`): `Qname` frozen dataclass
      with `segments`/`last`/`parent`/`__str__` (contracts §3.1). Make
      `test_qname.py` green.

- [x] **4.2.3 — `semantics/package_inference.py`** (← `package-inference.ts`):
      `infer_package(file_path, project_root) -> Qname`; `<root>/foo/bar/baz.ttr`
      → `foo.bar`; root file → empty package. Advisory (not used to build qnames,
      matching modeler's declaration-driven rule). Make `test_package_inference.py`
      green.

- [x] **4.2.4 — `semantics/symbol_table.py` — `SymbolEntry`** (contracts §3.2):
      frozen dataclass with qname/kind/name/package_name/schema_code/definition/
      source_file/mapping_source.

- [x] **4.2.5 — `SymbolTable` (project-level, ← `project-symbols.ts` +
      `symbol-table.ts`).** `upsert_document(uri, result, package_name="")` builds
      qnames per def kind (mirror the TS qname prefixes — entity/table/view/etc.),
      and registers them; `get` / `get_all` / `get_by_package` / `get_by_suffix` /
      `duplicates`. **Keep the `is_stock_cnc` gate**: schema `cnc` + empty package +
      `stock://` URI → stock stored under the doubled `cnc.cnc.role.*` shape. Make
      `test_symbol_table.py` green.

- [x] **4.2.6 — `semantics/package_graph.py`** (← `package-graph.ts`):
      `add_edge` + `detect_cycles` (contracts §3.5). Make `test_package_graph.py`
      green.

- [x] **4.2.7 — `mypy --strict` + `ruff`** clean across `semantics/`. No `Any`.

**Verification commands:**
```bash
cd packages/python/ttr-parser && . .venv/bin/activate
pytest -q tests/test_qname.py tests/test_package_inference.py tests/test_symbol_table.py tests/test_package_graph.py
mypy --strict src/ttr_parser/semantics
```

**Stage DoD:**
- All seven tasks checked; those four suites green.
- `SymbolTable` builds the same qnames the TS layer does (verified against a
  fixture; fully pinned later by the §5.1 dump in stage 5.1).
- The stock `is_stock_cnc` doubled-qname gate is in place (resolver in 4.3 and
  stock loader in 4.4 depend on it).
