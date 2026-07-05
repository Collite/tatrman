# model-ttr-seed (Fixture A)

Provenance: copied from `kantheon/services/ariadne/src/main/resources/model-ttr/ucetnictvi/`
at kantheon commit `9328e98` (M1.1 T1.1.1). Only the self-contained `ucetnictvi`
package is included (every `er.entity.*` it references is defined within it) — the
stable guard for the loader/reconciler path. Files are directive-less (grammar 4.0
allows a document with no `model <code>` directive), so no directive migration was
needed. Consumed by `ModelTtrLoadSpec`.
