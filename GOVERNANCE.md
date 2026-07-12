# Governance

This document describes how Tatrman is governed: who decides, how changes to the
core are made, and how the project stays a *standard* rather than just a codebase.
It is deliberately short and honest.

## Steward model

Tatrman is a **steward-led** project. The steward is **Collite** (Collite s.r.o.),
which owns the trademark, the public infrastructure (the GitHub organization, the
`tatrman.org` domain), and the release-signing keys, and acts as benevolent
dictator for the project's direction (a BDFL model).

This is not a foundation, and it does not pretend to be one. A steward model is
honest about where decisions currently sit; a move to a foundation is the named
**graduation path** if the standard takes hold and the community outgrows a single
steward.

## How core changes are made — the RFC process

Tatrman's design discipline **is** its governance, made visible. Changes to the
**core** — the language (grammar, semantics), the published contracts, the plan
hub — go through a public design effort, the same *control-room* method we use
internally:

1. **Diverge** — a problem is framed and the solution space is explored openly
   (an options document, alternatives named, trade-offs written down).
2. **Converge** — a decision is taken and recorded in an **append-only decision
   log**, with the rejected alternatives and the reasons kept alongside it.
3. The resulting contract change ships with tests (conformance where applicable).

We will link a public worked example of this process once the design corpus is
public. Until then, the decision logs already in this repository under
`docs/**/design/` are the pattern.

**Edges** — SPI-shaped additions (workers, connectors, emit plugins) — do **not**
need an RFC. They go through ordinary PR review plus conformance. See
[CONTRIBUTING.md](./CONTRIBUTING.md) for the edges-vs-core split.

## Maintainers

The maintainer set starts with Collite. Additional maintainers are added on the
basis of a **sustained track record** of good contributions and good judgment —
never as a courtesy, and never because someone shipped one large PR. Every merged
contribution is a maintenance obligation the maintainers take on; we add people
who have shown they will help carry it.

## Conformance is the certification lever

Tatrman's real control point is not copyright — it is **conformance**. An
implementation earns the right to call itself "Tatrman-conformant" by passing the
core conformance suite (see the MCP surface contract and the conformance
conversation suite). That mechanism, and what may and may not be called "Tatrman",
is set out in the [Trademark Policy](./TRADEMARKS.md). The conformance program is
marked as **coming with 1.0** — it is being authored, not yet claimable.
