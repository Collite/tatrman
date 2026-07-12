# Q-10 spike — NLP self-hosting sizing + protocol parity

> **RG-P0.S1.** Gates **RS-4** (the Lindat → self-hosted endpoint swap) and sizes
> the **RG-P1** backends. Spike = measurement report, not shipped code. Harness
> (reproducible): [`ai-platform/infra/nlp/spikes/q10/`](../../../../../../../ai-platform/infra/nlp/spikes/q10) — `README.md` there has the run commands; raw outputs in its `results/`.
>
> **Verdict (one line): RS-4 swap = SAFE.** With the same model pinned on both
> sides, self-hosted output is **byte-identical** to Lindat across 22 Czech inputs
> (0 mismatches), and both backends size comfortably for the pilot load shape.

## 1. What was stood up

| Backend | Model (self-hosted == Lindat-available) | Server | Image | Endpoint |
|---|---|---|---|---|
| **MorphoDiTa** | `czech-morfflex2.0-pdtc1.0-220710` (= `czech-morfflex2.0+pdtc`), LINDAT hdl:11234/1-4794 | `morphodita_server` built from `ufal/morphodita` **v1.11.3** (`make server`) | **154 MB** | `POST /tag` (input=untokenized, output=vertical) |
| **NameTag 3** | `nametag3-czech-cnec2.0-240830`, LINDAT hdl:11234/1-5677 | `nametag3_server.py` from `ufal/nametag3` **v3.2.0**, CPU torch, base PLM `ufal/robeczech-base` baked | **5.49 GB** | `POST /recognize` (input=untokenized, output=conll) |

Both models are **CC BY-NC-SA 4.0** (FI-4 legal item — gates *publishing* images,
not local build/run; parity/sizing here are local).

**Launch commands (recorded for RG-P1):**

```bash
# MorphoDiTa (make server -> rest_server/morphodita_server)
morphodita_server --connection_timeout=120 --max_request_size=65536 \
  8080 czech /models/czech-morfflex2.0-pdtc1.0-220710/czech-morfflex2.0-pdtc1.0-220710.tagger "<ack>"

# NameTag 3
python3 ./nametag3_server.py 8001 nametag3-czech-cnec2.0-240830 \
  nametag3-czech-cnec2.0-240830:czech:cs /models/nametag3-czech-cnec2.0-240830/ "" "<ack>"
```

> **Build note (aarch64):** MorphoDiTa v1.11.3's build system rejects `aarch64`
> and hard-codes x86 SSE flags; a small patch (`morphodita/patch-aarch64.py`) adds
> a native arm64 branch. No-op on x86_64. NameTag 3/torch builds natively on arm64.

## 2. Protocol parity — the RS-4 gate

**Method.** Same model pinned on **both** sides (Lindat serves the exact
`czech-morfflex2.0-pdtc1.0-220710` and `nametag3-czech-cnec2.0-240830` we
self-host), so a mismatch would be an implementation/tokenization difference, not
model-version drift. MorphoDiTa asserts `(form, lemma, tag)` triples; NameTag 3
asserts `(entity_text, label)` spans. 22 Czech inputs = the hero sentence + 21
from `seed.jsonl`.

**Result: 22/22 exact parity, both tools, 0 errors, 0 mismatches.**

| id | input | MorphoDiTa | NameTag 3 |
|---|---|---|---|
| hero | Kolik jsme utržili za Octavie v pražských pobočkách za poslední fiskální čtvrtletí? | ✅ | ✅ |
| cs-q-001..021 | a spread of seed.jsonl (ORG/LOC/DATE/PRODUCT/INV + no-entity cases) | ✅ ×21 | ✅ ×21 |

Full per-row table + raw tuples: harness `results/parity.md` / `results/parity.json`.

The hero sentence lemmatizes exactly as the RG-P1 DoD requires (self-hosted, offline):
`utržili→utržit`, `Octavie→Octavia`, `pražských→pražský`, `pobočkách→pobočka`;
NameTag 3 tags the CNEC entities identically to Lindat. **This is the RG-P1
protocol-parity fixture seed.**

> **Separate, non-gating drift (not a parity failure).** The *live ai-platform
> adapter* lets Lindat pick its **newer defaults** — MorphoDiTa `czech-morfflex2.1-pdtc2.0-250909`
> and a `nametag3-czech` alias — instead of pinning a model. That is exactly the
> unpinned-model risk (`RG-NLP-002`) the RS-4 swap removes: RG-P1 pins the model
> id per backend (S-1). Parity above proves the *self-hosted* pinned models match
> Lindat's *same-id* models; the swap is safe precisely because it also pins.

## 3. Sizing

Measured on **Apple M4 Pro (arm64, 14 cores)**, native (not emulated), CPU-only.
⚠️ **Arch caveat:** these are arm64-dev-box numbers; the production cluster is
x86 — treat as order-of-magnitude for the RG-P1 resource requests and re-baseline
on target hardware. Single call = a full parse of the hero sentence.

