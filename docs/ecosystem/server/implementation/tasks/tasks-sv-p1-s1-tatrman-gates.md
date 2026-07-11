# SV-P1 · S1 — Gates 1+2: the tatrman 0.9.x line (translate.v1 goes public)

> Repo: **tatrman** (publisher) → **kantheon**, **tatrman-server** (consumers repoint). Pre-flight: S0·T1 (RO-13 — schemas frozen) + S0·T6 (branches folded; tag from `master` only). The `proteus.v1 → translate.v1` rename is **already in source** (S2 of SV-P0); the published `0.8.5` artifacts still carry `proteus.v1` — this stage publishes the renamed line as **0.9.0** and retires every `0.0.1-LOCAL`/`0.8.5` pin of plan-proto/translator. Mechanics reference: `tatrman/PUBLISHING.md` (tag-driven publishing, Gotcha 6).

- [ ] **T1 — Persona gate for tatrman publishables (TDD: write the check first).** Add a grep-gate step to tatrman CI (`.github/workflows/ci.yml` or the publish workflow's first job) scanning `packages/kotlin/**` and `packages/python/**` sources (exclude `build/`, `README.md` history notes): pattern `ariadne|theseus|proteus|argos\b|kyklop|\barges\b|brontes|steropes|echo\b|kadmos|prometheus` case-insensitive, exclusions as in the S6 gate (`formerly`, CHANGELOG, monitoring). Expected: **green already** (S2 renamed the sources) — if it fires, stop ⚑. This is the N2 check from the naming ledger §4, now permanent.
- [ ] **T2 — Reconcile PUBLISHING.md Gotcha 6 (the publish.yml race).** Trim `publish.yml`'s `else` branch back to the three-module bundle (`ttr-parser`, `ttr-writer`, `ttr-semantics`) so `kotlin-metadata/v*` is the **sole** metadata publisher — matching the table + header comment. Update PUBLISHING.md: delete Gotcha 6's interim guidance, state the rule ("one tag per module family"). *Why this direction: RO-24 wants independent artifact versioning after 1.0 — a five-module lockstep bundle fights that.*
- [ ] **T3 — Audit the published inventory (no guessing).** Enumerate what actually exists on GH Packages `Collite/tatrman` (`gh api "/orgs/Collite/packages?package_type=maven"` + per-package versions, or the repo Packages tab) and PyPI (`pip index versions ttr-plan-proto ttr-parser`). Record in findings: current latest of parser/writer/semantics/metadata(-git)/plan-proto/translator + wheels. Confirm: plan-proto/translator latest = **0.8.5 carrying `org.tatrman.proteus.v1`** (unzip the jar: `unzip -l ttr-plan-proto-0.8.5.jar | grep -E 'proteus|translate'`).
- [ ] **T4 — Cut `kotlin-translator/v0.9.0`. [GATE]** Preconditions: T1 gate green · RO-29 recorded (S0·T1) · tag from folded `master`. Publishes `ttr-plan-proto` + `ttr-translator` 0.9.0 (lockstep) with `org/tatrman/translate/v1/translator.proto`. After the workflow: download the published `ttr-plan-proto-0.9.0.jar` and verify `unzip -l` shows `org/tatrman/translate/v1/` and **no** `proteus` path; verify the bundled `.proto` resources (protoc include-path contract) match.
- [ ] **T5 — Cut `python-plan/v0.9.0`. [GATE]** The wheel's pre-generated `*_pb2.py` must be regenerated from the renamed protos first — check `packages/python` (or wherever `publish-python.yml` builds from) contains `translate/v1` modules, regenerate if stale, then tag. Verify on PyPI: `pip download ttr-plan-proto==0.9.0 && unzip -l *.whl | grep -E 'proteus|translate'` → translate only.
- [ ] **T6 — Metadata line decision + (if needed) cut `kotlin-metadata/v0.9.x`.** From T3's audit: if `ttr-metadata(-git)` latest is already ≥ 0.9.0 (the `kotlin/v0.9.x` bundle side-effect), gate 1 is satisfied — record and skip the tag. If latest is 0.8.6, cut `kotlin-metadata/v0.9.0` (post-T2 this publishes exactly metadata+git). Either way record the canonical 0.9.x version in findings — S4 and consumers use it.
- [ ] **T7 — Repoint consumers, retire the local pins.** kantheon `gradle/libs.versions.toml`: `tatrman-translator 0.8.5 → 0.9.0`, `tatrman-ttr-metadata → <T6 version>`, modeler bump only if T3 found a newer bundle. tatrman-server `gradle/libs.versions.toml`: `ttr-plan-proto`/`ttr-translator` `0.0.1-LOCAL → 0.9.0` (delete the interim comment block), metadata likewise. Both repos: `./gradlew build` green **with `mavenLocal()` temporarily commented out for the org.tatrman group** — proving resolution from the registry. Branch `sv-p1-tatrman-gates` in each; Bora folds.
- [ ] **T8 — Findings + register updates.** Update [`../plan.md`](../plan.md): mark gate 1 + gate 2 rows done (staging level; public = S4); note the query-translator vendoring exception (contracts §7) never materialized — the extraction was already complete, record as an OBSERVED delta. One line in `PUBLISHING.md` if tag families changed.

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

*(published-inventory table from T3 · canonical metadata version from T6 · any ⚑)*
