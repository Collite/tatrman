> **⚠ HISTORICAL (banner 2026-07-13):** a 2026-06-26 machine-bridge snapshot, wholly pre-fork
> (still says "modeler"). Nothing here is live — kept for history only.

# Cowork handoff bundle

Purpose: let a Claude/Cowork session **on another machine** (the laptop) pick up the
modeler work where this Mac left off. The code already travels via GitHub; this folder
carries the two things that don't — **Claude's memories** and a **summary + transcripts**
of the in-progress multidimensional (MD) feature work.

Created 2026-06-26.

## What's here

```
docs/handoff/
├── README.md                       ← this file
├── handoff-summary.md              ← project state + where we left off (read this first)
├── memory/                         ← copies of Claude's 5 memory files from this machine
│   ├── MEMORY.md                   ← the index (one line per memory)
│   ├── md-model-design.md
│   ├── ai_platform_repo.md
│   ├── ai_models_repo.md
│   └── modeler_repo_path.md
└── md-feature/
    ├── db-er-binding-summary.md    ← summary of the DB & ER schemas and the binding concept
    └── brainstorm-transcript.md    ← transcript of the MD design + planning sessions
```

The authoritative MD design lives in the repo proper at `docs/features/md/` (design.md,
contracts.md, grammar-md-changes.md, map-catalog.md, and the `plan/` task lists). This
folder is a portable companion, not a replacement.

## How to re-import the memories on the laptop

Claude's memory is stored per-machine in a local Cowork "space" folder, so the files
don't sync automatically — but they're plain markdown and easy to re-seed.

1. Clone/pull the modeler repo on the laptop and open it as the Cowork workspace folder.
2. Tell Claude:

   > Import the memory files in `docs/handoff/memory/` into your memory — copy each into
   > your memory directory and add the index lines to your MEMORY.md.

   Claude will write the four fact files into the laptop's memory directory and merge the
   index lines into that machine's `MEMORY.md`. (If `MEMORY.md` already has entries, it
   merges rather than overwrites.)
3. Sanity check by asking Claude what it remembers about the MD feature.

Note: paths in the memories assume repos at `~/Dev/collite-gh/modeler`,
`~/Dev/ai-platform`, and `~/Dev/ai-models`. If the laptop clones them elsewhere, tell
Claude the new paths so it can update the memories.

## What does NOT transfer

- **Live chat history.** Conversation transcripts are tied to this machine's Cowork
  install; they can't be "logged into" elsewhere. `handoff-summary.md` and the captured
  `md-feature/brainstorm-transcript.md` stand in for that context.
- The foundational MD brainstorm dialogue (origin session `49a16e4b`) wasn't separately
  re-exportable, but its full outcome is `docs/features/md/design.md` plus the two session
  transcripts captured here.
