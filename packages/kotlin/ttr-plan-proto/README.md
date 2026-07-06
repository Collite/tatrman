# ttr-plan-proto

Canonical **wire formats** for the TTR-P plan pipeline: `plan.v1` (the RelOp plan
tree — `PlanNode`, `Expression`, `AggregateCall`, `SchemaCode`, …), `transdsl.v1`
(Transformation DSL AST), and `dfdsl.v1` (DataFrame DSL pipeline). Published as
`org.tatrman:ttr-plan-proto` (Maven) and the `ttr-plan-proto` PyPI wheel.

## Canonical ownership

As of this arc, **these `.proto` files are the source of truth** — tatrman owns the
plan wire format (decision TR-3, S25 finalized as ownership transfer). They were
formerly generated inside kantheon `shared/proto`; **transferred byte-identical from
`Collite/kantheon` @ `f2e2efb` on 2026-07-06.** Proto `package` / `java_package` stay
exactly `org.tatrman.plan.v1` etc., so every downstream consumer's generated FQCNs
are unchanged — kantheon switched classpath source, not imports.

Consumers: TTR-P `ttrp-emit` (island → RelNode → `plan.v1` payloads) and kantheon's
Proteus / Ariadne (via the published artifact).

## What ships

- Generated Kotlin/Java message classes (message-only — no `service` blocks, no gRPC).
- **The `.proto` files themselves, bundled as jar resources** (the import-path contract,
  `docs/ttr-translator/architecture/contracts.md` §4.1): kantheon's protoc include path
  extracts them from the dependency jar without re-generating. The `verifyProtosInJar`
  Gradle check guards that all 5 stay in the jar.

## Governance

Proto changes are driven by kantheon runtime needs (typically `PipelineContext` — user,
roles, `SchemaCode`); they arrive as PRs here, and this repo cuts a prompt lockstep
`kotlin-translator/v*` release. Field numbers/types are **append-only within `v1`**
(contracts §2); a breaking wire change requires a `v2` package and a major bump.

See `docs/ttr-translator/` for the full arc (architecture, contracts, plan).
