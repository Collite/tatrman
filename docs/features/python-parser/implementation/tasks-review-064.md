# Tasks — Review 064 (Python parser Phase P1)

> **STATUS (2026-06-19): all closed.** R2 (3.13 floor) and R3 (`graphify-out/`)
> were resolved as owner decisions — 3.13+ is intended; `graphify-out/` is
> committed-and-shared by design, so **do not** run R3. R1, R4, R5, R6 were
> applied by the reviewer (R1 landed as the Hatchling `artifacts` pattern, not
> `force-include`, which double-adds against `packages`). The steps below are
> retained for the record. Final gate (R7) re-verified green.

Follow these in order. Each task says **exactly** what to change, in which file,
and how to verify. Check the box only after the verification command passes.
All paths are relative to the repo root `/Users/bora/Dev/collite-gh/modeler`.

> Findings reference: [`review-064.md`](review-064.md) F1–F6.

---

## R3 — Remove the `graphify-out/` artifact from the branch *(do this FIRST — merge blocker, F3)*

`graphify-out/` is a ~55 MB `/graphify` knowledge-graph dump accidentally
committed in the `graphify` commit. It is unrelated to the Python parser and must
not reach `master`.

- [ ] **R3.1 — Stop tracking the directory.** Run exactly:
      ```bash
      cd /Users/bora/Dev/collite-gh/modeler
      git rm -r --cached graphify-out
      ```
      (`--cached` removes it from git but leaves the files on disk.)

- [ ] **R3.2 — Add it to `.gitignore`.** Open `.gitignore`. At the end of the
      file (after the existing Python block), add these two lines:
      ```gitignore
      # graphify knowledge-graph output (never commit)
      graphify-out/
      ```

- [ ] **R3.3 — Commit the removal.** Run:
      ```bash
      git add .gitignore
      git commit -m "chore: drop accidentally-committed graphify-out/ artifact"
      ```

- [ ] **R3.4 — Verify.** All three must hold:
      ```bash
      git ls-files graphify-out/        # expect: no output (nothing tracked)
      git check-ignore graphify-out/graph.json   # expect: prints the path (ignored)
      git status --short                # expect: clean, graphify-out not listed
      ```

---

## R2 — Get a decision on the CPython 3.13 floor *(merge blocker, F2 — needs the user, do NOT decide unilaterally)*

The branch changed the public library's CPython floor from **3.10** to **3.13**
by editing the *pinned* spec, justified only by the developer's local `pyenv`.
This restricts the public PyPI audience and reverses a locked decision.

- [ ] **R2.1 — Surface the decision to the user.** Ask explicitly: *"Should the
      public `ttr-parser` PyPI floor be 3.13 (matches our local toolchain, but
      excludes 3.11/3.12 consumers) or stay at the originally-pinned 3.10 (wider
      reach; all features we use are 3.10-available)?"* Do **not** proceed to
      R2.2 or R2.3 until the user answers.

