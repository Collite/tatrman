# ttrp-emit golden corpus

Byte-stable snapshots of emitted island payloads. Layout is **per-dialect**:

- `sql/postgres/*.sql` — Postgres SQL islands (Stage 3.1, CTE-per-node, E-b / Q9-3).
- `polars/*.py` — Polars straight-line scripts (Stage 3.2).
- `transfers/*.py` — generated ADBC/Arrow-IPC transfer scripts (Stage 3.2).

## Updating a golden

1. `./gradlew :packages:kotlin:ttrp-emit:test -DupdateGolden=true` — rewrites the changed
   goldens and fails each updated test with a "re-run without -DupdateGolden" message.
2. **Review `git diff`** — an updated golden is a reviewed artifact, never silently green.
3. Re-run clean: `./gradlew :packages:kotlin:ttrp-emit:test`.

The SQL is produced by `org.tatrman:ttr-translator` (Calcite-backed) via `TranslatorFacade`;
identifiers are double-quoted and deterministic. CTE references render bare (the emitter
registers CTE pseudo-tables under a sentinel namespace it strips post-unparse).
