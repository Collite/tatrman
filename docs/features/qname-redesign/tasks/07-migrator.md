# 07 — Migrator (modeler `@modeler/migrate`) — **the hard cut**

Goal: a trivia-preserving codemod that rewrites any pre-version file + `modeler.toml`,
plus a `--dry-run` diff. This is the tool Phases 08–09 run. See [`../plan.md`](../plan.md)
Phase 7.

**Pre-flight:** Phases 01–06 merged. Read `packages/migrate/src/{index.ts,cli.ts,phase0.ts}`
— mirror the `phase0.ts` pattern and reuse the CST/trivia layer (lossless rewrites).

## Tasks

- [ ] **7.1 Tests first (golden).** `migrate/src/__tests__`: before/after for
  `def model`→`def project`; directive `schema <code> [namespace <id>]` →
  `model <code> [schema <id>]`; `graph … schema <code>` → `graph … model <code>`;
  **reference rewrites (D14/D15): `db.query.<X>` → `db.dbo.<X>` (default schema) and
  `cnc.cnc.<rest>` → `cnc.<rest>`**; manifest lift; a file that must **not** change;
  idempotency (run twice = no-op); a mixed-model file.
- [ ] **7.2 Keyword rewrite.** Token-aware, trivia-preserving rewrites for the three
  forms above. Never reflow unrelated text.
- [ ] **7.3 Manifest lift.** `[schemas] namespaces = { db="dbo", … }` → one
  `[schemas.<ns>]` table each, pulling `database`/`db-schema`/`dialect` from any
  existing `[[sql.namespace-map]]`/`[sql.defaults.*]`; drop the old keys. **db-schema
  fillable; `database` may be unknown → emit a TODO comment + warning, not a guess.**
- [ ] **7.4 Reference rewrites (mandatory) + `--shorten` (optional).** Always rewrite
  the D14/D15 forms: `db.query.<X>`→`db.dbo.<X>`, `cnc.cnc.<rest>`→`cnc.<rest>` (these
  change *meaning* under the new slot model, so they are not optional). Separately,
  an off-by-default `--shorten` pass strips other now-redundant model/kind prefixes,
  gated behind review.
- [ ] **7.5 Verify + dry-run.** Re-parse every output; fail the run on any new
  parse/resolve regression. `--dry-run` prints a unified diff. CLI subcommand
  `migrate-qnames <root>`.

## Done when

- [ ] migrate suite green incl. idempotency.
- [ ] Dry-run over `docs/manual/en/examples/retail` yields a clean, re-parseable tree;
  `--shorten` produces a reviewable diff.

**Verify:** `pnpm --filter @modeler/migrate test` ·
`pnpm --filter @modeler/migrate cli -- migrate-qnames docs/manual/en/examples/retail --dry-run`
