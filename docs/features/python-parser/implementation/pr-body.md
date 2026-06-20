## Python parser (`ttr-parser`) — parser + semantics + conformance + PyPI `0.1.0`

Adds a standalone, **pure-Python** package `ttr-parser` for external Python
consumers: it parses `.ttr` models into a typed AST and resolves references
through the same six-step chain the platform uses — **no JVM** at install or
runtime. Editor-tooling-only and additive; **no grammar / TS / Kotlin source is
touched** (the lone shared-file change is a stale conformance-golden refresh).

Authoritative docs: [`docs/features/python-parser/`](docs/features/python-parser/)
(plan, architecture, contracts, per-stage tasks, reviews 064–069).

### What landed (six phases)

- **P1 — Scaffolding** (`packages/python/ttr-parser/`): Hatchling build, ANTLR
  Python parser generated from the canonical `TTR.g4` at build time, pinned to
  `antlr4-python3-runtime==4.13.2`, CPython 3.13+.
- **P2 — AST + walker + loader**: frozen dataclass model of the full grammar
  surface, `parse_string`/`parse_file`/`parse_directory`, triple-string dedent,
  source locations on every node.
- **P3 — AST conformance** (`dump.py` + `py-vs-ts`): the Python AST is pinned
  byte-for-byte to the committed TS golden on every fixture, in CI.
- **P4 — Semantics core**: `Qname`, `SymbolTable`, the six-step `Resolver`, the
  portable `Validator` subset, the stock CNC vocab, `PackageGraph`, and the
  `Project` / `load_project` entry point — the contracts §3 surface.
- **P5 — Semantics conformance** (`dump_sem.py` + `py-sem-vs-ts`): resolution +
  diagnostics + symbols pinned byte-for-byte to the TS golden, single files
  **and** the multi-doc subdirectories.
- **P6 — Publish**: `publish-python.yml` (tag `python/v*`, **Trusted Publishing**
  / OIDC, no token), README + CHANGELOG. **`ttr-parser 0.1.0` is live on PyPI**
  as a `py3-none-any` wheel bundling the generated parser + stock vocab.

### Verification (runtime, see review-069)

- Python: **144 unit + 108 conformance** pass; `mypy --strict` + `ruff` clean.
- Both conformance gates byte-identical to the TS golden across all fixtures.
- Kotlin Kotest suites: **BUILD SUCCESSFUL** (untouched).
- `pip install ttr-parser==0.1.0` into a clean **Java-free** venv: parse + stock
  resolution work.

### Known: one pre-existing red test (not from this PR)

`tests/integration/src/embedded-sql-diagnostics.test.ts` →
`sql-unknown-column` fails on `master` already. This branch changes **zero** TS
source (`git diff merge-base..HEAD` over `packages/{semantics,lsp,parser,edit,
vscode-ext}` is empty), so it is inherited, not introduced. Tracked separately
(review-069 F1); **not addressed here**.

### CI

`conformance.yml` gains `py-dump`, `py-vs-ts`, and `py-sem-vs-ts` jobs (run on
every PR). The refreshed `out-ts-sem/` baseline makes the existing `ts-dump`
"committed baseline current" assertion pass.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
