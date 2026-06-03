Review the code generated; especially against the task list. Look for architectural mistakes, not clean duty separation, wrong modularization etc. I want you to mainly check the cleanliness and understandability of the code, the architectural solidness, clear modularity and open paths to future development and extendability.

The work is done according to a plan, so always check what the plan was and if there are any (undocumented) deviations. Plans live per-release under `docs/<release>/plan/` (e.g. `docs/v1/plan/`, `docs/v1-1/plan/`); see the `implementation-plan*.md` at the root of each and the `tasks-phase-*.md` files for the task list of each stage. Point out the deviations to the task list.

Write the findings to the file `docs/<release>/implementation/review-xxx.md` (xxx being the sequential numbering, continuing the existing sequence — v1 reached review-024), and prepare a clear task list `docs/<release>/implementation/tasks-review-xxx.md` with checkboxes that the developer can follow. The developer sometimes has problems following the task list, please, be very specific with the instructions, do not leave any room for misuderstandings what exactly needs to be done.

