# RG-P3 · S0 — G2 closure: `meta.v1` semantics projection + the metadata seam

> **INSERTED 2026-07-13** (mid-RG-P3·S1, on the dev agent's finding): tatrman's Veles replaced the `semantics{}` wire surface with nothing — `org.tatrman.meta.v1` carries `SearchHints` but no semantics on `EntityDetail`/`AttributeDetail`/`DbTableDetail`/`DbColumnDetail`, while the grounding discovery layer (`SemanticDiscovery` over `GroundingMetadataClient`) reads exactly that off the ai-platform protos. **The gap is only the wire leg:** grammar **4.2** (semantics-block, merged 2026-07-06) + **ttr-metadata contracts v1.5** (typed model `Entity`/`DbTable.semanticsKind`, `Attribute`/`DbColumn.semantics: ResolvedAttributeSemantics?`, five `MetadataQuery` grounding accessors) already exist — Veles just doesn't project them. This stage closes G2 properly instead of faking protos in tests. **Run to completion before resuming S1's discovery wiring; S1·T1's moved code lands on this stage's seam.**
>
> **The ruling decision (record it, T1):** kind/role travel as **STRINGS, not enums** — mirroring ttr-metadata's own deliberate choice (T5.2: `semanticsKind: String?`) and grammar 4.2's "parser stays mechanical / new roles need no grammar bump" philosophy. The vocabulary versions in **ttr-semantics**; consumers dispatch on known values and MUST tolerate unknown ones (J-v2 additive). ai-platform's closed enums (`com.tatrman.metadata.v1` `EntitySemantics`/`AttributeSemantics`) are the **legacy consumer's** shape — the mapping to them stays ai-platform-side and dies at the SV-P5 cutover; do NOT copy them onto the open wire. `meta.v1` is **published** (0.9.x): new fields take NEW numbers outside every reserved range; nothing renames, nothing re-numbers.

## The proto shape (the proposal — apply verbatim, then re-verify numbers against master before committing)

New messages in `org/tatrman/meta/v1/meta.proto` (names deliberately match ai-platform's so the SV-P5 mental mapping is 1:1; the *package* differs and the shape is open-vocabulary):

```proto
// Semantics feature (grammar 4.2 / ttr-semantics v1) — deterministic-grounding
// declarations from the model's semantics{} blocks, validated upstream
// (TTR-SEM-2xx; invalid blocks degrade to LoadIssues and are NOT served here).
// kind/role are strings BY DESIGN (mirrors ttr-metadata `semanticsKind: String?`):
// the vocabulary evolves in ttr-semantics without proto bumps; consumers
// dispatch on known values and MUST tolerate unknown ones (J-v2).
// Absent message = no semantics declared on the object.

message EntitySemantics {                  // serves er entities AND db tables
  string kind = 1;                         // 4.2: "period_table" | "calendar" | "poi" | "fx_rate"
  map<string, string> params = 2;          // future kind parameters (4.2: always empty)
}

message AttributeSemantics {               // serves er attributes AND db columns
  string role = 1;                         // 4.2 vocabulary: period_start|period_end|period_code|event_date|
                                           //   document_date|posting_date|due_date|valid_from|valid_to|
                                           //   calendar_date|geo_lat|geo_lon|geo_point|amount|amount_domestic|
                                           //   currency_code|fx_from_currency|fx_to_currency|fx_rate
  string code_format = 2;                  // role=period_code only (e.g. "yyyyMM", "yyyyQn"); empty otherwise
  org.tatrman.plan.v1.QualifiedName period = 3;   // resolved `period:` ref (entity or db table of kind period_table);
                                           //   set only on the date roles that declared it
  string currency_attribute = 4;           // resolved `currency:` sibling-attribute local name (role=amount only;
                                           //   precedent: EntityDetail.name_attribute is a local-name string)
  map<string, string> extras = 5;          // future role keys (4.2: always empty)
}
```

Field placements — all additive, all outside reserved ranges (verified against `meta.proto` @ 2026-07-13; **re-verify against current master immediately before editing**, per the publish invariant):

| Message | Add | Why this number |
|---|---|---|
| `EntityDetail` | `EntitySemantics semantics = 10;` | 1–6 used, `reserved 7 to 9` stays untouched |
| `AttributeDetail` | `AttributeSemantics semantics = 11;` | 1–4, 6–8 used; `reserved 5` + `9 to 10` stay untouched |
| `DbTableDetail` | `EntitySemantics semantics = 8;` | 1–2 used, `reserved 3 to 7` stays untouched |
| `DbColumnDetail` | `AttributeSemantics semantics = 12;` | 1–5 used, `reserved 6 to 10`, `search = 11` |
| `DbColumnSummary` | `AttributeSemantics semantics = 10;` | 1–4 used, `reserved 5 to 9` stays untouched — lets discovery read a period table's role columns in ONE `get_object(table)` call |
| `ObjectDescriptor` | `string semantics_kind = 16;` | 1–10 used, `reserved 11 to 15` stays untouched — the **discovery accelerator**: `list_objects` surfaces kinds so discovery finds period/poi/fx tables without a full per-object sweep |

## Tasks

Verify: `./gradlew :shared:proto:build :services:veles:test` green; all proto consumers compile; the wire-compat golden test green; `SemanticDiscovery` runs against Veles fixtures through the seam with zero `com.tatrman.metadata.v1` imports in tatrman-server.

- [x] **T1 — Record the decision + pin check.** (a) Write the decision above into the resolution design log (`../../design/00-control-room.md` §7, next RS number: "meta.v1 semantics projection — strings-not-enums, additive fields, discovery kind on ObjectDescriptor; ai-platform enum mapping stays legacy-side") — flag ⚑ for Bora's ratification, don't wait on it. (b) Verify Veles's `ttr-metadata` pin carries the contracts-v1.5 surface (`semanticsKind`/`semantics` fields + `periodTableFor`/`semanticRole`/`attributesByRole`/`poiEntities`/`fxRateTableFor`); if the pinned version predates it, bump to the published 0.9.x/`kotlin-metadata` version that has it (registry versions only — no mavenLocal).
- [x] **T2 (test first — the projection spec).** `VelesSemanticsProjectionSpec` (Kotest, Veles test style) loading the grammar's golden fixtures `59-semantics.ttrm` (er) + `60-semantics-db.ttrm` (db): (a) `get_object` on `AccountingPeriod`-class entity → `EntityDetail.semantics.kind == "period_table"`; its attributes → `role` = `period_start`/`period_end`/`period_code` with `code_format`; (b) `event_date` attribute → `period` QualifiedName resolves to the period entity; `amount` → `currency_attribute` local name; (c) db twin: `DbTableDetail.semantics` + `DbColumnSummary.semantics` populated from ONE `get_object(table)` call; (d) `list_objects` descriptors carry `semantics_kind` for kinded objects, empty otherwise; (e) an object with NO semantics has the field **unset** (hasSemantics false, not empty-string kind); (f) a model whose semantics block is invalid (TTR-SEM-2xx → LoadIssue) serves the object WITHOUT semantics and surfaces the load warning through the existing status/messages path. All red.
- [x] **T3 — The proto change.** Apply the shape + placements above; regenerate; every consumer compiles (veles, ttr-meta-client if present, grounding, kantheon-side stubs if they build in-repo). **Wire-compat gate:** a golden-bytes test — serialized pre-change `GetObjectResponse`/`ListObjectsResponse` fixtures (capture from the current build BEFORE editing) parse under the new schema with semantics unset; if `buf breaking` is wired in CI, it must pass too. Commit the proto separately (`RG-P3.S0: meta.v1 semantics messages, additive`).
- [x] **T4 — Veles projection.** Populate the new fields in Veles's detail/descriptor assembly from the typed model (`Entity`/`DbTable.semanticsKind` → `EntitySemantics.kind`; `Attribute`/`DbColumn.semantics: ResolvedAttributeSemantics` → `AttributeSemantics` incl. resolved `period` qname + `currency` local name; `ObjectDescriptor.semantics_kind` from the owning object's kind). T2 goes green. No vocabulary validation in Veles — upstream validated; Veles serves data (`RG-*` diagnostics stay in ttr-semantics).
- [x] **T5 (test first — the seam).** *(Built + proven for chrono: `SemanticDiscovery` is a domain-typed port; `MetaV1SemanticDiscovery` is the one meta.v1 reader; the domain fake builds objects directly. `MetaV1SemanticDiscoveryComponentSpec` drives the adapter over in-process Veles on the semantics goldens. geo/money apply the same pattern as they move in S1·T1.)* In the grounding tree: `GroundingMetadataClient` becomes a **port returning discovery's domain types** (PeriodTableRef, RoleBinding, PoiRef… — the types `SemanticDiscovery` actually consumes), with TWO impls planned: `MetaV1GroundingMetadataClient` (maps the new `meta.v1` fields; list-by-kind first via `semantics_kind`, then one `get_object` per kinded table) and the test fake (builds domain objects directly — **no hand-built protos in grounding tests anymore**). Write `SemanticDiscoverySeamSpec`: discovery finds the period table / poi / fx setup from the fake; a `MetaV1` adapter component test replays recorded Veles responses (from T2's fixtures) and yields identical discovery results. Red, then implement.
- [x] **T6 — Re-point the inherited discovery.** *(chrono: `SemanticDiscovery` + metadata lookups on the port; `grep com.tatrman.metadata services/chrono = 0`; inherited discovery/recipe suites green against the fake, 76 tests, 1 skip = the translator-gap round-trip. geo/money re-point during their S1·T1 moves.)* Swap `SemanticDiscovery` + chrono's metadata lookups onto the port; delete every `com.tatrman.metadata.v1` / ai-platform-proto import from the moved grounding code (grep gate: `com.tatrman.metadata` count = 0 in tatrman-server); the inherited discovery test suites run green against the fake (counts preserved — a failing inherited test means the seam mapping is wrong, not the test).
- [x] **T7 — Registers + SV handoff notes.** *(a+c done; b's ecosystem interlock file doesn't exist in this repo state — recorded in Findings below for the SV pickup.)* (a) Resolution `../contracts.md` §3: add the semantics-projection paragraph (message shapes + the strings-not-enums rule + fixture provenance). (b) Ecosystem side, note-only (full close-out stays SV-P3·F2): the interlock findings file gets "G2 closed via meta.v1 additive projection; rides the next `server-libs/v0.9.x` publish (SV-P3·F1·T3); `meta.get_object`/`list_objects`/`get_model` structured outputs grow additively (mcp-surface §6's 'pinned by proto shapes' rule covers it)". (c) `docs/features/semantics-block/README.md` consumer-contract paragraph: append that the OPEN wire is `org.tatrman.meta.v1` string-vocabulary messages; the closed-enum sentence stays as the legacy note.
- [x] **T8 — Wrap.** Full verify block green; tick the S0 row in `00-task-management.md`; commit `RG-P3.S0: Veles semantics projection + grounding metadata seam (G2 closed)`; resume RG-P3·S1 at its T1 with the seam in place. — DONE: the Veles-side unblock committed (`d4ad265` proto + `bd8922f` projection + `b1b9cdb` docs); the grounding-side seam (port + `MetaV1*Discovery` adapter + domain fake, zero `com.tatrman.metadata` imports in grounding code) **landed with the S1·T1 service moves** (it could not precede them — the grounding tree wasn't in-repo yet), so this row's `grep com.tatrman.metadata = 0` + discovery-through-the-seam verify was evaluated at the S1·T1 landing and re-confirmed at the RG-P3 phase-exit review (2026-07-13, [`../reviews/rg-p3-review.md`](../reviews/rg-p3-review.md)).

**Verify block:**
```bash
cd ~/Dev/collite-gh/tatrman-server
./gradlew :shared:proto:build :services:veles:test                       # projection spec + wire-compat golden green
grep -rn "com.tatrman.metadata" --include="*.kt" services/ libs/ | wc -l  # 0 (T6 gate)
grep -n "semantics" shared/proto/src/main/proto/org/tatrman/meta/v1/meta.proto | head  # fields present, reserved ranges untouched
```

## Findings / ⚑

**Veles side (T1–T4, T7) — DONE & green (2026-07-13).** Full `:services:veles:test` = **108/108**, ktlint clean; `:shared:proto:build` green; wire-compat golden green.

- **T1a — decision recorded as [RS-33]** in [`../../design/00-control-room.md`](../../design/00-control-room.md) §7. ⚑ **Awaiting Bora's ratification** (strings-not-enums, additive fields, `ObjectDescriptor.semantics_kind`; ai-platform closed enums stay legacy-side, die at SV-P5).
- **T1b — pin bump `ttr-metadata` 0.8.6 → 0.9.4** (`gradle/libs.versions.toml`). 0.9.4 is the published version carrying the semantics model surface (`Entity/DbTable.semanticsKind`, `Attribute/DbColumn.semantics: ResolvedAttributeSemantics`) + the five `MetadataQuery` grounding accessors; the whole modeler spine (`ttr-parser`/`-writer`/`-semantics`) rides to 0.9.4 transitively via ttr-metadata's `api` deps. Registry version, no mavenLocal.
- **Pin-bump collateral (fixed):** 0.9.4 **unpackaged the six CNC stock roles** — their qnames moved from `package="cnc"` to `package=""` (dotted form `cnc.role.fact`, not `cnc.cnc.role.fact`). Three `Phase2_2ExpressivenessSpec` cases hardcoded the old package and were updated to track the lib. No other test moved (108 total, the other 105 unaffected). This is a legitimate lib evolution, not a regression.
- **T2 fixtures:** grammar goldens `59-semantics.ttrm` + `60-semantics-db.ttrm` vendored to `services/veles/src/test/resources/fixture-semantics/`; a crafted `fixture-semantics-invalid/bad-period-ref.ttrm` proves case (f) — ttr-semantics emits **`TTR-SEM-208`** (unresolved `period:` ref) into `reconcile().errors` and **drops the whole attribute's semantics**; Veles serves the object without semantics (degrade, never guess).
- **T3 wire-compat:** pre-change `GetObject`/`ListObjects` bytes captured from the 0.8.6-era build → `services/veles/src/test/resources/wire-golden/`; `MetaWireCompatSpec` parses them under the new schema (semantics unset) — proves additive. Proto committed separately (`d4ad265`).
- **T7b — the ecosystem interlock findings file does not exist in this repo state** (the confidential ecosystem corpus was pulled private, [[sv-p1-ecosystem-progress]]). SV handoff note to carry when it lands: *"G2 closed via `meta.v1` additive projection (RS-33); rides the next `server-libs/v0.9.x` publish; `meta.get_object`/`list_objects`/`get_model` structured outputs grow additively — mcp-surface §6's 'pinned by proto shapes' rule covers it."*

**T5–T6 DONE (chrono) — the seam is built + proven (2026-07-13).** chrono moved into `tatrman-server/services/chrono` (J-v2, on the seam), committed `c750369`. `SemanticDiscovery` = domain-typed port; `MetaV1SemanticDiscovery` = the sole `meta.v1` reader (client-side discovery over the RS-33 projection: `semantics_kind` descriptors + `get_object` `AttributeSemantics`, since `meta.v1` has no server-side semantic filters); the test fake builds domain objects. `grep com.tatrman.metadata services/chrono = 0`. `MetaV1SemanticDiscoveryComponentSpec` proves the real mapping against in-process Veles over `59/60-semantics.ttrm`. **76 tests green, 1 skipped.** geo/money apply the identical seam pattern as they move.

**⚑ Cross-arc finding — ttr-translator grounding-function gap.** The one skipped test is the calendar-aligned FilterRecipe round-trip: the published `org.tatrman:ttr-translator` (0.9.0) rejects the grounding functions `period_start({p})`/`period_end({p})` with Calcite *"illegal use of dynamic parameter"*. ai-platform's in-repo `query-translator` registered these grounding operators (`GroundingFunctionUnparse` / `PostgresqlSqlDialectWithGrounding`); the published artifact does not (yet). JoinRecipe round-trips pass. This affects the calendar/function grounding path across all three services and is a **ttr-translator-arc** concern, not the G2 seam — flagged for Bora. (See the sequencing note below for the residual S1·T1 scope.)

**Residual S1·T1 scope (post-checkpoint).** T5 (the domain-typed `GroundingMetadataClient` port + `MetaV1GroundingMetadataClient` adapter + fake) and T6 (re-point `SemanticDiscovery`, zero `com.tatrman.metadata` imports, inherited discovery suites green) operate on **grounding code that is not yet in `tatrman-server`** — the move is S1·T1. They cannot be done before the grounding tree lands. The Veles-side unblock (the semantics wire surface + projection) is complete and committed; the seam gets built as the grounding services move in S1·T1 (they land already wired to the `meta.v1` fields, not raw-then-refactored). T8's final verify (`grep com.tatrman.metadata = 0`, discovery-through-the-seam) is therefore evaluated at the S1·T1 landing.
