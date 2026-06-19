# Phase P6 / Stage 6.1 — Packaging metadata + publish-python.yml + first 0.1.0

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** half–1 day.
Goal: `pip install ttr-parser` from **public PyPI** gives a pure-Python wheel —
parser **and** resolution — with no JVM at the consumer.

**Pre-flight:**
- Phase P5 merged (both conformance gates green in CI).
- Read [`../contracts.md`](../contracts.md) §1 (coords), §6.2 (publish workflow);
  [`../architecture.md`](../architecture.md) §8 (distribution); [`../INDEX.md`](../INDEX.md) D3, D4, D7, D8.
- PyPI account + project name `ttr-parser` reserved; a **PyPI API token** added to
  the repo secrets as `PYPI_TOKEN` (or Trusted Publishing configured — preferred;
  see note in 6.1.4).

**Tasks** (check each immediately after completion):

- [ ] **6.1.1 — Finalise `pyproject.toml` metadata.** Add `authors`,
      `license`, `readme = "README.md"`, `keywords`, `classifiers`
      (`Programming Language :: Python :: 3.10/3.11/3.12/3.13`,
      `License :: …`, `Topic :: Software Development :: Compilers`),
      `[project.urls]` (Homepage/Repository/Issues). Keep
      `requires-python = ">=3.10"` and `dependencies = ["antlr4-python3-runtime==4.13.2"]`.

- [ ] **6.1.2 — Confirm the wheel is self-contained.** Build locally
      (`pipx run build --wheel packages/python/ttr-parser`) and `unzip -l` the
      wheel: it must contain `ttr_parser/_generated/*.py` **and**
      `ttr_parser/semantics/stock/cnc-roles.ttr` (both `force-include`d). Install
      the built wheel into a **fresh venv with no Java** and run
      `python -c "from ttr_parser import parse_string; from ttr_parser.semantics import load_project; print('ok')"`.

- [ ] **6.1.3 — `README.md`** (consumer quickstart): `pip install ttr-parser`; a
      ~15-line example that `load_project(root)`, iterates `definitions`, and
      `resolve`s a reference; a one-line note that no JVM is required. This is the
      PyPI landing page (referenced by `readme` in 6.1.1).

- [ ] **6.1.4 — `.github/workflows/publish-python.yml`** (contracts §6.2). Trigger
      on tags `python/v*`; derive the version from the tag (`python/v0.1.0` →
      `0.1.0`) and inject it (e.g. `hatch version` or `SETUPTOOLS_SCM`-style env /
      a `sed` into `pyproject.toml` before build). Steps: checkout →
      setup-python 3.12 → setup-java 21 (generate step) →
      `pipx run build --wheel --sdist packages/python/ttr-parser` →
      `pipx run twine upload dist/*` to **public PyPI**.
      **Note:** prefer **PyPI Trusted Publishing** (OIDC, `permissions: id-token:
      write`, `pypa/gh-action-pypi-publish`) over a long-lived `PYPI_TOKEN` if the
      org allows it — no secret to rotate.

- [ ] **6.1.5 — `CHANGELOG.md`** — seed `0.1.0`: "Initial release — parser, walker
      and reference resolver (six-step chain + stock CNC vocab) for TTR
      `@grammar-version 2.2`." Note it ships parser **and** semantics together (D8).

- [ ] **6.1.6 — Dry-run via TestPyPI.** Publish `0.1.0rc0`-equivalent to
      **TestPyPI** first (`--repository testpypi`), `pip install -i
      https://test.pypi.org/simple/ ttr-parser` into a clean venv, verify import +
      a real parse/resolve. (This rc is the **only** allowed pre-release — for the
      dry run; real releases are clean `0.x.y`, D7.)

- [ ] **6.1.7 — Cut `python/v0.1.0`.** Push the tag; confirm the workflow builds
      (Java present in CI) and uploads to PyPI; `pip install ttr-parser==0.1.0`
      into a clean venv with **no Java** and run the README example end-to-end.

**Verification commands:**
```bash
pipx run build --wheel packages/python/ttr-parser
unzip -l packages/python/ttr-parser/dist/ttr_parser-*.whl | grep -E '_generated/|stock/cnc-roles.ttr'
python -m venv /tmp/clean && /tmp/clean/bin/pip install packages/python/ttr-parser/dist/ttr_parser-*.whl
/tmp/clean/bin/python -c "from ttr_parser.semantics import load_project; print('ok')"   # no Java on PATH
```

**Stage DoD:**
- All seven tasks checked.
- `pip install ttr-parser==0.1.0` from PyPI works in a clean, **Java-free** venv;
  both `parse_file` and `load_project(...).resolve(...)` run on a real model.
- `python/v0.1.0` tag pushed; the publish workflow is green; `CHANGELOG.md`
  records `0.1.0`.

---

## Phase P6 DoD = feature DoD

- [ ] `ttr-parser` `0.1.0` published to public PyPI; pure-Python wheel (no JVM at
      install) bundling the generated parser + stock vocab.
- [ ] Both conformance gates (`py-vs-ts` AST, `py-sem-vs-ts` resolution) green in
      CI on every PR.
- [ ] A Python consumer can parse `.ttr` models and resolve references identically
      to the platform — verified by the §5.1 gate against the TS golden.
- [ ] No grammar/TS/Kotlin regressions (`pnpm -r test`, Kotlin + existing
      conformance all green).
