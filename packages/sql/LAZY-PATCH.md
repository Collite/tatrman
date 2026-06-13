# SQL grammar lazy-patch backlog

Per DESIGN §12.7, the vendored grammars (see [`vendor/PINNED.md`](vendor/PINNED.md))
are **not** edited up front. When a concrete corpus query fails to parse — or an
adapter can't extract a real construct — record it here, then either patch the
throwaway-copy generation (`scripts/generate.sh`) or special-case the adapter,
and link the fixture that drove it.

**Status: empty.** The Phase 0 spike corpus and the sample corpus
(`samples/**/query.ttr`) both parse 100% cleanly under the default error strategy
(see `src/__tests__/extract-corpus.test.ts`). No patches are needed yet.

| Date | Dialect | Construct / query | Symptom | Resolution |
|---|---|---|---|---|
| — | — | — | — | — |
