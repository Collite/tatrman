# docs-site — the public Tatrman docs

The source of [tatrman.org](https://tatrman.org). MkDocs Material + `mike`,
built and published by CI (RO-27).

## Run it locally

```bash
cd docs-site
pip install -r requirements.txt   # pinned — see the file's note on MkDocs 2.0
mkdocs serve                      # http://127.0.0.1:8000, live reload
```

Before pushing, run what CI runs:

```bash
mkdocs build --strict                        # broken nav/links/anchors = failure
bash ../scripts/check-docs-site-boundary.sh  # the site/repo-docs boundary
```

## Where a page goes

The nav is four **goal-shaped tracks** (RO-27 ①), one per reader's job:

| Track | The reader's job |
|---|---|
| **Get running** | *I have a database; show me the promise.* |
| **Model** | *I own the semantics.* |
| **Connect** | *I build agents.* |
| **Operate** | *I run this.* |

Pick the track by the reader's **job**, not by the subsystem the page describes.
Within a track, keep genres unmixed — no page is a lesson, a recipe, and a
reference at once. Scope per track: `project/server/design-corpus/design/docs-dx.md` §2.

**Cross-track rule: one concept, one home.** Tracks link to each other; they
never restate. Restating is how a wiki rots — two homes for one concept means one
of them is quietly wrong and the reader cannot tell which.

## This is not the engineering record

`docs/features/` and `docs/ecosystem/` are the engineering record and are **not**
site content. A page here may distill them into an explanation; it may never
mirror them or relative-link into them (CI enforces this — see the boundary
script above). Link outward to a GitHub URL if a page truly needs to point there.

## Versioning

`mike`, latest-only until 1.0.0 (RO-27 ④) — no version dropdown ships pre-1.0.
The wiring is in place so the debut's docs freeze is one command. Versions are
labelled with the **product** version (= the umbrella chart's `appVersion`, RO-24).

## Status

Scaffold (SV-P4·S1): structure, nav, and the build gate. The prose depth and the
quickstart's real commands arrive in **SV-P4·S6**, drafted against the umbrella
chart and verified on a live cluster. `TODO(S6)` markers across the tree are that
stage's inventory — `grep -rn "TODO(S6)" docs/`.