| Metric | MorphoDiTa | NameTag 3 |
|---|---|---|
| Idle RSS | **256 MB** | **909 MB** |
| Loaded RSS | 248 MB | **1.09 GB** |
| Cold start (recreate → 1st 200) | **2.0 s** | **5.2 s** (warm page cache) |
| Latency p50 / p95 | **4.8 / 7.5 ms** | **72 / 95 ms** |
| Latency min / max | 3.7 / 9.2 ms | 62 / 116 ms |
| Throughput @1 / 2 / 4 conc. | **112 / 225 / 339 rps** | **11.6 / 12.0 / 13.4 rps** |

**Reading it.**
- **MorphoDiTa** is featherweight: ~250 MB, sub-10 ms, throughput scales
  near-linearly with concurrency (C++ core, no GIL). One replica is ample.
- **NameTag 3** is the heavy one: ~1.1 GB RSS, ~72 ms p50, and throughput
  **flat across in-process concurrency** (~12 rps) — CPU torch is thread-bound,
  so **scale by replicas, not by concurrency**. Each replica ≈ 12 rps.
- **Cold start is ~5 s, not the feared 180 s.** The live system's 180 s resolver
  timeout was set against cold Stanza/UFAL over the network; a self-hosted NameTag 3
  with the PLM baked warm-loads in ~5 s (first-ever load adds the ~500 MB checkpoint
  disk read). Readiness/latency budgets can be set, not discovered — watch item closed.

## 4. Bulk-lemmatize (T5 — sizes the `BatchLemmatize` contract, couples to Q-11)

MorphoDiTa throughput on a fuzzy-vocabulary-refresh shape (N short Czech strings),
**per-string HTTP vs one batched `/tag`** (newline-joined → one tagger pass):

| N strings | Batched | Per-string HTTP |
|---|---|---|
| 1,000 | 0.033 s — **30.4k str/s** | 937 str/s |
| 10,000 | 0.156 s — **64.2k str/s** | 821 str/s |
| 50,000 | 0.662 s — **75.5k str/s** | — |
| 100,000 | 1.303 s — **76.8k str/s** | — |

- **Batching wins ~80×** and saturates at **~77k strings/s**; a 100k-term
  vocabulary refresh completes in **~1.3 s** at the backend hop. Per-string HTTP
  is round-trip-bound at ~900/s regardless of N.
- **One config knob:** MorphoDiTa's default `--max_request_size=1024` KB rejects a
  single 100k-string call (`413`). Raise it (spike uses **65536 KB**) **or** the
  `ttr-nlp` front chunks the batch to fit. → **`BatchLemmatize` shape:** front
  accepts the full list, chunks to the backend's `max_request_size`, and forwards
  newline-joined; the Q-11 cardinality (≤100k) fits one raised-limit call.

## 5. Recommended RG-P1 backend resource requests

Derived from §3–§4 (arm64 numbers; **verify on x86 in RG-P1**, add headroom):

| Backend | request cpu / mem | limit mem | replicas | notes |
|---|---|---|---|---|
| **MorphoDiTa** | 250m / 384Mi | 512Mi | 1 (HPA optional) | cheap; C++; scales with concurrency. Set `--max_request_size` ≥ 65536 for BatchLemmatize. |
| **NameTag 3** | 1 cpu / 1.5Gi | 2Gi | **2+ (scale by replicas)** | torch CPU thread-bound (~12 rps/replica); readiness probe ~15 s; GPU optional (not measured — CPU meets pilot). |
| **ttr-nlp front** | 100m / 128Mi | 256Mi | 2 | engine-free (langid + routing); no model memory. |

- **Image slimming (RG-P1):** the NameTag 3 image is **5.49 GB** (torch CPU +
  transformers + baked RobeCzech + training-only deps `keras`/`tensorboard`/
  `datasets`/`peft`). Dropping the training deps and using an inference-only base
  should cut this materially — a build task for RG-P1, not a blocker.
- **Offline confirmed:** NameTag 3 runs with `HF_HUB_OFFLINE=1` / `TRANSFORMERS_OFFLINE=1`
  (PLM baked); MorphoDiTa needs no network. No Lindat/HF egress at run time — the
  determinism/offline claim (RG-P1 DoD) holds.

## 6. Verdict

**RS-4 (Lindat → self-hosted) = SAFE.**
- Protocol parity is **exact** (22/22, both tools) when the same model is pinned —
  the swap changes only the endpoint URL + pins the model id; outputs do not move.
- Both backends size within a modest envelope; NameTag 3 is the cost center and
  scales by replicas.
- Cold start (~5 s) and the batch path (~77k str/s) are both well inside the pilot
  load shape. The two watch items (NameTag cold start; batch at two hops) are
  characterized, not open.

**Carried into RG-P1:** pin model ids per backend (S-1, closes `RG-NLP-002`); set
NameTag 3 replicas ≥ 2; raise MorphoDiTa `--max_request_size`; slim the NameTag 3
image; use the §2 parity tuples as the protocol-parity fixture; **re-baseline §3
numbers on the x86 target cluster.**
