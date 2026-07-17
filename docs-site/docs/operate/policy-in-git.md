<!-- SPDX-License-Identifier: Apache-2.0 -->
# Policy in git

*How-to. Writing, reviewing and shipping policy the same way you ship the model.*

Tatrman's access policy — who may see which rows, which columns are masked, which roles a question
requires — is not clicked into a console. It lives in an **open validator store**: text, versioned
in git, reviewed in a pull request, and shipped like any other change. This is deliberate. Policy
that lives in a UI has no history, no review, and no diff; policy that lives in git has all three.

## The shape of it

- **Policy is text in a store** (HOCON), versioned beside the model it governs.
- **Robots write through git.** The tools that propose changes — `ttr import-schema`, a harvest
  connector — never mutate live configuration. They open a pull request. A human reviews and merges;
  the merge is the deployment. Nothing changes under you without a diff you approved.
- **The validator reads the store.** At request time the validator applies the policy from the
  store to the per-user identity the door forwarded, and records what it did in `pipelineWarnings`.
  Change the policy in git, ship it, and the next request reflects it.

## The workflow

1. **Edit** the policy in the store, in a branch — the same repository discipline you use for the
   model.
2. **Review** it in a pull request. Because it is text, a reviewer sees exactly what changed: a new
   row-level filter, a column newly masked, a role requirement added or removed.
3. **Ship** by merging. The running system picks up the merged policy; the change is auditable
   forever because it is a commit.

## Why this matters to you as an operator

- **Every access decision is auditable after the fact.** When someone asks "why could they see
  that?" six months later, the answer is a commit — the policy that was in force, who approved it,
  and when.
- **Least privilege is reviewable, not assumed.** A widening of access is a diff a second person
  signed off on, not a checkbox nobody remembers ticking.
- **The same review muscle covers model and policy.** You already review the model in git; policy
  rides the same rails, so there is no second, weaker change-management path around your governance.

Pair this with [OIDC and Keycloak](identity-and-oidc.md): identity says *who* is asking, policy in
git says *what they may see*, and `pipelineWarnings` on every answer says *what was applied*.
