# Phase P1 / Stage 1.1 — Scaffolding: package skeleton + ANTLR Python generate

**Repo:** modeler. **Owner:** one developer. **Estimated effort:** half day.

**Pre-flight:**
- Java 21 (`temurin`) installed locally — `java -version` reports 21.x (needed
  only to run the ANTLR generator, never at consumer install).
- Python 3.10+ and a venv tool (`python -m venv`); `pipx` available.
- Fresh branch off `main`, e.g. `python/p1-scaffolding`.
- Read [`../architecture.md`](../architecture.md) §2 (Tech stack), §3 (Repo
  layout), §4 (ANTLR generation flow); [`../INDEX.md`](../INDEX.md) D1, D2, D4.

**Reference files:**
- `packages/grammar/scripts/generate-typescript-parser.sh` (the script to mirror).
- `packages/kotlin/ttr-parser/build.gradle.kts` (how the canonical `.g4` is
  pointed at without copying; the `-visitor` / `-package` args).

**Tasks** (check each immediately after completion):

- [ ] **1.1.1 — Create the package tree.** Make
      `packages/python/ttr-parser/{src/ttr_parser,scripts,tests}` and
      `src/ttr_parser/__init__.py` (empty for now). Per [`../architecture.md`](../architecture.md) §3.

- [ ] **1.1.2 — Write `pyproject.toml`** (Hatchling backend). Pin the runtime to
      `4.13.2` (D1) and the Python floor to 3.10 (D2). The version is derived
      from the publish tag later (stage 6.1); use a static `0.0.0` placeholder now.
      ```toml
      [build-system]
      requires = ["hatchling"]
      build-backend = "hatchling.build"

      [project]
      name = "ttr-parser"
      version = "0.0.0"
      description = "Parser, walker and reference resolver for the TTR modeling language"
      requires-python = ">=3.10"
      dependencies = ["antlr4-python3-runtime==4.13.2"]

      [tool.hatch.build.targets.wheel]
      packages = ["src/ttr_parser"]

      # _generated/ is produced by the generate step and bundled into the wheel,
      # but is gitignored in the source tree (D4). force-include guarantees the
      # generated parser ships even though it is not tracked by git.
      [tool.hatch.build.targets.wheel.force-include]
      "src/ttr_parser/_generated" = "ttr_parser/_generated"

      [tool.ruff]
      extend-exclude = ["src/ttr_parser/_generated"]
      [tool.mypy]
      strict = true
      exclude = ["src/ttr_parser/_generated"]
      ```

- [ ] **1.1.3 — Write `scripts/generate-python-parser.sh`** mirroring the TS
      generate script. Pin the ANTLR **tool** to 4.13.2 and use the **reference**
      jar (not `antlr-ng`). Output into `src/ttr_parser/_generated/` and drop an
      `__init__.py` there.
      ```bash
      #!/usr/bin/env bash
      set -euo pipefail
      HERE="$(cd "$(dirname "$0")/.." && pwd)"
      GRAMMAR="$HERE/../../grammar/src/TTR.g4"
      OUT="$HERE/src/ttr_parser/_generated"
      ANTLR_VERSION="4.13.2"
      JAR="$HOME/.cache/antlr/antlr-$ANTLR_VERSION-complete.jar"
      mkdir -p "$(dirname "$JAR")" "$OUT"
      [ -f "$JAR" ] || curl -fsSL -o "$JAR" \
        "https://www.antlr.org/download/antlr-$ANTLR_VERSION-complete.jar"
      java -jar "$JAR" -Dlanguage=Python3 -visitor -long-messages \
        -o "$OUT" "$GRAMMAR"
      touch "$OUT/__init__.py"
      ```
      Make it executable (`chmod +x`). **Note:** the generator emits
      `TTRLexer.py`, `TTRParser.py`, `TTRListener.py`, `TTRVisitor.py` into `$OUT`.

- [ ] **1.1.4 — Wire the generate step into the Hatchling build.** Add a custom
      build hook (`hatch_build.py` + `[tool.hatch.build.hooks.custom]`) that runs
      `scripts/generate-python-parser.sh` before the wheel is assembled, so a CI
      `build` regenerates the parser fresh. Keep it idempotent (skip if `_generated`
      already populated and grammar mtime unchanged is optional — simplest is
      always-run).

- [ ] **1.1.5 — `.gitignore`** — add `packages/python/ttr-parser/src/ttr_parser/_generated/`
      and `**/__pycache__/`, `*.egg-info/`, `.venv/`, `dist/` (D4: generated is
      never committed).

- [ ] **1.1.6 — Generate + install + import.** Run:
      ```bash
      cd packages/python/ttr-parser
      ./scripts/generate-python-parser.sh
      python -m venv .venv && . .venv/bin/activate
      pip install -e ".[dev]" || pip install -e .
      python -c "from ttr_parser._generated.TTRParser import TTRParser; print('ok')"
      ```
      Add a `[project.optional-dependencies] dev = ["pytest","ruff","mypy"]` group
      to support the `[dev]` extra.

- [ ] **1.1.7 — Smoke test** `tests/test_smoke.py` — parse a trivial document to a
      tree with no recogniser errors, using the runtime wiring the walker will use:
      ```python
      from antlr4 import InputStream, CommonTokenStream
      from ttr_parser._generated.TTRLexer import TTRLexer
      from ttr_parser._generated.TTRParser import TTRParser

      def test_smoke_parses_empty_model():
          lexer = TTRLexer(InputStream("model X {}\n"))
          parser = TTRParser(CommonTokenStream(lexer))
          tree = parser.document()              # `document` is the entry rule (TTR.g4)
          assert parser.getNumberOfSyntaxErrors() == 0
          assert tree is not None
      ```

**Verification commands:**
```bash
cd packages/python/ttr-parser
./scripts/generate-python-parser.sh
ls src/ttr_parser/_generated/        # expect TTRLexer.py TTRParser.py TTRListener.py TTRVisitor.py __init__.py
. .venv/bin/activate && pytest -q && ruff check . && mypy src
cd ../../.. && pnpm -r test          # TS unaffected
```

**Stage DoD:**
- All seven tasks checked.
- `_generated/` populated and gitignored; `import ttr_parser._generated.*` works.
- Smoke test green; `ruff` + `mypy` green (excluding `_generated/`).
- `pnpm -r test` still green (Python addition didn't touch the TS build).
