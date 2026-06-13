# Claude Code Instructions — Ledger

## Read first, act second

Before starting any task — adding a feature, fixing a bug, refactoring — read `project.md` in full. It contains the architecture, conventions, common pitfalls, build instructions, and development patterns for this project. Do not rely on memory alone; the file is the authoritative reference.

## Keep project.md current

After completing any task that introduces a new pattern, reveals a new pitfall, changes the architecture, adds a new screen/VM/entity, or updates the build process — update `project.md` to reflect it. Update the relevant section inline; do not append a changelog at the bottom.

Things that always warrant an update to `project.md`:
- New screen added → update the structure section
- New Rust entity or method → update the data entities table
- New dependency added → update the tech stack table
- New common error encountered and fixed → add to Common Pitfalls
- New preference key → note the pattern used
- Build process changed → update the build section
- New architectural layer or repository introduced → update the architecture section

## Memory

Auto-memory files live in `.claude/projects/.../memory/`. Save user preferences, feedback, and project context there so they persist across sessions. Do not save ephemeral task details to memory.
