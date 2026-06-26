---
name: ai-platform-repo
description: ai-platform companion repo location and relationship to modeler
metadata: 
  node_type: memory
  type: reference
  originSessionId: 7c56d7f3-413d-48a8-b27d-6a08c1020b27
---

The `ai-platform` repo is at `~/Dev/ai-platform` on this machine. Used often
alongside `~/Dev/modeler`.

Relationship: `ai-platform` consumes TTR at runtime; `modeler` is editor
tooling only. The TTR grammar is canonical in `modeler/packages/grammar/src/TTR.g4`
and is **NOT vendored** anywhere. It feeds three generated parsers (TS via antlr-ng,
Kotlin via Gradle, Python via the ANTLR jar). ai-platform consumes the **published
artifacts** — `org.tatrman:ttr-parser` on Maven (Kotlin) and the `ttr-parser` wheel
on PyPI (Python) — by bumping the dependency version, never a grammar copy.

The old `sync-to-ai-platform.sh` / `check-sync.sh` scripts and the `grammar-sync.yml`
workflow were **deleted** (2026-06-24); cross-target drift is caught by the
conformance harness (`conformance.yml`). Procedure for a new grammar version:
`modeler/docs/grammar-master/new-grammar-version-process.md`. See [[md-model-design]].
