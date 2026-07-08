# S6-B — ttrm/* protocol & compile-path client

Goal: serve members over the designer-server's WS JSON-RPC (`ttrm/getMemberDomains`,
`ttrm/getMembers`), and close the loop: a connected compile resolves bare members end to end;
the manifest records the snapshot fingerprint.

Prereq: S6-A; S3-A (frontend snapshot injection point). TDD: S6-B1 (red) before S6-B2–B4.

## Tasks

- [ ] **S6-B1 — red server integration spec.** In ttr-designer-server's existing WS-spec
  harness, over a fixture repo (sales-model + a PG testcontainer or the harness's existing DB
  provisioning — reuse, don't invent): `ttrm/getMemberDomains` returns exactly the published
  domains with counts + fingerprints; `ttrm/getMembers` — plain page, `prefix` filter, cursor
  paging past one page (set page size low in the spec), stable `fingerprint` across pages;
  unpublished domain → the server's established JSON-RPC not-found error shape; DTO field-name
  pinning test (serialization golden — public protocol from v1).
- [ ] **S6-B2 — implement handlers** following the existing `ttrm/*` handler pattern (read-only,
  registered beside `getModelIndex` etc.), wiring `DbMemberSource` with the server's DataSource;
  update the `ttrm` protocol handshake/version per the server's established evolution rule
  (batch/version notes in ttr-metadata contracts §4).
- [ ] **S6-B3 — compile-path client.** Connected mode in ttrp-frontend/ttrp-cli: a
  `WsMemberCatalog` client (same WS client stack the repo already uses designer-side — if none
  exists Kotlin-side, a minimal Ktor client here is acceptable, it lives outside
  ttr-md-resolver) filling S3-A3's injection point; mode selection follows the compiler's
  existing connected/serverless switch (align flag names with the optimizer's — coordination
  review). Snapshot taken **once per pass** (contracts §7.1).
- [ ] **S6-B4 — E2E connected/disconnected spec.** Same `.ttrp` program: connected → bare
  `Kaufland` resolves (S4-B pipeline green with server-sourced members); disconnected → MD-007
  for the bare token, and the `customer.Kaufland` variant compiles with `deferred` + bind-time
  guard. Manifest now records real `memberFingerprint` + `mdAsof` (replaces S4-B5 placeholder).
- [ ] **S6-B5 — degradation E2E.** Kill the server mid-session in the spec → held snapshot +
  TTRP-MD-013 surfaces; fresh pass with server down + connected requested → hard error.
- [ ] **S6-B5b — materialization status (D22, contracts §7.2).** Red first:
  `ttrm/getMaterializationStatus` spec — a bound cubelet whose table exists and matches →
  `materialized`; binding present, table absent (a freshly `:=`-generated `.ttrm` before its
  first run) → `declared-only`; table present with a missing/mistyped column vs the binding →
  `drifted` + `detail`. Implement by comparing the registry's bindings against the DB catalog
  (`information_schema` via the server's DataSource); per-cubelet filter param; DTO pinning
  test. (If S5C hasn't merged yet, the generated-`.ttrm` case uses a hand-written fixture — the
  method has no code dependency on S5C.)
- [ ] **S6-B6 — docs + gates.** ttr-metadata contracts §4 changelog entry for the two methods;
  both domains' gates green. Commit `md-sugar S6B: member protocol + connected compile`.

## Coder notes

_(empty)_

## References

- Contracts §7.2 (DTOs normative) · ttr-metadata architecture §"designer-server" (MD6 thin-DTO
  philosophy, protocolVersion evolution) · S3-A3 injection point.
