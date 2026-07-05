# Day 1 — Git workflow on the course repo

> Starting point: this repository. No `starter/` — you practise on real history.

## Objective

Run the full feature-branch workflow end to end: branch, commit cleanly through the three trees,
create and resolve a merge conflict, rebase to a linear history, rescue a "lost" commit with
`reflog`, and review a partner's pull request.

## Concepts in play

- [The three trees](../../docs/content/day-01/index.md#the-three-trees)
- [Rebase vs merge](../../docs/content/day-01/index.md#rebase-vs-merge)
- [Undo: nothing is really lost](../../docs/content/day-01/index.md#undo-nothing-is-really-lost)
- [Branching strategy](../../docs/content/day-01/index.md#branching-strategy-for-microservices)
- [Code review for intent](../../docs/content/day-01/index.md#code-review-for-intent)

## Steps

1. `git switch -c feature/<your-name>-notes` and add a line to a shared scratch file
   (`labs/day-01/scratch.md`) — your name and one thing you learned. Use `git status` after each
   step and watch your change move from working directory → staged → committed.
2. Commit with a clear message. Make a second commit, then squash the two with
   `git rebase -i HEAD~2` (or `git commit --fixup` + `--autosquash`).
3. Recover from a mistake on purpose: `git reset --hard HEAD~1` to "lose" your last commit, then
   `git reflog` to find its hash and `git reset --hard <hash>` to bring it back.
4. Pair up: both partners edit the **same line** of `scratch.md` on their own branches and
   merge one, then rebase the other onto `main` — resolve the conflict.
5. Open a pull request (or, locally, `git switch main && git merge --no-ff`) and have your
   partner review it: is the intent clear from the message alone?
6. Delete the merged branch.

## Acceptance criteria

- [ ] A linear, rebased feature branch with one coherent commit.
- [ ] A commit deliberately "lost" with `reset --hard` and recovered via `reflog`.
- [ ] A conflict created and resolved deliberately (not auto-merged).
- [ ] A reviewed change merged to `main`; branch deleted.
- [ ] `git log --graph --oneline` reads as a clear story.

## Stretch goals

- Use `git bisect` to find which commit introduced a planted bug.
- Try `git worktree add` to check out a `checkpoint/day-NN` tag alongside `main`.
- Inspect the plumbing: `git cat-file -p HEAD` and `git cat-file -p HEAD^{tree}`.
