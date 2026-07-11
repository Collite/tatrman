# SV-P2 · S1 — License mechanics (NOTICE · SPDX headers · the MIT sweep)

> Repos: **tatrman** + **tatrman-server** (the two open repos — kantheon is NOT in SV-P2 scope). Pre-flight: none (OQ-2 ✅ RO-18: SPDX one-liners, minimal NOTICE, © Collite). State verified 2026-07-11: both LICENSEs already Apache-2.0; tatrman-server has NOTICE, tatrman does not; **zero SPDX headers in either repo**. Land before SV-P1·S4 if possible, so Central jars ship headered sources. Branch `sv-p2-license` per repo.

- [ ] **T1 — NOTICE in tatrman** (mirror tatrman-server's, adjusted): product line (`Tatrman — Table Transformation Manager`), `Copyright <year> Collite s.r.o.` (match the exact legal-entity string in tatrman-server's NOTICE), one line for the Apache-2.0 license pointer. Minimal per RO-18 — NOTICE is for *required* attributions only; do not inventory dependencies here.
- [ ] **T2 — Header-check CI, both repos (TDD: the check comes FIRST and must fail).** Add `scripts/check-spdx.sh`: finds tracked `*.kt`, `*.kts`, `*.py`, `*.proto`, `*.ts` files (exclude generated: `build/`, `**/generated/**`, `*_pb2.py`, `node_modules/`, `graphify-out/`) whose first 3 lines lack `SPDX-License-Identifier: Apache-2.0`; exits 1 listing offenders. Wire into both repos' `ci.yml`. Run it → it fails everywhere → that failing list is T3's worklist.
- [ ] **T3 — Apply headers.** `scripts/add-spdx.sh` (idempotent — skips files already carrying the line): insert as the FIRST line (after shebang for `.py`/`.sh`) `// SPDX-License-Identifier: Apache-2.0` (Kotlin/proto/TS) / `# SPDX-License-Identifier: Apache-2.0` (Python/YAML-adjacent). One-liner only, no copyright banner blocks (RO-18). Run over both repos; `check-spdx.sh` goes green; the pair of scripts stays in-repo (external contributors need them).
- [ ] **T4 — The MIT sweep, living docs (plain replacement).** Find real claims with **word-boundary** grep (`grep -rnE '\bMIT\b' --include='*.md' --include='*.json' --include='*.toml' --include='*.xml' .` — plain `mit` false-positives on commit/permit): tatrman README.md, CONTRIBUTING.md, HOWTO.md, PUBLISHING.md confirmed carriers. Living docs say Apache-2.0 plainly. Machine-readable license fields too: every `package.json` `"license"`, `pyproject.toml` classifiers/`license`, `intellij-plugin` manifest, vsix `package.json`, POM blocks not covered by SV-P1·S4·T1.
- [ ] **T5 — The MIT sweep, design corpus (superseded markers — NEVER rewrite history).** For hits inside `docs/ecosystem/platform/**`, `docs/grammar-master/**`, decision logs, old task lists: leave the text, append the standard marker at first occurrence per document: `> **[superseded 2026-07-10 · STRAT-3]** license is Apache-2.0 across the open surface; MIT mentions below are historical.` Skip files that are themselves append-only decision logs where RO/STRAT entries already record the change (control room §7 needs nothing).
- [ ] **T6 — Verify + findings.** Verify block below; record the before/after counts; ⚑ any file where the license claim was load-bearing and ambiguous (e.g., third-party snippets) rather than deciding solo.

**Verify block:**
```bash
for r in ~/Dev/collite-gh/tatrman ~/Dev/collite-gh/tatrman-server; do cd $r &&
  ./scripts/check-spdx.sh &&
  grep -rnE '\bMIT\b' --include='*.md' --include='*.json' --include='*.toml' . \
    | grep -viE 'superseded|historical|docs/ecosystem/platform|grammar-master|node_modules' | head; done
# expect: check green; grep prints nothing (all remaining hits are marked history)
```

## Findings / ⚑

*(header counts per repo · MIT before/after · ambiguous cases)*
