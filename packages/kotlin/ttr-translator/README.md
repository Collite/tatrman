# ttr-translator

The **TTR-P translation core**: island ↔ RelNode ↔ SQL / `plan.v1` payloads, backed by
Apache Calcite. This is the "Proteus translation core" of TTR-P decision **E-a α′** —
a Kotlin library the PL compiler (`ttrp-emit`) embeds offline/in-process, and that
kantheon's Proteus consumes as a thin gRPC wrapper.

## Provenance

Extracted **whole** (decision TR-1) from kantheon `shared/libs/kotlin/query-translator`
@ `f2e2efb` (2026-07-06). No behavioral change rode the move (TR-7): the diff vs.
kantheon is **package names + build wiring only** — proven by a test-name parity check
(34 specs / 359 tests, identical modulo package prefix).

**Package map (TR-2):** `shared.translator.*` → `org.tatrman.translator.*`. Source dirs
normalized to match packages (kantheon kept sources under `src/main/kotlin/shared/translator/`;
that quirk is dropped). Subpackages preserved: `codec/{sql,transdsl,dfdsl}`, `detect`,
`dialects`, `framework`, `joiner`, `orchestrator`, `params`, `schema`, `suggest`, `wire`.

Two wire-adjacent symbols the lib compiles against — the `org.tatrman.proteus.v1`
`Language`/`SqlDialect` enums and `org.tatrman.plan.v1.{parseSchemaCode,schemaCodeToToken}` —
travelled into the sibling **`ttr-plan-proto`** artifact (blocker A2-1, FQCNs unchanged).

## API surface

Package root `org.tatrman.translator` — see
`docs/ttr-translator/architecture/contracts.md` §3 for the full entry-point map
(`orchestrator.Translator`, `framework.{TranslatorFramework, ModelHandle SPI}`,
`codec.*`, `wire.{PlanNodeEncoder,PlanNodeDecoder}`, `dialects`, `schema`, `params`,
`detect`/`suggest`). `InMemoryModelHandle` (the `ModelHandle` SPI test double) ships
via `java-test-fixtures` so consumers can test against the SPI without a real model.

- **Calcite is an `api` dependency** — RelNode types appear in signatures. Consumers that
  must stay Calcite-free (e.g. `ttrp-emit` outside its `TranslatorFacade`) enforce that on
  their side (the facade is the only class importing `org.tatrman.translator.*`;
  see TTR-P `tasks-p3-s3.1` T3.1.1/T3.1.7 for the Calcite-engagement canon).
- The test JVM pins the Calcite default charset to UTF-8 (see `build.gradle.kts`) — carried
  from the source lib to keep Unicode-literal coverage deterministic.

Published as `org.tatrman:ttr-translator`, lockstep with `ttr-plan-proto` under the
`kotlin-translator/v*` tag. See `docs/ttr-translator/` for the full arc.
