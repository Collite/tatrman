# Tasks — Review 068 (Python parser Stage 3.1, AST conformance) + takeover

> **STATUS (2026-06-20): Stage 3.1 completed by the reviewer.** The harness is
> green (50/50 byte-identical), the prove-red gate is verified, `out-py/` is
> gitignored, and the `py-dump` + `py-vs-ts` CI jobs are in `conformance.yml`.
> The walker `and False` hack was removed. **Remaining for the developer:** R1
> (commit) and R2 (optional coverage fixture). R3 is a verification checklist.

Paths are relative to the repo root `/Users/bora/Dev/collite-gh/modeler`.

---

## R1 — Commit Stage 3.1 *(do this to land the stage)*

- [ ] **R1.1 — Sanity-check the tree** (no stray artifacts; `out-py/` must NOT
      appear — it is now gitignored):
      ```bash
      git status --short | grep -v graphify
      ```
      Expect only: `conformance.yml`, `.gitignore`, `walker.py`, `test_dedent.py`
      (modified); `dump.py`, `test_conformance.py`, `py.typed`,
      `docs/features/python-parser/implementation/review-068.md` +
      `tasks-review-068.md` (new). **No `tests/conformance/out-py/` entries.**

- [ ] **R1.2 — Stage and commit:**
      ```bash
      git add .github/workflows/conformance.yml .gitignore \
              tests/conformance/dump.py tests/conformance/test_conformance.py \
              packages/python/ttr-parser/src/ttr_parser/walker.py \
              packages/python/ttr-parser/src/ttr_parser/py.typed \
              packages/python/ttr-parser/tests/test_dedent.py
      git commit -m "Section P3.1: Python AST conformance harness (dump + py-vs-ts + CI)"
      ```

- [ ] **R1.3 — Commit the review docs** (separately or together):
      ```bash
      git add docs/features/python-parser/implementation/review-068.md \
              docs/features/python-parser/implementation/tasks-review-068.md
      git commit -m "docs: review-068 (Stage 3.1 conformance review + takeover)"
      ```

---

## R2 — Close the fixture-coverage gap *(optional follow-up, Low)*

The gate only pins what the fixtures exercise; no fixture covers a `view` with
`tags` (the path the stuck mutation hit). Pin it so a future regression can't slip
through.

- [ ] **R2.1 — Add a view-with-tags fixture.** Create
      `tests/conformance/fixtures/54-view-tags.ttr` with a `view` carrying a
      non-empty `tags:` list (plus a `definitionSql`/`columns` so it is a realistic
      view). Keep it minimal.

- [ ] **R2.2 — Regenerate the TS golden** (the TS dumper owns the reference):
      ```bash
      pnpm --filter @modeler/conformance dump
      git status --short tests/conformance/out-ts/      # expect new 54-view-tags.json
      ```

- [ ] **R2.3 — Confirm Python matches** and commit both fixture + golden:
      ```bash
      . packages/python/ttr-parser/.venv/bin/activate
      python -m pytest tests/conformance/test_conformance.py -q   # expect green incl. 54
      deactivate
      git add tests/conformance/fixtures/54-view-tags.ttr tests/conformance/out-ts/54-view-tags.json
      git commit -m "test(conformance): pin view-with-tags path"
      ```

---

## R3 — Verification checklist (already passing — re-run any time)

- [ ] **R3.1 — Full local gate:**
      ```bash
      cd packages/python/ttr-parser && . .venv/bin/activate
      pytest -q && mypy src && ruff check .
      cd /Users/bora/Dev/collite-gh/modeler
      python -m pytest tests/conformance/test_conformance.py -q
      deactivate
      ```
      Expect: unit `86 passed`, `mypy`/`ruff` clean, conformance `52 passed`.

- [ ] **R3.2 — Prove-red is reproducible** (optional): temporarily drop a covered
      property in `dump.py` (e.g. the `"tags": list(d.tags),` line in
      `_definition`), run the conformance suite → expect a wall of red with unified
      diffs, then `git checkout tests/conformance/dump.py`. Do **not** mutate a
      property no fixture exercises (e.g. view tags) — it will stay green.

- [ ] **R3.3 — `out-py/` stays uncommitted:**
      ```bash
      git check-ignore tests/conformance/out-py/01-model.json   # prints the path = ignored
      git ls-files tests/conformance/out-py/ | wc -l            # 0
      ```
