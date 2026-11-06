# Git

This project is not a git repository yet. Whether to `git init` it, and whether to add a
remote, is a separate question for the developer - never assumed or done proactively.

Once it is one:

- Two permanent branches: `main` (always deployable) and `dev` (integration). All work
  branches off `dev` (`feature/...`, `fix/...`, `chore/...`) and merges back into `dev` via
  PR; `dev` merges into `main` only at a release point.
- Commits: English, imperative mood, Conventional Commits prefixes (`feat`, `fix`, `chore`,
  `docs`, `test`, `build`). Atomic - one concern per commit.
- No AI/tool attribution in commit messages or PR descriptions - no `Co-Authored-By:`
  trailer, no "Generated with ..." line. A commit is the subject line plus an optional body,
  nothing else.
- `.env` (and any local secrets file) is always in `.gitignore`, no exceptions - only
  `.env.example` with placeholders is committed.
- Before every commit: `git status` to confirm nothing unintended is staged (build output,
  local config, secrets).
