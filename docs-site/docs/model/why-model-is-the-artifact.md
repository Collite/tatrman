<!-- SPDX-License-Identifier: Apache-2.0 -->
# Why the model is the deployment artifact

*Explanation. What you get by putting semantics in git instead of in a BI tool.*

In most stacks, "the model" is scattered: some meaning in a BI tool's semantic layer, some in a
warehouse view, some in the head of the analyst who left last year. Tatrman makes a different
choice — the model is a **text artifact you deploy**, versioned in git and shipped through a pull
request, the same way you ship code. This page is about why that choice pays off.

## The model is text, and text has history

Because a TTR-M model is `.ttrm` files in a repository:

- **Every change is a diff.** A new relation, a renamed attribute, a column newly masked — each is a
  reviewable change with an author and a date, not a silent edit in a console.
- **Review happens before deployment, not after an incident.** A second person sees what changed and
  why. Governance especially: widening who-can-see-what is a diff someone approved, not a checkbox
  nobody remembers ticking.
- **You can go back.** The model that was in force last quarter is a commit. "Why did it answer that
  then?" has an answer.

## One source of meaning, consumed many ways

The same model feeds the whole surface. The agent doors read it (`get_model`, `search`), the query
path validates against it, the understanding layer resolves against its vocabulary, and the
validator enforces its governance. There is no second, weaker copy of the meaning to drift out of
sync — the model *is* the meaning, and everything consumes it.

## The machine keeps the mirror; you keep the meaning

The layered design (see [the layers](layers.md)) is what makes "model as artifact" practical rather
than a burden. `ttr import-schema` owns the `db` mirror and keeps it current as your database
evolves — proposing changes as pull requests, never overwriting your work. The `er` meaning you
author is born once and stays yours. So the artifact is *alive*: the tool maintains the mechanical
half while your judgement accumulates in the half that matters.

## Governance is part of the artifact, not bolted on

Access policy is not a separate system you configure elsewhere. Roles live in the model; policy
lives in git beside it (see the Operate track's [policy in git](../operate/policy-in-git.md)); and
every governed answer carries `pipelineWarnings` saying what was applied. The thing you deploy is
also the thing that governs — so there is no gap between "what the model says" and "what the system
enforces."

## What this buys you

- A single, reviewed, versioned source of organisational meaning.
- Governance you can audit after the fact, because it is a commit.
- A model that stays current without losing the judgement you invested in it.
- A surface — agents, queries, resolution — that cannot drift from the model, because it is built
  from the model.

That is the whole bet: put the meaning in git, and everything downstream inherits its history,
review and honesty for free.
