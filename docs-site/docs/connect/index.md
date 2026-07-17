# Connect

> **I build agents.**

Tatrman speaks MCP. Your agent asks questions through a small, versioned surface
of doors and tools, forwards the end user's identity, and gets back an answer
*plus* the trail of everything the platform did to keep that answer legal.

This track is the contract, written for you rather than for us.

## Start here

- [**Your first agent against the door**](first-agent.md) — the tutorial: ask one
  question, read the envelope, see the provenance.

## The reference

- [**The doors and tools**](doors-and-tools.md) — every door, every tool, its
  arguments, the result envelope, and the refusal modes.
- [**The identity contract**](identity.md) — on-behalf-of: what your agent MUST
  forward, what comes back, and why an agent that guesses instead of refusing is a bug.
- [**The compatibility rule**](compat.md) — what the platform may change under you,
  and what it may not.

## Prove it

- [**The conformance suite as your test harness**](conformance-harness.md) — the same
  fixtures the platform gates on, pointed at your agent. Each fixture doubles as a
  worked example, so it cannot rot into fiction.

_Framework specifics (LangGraph, Koog, MCP-capable assistants) differ only in how you
register a door and attach the bearer — the tutorial's four steps are the same
everywhere; framework-specific how-tos land as the reference agents publish._
