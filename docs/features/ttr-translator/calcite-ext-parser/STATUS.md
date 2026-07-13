---
effort: CEP — CalciteExtParser port (ttr-translator T-SQL extension parser)
repo_home: tatrman
code_home: [tatrman (packages/kotlin/ttr-translator), tatrman-server (consumer, published-artifact)]
state: executing (CEP-P0..P2 done+pushed; P3 release maintainer-gated)
phase: CEP-P0+P1+P2 DONE + pushed; P3 R1 fixed; release gated on Bora
next: Bora cuts kotlin-translator/v0.9.6 (Maven Central) + tatrman-server pin bump
blocked_on: []
gates: []
updated: 2026-07-13
---
Restores the custom-parser codegen (`CalciteExtParserImpl` via FMPP/JavaCC) dropped when
ttr-translator was extracted from ai-platform `query-translator`. Closes the "Fix B" gap
found porting ai-platform `7307f3f9` (SqlParser could not use the extended parser factory
because it does not exist here). Reference implementation: `~/Dev/ai-platform/shared/libs/kotlin/query-translator`
(decision D7; Calcite 1.41.0, identical to tatrman's pin). Detail: `plan/plan.md`;
task tracker: `plan/tasks/00-task-management.md`. Sibling of the extraction arc under
`docs/features/ttr-translator/implementation/`.
