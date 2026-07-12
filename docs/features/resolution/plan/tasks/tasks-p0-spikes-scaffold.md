# RG-P0 — Spikes + scaffold

> Pre-flight: tracker gate checked (SV-P2 closed; `ai-platform` reachable for corpora). DoD: [`../plan.md`](../plan.md) §RG-P0. Check each box the moment its task is done.
>
> **Nature of this phase:** S1 and S2 are **spikes** — they produce measurement reports with go/no-go decisions, not shipped code (throwaway harnesses are fine). S3 is real, shipped scaffolding under TDD. The spikes de-risk RG-P1 (sizing) and RG-P5 (the split's one empirical gate). Record numbers as ground truth — later phases cite them.

## S1 · Q-10 spike — self-hosting sizing + protocol parity {#s1}

Goal: prove the RS-4 endpoint swap (Lindat → self-hosted) is safe and size the RG-P1 backends. Deliverable: `docs/features/resolution/plan/spikes/q10-nlp-selfhosting.md` with a sizing table, a parity table, and a one-line verdict.
Verify: the spike report exists and contains (a) a parity table with ≥ 20 cases showing self-hosted == Lindat outputs, (b) cold-start + memory + throughput numbers for both tools, (c) an explicit "RS-4 swap: SAFE / NEEDS-WORK" verdict.

- [x] **T1.** Stand up NameTag 3 self-hosted: run `nametag3_server.py` in a container with model `nametag3-czech-cnec2.0-240830` (CPU). Confirm current server flags + model id via the upstream repo (or `context7`); record the exact launch command in the report. Expose `/recognize`-class endpoint.
- [x] **T2.** Stand up MorphoDiTa self-hosted: build `src/rest_server` in a container with model `czech-morfflex2.0+pdtc` (verify the in-tree `src/rest_server` builds; record the build + launch command). Expose the tag endpoint.
- [x] **T3 (parity — the assertion that gates RS-4).** Assemble ≥ 20 Czech inputs (the hero sentence + a spread from `seed.jsonl`). For each, call **both** Lindat and the self-hosted server; assert token/lemma/POS parity (MorphoDiTa) and entity-span/label parity (NameTag 3). Tabulate matches/mismatches; any mismatch gets a root-cause note (model-version drift vs protocol difference). This table is the RG-P1 protocol-parity fixture seed.
- [x] **T4 (sizing).** Under the pilot load shape, measure for each backend: cold-start time (container start → first successful response), steady-state p50/p95 latency for a single 7-op-equivalent call, memory RSS at idle and under load, and throughput (req/s) at 1/2/4 concurrency. Note NameTag 3 CPU-vs-GPU delta if a GPU is handy (optional).
- [x] **T5 (bulk-lemmatize sizing — couples to Q-11).** Measure MorphoDiTa throughput on a bulk request shaped like a fuzzy vocabulary refresh (e.g. 10k–100k short strings): per-string HTTP vs a batched request, at the front→backend hop. Record the cardinality/latency curve — this sizes the RG-P1 `BatchLemmatize` contract.
- [x] **T6.** Write the report: sizing table, parity table, the RS-4 verdict, and a recommended backend resource request (CPU/mem/replicas) for RG-P1's charts. Check the S1 box in the tracker; commit `RG-P0.S1: Q-10 self-hosting spike report`.

## S2 · Q-20 spike — deterministic span-gating precision {#s2}

Goal: the one empirical gate on the E2-ε resolver core. Does all-spans × batch-fuzzy score gating reach the live LLM value-extraction's precision? Deliverable: `docs/features/resolution/plan/spikes/q20-span-gating.md` with precision/recall vs the LLM baseline and a **go/no-go**.
Verify: the report exists with (a) precision/recall/F1 for deterministic gating vs the LLM baseline on both corpora, (b) a per-behavior breakdown (sibling-column, code-vs-name), (c) an explicit "E2-ε core: GO / GO-WITH-FALLBACK / NO-GO" verdict.

- [x] **T1.** Copy the corpora into the spike harness: `seed.jsonl` (50 Czech questions w/ expected tokens/lemmas/entities/function/args) + `ucetnictvi_entities_only.jsonl` (12 binding cases) from `ai-platform/agents/resolver/eval/`. Record their commit/hash in the report.
- [x] **T2.** Build the deterministic gating harness: for each question, propose candidate spans (noun heads from the dep parse + n-gram windows), fan them out via **batch fuzzy** over the seed vocabulary (source-tagged), and gate by score thresholds + entity-identity (mirror the live ENTITIES_ONLY thresholds: 0.5 / ambiguity gap 0.05 / exact 0.9999). Use the self-hosted NLP from S1 for the parse (lemma axis ON).
- [x] **T3 (baseline).** Run the live LLM value-extraction path (haiku) on the same inputs, or reuse the pilot's recorded outputs if available, as the precision baseline. Record its precision/recall on entity binding.
- [x] **T4 (the comparison).** Compute precision/recall/F1 for deterministic gating vs the LLM baseline on both corpora. Break out the two known-hard behaviors: **sibling-column** (same-table name-vs-code columns) and **code-vs-name** disambiguation — the LLM's two tricks. Note where deterministic gating over-generates (more fuzzy candidates → false positives) and where it misses.
- [x] **T5 (verdict + fallback shape if needed).** Write the go/no-go. If gating underperforms on sibling-column/code-vs-name, sketch the deterministic equivalent (e.g. code-pattern detection, sibling-column co-scoring) *or* the narrow agent-side pre-filter that keeps the **service** LLM-free — so RG-P5 starts from a decided position, not an open question.
- [x] **T6.** Finalize the report; check the S2 box; commit `RG-P0.S2: Q-20 span-gating precision spike report`.

## S3 · Scaffold — fold lib (S-2), proto renames, diagnostics {#s3}

Goal: the shared, shipped scaffolding every later phase depends on. TDD. Verify: `./gradlew :tatrman-server:<fold-lib-module>:test` green; renamed protos compile across all consumers; every `RG-*` diagnostic renders from a fixture.

- [x] **T1 (test first).** Create golden test-vectors for the S-2 fold: a table of `input → folded` covering Czech diacritics (`Zákazník→zakaznik`, `Octavie→octavie`, `pražských→prazskych`), mixed case, combining marks, and idempotency (`fold(fold(x)) == fold(x)`). Write them as a data-driven Kotest `FunSpec` against a not-yet-existing `Normalization.fold()`.
- [x] **T2.** Implement the shared **normalization lib** (S-2): `fold(text) = lowercase → NFD → strip combining marks`, as a small Kotlin module in `tatrman-server` shared libs (decide the home: a `ttr-text`-class lib vs a package inside `ttr-grounding-core` — record the choice in a header comment; contracts §6 leaves the physical home to P0). Expose `fold(String): String` only. Make the golden vectors pass.
- [x] **T3.** Wire the fold lib as a dependency of `ttr-fuzzy` (replace its `TextNormalizer.fold`) behind the same call site, with a characterization test asserting identical output to the old implementation on the S1/S2 corpora sample — proving the ≥5-copies consolidation is behavior-preserving. (Grounding/meta.search call-site swaps happen in their own phases; note the TODO markers.)
- [x] **T4 (test first).** For each proto package rename (`cz.dfpartner.{nlp,fuzzy,grounding,resolver}.v1 → org.tatrman.*`), add a compile-smoke test in each consumer module asserting the new package's generated types are importable. Watch them fail.
- [x] **T5.** Execute the proto renames: move packages, update `build`/codegen wiring, update every import in consumers (nlp adapters, fuzzy, grounding services, resolver, any Golem/agent stubs in-repo). **No behavior change** — pure rename. Fix the grounding proto `ResponseMessage` import wart while there (RS-21 fix-at-rename). Make the smoke tests green.
- [x] **T6 (test first).** `DiagnosticsTest.kt` (data-driven, the fixture IS contracts §8): every `RG-*` id exists in an `RgDiagnostics` registry, has a non-blank message template + suggestion, and its severity matches the table. Write against the not-yet-existing registry.
- [x] **T7.** Implement `RgDiagnostics` (id, severity, template, suggestion) + `RgDiagnosticException`; register all ids from contracts §8 with fixture-backed messages. Make the test green.
- [x] **T8.** All tests green; renamed protos compile across consumers; check the S3 box + the RG-P0 review box after the phase-exit `/review`; commit `RG-P0.S3: fold lib, proto renames, diagnostics registry`.
