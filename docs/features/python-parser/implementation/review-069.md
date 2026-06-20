# Review 069 — Python parser feature (Phases P1–P6, whole-feature) + regression sweep

**Scope.** Full-feature review of `python-parser` — the standalone `ttr-parser`
Python package (parser + walker + loader + semantics) and its two conformance
gates, culminating in the `0.1.0` PyPI release. Reviewed against the plan
([`../plan.md`](../plan.md)) and the per-stage task lists
([`../tasks/`](../tasks/)). This is the capstone review; per-stage reviews
064–068 covered P1–P3.

**Branch:** `python-parser` (merge-base with `master`: `4971bf9`).

**Reviewer ran (runtime, not just reading):** the full Python unit + conformance
suites, `mypy --strict`, `ruff`, the whole TS workspace (`pnpm -r build` +
`pnpm -r test`), the Kotlin Kotest suites, and a clean **Java-free** venv install
of `ttr-parser==0.1.0` from PyPI.

---

## Verification results

| Suite | Result |
|---|---|
| Python unit (`pytest`) | **144 passed** |
| Python conformance (AST + semantics) | **108 passed** (51 AST + 54 sem + 3 guards) |
| `mypy --strict` (18 src files) | clean |
| `ruff` | clean |
| Kotlin (`ttr-parser` + `ttr-semantics` + `ttr-writer`) | **BUILD SUCCESSFUL** |
| TS workspace (`pnpm -r test`) | 24/25 files pass; **1 pre-existing failure** — see F1 |
| PyPI install in clean Java-free venv | `parse_string` + `StockLoader.load()` green |
| `ttr-parser 0.1.0` on PyPI | live (`ttr_parser-0.1.0-py3-none-any.whl`) |

Both conformance gates (`py-vs-ts` AST, `py-sem-vs-ts` resolution) are byte-identical
to the TS golden across every fixture, including the three multi-doc subdirectories.

---

## Architecture assessment — solid

- **Clean dependency direction, mirroring the canon.** `model` (frozen
  dataclasses) → `walker`/`loader` → `semantics/{qname, default_schema,
  package_inference, symbol_table, resolver, references, validator,
  stock_loader, project}`. Each semantics module is a faithful, self-contained
  port of its TS/Kotlin twin; no cross-layer leakage.
- **Parser stays mechanical**, semantics owns resolution — the same invariant the
  TS/Kotlin layers hold. The resolver is one method per the six-step chain in the
  exact canon order; the validator is the portable subset only.
- **Conformance is the safety net, not a hope.** The AST (`dump.py`) and
  semantics (`dump_sem.py`) dumpers pin Python byte-for-byte to the committed TS
  golden, in CI on every PR. The §5.1 dumper correctly reproduces the *two*
  resolution passes of `dump-sem.ts` (raw schema-code for `resolved`, the
  defaulted-schema validator for `diagnostics`).
- **Extendability.** `kind_segment` centralises the snake→camel kind mapping that
  keeps qnames byte-identical; `default_schema` is the single source for
  schema/namespace defaults; adding a def kind or property is localised.
- **Distribution.** Pure-Python wheel — the generated ANTLR parser and the stock
  vocab are bundled via the `artifacts` pattern and verified present in CI before
  upload. No JVM at the consumer. Trusted Publishing (OIDC) — no token to rotate.

The code reads like the surrounding TS/Kotlin canon and is easy to follow. No
architectural defects found.

---

## Deviations from the task lists — all intentional and documented

Each is recorded in the relevant task file; summarised here for the capstone.

1. **No cardinality / target-shape / type-alias / drill_map validator** (tasks
   4.1.6, 4.4.3 list them). The canon portable subset (`Validator.kt`) emits
   none — they are TS-only. Porting them would diverge from the §5.1 golden.
   Mirrored `Validator.kt` exactly. *Pinned green by `py-sem-vs-ts`.*
2. **`SymbolEntry.mapping_source` is `SourceLocation | None`, currently always
   `None`** — inline er2db mapping synthesis is not ported, so the
   duplicate-mapping validator is a faithful no-op. Confirmed no divergence: the
   mapping fixtures (24–27) are byte-identical in the semantics dump.
3. **`PackageGraph` is the thin §3.5 edge API** (`add_edge` + Tarjan
   `detect_cycles`), not the TS document-driven `PackageGraphBuilder` (out of
   scope per contracts §3.5).
4. **Wheel via `artifacts`, not `force-include`** — `force-include` double-adds
   against the `packages` walk (the `ValueError` fixed in review-064). Same
   pattern the generated parser uses.
5. **Wheel-only publish (no sdist)** — the build hook regenerates from
   `../../grammar` and copies stock from `../../semantics`, neither inside the
   package, so a standalone sdist isn't buildable (and would need a JVM). The
   `py3-none-any` wheel is universal, so pip never needs an sdist.
6. **Trusted Publishing (OIDC), not `PYPI_TOKEN`** — preferred per task 6.1.4.
7. **`out-ts-sem/` golden refreshed** — it was stale (predated the `db→dbo`
   default namespace on 8 fixtures; missing the 14 embedded-sql/view-tags
   fixtures). The refresh reflects current TS/Kotlin semantics and matches Python
   byte-for-byte; it also makes the `ts-dump` CI baseline assertion pass.
8. **TestPyPI dry-run (6.1.6) substituted by a local clean-venv test** — TestPyPI
   wasn't set up; the wheel was built, verified self-contained, installed into a
   fresh Java-free venv, and exercised (`parse_string` + `StockLoader.load()`).

---

## Findings

### F1 — `embedded-sql-diagnostics` test fails (pre-existing on master, NOT this feature) · Medium · out of scope

`tests/integration/src/embedded-sql-diagnostics.test.ts` →
"reports sql-unknown-column at the column position" fails: expected 1
`sql-unknown-column` diagnostic, got 0 (`embedded-sql-diagnostics.test.ts:114`).

**Attribution (verified):** the whole `python-parser` branch changes **zero** TS
source — `git diff 4971bf9..HEAD` over `packages/{semantics,lsp,parser,edit,
vscode-ext}` is empty; the only non-Python/non-conformance/non-docs/non-CI
changes are `tests/conformance/dump.py` and the new `54-view-tags.ttr` fixture,
neither read by this test. The test and the embedded-SQL feature exist unchanged
at the merge-base. It reproduces after a clean `pnpm -r build`. Therefore it is a
red test inherited from `master`, independent of the Python feature. **Not a
blocker for this PR**, but `master` has a real failing test worth a separate fix.

### F2 — `_Doc.uri` is a dead field in `validator.py` · Low · cosmetic

`Validator._doc()` populates `_Doc.uri` but no validation method reads it (the TS
`lintDocument` takes a uri; the Python validator doesn't need it). Harmless;
either wire it into a diagnostic source or drop it.

### F3 — `Resolver.get_symbol` is unused public API · Low · cosmetic

Ported for parity with TS `getSymbol`; no caller. Keep for API parity (it is a
reasonable public helper) or drop — reviewer's preference is keep.

No High-severity findings. The feature is ready to merge.

---

## Disposition

Phases P1–P6 are complete and runtime-verified; `ttr-parser 0.1.0` is published.
The feature is additive — no grammar/TS/Kotlin source touched — so it introduces
no regressions (F1 is pre-existing on master). **Recommend merging
`python-parser → master`** after acknowledging F1. Actionable steps in
[`tasks-review-069.md`](tasks-review-069.md).
