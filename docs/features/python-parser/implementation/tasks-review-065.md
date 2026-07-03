# Tasks — Review 065 (Python parser Stage 2.1, tests-first)

> **STATUS (2026-06-19): R1–R4 applied by the reviewer.** F1 fixtures fixed
> (`type:`), F2 `extract_reference` documented in contracts §2.6 (Option A), F3
> test now uses a constructed `StringValue`, F4 note added to stage 2.2
> `03-model.md` §2.2.4. **Remaining for the developer:** R5 (docstring nit,
> optional) and R6 (commit the suites — DoD 2.1.8). The R1–R4 steps below are
> retained for the record.

Follow in order. Each task says exactly what to change and how to verify. Check a
box only after its verification passes. Paths are relative to the repo root
`/Users/bora/Dev/collite-gh/modeler`. Work inside the package:
`cd packages/python/ttr-parser` and activate a venv
(`python3 -m venv .venv && . .venv/bin/activate && pip install -e ".[dev]"`).

> Findings: [`review-065.md`](review-065.md) F1–F6.

---

## R1 — Fix the two grammatically invalid fixtures *(must fix, F1)*

The surface property keyword for index/constraint type is **`type`**, not
`indexType`/`constraintType` (the snake_case AST field `index_type` is internal).

- [ ] **R1.1 — `tests/fixtures/05-index.ttr`.** Change the property key on line 2
      from `indexType: btree` to `type: btree`. Final file:
      ```ttr
      def index ix_customers_name {
          type: btree
          columns: ["name"]
      }
      ```

- [ ] **R1.2 — `tests/fixtures/06-constraint.ttr`.** Change `constraintType: unique`
      to `type: unique`. Final file:
      ```ttr
      def constraint uq_customers_email {
          type: unique
          columns: ["email"]
      }
      ```

- [ ] **R1.3 — Verify both fixtures now parse cleanly.** Run from the package dir
      with the venv active:
      ```bash
      python - <<'PY'
      from pathlib import Path
      from antlr4 import InputStream, CommonTokenStream
      from antlr4.error.ErrorListener import ErrorListener
      from ttr_parser._generated.TTRLexer import TTRLexer
      from ttr_parser._generated.TTRParser import TTRParser
      class C(ErrorListener):
          def __init__(s): s.e=[]
          def syntaxError(s,r,sym,l,c,m,ex): s.e.append((l,c,m))
      bad=0
      for f in sorted(Path("tests/fixtures").glob("*.ttr")):
          lex=TTRLexer(InputStream(f.read_text())); p=TTRParser(CommonTokenStream(lex))
          cl=C(); p.removeErrorListeners(); p.addErrorListener(cl)
          lex.removeErrorListeners(); lex.addErrorListener(cl); p.document()
          if cl.e: bad+=1; print("FAIL", f.name, cl.e[:1])
      print("OK" if bad==0 else f"{bad} BAD")
      PY
      ```
      Expect: `OK` (all 20 fixtures parse, zero syntax errors).

---

## R2 — Resolve the `extract_reference` contract question *(should fix, F2)*

`tests/test_id_value_parts.py` imports `extract_reference` from `ttr_parser`, but
it is not in the public API contract. Pick ONE option and apply it.

- [ ] **R2.1 — Decide.** Default recommendation: **make it public** (it mirrors
      `walker.ts` `extractReference` and is genuinely useful to consumers). If you
      disagree, take option B instead and skip R2.2.

- [ ] **R2.2 — Option A (make it public, recommended):** add it to
      [`../contracts.md`](../contracts.md) §2. Under §2.6 (`PropertyValue`) or a new
      "§2.6a helpers" note, add the signature and behaviour:
      ```python
      def extract_reference(value: PropertyValue) -> Reference | None: ...
      # Returns value.ref for an IdValue; None for every other PropertyValue variant.
      # Mirrors walker.ts `extractReference`.
      ```
      Note explicitly that the argument is a `PropertyValue` (this feeds R3).

- [ ] **R2.3 — Option B (keep it internal):** in `tests/test_id_value_parts.py`,
      delete the `extract_reference` import and the
      `test_extract_reference_returns_id_for_id_value_only` test; assert the
      documented surface instead — `IdValue.ref` / `IdValue.parts` (already covered
      by `test_id_value_splits_dotted_reference_into_parts`). If you do this, R3 is
      moot.

---

## R3 — Stop feeding a `str` to `extract_reference` *(low, F3 — only if you took R2 Option A)*

`description` is a plain `str` (§2.5), not a `PropertyValue`, so it is the wrong
input for the None-branch test.

- [ ] **R3.1 — Edit `tests/test_id_value_parts.py`.** In
      `test_extract_reference_returns_id_for_id_value_only`, replace the
      `description`-based None check with a real non-Id `PropertyValue`. For
      example, read a `StringValue`/`NumberValue` off a property that the walker
      keeps as a `PropertyValue` (not one unwrapped to a scalar), e.g. a list item
      or a tagged/triple-string value, and assert `extract_reference(that) is None`.
      Do **not** pass the unwrapped `description` string.

- [ ] **R3.2 — Verify the file still imports/collects** (it will still fail at
      runtime until the walker exists, but must not be a syntax/type-shape error):
      ```bash
      python -m py_compile tests/test_id_value_parts.py && echo "compiles"
      ```

---

## R4 — Note the `@runtime_checkable` requirement in the contract *(low, F4 — doc only)*

`isinstance(d, Definition)` in the tests requires `Definition` to be a
`@runtime_checkable` Protocol.

- [ ] **R4.1 — Edit [`../contracts.md`](../contracts.md) §2.5.** On the
      `class Definition(Protocol)` block, add a one-line note:
      ```
      # Must be @runtime_checkable — consumers and tests use isinstance(x, Definition).
      ```
      This is a heads-up for stage 2.2 (the walker/model implementation); no code
      change in this stage.

---

## R5 — De-rot the source citations in test docstrings *(nit, F5)*

- [ ] **R5.1 — Edit `tests/test_source_location.py`.** Change
      `makeSourceLocation (walker.ts:1847)` to `makeSourceLocation (walker.ts)` —
      cite the symbol, drop the line number.

- [ ] **R5.2 — Sweep the other test docstrings** (`test_dedent.py`,
      `test_id_value_parts.py`, `test_tagged_block.py`) and remove any `walker.ts:<n>`
      style line-number citations, keeping the symbol name. Verify none remain:
      ```bash
      grep -rn "walker.ts:[0-9]" tests/ || echo "no line-number citations left"
      ```

---

## R6 — Commit the suites (DoD 2.1.8) *(process, F6 — do this LAST)*

- [ ] **R6.1 — Re-confirm the red state is correct** after R1–R5:
      ```bash
      pytest -q 2>&1 | tail -8
      ```
      Expect: the 5 new suites fail with `ImportError` (model/walker/loader not
      built), `test_smoke` passes. No `SyntaxError`, no fixture syntax errors.

- [ ] **R6.2 — Confirm only intended files are staged** (no caches/venv):
      ```bash
      cd /Users/bora/Dev/collite-gh/modeler
      git add packages/python/ttr-parser/tests/
      git status --short          # expect only tests/*.py and tests/fixtures/*.ttr
      ```
      If any `.venv/`, `.*_cache/`, or `dist/` paths appear, stop and confirm
      `.gitignore` covers them before committing.

- [ ] **R6.3 — Commit.**
      ```bash
      git commit -m "Section P2.1: parser pytest suites + fixtures (tests-first, red)"
      ```

- [ ] **R6.4 — Verify clean tree.** `git status --short` → clean (only the new
      commit; no stray artifacts).
