---
effort: CEP — CalciteExtParser port (ttr-translator T-SQL extension parser)
repo_home: tatrman
code_home: [tatrman (packages/kotlin/ttr-translator), tatrman-server (consumer, published-artifact)]
state: executing
phase: CEP-P0 DONE (Fix B closed); CEP-P1 next
next: CEP-P1 (DATEADD/DATEDIFF/DATEPART/DATE_PART) — SqlLibraryOperators table + wire SYMBOL round-trip
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
