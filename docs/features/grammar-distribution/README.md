# grammar-distribution — moved to `project/`

This effort's design/planning/status live in the centralized project repo, per the
2026-07-15 convention (new efforts author in `project/` only):

> **`project/tatrman/features/grammar-distribution/`**

- `README.md` — goal, finding, scope boundary
- `contracts.md` — published artifact, registry/scope, consumer contract, versioning
- `plan.md` — GD-P0 (spike) → GD-P1 (publish pipeline) → GD-P2 (modeler adoption)
- `T1-publish-pipeline.md` (tatrman) · `T2-modeler-adopt.md` (modeler)
- `STATUS.md` — tracked state (Dev · senior1)

Summary: publish `@tatrman/grammar` (the TS grammar package has no publish lane today —
only Kotlin→Maven and Python→PyPI exist) so `modeler` and other TS consumers depend on it
instead of vendoring a copy of `TTR.g4` that silently freezes. T1 touches this repo
(`packages/grammar`, a new `publish-ts.yml`, `PUBLISHING.md`, and
`docs/features/grammar-master/new-grammar-version-process.md`).
