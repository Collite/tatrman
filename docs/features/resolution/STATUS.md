---
effort: RG — Resolution & Grounding (= SV-P3 body)
repo_home: tatrman (corpus)
code_home: [tatrman-server (branch rg-p3-grounding), tatrman, ai-platform (lab bench)]
state: executing
phase: RG-P0 ✅ · RG-P1 S1 ✅ (S2/T7 offline run open) · RG-P2 ✅ · RG-P3·S0 7/8
next: wrap P3·S0·T8 (needs ⚑ RS-33) → P3·S1 grounding extraction → S2 → P4 lexicon grammar (4.3 window) → P5 resolver core → P6 doors/parity
blocked_on: ["⚑ RS-33 ratification (Bora)", "LINDAT model URL for the P1 offline container run", "⚑ ttr-translator grounding-function gap — arc call (Bora)"]
gates: [SV-P3·F1/F2, SV-P4·S5 conformance seeds]
updated: 2026-07-13
---
Global DONE = the SV-P3 parity bar (RS-2). Grammar rule: RG-P4 holds the 4.3 window — dot-path
S0 and optimizer Z-P5 queue behind it. Detail: `plan/tasks/00-task-management.md`; decisions:
`design/00-control-room.md` (RS-1..33). Lane 1 of the 3-lane plan
(design repo `ecosystem/open-work-260713.md` §3).
