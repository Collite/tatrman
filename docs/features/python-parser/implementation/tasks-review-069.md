# Tasks — Review 069 (Python parser feature — merge readiness)

> **STATUS:** Phases P1–P6 complete and runtime-verified; `ttr-parser 0.1.0` is
> live on PyPI. The feature is additive (no grammar/TS/Kotlin source touched).
> The only red test (F1) is **pre-existing on `master`** and out of scope.
> Remaining work is landing the branch + two optional cleanups.

Paths are relative to the repo root `/Users/bora/Dev/collite-gh/modeler`.

---

## R1 — Land the feature *(do this to ship)*

- [ ] **R1.1 — Confirm the PR is open** `python-parser → master` (opened by this
      review; see the PR body for the six-phase summary). If it is not, run:
      ```bash
      gh pr create --base master --head python-parser \
        --title "Python parser (ttr-parser): parser + semantics + conformance + PyPI 0.1.0" \
        --body-file docs/features/python-parser/implementation/pr-body.md
      ```

- [ ] **R1.2 — Acknowledge F1 in the PR.** The CI may show
      `embedded-sql-diagnostics.test.ts` red. It is **pre-existing on master**
      (verified: zero TS source changed on this branch). Do **not** try to fix it
      in this PR — open a separate issue/PR. To re-confirm it is pre-existing:
      ```bash
      git diff --name-only $(git merge-base master python-parser)..python-parser \
        | grep -E '^packages/(semantics|lsp|parser|edit|vscode-ext)/'   # expect: no output
      ```

- [ ] **R1.3 — Merge** once review approves and the (Python/Kotlin/conformance)
      checks are green. The embedded-sql red is the only failure and is inherited.

---

## R2 — Optional cleanups *(Low; can be a follow-up commit on the branch before merge)*

- [ ] **R2.1 — (F2) Drop the dead `_Doc.uri` field** in
      `packages/python/ttr-parser/src/ttr_parser/semantics/validator.py`:
      remove the `uri` field from the `_Doc` dataclass and the `uri=...` arg in
      `_doc()`. Then:
      ```bash
      cd packages/python/ttr-parser && . .venv/bin/activate
      mypy --strict src/ttr_parser && ruff check . && pytest -q
      ```

- [ ] **R2.2 — (F3) Decide on `Resolver.get_symbol`.** Keep (API parity — the
      reviewer's recommendation) or remove. No action required if keeping.

---

## R3 — Separate from this feature *(track, don't do here)*

- [ ] **R3.1 — Fix `sql-unknown-column` on master (F1).** Open an issue against
      the embedded-SQL diagnostics: `embedded-sql-diagnostics.test.ts:114` expects
      one `sql-unknown-column` diagnostic but the resolver produces none. This
      lives in the TS `@modeler/lsp` / `@modeler/semantics` SQL layer, unrelated
      to the Python package.

---

## R4 — Verification checklist (already passing — re-run any time)

- [ ] **R4.1 — Python gate:**
      ```bash
      cd packages/python/ttr-parser && . .venv/bin/activate
      pytest -q && mypy --strict src/ttr_parser && ruff check .
      cd /Users/bora/Dev/collite-gh/modeler
      python -m pytest tests/conformance/test_conformance.py -q   # 108 passed
      ```
- [ ] **R4.2 — Cross-language conformance (regenerate, expect no diff):**
      ```bash
      pnpm --filter @modeler/conformance dump-all
      git diff --exit-code -- tests/conformance/out-ts tests/conformance/out-ts-sem
      ```
- [ ] **R4.3 — Clean Java-free install from PyPI:**
      ```bash
      python -m venv /tmp/ttr && /tmp/ttr/bin/pip install "ttr-parser==0.1.0"
      /tmp/ttr/bin/python -c "from ttr_parser import parse_string; from ttr_parser.semantics import StockLoader; print('ok')"
      ```
