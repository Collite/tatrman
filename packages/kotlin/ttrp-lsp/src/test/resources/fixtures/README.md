# ttrp-lsp test fixtures

- `hero.ttrp` / `hero-er.ttrp` — copied from the P1/P2 corpus
  (`packages/kotlin/ttrp-graph/src/test/resources/fixtures/graph/`), aligned to the
  shared erp-project world `acme.worlds.dev` (contracts §8). The LSP harness resolves
  them against that world via `FixtureProjectResolver`.
- `hero-broken.ttrp` — the hero with one filter condition changed to `amount == 0`,
  yielding the deterministic diagnostic `TTRP-EQ-001` (S9: `==` rejected outside
  TTR-pandas; suggested alternative "use `=`").
