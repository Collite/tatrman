# SV-P1 · S1 — Gates 1+2: the tatrman 0.9.x line (translate.v1 goes public)

> Repo: **tatrman** (publisher) → **kantheon**, **tatrman-server** (consumers repoint). Pre-flight: S0·T1 (RO-13 — schemas frozen) + S0·T6 (branches folded; tag from `master` only). The `proteus.v1 → translate.v1` rename is **already in source** (S2 of SV-P0); the published `0.8.5` artifacts still carry `proteus.v1` — this stage publishes the renamed line as **0.9.0** and retires every `0.0.1-LOCAL`/`0.8.5` pin of plan-proto/translator. Mechanics reference: `tatrman/PUBLISHING.md` (tag-driven publishing, Gotcha 6).

- [x] **T1 — Persona gate for tatrman publishables (TDD: write the check first).** Add a grep-gate step to tatrman CI (`.github/workflows/ci.yml` or the publish workflow's first job) scanning `packages/kotlin/**` and `packages/python/**` sources (exclude `build/`, `README.md` history notes): pattern `ariadne|theseus|proteus|argos\b|kyklop|\barges\b|brontes|steropes|echo\b|kadmos|prometheus` case-insensitive, exclusions as in the S6 gate (`formerly`, CHANGELOG, monitoring). Expected: **green already** (S2 renamed the sources) — if it fires, stop ⚑. This is the N2 check from the naming ledger §4, now permanent.
- [x] **T2 — Reconcile PUBLISHING.md Gotcha 6 (the publish.yml race).** Trim `publish.yml`'s `else` branch back to the three-module bundle (`ttr-parser`, `ttr-writer`, `ttr-semantics`) so `kotlin-metadata/v*` is the **sole** metadata publisher — matching the table + header comment. Update PUBLISHING.md: delete Gotcha 6's interim guidance, state the rule ("one tag per module family"). *Why this direction: RO-24 wants independent artifact versioning after 1.0 — a five-module lockstep bundle fights that.*
- [x] **T3 — Audit the published inventory (no guessing).** Enumerate what actually exists on GH Packages `Collite/tatrman` (`gh api "/orgs/Collite/packages?package_type=maven"` + per-package versions, or the repo Packages tab) and PyPI (`pip index versions ttr-plan-proto ttr-parser`). Record in findings: current latest of parser/writer/semantics/metadata(-git)/plan-proto/translator + wheels. Confirm: plan-proto/translator latest = **0.8.5 carrying `org.tatrman.proteus.v1`** (unzip the jar: `unzip -l ttr-plan-proto-0.8.5.jar | grep -E 'proteus|translate'`).
- [x] **T4 — Cut `kotlin-translator/v0.9.0`. [GATE]** Preconditions: T1 gate green · RO-29 recorded (S0·T1) · tag from folded `master`. Publishes `ttr-plan-proto` + `ttr-translator` 0.9.0 (lockstep) with `org/tatrman/translate/v1/translator.proto`. After the workflow: download the published `ttr-plan-proto-0.9.0.jar` and verify `unzip -l` shows `org/tatrman/translate/v1/` and **no** `proteus` path; verify the bundled `.proto` resources (protoc include-path contract) match.
- [x] **T5 — Cut `python-plan/v0.9.0`. [GATE]** The wheel's pre-generated `*_pb2.py` must be regenerated from the renamed protos first — check `packages/python` (or wherever `publish-python.yml` builds from) contains `translate/v1` modules, regenerate if stale, then tag. Verify on PyPI: `pip download ttr-plan-proto==0.9.0 && unzip -l *.whl | grep -E 'proteus|translate'` → translate only.
- [x] **T6 — Metadata line decision + (if needed) cut `kotlin-metadata/v0.9.x`.** From T3's audit: if `ttr-metadata(-git)` latest is already ≥ 0.9.0 (the `kotlin/v0.9.x` bundle side-effect), gate 1 is satisfied — record and skip the tag. If latest is 0.8.6, cut `kotlin-metadata/v0.9.0` (post-T2 this publishes exactly metadata+git). Either way record the canonical 0.9.x version in findings — S4 and consumers use it.
- [x] **T7 — Repoint consumers, retire the local pins.** kantheon `gradle/libs.versions.toml`: `tatrman-translator 0.8.5 → 0.9.0`, `tatrman-ttr-metadata → <T6 version>`, modeler bump only if T3 found a newer bundle. tatrman-server `gradle/libs.versions.toml`: `ttr-plan-proto`/`ttr-translator` `0.0.1-LOCAL → 0.9.0` (delete the interim comment block), metadata likewise. Both repos: `./gradlew build` green **with `mavenLocal()` temporarily commented out for the org.tatrman group** — proving resolution from the registry. Branch `sv-p1-tatrman-gates` in each; Bora folds.
- [x] **T8 — Findings + register updates.** Update [`../plan.md`](../plan.md): mark gate 1 + gate 2 rows done (staging level; public = S4); note the query-translator vendoring exception (contracts §7) never materialized — the extraction was already complete, record as an OBSERVED delta. One line in `PUBLISHING.md` if tag families changed.

**Verify block:**
```bash
cd ~/Dev/collite-gh/tatrman && git tag | grep -E 'kotlin-translator/v0.9.0|python-plan/v0.9.0'
# consumers build clean off the registry (no mavenLocal for org.tatrman):
cd ~/Dev/collite-gh/kantheon && ./gradlew build -x test --refresh-dependencies
cd ~/Dev/collite-gh/tatrman-server && ./gradlew build -x test --refresh-dependencies
# no 0.0.1-LOCAL pins of tatrman-owned artifacts remain:
grep -rn "0.0.1-LOCAL" ~/Dev/collite-gh/kantheon/gradle/libs.versions.toml ~/Dev/collite-gh/tatrman-server/gradle/libs.versions.toml | grep -vE 'tatrman-server\s*=' # only the tatrman-server line may remain (retires in S2)
# published artifact carries no persona:
unzip -l ~/.gradle/caches/modules-2/files-2.1/org.tatrman/ttr-plan-proto/0.9.0/*/*.jar | grep -ci proteus   # 0
```

## Findings / ⚑

**Status (2026-07-11): branch `sv-p1-tatrman-gates` prepped — T1/T2/T3/T6 done; T4/T5 [GATE] await Bora's fold, then agent cuts tags (RO decision); T7/T8 follow the publish.**

### T1 — persona gate (DONE, green)
- Added a permanent `persona-gate` job to `.github/workflows/ci.yml` scanning `packages/kotlin` + `packages/python` (the N2 check, now enforcing).
- The gate fired on **15 historical provenance references** (all prose comments + 1 POM `description`; **zero wire/code identifiers**): `ttr-metadata` × Ariadne (13), `ttr-translator` × Brontes (2). Per Bora's call (2026-07-11) → **scrubbed to functional wording** (Ariadne → "the kantheon metadata service"; Brontes → "the MSSQL worker"). Gate now CLEAN; both packages compile.
- ⚑ **`echo` deliberately excluded from the tatrman gate** — the retired Echo persona is a *server* service (now `ttr-fuzzy` in tatrman-server); in tatrman publishables `echo` is only ttrp-cli's Clikt output primitive + generated bash. Documented in the workflow. tatrman-server's gate keeps `echo\b`.

### T2 — Gotcha 6 reconciled (DONE)
- `publish.yml`: trimmed the `else # kotlin/v*` branch to the **three grammar-toolchain modules** (`ttr-parser` + `ttr-writer` + `ttr-semantics`); `ttr-metadata(-git)` now has one publisher — `kotlin-metadata/v*`. Header comment updated.
- `PUBLISHING.md`: table row + Gotcha 6 rewritten to the rule ("one tag per module family", RO-24; never re-cut an existing `<family>/v<x.y.z>`).

### T3 — published-inventory audit (DONE; token lacks `read:packages`, so read from git tags — authoritative for what publish workflows ran)
| Artifact | Latest published | Wire state |
|---|---|---|
| `ttr-parser` / `ttr-writer` / `ttr-semantics` | **0.9.1** (`kotlin/v0.9.1`) | fine |
| `ttr-metadata(-git)` | **0.9.1** (rode `kotlin/v0.9.1`; also `kotlin-metadata/v0.9.0`) | fine — **gate 1 already satisfied** |
| `ttr-plan-proto` / `ttr-translator` | **0.8.5** (`kotlin-translator/v0.8.5`) | ⚠ **`proteus.v1`** — S1 replaces with 0.9.0 |
| `ttr-plan-proto` wheel (PyPI) | **0.8.4** (`python-plan/v0.8.4`) | ⚠ pre-rename — S1 replaces with 0.9.0 |

### T4/T5 — tag preconditions VERIFIED (ready to cut post-fold)
- Kotlin proto source is `org.tatrman.translate.v1` (dir `…/proto/org/tatrman/translate/v1`; **no `proteus` dir**). T4 (`kotlin-translator/v0.9.0`) will publish the renamed line.
- Python wheel: `hatch_build.py` regenerates `*_pb2.py` from the sibling Kotlin `.proto` at build time; **no committed proteus pb2** in `packages/python/ttr-plan-proto/src`. T5 (`python-plan/v0.9.0`) inherits `translate/v1` automatically — no manual regen needed.

### T6 — metadata line DECISION (DONE)
Gate 1 is **already satisfied**: `ttr-metadata(-git)` latest = **0.9.1**. **No `kotlin-metadata/v0.9.x` tag needed.** Canonical metadata version for S4 + consumers = **0.9.1**.

### T7/T8 — pending the publish
- T7 consumer repoints (kantheon + tatrman-server `libs.versions.toml`: translator/plan-proto → 0.9.0, metadata → 0.9.1, retire `0.0.1-LOCAL` for tatrman-owned) can only be verified *building off the registry* once T4/T5 tags publish → done post-fold.
- T8 register updates (plan.md gate 1/2 rows; query-translator vendoring exception never materialized — OBSERVED delta) → after the publish confirms.

---

## Post-publish verification (2026-07-11 — tags cut from master after Bora's fold)

**Both gates published + verified. RO-29 (plan-proto freeze) recorded before the cut.**

### T4 — `kotlin-translator/v0.9.0` ✅
Publish run success. Downloaded `ttr-plan-proto-0.9.0.jar` from GH Packages: **7 `org/tatrman/translate/v1` entries, 0 `proteus`**; bundled `.proto` resources include `org/tatrman/translate/v1/translator.proto` + `plan/v1/*`, `transdsl/v1`, `dfdsl/v1` (protoc include-path contract intact).

### T5 — `python-plan/v0.9.0` ✅ (approved through the `pypi` environment gate by Bora)
Wheel on PyPI. **0 `proteus`.** ⚑ **Observed vs the task's verify expectation:** the wheel's module set is **identical to 0.8.4** (`plan/{plan,context,parameters}`, `transdsl`, `dfdsl` — no `translate` module). `hatch_build.py` compiles an explicit 5-proto list that **never included `translator.proto`** — the translator proto is Kotlin-only in the plan wheel *by design*, so "translate only" was an over-assumption; the real gate (no persona) holds. `plan_pb2` grew 12281→12634 B = TableHint field-3 (SV-P0 S2). No regression.

### T6 — metadata ✅ skip-tag (gate 1 already satisfied)
`ttr-metadata(-git)` latest = **0.9.1** (`kotlin/v0.9.1` bundle + `kotlin-metadata/v0.9.0`). No tag cut. Canonical = 0.9.1.

### T7 — consumers repointed (gate-2 pins retired) ✅ / metadata+modeler hygiene DEFERRED
- **tatrman-server** (`sv-p1-tatrman-gates`, `15c6351`): `ttr-plan-proto`/`ttr-translator` `0.0.1-LOCAL → 0.9.0`. Verified `:services:ttr-translate:compileKotlin` green against 0.9.0 pulled fresh (`--refresh-dependencies`; 0.9.0 not in mavenLocal → proves registry resolution).
- **kantheon** (`sv-p1-tatrman-gates`, `7cdd5da`): `tatrman-translator 0.8.5 → 0.9.0` + `shared/proto` Python pin `ttr-plan-proto 0.8.4 → 0.9.0`. Zero `proteus.v1`/`translate.v1` Kotlin imports; only consumer is `shared/proto`'s plan.v1 wire (additive TableHint). Verified `:shared:proto:build` green (incl. duplicate-class guard).
- ⚑ **DEFERRED to S2** (batches with the server-lib repoint that already touches these files): `tatrman-ttr-metadata 0.8.6 → 0.9.1` and `tatrman-modeler 0.8.6 → 0.9.1` (parser/writer/semantics) in both repos. Not gate-blocking — gate 1 is satisfied by *publication*; 0.8.6 still resolves. The **full "build off registry with mavenLocal commented out" verify is BLOCKED on S2** — kantheon/tatrman-server still consume the server libs at `0.0.1-LOCAL` from mavenLocal until gate 3 publishes them (SV-P0 review ⚑5, the known interim). Local builds pass using mavenLocal for the server libs.

### Branches for the fold
- tatrman `sv-p1-s1-followup` (RO-29 + this doc + plan.md gate status) · tatrman-server `sv-p1-tatrman-gates` · kantheon `sv-p1-tatrman-gates`.
