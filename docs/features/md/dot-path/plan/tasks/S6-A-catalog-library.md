# S6-A — MemberCatalog library (ttr-metadata)

Goal: the member-catalog capability in `ttr-metadata` (MDS3): snapshot + fingerprint + index +
degradation ladder + the DB-backed source. May start any time after S2.

Prereq: S2-C (interfaces exist in ttr-md-resolver — **move** them here or depend? decision:
interfaces live in `ttr-md-resolver` (the consumer-facing contract); ttr-metadata implements —
keeps resolver dependency-light and metadata → resolver arrow one-way. Confirm at the
**optimizer-coordination review** (arc pre-flight) and record the outcome here before starting).
TDD: S6-A1–A3 (red) before S6-A4–A5.

## Tasks

- [ ] **S6-A0 — coordination review outcome.** One paragraph here: where the interfaces live,
  and that snapshot/fingerprint mechanics are shared with the optimizer's stats snapshot
  (same canonicalization helper if one exists in ttr-metadata — reuse `WorldFingerprint`'s
  canonical-JSON+sha256 pattern).
- [ ] **S6-A1 — red MemberSnapshotSpec.** Property tests mirroring `FingerprintSpec`:
  fingerprint insensitive to member insertion order; sensitive to any member add/remove/rename
  and to the domain set; spelled `sha256:<hex>`; `asof` carried; `domains()` = **published**
  domains only (S0's `publish: members` honored — unpublished domain in the model yields no
  index).
- [ ] **S6-A2 — red MemberIndexSpec.** `contains` exact (case-sensitive — members are data,
  D-note); `lookup(prefix, limit)` sorted + truncation; `count`; interning proof (two indexes
  over same strings share references — memory test can be a simple identity check); paging
  behavior at the source level (S6-A5) not here.
- [ ] **S6-A3 — red DegradationSpec.** GI-19 mirror: connected compile requested + catalog
  unavailable at pass start ⇒ hard error (the frontend maps it; library throws typed
  `CatalogUnavailable`); catalog lost mid-session ⇒ held snapshot continues + a
  `StaleSnapshot` signal the frontend maps to TTRP-MD-013.
- [ ] **S6-A4 — implement** `org.tatrman.ttr.metadata.members`: `InMemoryMemberIndex`
  (interned, sorted array + binary search), snapshot assembly + fingerprint, the degradation
  state machine on the registry/refresher pattern ttr-metadata already has (see
  `MetadataRefresherSpec` for the listener conventions).
- [ ] **S6-A5 — DB-backed member source.** `DbMemberSource`: for each published domain, locate
  the backing column via the domain's attribute's `md2db` binding (through ttr-semantics.md +
  the metadata registry), `SELECT DISTINCT` with keyset paging (page size configurable, default
  10 000), cache per (domain, table-state) — invalidation is refresh-driven, not TTL. JDBC only,
  through whatever connection provision ttr-metadata already uses for model-adjacent access; if
  none exists, take a `javax.sql.DataSource` parameter and keep provisioning the caller's
  problem (designer-server wires it in S6-B).
- [ ] **S6-A6 — green + gates.** `./gradlew :packages:kotlin:ttr-metadata:test` + Kotlin gate;
  ttr-metadata publishes locally. Commit `md-sugar S6A: MemberCatalog library`.

## Coder notes

_(empty — S6-A0 paragraph first)_

## References

- Contracts §7.1 · architecture MDS3 + risk table (paging/interning) ·
  `docs/ttr-metadata/architecture/contracts.md` (§5 fingerprint pattern, registry/refresher).
- Optimizer parallel: `docs/ttr-p/optimizer/architecture.md` (MetadataSource, GI-19 ladder).
