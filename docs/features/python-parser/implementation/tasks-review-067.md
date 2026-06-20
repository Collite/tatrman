# Tasks — Review 067 (Python parser Stage 2.3, walker + loader)

> **STATUS (2026-06-20): R1–R3 applied by the reviewer.** Rich
> `19-inline-mapping.ttr` restored + `test_parse_string_inline_mapping_block`
> strengthened to assert the three column forms; contracts §2.4 corrected to
> "character offsets"; `walker.py` got the `Any` rationale. Verified: `pytest` 86
> passed, `mypy --strict` + `ruff` clean. **Remaining for the developer:** R4
> (commit the 2.3 files). The R1–R3 steps below are retained for the record.

Follow in order. Check a box only after its verification passes. Paths are
relative to the repo root `/Users/bora/Dev/collite-gh/modeler`. Work in the
package with a venv:
`cd packages/python/ttr-parser && python3 -m venv .venv && . .venv/bin/activate &&
pip install -e ".[dev]"`.

> Findings: [`review-067.md`](review-067.md) F1–F4.

---

## R1 — Restore rich-mapping coverage *(should fix, F1)*

The walker handles rich mappings correctly (verified); the committed fixture was
gutted, leaving `MappingColumnObject` / nested `TargetValue` / multi-column
mappings untested. Restore the fixture **and** assert its structure.

- [ ] **R1.1 — Restore `tests/fixtures/19-inline-mapping.ttr`** to a rich mapping
      that exercises all three column forms. Use:
      ```ttr
      schema er

      def entity artikl {
          mapping: {
              target: { table: db.dbo.QZBOZI_DF }
              columns: {
                  id_artiklu: IDZBOZI
                  kod_artiklu: { target: KOD_ZBOZI }
                  nazev_artiklu: { target: { column: NAZEV_ZBOZI } }
              }
          }
          attributes: [
              def attribute id_artiklu { type: int, isKey: true },
              def attribute kod_artiklu { type: text },
              def attribute nazev_artiklu { type: text }
          ]
      }
      ```
      (Verify it parses cleanly first — it does in the reviewer's probe. If your
      grammar build rejects a separator, keep the commas as in the pre-2.3
      version; the point is the three column forms + nested target.)

- [ ] **R1.2 — Strengthen the test.** In `tests/test_loader.py`, expand
      `test_parse_string_inline_mapping_block` to assert the mapping **structure**,
      not just `is not None`:
      ```python
      from ttr_parser import (
          MappingPropertyBlock, TargetObjectValue,
          MappingColumnBareId, MappingColumnObject,
      )

      def test_parse_string_inline_mapping_block() -> None:
          r = ttr_parser.parse_string(_read("19-inline-mapping.ttr"))
          assert r.ok
          e = r.definitions[0]
          assert isinstance(e, EntityDef)
          m = e.mapping
          assert isinstance(m, MappingPropertyBlock)
          assert isinstance(m.target, TargetObjectValue)
          cols = {c.name: c.value for c in m.columns}
          assert isinstance(cols["id_artiklu"], MappingColumnBareId)
          assert isinstance(cols["kod_artiklu"], MappingColumnObject)
          assert isinstance(cols["nazev_artiklu"], MappingColumnObject)
      ```
      (Add the imports to the existing `from ttr_parser import (...)` block.)

- [ ] **R1.3 — Verify** the suite is green with the richer fixture/test:
      ```bash
      pytest -q tests/test_loader.py
      ```
      Expect: all green (the parametrised `19-inline-mapping` row still asserts the
      single top-level `entity` def; the new structural test passes).

> Note on the missing `test_inline_mappings.py` / `test_drill_map.py` (task 2.3.6):
> R1.2 covers the inline-mapping gap inline. If you prefer the task's file layout,
> create `tests/test_inline_mappings.py` with R1.2's body instead — either is fine,
> but the structural assertions must exist somewhere.

---

## R2 — Fix the offset wording in contracts §2.4 *(doc, F2)*

The walker emits character offsets (matching `walker.ts`), not byte offsets.

- [ ] **R2.1 — Edit [`../contracts.md`](../contracts.md) §2.4.** In the
      `SourceLocation` field comments, change:
      ```
      offset_start: int    # 0-indexed byte offset, inclusive
      offset_end: int      # 0-indexed byte offset, exclusive
      ```
      to:
      ```
      offset_start: int    # 0-indexed character offset, inclusive
      offset_end: int      # 0-indexed character offset, exclusive
      ```

- [ ] **R2.2 — Rewrite the "Byte-offset note"** in §2.4 to:
      > **Offset note:** `offset_start`/`offset_end` are raw ANTLR **character**
      > offsets (`token.start` / `token.stop + 1`), matching the TS/Kotlin walkers
      > — **not** UTF-8 byte offsets. On non-ASCII sources they differ from a
      > `str.encode()` byte index; slice the source **string**, not its bytes.
      > (JVM ANTLR counts UTF-16 code units, so astral-plane characters can differ
      > from CPython codepoints — irrelevant for BMP text, which is all TTR uses.)

- [ ] **R2.3 — Verify** no stray "byte offset" claim remains in §2.4:
      ```bash
      grep -n "byte offset" ../../../docs/features/python-parser/contracts.md
      ```
      Expect: no matches in §2.4 (a mention in the rewritten note that says "not
      byte offsets" is fine).

---

## R3 — Make the walker's `Any` usage deliberate *(nit, F3)*

- [ ] **R3.1 — Add a rationale comment** near the top of `src/ttr_parser/walker.py`
      (after the module docstring) explaining the `ctx: Any` choice, e.g.:
      ```python
      # Parse-tree contexts are typed `Any`: ANTLR's generated Python context
      # classes are awkward to thread through mypy --strict, and the walker only
      # navigates them by the rule-accessor names the grammar guarantees. Field
      # *outputs* are fully typed via the model dataclasses, so type safety holds
      # at the module boundary.
      ```
      (Optional stronger fix: type the few hottest contexts — `DocumentContext`,
      `DefinitionContext` — with the generated classes. Not required.)

---

## R4 — Re-verify, then commit Stage 2.3 *(process, F4 — do LAST)*

- [ ] **R4.1 — Full gate green:**
      ```bash
      pytest -q && mypy src && ruff check .
      python -c "from ttr_parser import parse_file; r=parse_file('../../../samples/2.1/db.ttr'); print(r.ok, len(r.definitions))"
      ```
      Expect: all tests pass; `mypy`/`ruff` clean; the sample prints `True <n>`.

- [ ] **R4.2 — Stage only intended files** (no `.venv`, caches, `dist`):
      ```bash
      cd /Users/bora/Dev/collite-gh/modeler
      git add packages/python/ttr-parser/src/ttr_parser/{walker,loader,dedent,tag_registry,__init__,model}.py \
              packages/python/ttr-parser/tests/ \
              docs/features/python-parser/contracts.md
      git status --short
      ```

- [ ] **R4.3 — Commit:**
      ```bash
      git commit -m "Section P2.3: walker + dedent + tag registry + loader"
      ```

- [ ] **R4.4 — Clean tree:** `git status --short` shows no stray artifacts; then
      `rm -rf packages/python/ttr-parser/.venv`.
