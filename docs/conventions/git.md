# Git

Hosted on GitHub (`origin`). PRs are opened and merged with the `gh` CLI.

- Two permanent branches: `main` (always deployable) and `dev` (integration). All work
  branches off `dev` (`feature/...`, `fix/...`, `chore/...`) and merges back into `dev` via
  PR; `dev` merges into `main` only at a release point, via its own PR. PRs are merged
  with a merge commit (no squash, no rebase). A work branch is deleted, locally and on
  `origin`, once it is merged.
- Commits: English, imperative mood, Conventional Commits prefixes (`feat`, `fix`, `chore`,
  `docs`, `test`, `build`). Atomic - one concern per commit.
- No AI/tool attribution in commit messages or PR descriptions - no `Co-Authored-By:`
  trailer, no "Generated with ..." line. A commit is the subject line plus an optional body,
  nothing else.
- `.env` (and any local secrets file) is always in `.gitignore`, no exceptions - only
  `.env.example` with placeholders is committed.
- Before every commit: `git status` to confirm nothing unintended is staged (build output,
  local config, secrets).
