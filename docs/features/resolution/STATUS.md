---
effort: RG — Resolution & Grounding (= SV-P3 body)
repo_home: tatrman (corpus)
code_home: [tatrman-server (master), tatrman, ai-platform (lab bench)]
state: executing
phase: RG-P0 ✅ · RG-P1 ✅ ACCEPTED 2026-07-13 (offline run + component parity, Bora) · RG-P2 ✅ · RG-P3 ✅ merged+reviewed · RG-P4 IN PROGRESS (branch rg-p4-lexicon: grammar 4.4 + parser + desugar + LSP + formatter + Python/Kotlin parity = S1 DONE; S2 CORE DONE — RS-32 legacy migration + deprecations + migrate-lexicon codemod + descriptions fold + canonical snapshot serializer; one S2 sub-item remains = valueLabels A4-β widening) · P5/P6 remain
next: RG-P4·S2 valueLabels A4-β widening (focused cross-target grammar follow-up) → S2 phase-exit /review; then RG-P5 resolver. Pre-existing/separate: Python semantics-block port to clear py-vs-ts red (59/60)
blocked_on: []
gates: [SV-P3·F1/F2, SV-P4·S5 conformance seeds]
updated: 2026-07-13
---
Global DONE = the SV-P3 parity bar (RS-2). RG-P1 acceptance: five images built (DSpace URLs
recovered from the Q-10 harness), offline compose run green, hero parse + GetStatus verified,
component parity suite green — evidence in `plan/reviews/rg-p1-review.md` (G1 CLOSED) +
`rg-p1-offline-run-checklist.md`. Remember to paste the two MODEL_SHA256 digests into the
backend Dockerfiles (digest-pin — SV-P3·F1·T4 wants them). The ttr-translator grounding-function gap (⚑ from P3·S0) is RESOLVED — CEP closed 2026-07-13; functions restored at kotlin-translator 0.9.5/0.9.6. Grammar rule unchanged: RG-P4 holds
the 4.3 window — dot-path S0 and optimizer Z-P5 queue behind it. RG-P5 S1·T0 re-checks the Q-20
verdict. Detail: `plan/tasks/00-task-management.md`.
