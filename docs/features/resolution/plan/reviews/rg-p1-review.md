# RG-P1 phase-exit review — `ttr-nlp` self-hosting

> Verifies the RG-P1 Definition of DONE ([`../plan.md`](../plan.md) §RG-P1)
> against **runtime**, not progress-doc `[x]` marks. Performed 2026-07-12 after
> S1–S3 committed (tatrman-server `7e44e70` / `a9704f4` / `91e7fbd`, branch
> `rg-p1-nlp`). Method: full unit suite (81 green) + a **runtime smoke** — the
> real `NlpServicer` booted on a grpc.aio channel with the UFAL adapters pointed
> at the **pinned model** on Lindat (`NLP_UFAL_ENDPOINT_MODE=lindat`; Q-10 proved
> self-hosted output is byte-identical to Lindat's same-pinned-model output).

## DoD verification

| # | DoD clause | Verdict | Evidence |
|---|---|---|---|
| 1 | `cs` 7-op hero parse fully self-hosted + offline, lemmatizing `utržili→utržit`, `Octavie→Octavia`, `pražských→pražský`, `pobočkách→pobočka` | **✅ model-level / ⚑ container-level gated** | Runtime smoke: all four lemmas correct against the pinned model; `Octavie`→MISC entity. The *literal offline container* run is gated (see G1). DEP_PARSE needs the Stanza backend (not booted in the smoke). |
| 2 | `GetStatus` reports the matrix with `SELF_HOSTED_PINNED` tiers + real model versions | **✅** | Runtime: 14 rows; `cs/LEMMATIZE → morphodita ver=czech-morfflex2.0-pdtc1.0-220710`. Tier follows config (default `SELF_HOSTED_PINNED`; `REMOTE_UNPINNED` under the Lindat toggle, `RG-NLP-002`). |
| 3 | Every `Analyze`/`BatchLemmatize` echoes `used[]` (S-1 — no empty model; `RG-NLP-003` on violation) | **✅ (after F1 fix)** | Runtime: `S-1 used[] populated + no blank model: True`; one entry per served op. `iter_s1_violations` enforces it. |
| 4 | Bulk-lemmatize sustains the Q-11 cardinality within the Q-10 sizing envelope | **✅** | `Orchestrator.batch_lemmatize` = ONE batched `/tag` call (both hops); 10k-string test asserts a single call; Q-10 §4 measured ~77k str/s. |
| 5 | Q-10 protocol-parity fixtures pass against the self-hosted backends | **⚑ gated** | `tests/component/` reproduce Q-10's method (self-hosted ⇄ Lindat, pinned); the hero lemmas + NER pass live against the pinned model. Full green needs the self-hosted images up (G1). |
| 6 | Unsupported language returns the degrade floor, labelled | **✅** | Runtime (`de`): tokens via `FloorEngine`, `floor_lemmas` = fold, `codes=['RG-NLP-010']`, `used=[('TOKENIZE','floor')]`. No 500. |

## Findings

- **F1 (bug, FIXED + regression test).** `load_config`'s default `config.yaml`
  path resolved to `src/config.yaml` (`parent.parent`) instead of the service
  root (`parent.parent.parent`). Outside the container (no `CONFIG_FILE`) the
  file was never found, so every backend silently dropped to an empty-model
  `AppConfig()` default — a latent **S-1 violation** (Lindat then picked its own
  model; `Octavie→octavie` instead of `Octavia`). The unit tests hid it (they
  build `AppConfig` directly); the runtime smoke caught it. Fixed to resolve the
  service-root path; `tests/test_config_load.py` guards it. The container was
  unaffected (it sets `CONFIG_FILE` explicitly).
- **Observation (not a defect).** Isolated-token batch lemmatization differs
  from in-context (`Octavie→octavie` in `BatchLemmatize` vs `Octavia` in the
  full-sentence `Analyze`) — MorphoDiTa's context-free behavior, expected for the
  vocabulary-normalization use (RG-P2 fuzzy lemma axis).

## Gated (carried out of RG-P1)

- **G1 — S2.T7 literal offline container run.** Blocked on the LINDAT DSpace-7
  model-download URL (the legacy handle URL serves the SPA; the working REST
  bitstream URL is in the ai-platform Q-10 harness). Both backend Dockerfiles
  take `MODEL_URL` as a build-arg and fail loudly on the SPA. Once the images
  build: `docker compose -f docker-compose.offline.yml up` →
  `just test-py services/ttr-nlp -m component` closes clauses 1 (offline) + 5.
  The MorphoDiTa image's C++ build recipe is validated (compiles); only the
  model fetch is open.

## Verdict

RG-P1 is **code-complete and runtime-verified** at the pinned-model level: the
gRPC contract, S-1 `used[]`, capability matrix, batch path, and degrade floor
all behave correctly end-to-end. One real bug (F1) was found and fixed. Two DoD
clauses remain **gated on G1** (the literal self-hosted offline run), which is an
infra/model-fetch task, not a code gap. Recommend proceeding to RG-P2 with G1
tracked as the RG-P1 residual.
