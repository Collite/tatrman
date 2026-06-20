# Tasks — Review 066 (Python parser Stage 2.2, model)

> **STATUS (2026-06-20): R1–R5 applied by the reviewer** (F4 resolved with Option
> A — capitalised severity values). `description` → `str | None`;
> `source_text`/`definition_sql` → `PropertyValue | None` (`*_block` removed);
> `PropertyValue` exported; `extract_reference(value: PropertyValue)`. Verified:
> `mypy --strict` + `ruff` clean, exports resolve, 78 tests still collect.
> **Remaining for the developer:** R6 (commit the 2.2 files). The R1–R5 steps
> below are retained for the record.

Follow in order. Each task says exactly what to change and how to verify. Check a
box only after its verification passes. Paths are relative to the repo root
`/Users/bora/Dev/collite-gh/modeler`. Work in the package with a venv:
`cd packages/python/ttr-parser && python3 -m venv .venv && . .venv/bin/activate &&
pip install -e ".[dev]"`.

> Findings: [`review-066.md`](review-066.md) F1–F6. All edits are in
> `packages/python/ttr-parser/src/ttr_parser/`.

---

## R1 — `Definition.description` must be `str | None` *(must fix, F1)*

- [ ] **R1.1 — Edit `model.py`.** In the `Definition` base class, change:
      ```python
      description: StringValue | TripleStringValue | None
      ```
      to:
      ```python
      description: str | None
      ```
      Rationale: contracts §2.5 says `str | None`, the committed
      `test_loader.py` asserts `m.description == "ERP v1 model"` (plain-string
      equality), and the §5 dump serialises description as a bare string. The
      walker (2.3) will dedent triple-strings to `str` before storing.

- [ ] **R1.2 — Verify** `mypy --strict` stays clean and the string type is gone:
      ```bash
      mypy src && grep -n "description:" src/ttr_parser/model.py | head
      ```
      Expect: `Success`; the `Definition` line reads `description: str | None`.

---

## R2 — `source_text` / `definition_sql` must hold the `PropertyValue` *(must fix, F2)*

The committed `test_tagged_block.py` requires `q.source_text` to be the
`TaggedBlockValue` itself. Remove the invented `*_block` split.

- [ ] **R2.1 — Edit `QueryDef` in `model.py`.** Replace:
      ```python
      source_text: str | None = None
      source_text_block: PropertyValue | None = None
      ```
      with a single field:
      ```python
      source_text: PropertyValue | None = None   # StringValue | TripleStringValue | TaggedBlockValue
      ```
      Update the `QueryDef` docstring to drop the "flattened text / structured
      carrier" wording.

- [ ] **R2.2 — Edit `ViewDef` in `model.py`.** Replace:
      ```python
      definition_sql: str | None = None
      definition_sql_block: PropertyValue | None = None
      ```
      with:
      ```python
      definition_sql: PropertyValue | None = None
      ```
      and trim the docstring accordingly.

- [ ] **R2.3 — Grep for stragglers.** No `_block` companion fields should remain:
      ```bash
      grep -n "_block" src/ttr_parser/model.py || echo "no _block fields left"
      ```
      Expect: `no _block fields left`.

- [ ] **R2.4 — Verify** `mypy --strict` clean:
      ```bash
      mypy src
      ```

> Note: if you believe a flattened-string convenience is genuinely needed,
> do NOT add it here — raise it as a contract change (update contracts §2.5 and
> the Stage 2.1 `test_tagged_block.py`) and get it signed off first. The default
> for this fix is the single `PropertyValue` field above.

---

## R3 — Export `PropertyValue` from `__init__.py` *(should fix, F3)*

- [ ] **R3.1 — Edit `__init__.py`.** Add `PropertyValue` to the
      `from .model import ( … )` block (alphabetical position, between
      `ProcedureDef` and `QueryDef` or wherever it sorts — keep the existing
      ordering style). It is already in `__all__`; this makes that entry valid.

- [ ] **R3.2 — Verify the export resolves:**
      ```bash
      python -c "from ttr_parser import PropertyValue; print('ok')"
      python -c "import ttr_parser; assert 'PropertyValue' in dir(ttr_parser); print('in namespace')"
      ```
      Expect: `ok` and `in namespace`.

---

## R4 — Reconcile `DiagnosticSeverity` casing with the contract *(low, F4)*

Pick ONE and apply it so code and contract agree.

- [ ] **R4.1 — Option A (recommended — match contract §2.8 / Kotlin `.name`):**
      in `diagnostics.py` set the values capitalised:
      ```python
      ERROR = "Error"
      WARNING = "Warning"
      INFORMATION = "Information"
      HINT = "Hint"
      ```

- [ ] **R4.2 — Option B (keep lowercase, align to TS):** leave the code as-is and
      edit contracts §2.8 to `ERROR = "error"; WARNING = "warning"; INFORMATION =
      "information"; HINT = "hint"`, adding a note "lowercase to match the TS
      walker; severity is not part of the §5.1 dump."

- [ ] **R4.3 — Verify** code and contract now match:
      ```bash
      grep -n 'ERROR = ' src/ttr_parser/diagnostics.py
      grep -n 'ERROR =' ../../../docs/features/python-parser/contracts.md
      ```

---

## R5 — Tighten `extract_reference` to `PropertyValue` *(low, F5)*

- [ ] **R5.1 — Edit `__init__.py`.** Change the signature from
      `def extract_reference(value: object) -> Reference | None:` to:
      ```python
      def extract_reference(value: PropertyValue) -> Reference | None:
      ```
      (Requires R3 — `PropertyValue` imported. The body's `isinstance(value,
      IdValue)` check is unchanged.)

- [ ] **R5.2 — Verify** `mypy --strict` clean and the 2.1 id-parts test still
      compiles (it passes a real `StringValue` now):
      ```bash
      mypy src && python -m py_compile tests/test_id_value_parts.py && echo ok
      ```

---

## R6 — Re-verify, then commit Stage 2.2 *(process, F6 — do LAST)*

- [ ] **R6.1 — Full gate green / red-in-the-right-place:**
      ```bash
      mypy src && ruff check .
      pytest --collect-only -q 2>&1 | tail -3   # 78 tests collect; only test_dedent errors (2.3 module)
      ```

- [ ] **R6.2 — Stage only intended files** (no `.venv`, caches, `dist`):
      ```bash
      cd /Users/bora/Dev/collite-gh/modeler
      git add packages/python/ttr-parser/src/ttr_parser/model.py \
              packages/python/ttr-parser/src/ttr_parser/diagnostics.py \
              packages/python/ttr-parser/src/ttr_parser/__init__.py \
              docs/grammar-master/AST-NAMING.md
      git status --short
      ```

- [ ] **R6.3 — Commit:**
      ```bash
      git commit -m "Section P2.2: model dataclasses + diagnostics + AST-NAMING Python column"
      ```

- [ ] **R6.4 — Clean tree:** `git status --short` shows no stray artifacts; then
      `rm -rf packages/python/ttr-parser/.venv`.