- [ ] **R2.2 — IF the user keeps 3.13:** record it as an explicit, dated decision,
      not a toolchain note. In [`../PINNED.md`](../PINNED.md) row "CPython floor",
      change the "Why" to cite the *user decision* (e.g. "User-approved
      2026-06-…; consumers on this feature are 3.13+"), not "matches the repo
      toolchain". Leave the value `3.13`. Nothing else changes.

- [ ] **R2.3 — IF the user reverts to 3.10:** change the floor back in **all six
      places** the branch edited it. Do each, then verify with the grep below:
      - `packages/python/ttr-parser/pyproject.toml` → `requires-python = ">=3.10"`,
        `target-version = "py310"` (ruff), `python_version = "3.10"` (mypy),
        and the classifiers (`Programming Language :: Python :: 3.13` →
        keep `3.10` line; you may add `3.10`/`3.11`/`3.12`).
      - [`../PINNED.md`](../PINNED.md) — CPython floor row back to `3.10` with the
        original feature rationale.
      - [`../architecture.md`](../architecture.md) §2 "Python version floor" row.
      - [`../contracts.md`](../contracts.md) §2 intro line and §6.1 `py-dump` job.
      - [`../plan.md`](../plan.md) global pre-flight + P6 `requires-python`.
      - [`../tasks/01-scaffolding.md`](../tasks/01-scaffolding.md) §1.1.2 and
        pre-flight; [`../tasks/05-conformance-ast.md`](../tasks/05-conformance-ast.md)
        §3.1.7; [`../tasks/11-publishing.md`](../tasks/11-publishing.md).
      - Verify no stale `3.13` (or `3.10`, depending on outcome) floor remains:
        ```bash
        grep -rn "3\.1[03]" docs/features/python-parser packages/python/ttr-parser/pyproject.toml
        ```
        and eyeball that every hit is intentional.

---

## R1 — Add the `force-include` block for `_generated/` *(F1)*

Restore the pinned D4 mechanism so the wheel is guaranteed to ship the parser
regardless of Hatchling's default file-selection behaviour.

- [ ] **R1.1 — Edit `packages/python/ttr-parser/pyproject.toml`.** Directly after
      the existing block:
      ```toml
      [tool.hatch.build.targets.wheel]
      packages = ["src/ttr_parser"]
      ```
      insert:
      ```toml
      # _generated/ is produced by the build hook and bundled into the wheel, but
      # is gitignored in the source tree (D4). force-include guarantees it ships
      # even if a future Hatchling honours .gitignore for the wheel target.
      [tool.hatch.build.targets.wheel.force-include]
      "src/ttr_parser/_generated" = "ttr_parser/_generated"
      ```

- [ ] **R1.2 — Verify the wheel still bundles the parser.** Run:
      ```bash
      cd packages/python/ttr-parser
      python3 -m venv .venv && . .venv/bin/activate
      pip install -e ".[dev]" build >/dev/null
      python -m build --wheel >/dev/null
      unzip -l dist/*.whl | grep 'ttr_parser/_generated/TTRParser.py'
      ```
      Expect a line listing `ttr_parser/_generated/TTRParser.py`. Then clean up:
      ```bash
      deactivate && rm -rf .venv dist
      ```

---

## R4 — Fix the project URLs to the real GitHub org *(F4)*

- [ ] **R4.1 — Edit `packages/python/ttr-parser/pyproject.toml`.** In
      `[project.urls]`, replace `anomalyco` with `Collite` (the actual remote
      org — `git@github.com:Collite/modeler.git`):
      ```toml
      [project.urls]
      Homepage = "https://github.com/Collite/modeler"
      Issues = "https://github.com/Collite/modeler/issues"
      ```

- [ ] **R4.2 — Verify.** Run:
      ```bash
      grep -n "anomalyco" packages/python/ttr-parser/pyproject.toml   # expect: no output
      ```

---

## R5 — Make the build-hook Java guard consistent *(F5)*

`scripts/generate-python-parser.sh` always invokes `java`. The hook should give
the friendly error whenever Java is missing, not only when `_generated/` is empty.

- [ ] **R5.1 — Edit `packages/python/ttr-parser/hatch_build.py`.** In
      `CustomBuildHook.initialize`, replace the conditional guard:
      ```python
      generated = repo_root / "src" / "ttr_parser" / "_generated"
      env = os.environ.copy()
      if not (generated / "TTRParser.py").exists():
          if not env.get("JAVA_HOME") and not _which("java"):
              raise RuntimeError(
                  "Java is required to run scripts/generate-python-parser.sh "
                  "the first time _generated/ is empty. Install JDK 21+ or "
                  "populate _generated/ before building."
              )
      ```
      with an **unconditional** check (the generate script always needs Java):
      ```python
      env = os.environ.copy()
      if not env.get("JAVA_HOME") and not _which("java"):
          raise RuntimeError(
              "Java (JDK 21+) is required: the build hook runs "
              "scripts/generate-python-parser.sh, which always invokes the "
              "ANTLR jar. Install Java or build from a prebuilt wheel."
          )
      ```
      (The `generated = ...` line is now unused — delete it.)

- [ ] **R5.2 — Verify the hook still builds with Java present.** Run:
      ```bash
      cd packages/python/ttr-parser
      python3 -m venv .venv && . .venv/bin/activate
      pip install build >/dev/null && python -m build --wheel >/dev/null && echo "build OK"
      deactivate && rm -rf .venv dist
      ```
      Expect `build OK`.

---

## R6 — Correct the smoke-test example in the task doc *(F6 — doc only)*

The grammar requires `def model X {}`, not `model X {}`. The code is already
correct; only the task document is misleading.

- [ ] **R6.1 — Edit [`../tasks/01-scaffolding.md`](../tasks/01-scaffolding.md).**
      In §1.1.6 and §1.1.7 code blocks, change every `InputStream("model X {}\n")`
      / `model X {}` smoke example to `def model X {}` so it matches the grammar
      and the shipped `tests/test_smoke.py`.

- [ ] **R6.2 — Verify.** Run:
      ```bash
      grep -n 'InputStream("model X' docs/features/python-parser/tasks/01-scaffolding.md
      ```
      Expect: no output (only `def model X` examples remain).

---

## Final gate — re-run the full P1 DoD after all edits

- [ ] **R7.1 — Everything green from a clean state.** Run:
      ```bash
      cd /Users/bora/Dev/collite-gh/modeler/packages/python/ttr-parser
      ./scripts/generate-python-parser.sh
      python3 -m venv .venv && . .venv/bin/activate
      pip install -e ".[dev]" >/dev/null
      pytest -q && ruff check . && mypy src
      deactivate && rm -rf .venv
      ```
      Expect: `4 passed`, `All checks passed!`, `Success: no issues found`.

- [ ] **R7.2 — Tree is clean.** Run `git status --short` — expect no stray
      `_generated/`, `.venv/`, `dist/`, or `graphify-out/` entries.
