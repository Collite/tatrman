---
name: modeler-repo-path
description: The modeler repo moved to a new path on disk (June 2026)
metadata: 
  node_type: memory
  type: project
  originSessionId: 1ef9fbf5-9d00-42e4-8c21-4ee4a719a307
---

The `modeler` repo was moved from `~/Dev/modeler` to **`~/Dev/collite-gh/modeler`** on 2026-06-09.
The old `~/Dev/modeler` is being deleted. Always work against the new path; if a session is still
mounted to the old `~/Dev/modeler`, ask the user to connect `~/Dev/collite-gh/modeler` as the
workspace folder.

The linter/formatter/autofix design + plan live under `docs/features/linter/` (design.md and
plan/{architecture,contracts,implementation-plan}.md + plan/tasks/). These were authored in the old
repo and moved by the user into the new one.

Note: unclear whether the companion [[ai_platform_repo]] (referenced as `~/Dev/ai-platform` in the
P0 task list's sync step) also moved — verify before relying on that path.
