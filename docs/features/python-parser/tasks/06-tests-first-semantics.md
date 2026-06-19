# Phase P4 / Stage 4.1 — Tests first: semantics pytest suites

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1 day.
**TDD:** write these **before** the semantics implementation (stages 4.2–4.4);
they must initially **fail**.

**Pre-flight:**
- Phase P3 merged (parser conformance green).
- Read [`../contracts.md`](../contracts.md) §3 (semantics API) and §5.1
  (resolution dump); [`../architecture.md`](../architecture.md) §5a.
- Open the canon to mirror: `packages/semantics/src/__tests__/{resolver,
  symbol-table,package-inference,package-graph,qname}.test.ts` and Kotlin
  `packages/kotlin/ttr-semantics/src/test/kotlin/.../*Spec.kt` (esp.
  `ResolverSpec`, `StockLoaderSpec`, `StockAutoImportIntegrationSpec`).

**Tasks** (check each immediately after completion):

- [ ] **4.1.1 — `tests/test_qname.py`** (← `qname.test.ts`): `segments`, `last`,
      `parent` for single- and multi-segment qnames; root/empty handling.

- [ ] **4.1.2 — `tests/test_package_inference.py`** (← `package-inference.test.ts`):
      `<root>/foo/bar/baz.ttr` → `foo.bar`; a root-level file → empty package.

- [ ] **4.1.3 — `tests/test_symbol_table.py`** (← `symbol-table.test.ts`):
      `upsert_document` then `get` by qname; `get_by_package` (same-package set);
      `get_by_suffix` (wildcard matching); `duplicates` detection; `SymbolEntry`
      carries qname/kind/name/package_name/schema_code/source_file.

- [ ] **4.1.4 — `tests/test_resolver.py`** (← `resolver.test.ts` /
      `resolver-v1.1.test.ts`, `ResolverSpec`): **one test per step** of the
      six-step chain — lexical, same-package, named-import, wildcard-import
      (non-recursive: exactly one extra segment, and a *recursive* miss),
      `cnc.*` auto-import, fully-qualified — plus an **ambiguous** case
      (`Unresolved(reason="ambiguous")`) and a **not-found** case. Assert the
      `via_step` on success.

- [ ] **4.1.5 — `tests/test_stock_loader.py`** (← `StockLoaderSpec` +
      `StockAutoImportIntegrationSpec`): `StockLoader.load()` returns the stock
      defs; `stock_qnames()` returns the **doubled** `cnc.cnc.role.<name>` form;
      a reference to a stock role resolves via the auto-import step once stock is
      upserted under `stock://`.

- [ ] **4.1.6 — `tests/test_validator.py`**: the portable-subset codes only —
      e.g. an unresolved reference → `ttr/unresolved-reference`; a duplicate def →
      `ttr/duplicate-definition`; a bad cardinality string → its code. Do **not**
      assert the TS-only validators (file-ordering, `.ttrg` graph, package-decl,
      duplicate-search-property).

- [ ] **4.1.7 — `tests/test_project.py`**: `load_project(tmp_dir)` over a 2-file
      fixture (one importing the other) → `Project.resolve(ref, context)` succeeds
      across files; `Project.diagnostics()` aggregates parse + resolution +
      validation. Include a multi-file fixture with a same-named **decoy** in a
      different package so a targeted step is load-bearing.

- [ ] **4.1.8 — Confirm red.** `pytest -q tests/` runs; the new semantics suites
      fail with import/not-implemented errors. Commit.

**Verification commands:**
```bash
cd packages/python/ttr-parser && . .venv/bin/activate
pytest -q tests/test_resolver.py ; echo "expected: fail (semantics not implemented)"
```

**Stage DoD:**
- All eight tasks checked.
- Suites cover every resolver step + ambiguity + stock auto-import + the portable
  validator codes + the cross-file project flow.
- Committed and failing for the right reason (not-yet-implemented).
