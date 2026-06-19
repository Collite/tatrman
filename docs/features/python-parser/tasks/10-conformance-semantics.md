# Phase P5 / Stage 5.1 — Conformance: resolution dump + py-sem-vs-ts + CI

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** 1 day.
Goal: pin the Python **resolver** to the committed TS golden resolution dump on
every shared fixture, including the multi-document subdirectory scenarios.

**Pre-flight:**
- Phase P4 merged (semantics fully working).
- Read [`../contracts.md`](../contracts.md) §5.1 (resolution dump schema +
  multi-doc rule) and §6.1 (CI).
- Confirm the golden is current: the TS semantics dump
  (`pnpm --filter @modeler/conformance dump-sem` / `dump-all`) leaves no git diff
  under `tests/conformance/out-ts-sem/`.
- Open the TS sem-dumper and Kotlin `SemanticsConformanceDump` for the exact
  normalisation + the multi-doc directory handling.

**Tasks** (check each immediately after completion):

- [ ] **5.1.1 — `tests/conformance/dump_sem.py` (single files).** For each
      top-level `*.ttr` fixture: load stock, build a `SymbolTable`, resolve every
      reference (via `collect_all_references` + `Resolver`), run the portable
      validator, and emit normalised `{ diagnostics, resolved }` to
      `out-py-sem/<fixture>.json`.

- [ ] **5.1.2 — Normalisation (§5.1).** `resolved` = sorted
      `"<refPath> => <resolvedQname>"` strings (no positions); `diagnostics` =
      sorted `DiagnosticCode.value` strings (codes only — no severity/positions);
      stock always loaded under `stock://` first; validator = exactly the portable
      subset. Match the TS JSON formatting byte-for-byte.

- [ ] **5.1.3 — Multi-document subdirectories.** For each **subdirectory** of
      `tests/conformance/fixtures/` (e.g. `32-same-package/`, `33-named-import/`,
      `34-wildcard-import/`): load **all** its `.ttr` files into one `SymbolTable`
      before resolving; write the combined dump to `out-py-sem/<dir>.json`. (The
      decoy def in a different package makes the targeted step load-bearing — keep
      it.) Mirror `dumpSemDocs` / `SemanticsConformanceDump.dumpDocs`.

- [ ] **5.1.4 — Extend `test_conformance.py`.** Add resolution cases: diff
      `out-py-sem/<f>.json` against committed `tests/conformance/out-ts-sem/<f>.json`
      byte-for-byte (single files + subdirs); readable `difflib` output on
      mismatch.

- [ ] **5.1.5 — Prove the gate fails red.** Temporarily break the resolver (e.g.
      skip the auto-import step) and/or the stock copy; confirm `py-sem-vs-ts`
      goes red with a clear diff; revert.

- [ ] **5.1.6 — Make it green.** Resolve any drift until `py-sem-vs-ts` is green
      across all fixtures (single + multi-doc). Drift here is usually a resolver
      step-order bug or a stock-qname shape mismatch — fix the Python code, not the
      golden.

- [ ] **5.1.7 — `out-py-sem/` gitignored** (CI-only). Extend the `py-dump` CI job
      to also run `dump_sem.py` and upload `out-py-sem/`; add the `py-sem-vs-ts`
      diff job (download `ts-sem-dumps` + `py-sem-dumps`, byte diff). Contracts
      §6.1.

**Verification commands:**
```bash
cd packages/python/ttr-parser && . .venv/bin/activate
python ../../../tests/conformance/dump_sem.py        # writes out-py-sem/
pytest -q tests/conformance/test_conformance.py      # AST + resolution green
```

**Stage DoD:**
- All seven tasks checked.
- `py-sem-vs-ts` green across every fixture (single files **and** multi-doc
  subdirs) locally and in CI.
- An intentional resolver/stock break turns it red (gate proven, then reverted).
- `out-py-sem/` gitignored; `out-ts-sem/` remains the committed reference.
