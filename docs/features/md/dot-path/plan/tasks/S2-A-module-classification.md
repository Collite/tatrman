# S2-A — resolver module scaffold, classification, qualified pairs

Goal: stand up `packages/kotlin/ttr-md-resolver` (MDS1) with the contract DTOs, token
classification (R5), qualified pairs (R6), and star/set/range binding (R7).

Prereq: S0-B, S1-B. TDD: S2-A3–A4 (red) before S2-A5–A6.

## Tasks

- [ ] **S2-A1 — module scaffold.** `packages/kotlin/ttr-md-resolver/` mirroring
  `ttr-writer/build.gradle.kts` (Kotlin JVM, Kotest, publishing block →
  `org.tatrman:ttr-md-resolver`, `java-test-fixtures`). Deps: `ttr-semantics`, `ttr-parser`,
  `kotlinx-serialization-json`. **Forbidden deps** (assert in a small ArchSpec or a comment +
  review check): Ktor, MCP SDK, Calcite. Register in `settings.gradle.kts`.
- [ ] **S2-A2 — contract DTOs.** Package `org.tatrman.ttr.md.resolve`: everything in contracts §3
  verbatim (`Selector`, `MemberRef`, `Coordinate`, `CanonicalPath`, `PathShape`, `Explanation`,
  `ExplainStep`, `ResolutionOutcome`, `MdDiagnostic` + `MdDiagId` enum TTRP-MD-001…014 with the
  §6 texts), all `@Serializable`. Plus the inputs: `PathComponent` sealed type (Ident, IntLit,
  Quoted, SetLit, RangeLit, Star, Pair) built from the parser's `mdPath` AST, `MemberCatalog` /
  `MemberSnapshot` / `MemberIndex` interfaces (contracts §7.1) and
  `InMemoryMemberSnapshot` in testFixtures (domain → sorted member list; fingerprint =
  sha256 of content).
- [ ] **S2-A3 — red ClassificationSpec.** Table-driven over the S1-A1 fixture + an
  InMemoryMemberSnapshot (members: customers `Kaufland`, `Lidl`, `"Kaufland K123"`; years/months
  as numeric-domain members): each R5 case — `sum`→agg, `sales`→cubelet, `net`→measure,
  `region`→attribute, `lastMonth`→calc, `Kaufland`→member(customer.name), `2025`→member(time
  numeric domains only), a name that is both measure and member → **both** candidates (search
  disambiguates later, R9), zero-candidate token → MD-001 with the token echoed.
- [ ] **S2-A4 — red QualifiedPairSpec + StarSetRangeSpec.** R6: `customer.Kaufland`,
  `month.*`, `name.{Kaufland, Lidl}` bind atomically and are order-free within the path
  (permutation cases); pair whose first part is no attribute/dimension → falls back to plain
  components. R7: unqualified `{Kaufland, Lidl}` binds via unique common attribute; mixed-set
  `{Kaufland, 2025}` → MD-004; unqualified bare `*` → MD-004; `2024..2026` binds on the ordered
  domain; range over unordered domain → MD-004.
- [ ] **S2-A5 — implement classification** (`TokenClassifier`): pure function
  `(PathComponent, MdModel, MemberSnapshot?) → Set<SlotCandidate>` per R5's candidacy list;
  member index consulted only when snapshot present (disconnected candidacy is structural only —
  R13 enforcement lands in S2-C, but the classifier must already distinguish).
- [ ] **S2-A6 — implement pair/star/set/range binding** (`PairBinder`): consumes components,
  emits partially-bound `Coordinate`s + remaining free components for the search.
- [ ] **S2-A7 — green + gates.** Specs green; `./gradlew :packages:kotlin:ttr-md-resolver:test`
  + full Kotlin gate; publishes to mavenLocal. Commit
  `md-sugar S2A: ttr-md-resolver scaffold + classification + pairs`.

## Coder notes

_(empty)_

## References

- Contracts §2 (R5–R7), §3 (DTOs — field names are a public contract), §6 (diag texts).
- Fixture: S1-A1 shared model; extend its testFixtures with the member lists (do not fork it).
