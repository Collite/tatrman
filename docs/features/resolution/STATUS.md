---
effort: RG — Resolution & Grounding (= SV-P3 body)
repo_home: tatrman (corpus)
code_home: [tatrman-server (master), tatrman, ai-platform (lab bench)]
state: executing
phase: RG-P0 ✅ · RG-P2 ✅ · RG-P3 ✅ MERGED+REVIEWED 2026-07-13 · RG-P4·S1 CODE-COMPLETE (branch rg-p4-lexicon: grammar 4.4 + parser + desugar + LSP + formatter + cross-target Python/Kotlin, T1–T7; lexicon conformance green TS↔Kotlin 100% + Python; ⚠ pre-existing semantics-block Python gap on 59/60 flagged, NOT RG-P4) · RG-P1·S2/S3 still open
next: RG-P4·S1 phase-exit /review, then RG-P4·S2 (search-block slim + RS-32 legacy migration + canonical propagation + valueLabels widening). Separately: Python semantics-block port to clear the pre-existing py-vs-ts red (59/60).
blocked_on: ["LINDAT model URL for the P1 offline container run", "ttr-translator grounding-function gap — CEP arc is the vehicle (extended parser restored at CEP-P0)"]
gates: [SV-P3·F1/F2, SV-P4·S5 conformance seeds]
updated: 2026-07-13
grammar_version: 4.4 (TTR-M lexicon surface — additive; `.ttrl` held 4.3)
---
Global DONE = the SV-P3 parity bar (RS-2). RG-P3 delivered grounding extraction +
ttr-grounding-core kernel, geo posture (RS-19), Q-18 fiscal quarters, kind-named
grounding.{time,geo,money}:v1 capabilities, and the runnable eval corpus (Q-19). Grammar rule
unchanged: RG-P4 holds the 4.3 window — dot-path S0 and optimizer Z-P5 queue behind it. RG-P5
S1·T0 re-checks the Q-20 verdict. Detail: `plan/tasks/00-task-management.md`.
