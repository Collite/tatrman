---
effort: TTR-P v1 (+ optimizer Z parked)
repo_home: tatrman
code_home: [tatrman]
state: blocked
phase: P0–P7 done-claimed/reviewed except the v1 seal — T5.4.7 review + T5.4.8 A4 manual acceptance
next: Bora runs the A4 acceptance (needs 🐳 Docker PG+Polars env); then P6/P7 tails (conform three-ways, eval baseline 🔑, corpus 2→10)
blocked_on: ["🐳 Docker PG+Polars env (Bora)", "🔑 model API key (Bora)", "§4 design calls: sort/LIMIT carriage · bare-fragment priority · SQL rejects semantics (Bora)"]
gates: ["optimizer Z pre-flight #1", "DS-P5 live path (via C1-f designer-server + ttrp/run)"]
updated: 2026-07-13
---
"The one still-open phase-DONE box" (acceptance-guide.md) — the single biggest "is v1 real"
checkpoint. Optimizer Z (= TTR-P v2, `optimizer/`) is not started and waits on this seal.
`.ttrl` migration (TP-5 in the portfolio) unblocks the Designer's live processing path.
Detail: `implementation/v1/{tasks-overview,next-steps}.md`.
